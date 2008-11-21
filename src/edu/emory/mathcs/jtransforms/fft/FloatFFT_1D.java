/* ***** BEGIN LICENSE BLOCK *****
 * Version: MPL 1.1/GPL 2.0/LGPL 2.1
 *
 * The contents of this file are subject to the Mozilla Public License Version
 * 1.1 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTY OF ANY KIND, either express or implied. See the License
 * for the specific language governing rights and limitations under the
 * License.
 *
 * The Original Code is JTransforms.
 *
 * The Initial Developer of the Original Code is
 * Piotr Wendykier, Emory University.
 * Portions created by the Initial Developer are Copyright (C) 2007
 * the Initial Developer. All Rights Reserved.
 *
 * Alternatively, the contents of this file may be used under the terms of
 * either the GNU General Public License Version 2 or later (the "GPL"), or
 * the GNU Lesser General Public License Version 2.1 or later (the "LGPL"),
 * in which case the provisions of the GPL or the LGPL are applicable instead
 * of those above. If you wish to allow use of your version of this file only
 * under the terms of either the GPL or the LGPL, and not to allow others to
 * use your version of this file under the terms of the MPL, indicate your
 * decision by deleting the provisions above and replace them with the notice
 * and other provisions required by the GPL or the LGPL. If you do not delete
 * the provisions above, a recipient may use your version of this file under
 * the terms of any one of the MPL, the GPL or the LGPL.
 *
 * ***** END LICENSE BLOCK ***** */

package edu.emory.mathcs.jtransforms.fft;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import edu.emory.mathcs.utils.ConcurrencyUtils;

/**
 * Computes 1D Discrete Fourier Transform (DFT) of complex and real, single
 * precision data. The size of the data can be an arbitrary number. This is a
 * parallel implementation of split-radix and mixed-radix algorithms optimized
 * for SMP systems. <br>
 * <br>
 * This code is derived from General Purpose FFT Package written by Takuya Ooura
 * (http://www.kurims.kyoto-u.ac.jp/~ooura/fft.html) and from Jfftpack written
 * by Baoshe Zhang (http://www.netlib.org/fftpack/jfftpack.tgz)
 * 
 * @author Piotr Wendykier (piotr.wendykier@gmail.com)
 * 
 */
public class FloatFFT_1D {

	private int n;

	private int[] ip;

	private float[] w;

	private int nw;

	private int nc;

	private float wtable[];

	private float wtable_r[];

	private static final int[] factors = { 4, 2, 3, 5 };

	private boolean isPowerOfTwo = false;

	/**
	 * Creates new instance of FloatFFT_1D.
	 * 
	 * @param n
	 *            size of data
	 */
	public FloatFFT_1D(int n) {
		if (n < 1) {
			throw new IllegalArgumentException("n must be greater than 0");
		}
		this.n = n;

		if (!ConcurrencyUtils.isPowerOf2(n)) {
			wtable = new float[4 * n + 15];
			wtable_r = new float[2 * n + 15];
			cffti();
			rffti();
		} else {
			isPowerOfTwo = true;
			this.ip = new int[2 + (int) Math.ceil(2 + (1 << (int) (Math.log(n + 0.5) / Math.log(2)) / 2))];
			this.w = new float[n];
			int twon = 2 * n;
			nw = ip[0];
			if (twon > (nw << 2)) {
				nw = twon >> 2;
				makewt(nw);
			}
			nc = ip[1];
			if (n > (nc << 2)) {
				nc = n >> 2;
				makect(nc, w, nw);
			}
		}
	}

	/**
	 * Computes 1D forward DFT of complex data leaving the result in
	 * <code>a</code>. Complex number is stored as two float values in sequence:
	 * the real and imaginary part, i.e. the size of the input array must be
	 * greater or equal 2*n. The physical layout of the input data has to be as
	 * follows:
	 * 
	 * <pre>
	 * a[2*k] = Re[k], 
	 * a[2*k+1] = Im[k], 0&lt;=k&lt;n
	 * </pre>
	 * 
	 * @param a
	 *            data to transform
	 */
	public void complexForward(float[] a) {
		complexForward(a, 0);
	}

	/**
	 * Computes 1D forward DFT of complex data leaving the result in
	 * <code>a</code>. Complex number is stored as two float values in sequence:
	 * the real and imaginary part, i.e. the size of the input array must be
	 * greater or equal 2*n. The physical layout of the input data has to be as
	 * follows:
	 * 
	 * <pre>
	 * a[2*k] = Re[k], 
	 * a[2*k+1] = Im[k], 0&lt;=k&lt;n
	 * </pre>
	 * 
	 * @param a
	 *            data to transform
	 * @param offa
	 *            index of the first element in array <code>a</code>
	 */
	public void complexForward(float[] a, int offa) {
		if (n == 1)
			return;
		if (isPowerOfTwo) {
			cftbsub(2 * n, a, offa, ip, nw, w);
		} else {
			cfftf(a, offa, -1);
		}
	}

	/**
	 * Computes 1D inverse DFT of complex data leaving the result in
	 * <code>a</code>. Complex number is stored as two float values in sequence:
	 * the real and imaginary part, i.e. the size of the input array must be
	 * greater or equal 2*n. The physical layout of the input data has to be as
	 * follows:
	 * 
	 * <pre>
	 * a[2*k] = Re[k], 
	 * a[2*k+1] = Im[k], 0&lt;=k&lt;n
	 * </pre>
	 * 
	 * @param a
	 *            data to transform
	 * @param scale
	 *            if true then scaling is performed
	 */
	public void complexInverse(float[] a, boolean scale) {
		complexInverse(a, 0, scale);
	}

	/**
	 * Computes 1D inverse DFT of complex data leaving the result in
	 * <code>a</code>. Complex number is stored as two float values in sequence:
	 * the real and imaginary part, i.e. the size of the input array must be
	 * greater or equal 2*n. The physical layout of the input data has to be as
	 * follows:
	 * 
	 * <pre>
	 * a[2*k] = Re[k], 
	 * a[2*k+1] = Im[k], 0&lt;=k&lt;n
	 * </pre>
	 * 
	 * @param a
	 *            data to transform
	 * @param offa
	 *            index of the first element in array <code>a</code>
	 * @param scale
	 *            if true then scaling is performed
	 */
	public void complexInverse(float[] a, int offa, boolean scale) {
		if (n == 1)
			return;
		if (isPowerOfTwo) {
			cftfsub(2 * n, a, offa, ip, nw, w);
		} else {
			cfftf(a, offa, +1);
		}
		if (scale) {
			scale(n, a, offa, true);
		}
	}

	/**
	 * Computes 1D forward DFT of real data leaving the result in <code>a</code>
	 * . The physical layout of the output data is as follows:
	 * 
	 * If n is even then:
	 * 
	 * <pre>
	 * a[2*k] = Re[k], 0&lt;=k&lt;n/2
	 * a[2*k+1] = Im[k], 0&lt;k&lt;n/2
	 * a[1] = Re[n/2]
	 * </pre>
	 * 
	 * If n is odd then:
	 * 
	 * <pre>
	 * a[2*k] = Re[k], 0&lt;=k&lt;(n+1)/2
	 * a[2*k+1] = Im[k], 0&lt;k&lt;(n-1)/2
	 * a[1] = Im[(n-1)/2]
	 * </pre>
	 * 
	 * This method computes only half of the elements of the real transform. The
	 * other half satisfies the symmetry condition. If you want the full real
	 * forward transform, use <code>realForwardFull</code>. To get back the
	 * original data, use <code>realInverse</code> on the output of this method.
	 * 
	 * @param a
	 *            data to transform
	 */
	public void realForward(float[] a) {
		realForward(a, 0);
	}

	/**
	 * Computes 1D forward DFT of real data leaving the result in <code>a</code>
	 * . The physical layout of the output data is as follows:
	 * 
	 * If n is even then:
	 * 
	 * <pre>
	 * a[2*k] = Re[k], 0&lt;=k&lt;n/2
	 * a[2*k+1] = Im[k], 0&lt;k&lt;n/2
	 * a[1] = Re[n/2]
	 * </pre>
	 * 
	 * If n is odd then:
	 * 
	 * <pre>
	 * a[2*k] = Re[k], 0&lt;=k&lt;(n+1)/2
	 * a[2*k+1] = Im[k], 0&lt;k&lt;(n-1)/2
	 * a[1] = Im[(n-1)/2]
	 * </pre>
	 * 
	 * This method computes only half of the elements of the real transform. The
	 * other half satisfies the symmetry condition. If you want the full real
	 * forward transform, use <code>realForwardFull</code>. To get back the
	 * original data, use <code>realInverse</code> on the output of this method.
	 * 
	 * @param a
	 *            data to transform
	 * @param offa
	 *            index of the first element in array <code>a</code>
	 */
	public void realForward(float[] a, int offa) {
		if (n == 1)
			return;

		if (isPowerOfTwo) {
			float xi;

			if (n > 4) {
				cftfsub(n, a, offa, ip, nw, w);
				rftfsub(n, a, offa, nc, w, nw);
			} else if (n == 4) {
				cftx020(a, offa);
			}
			xi = a[offa] - a[offa + 1];
			a[offa] += a[offa + 1];
			a[offa + 1] = xi;
		} else {
			rfftf(a, offa);
			for (int k = n - 1; k >= 2; k--) {
				int idx = offa + k;
				float tmp = a[idx];
				a[idx] = a[idx - 1];
				a[idx - 1] = tmp;
			}
		}
	}

	/**
	 * Computes 1D forward DFT of real data leaving the result in <code>a</code>
	 * . This method computes the full real forward transform, i.e. you will get
	 * the same result as from <code>complexForward</code> called with all
	 * imaginary parts equal 0. Because the result is stored in <code>a</code>,
	 * the size of the input array must greater or equal 2*n, with only the
	 * first n elements filled with real data. To get back the original data,
	 * use <code>complexInverse</code> on the output of this method.
	 * 
	 * @param a
	 *            data to transform
	 */
	public void realForwardFull(float[] a) {
		realForwardFull(a, 0);
	}

	/**
	 * Computes 1D forward DFT of real data leaving the result in <code>a</code>
	 * . This method computes the full real forward transform, i.e. you will get
	 * the same result as from <code>complexForward</code> called with all
	 * imaginary part equal 0. Because the result is stored in <code>a</code>,
	 * the size of the input array must greater or equal 2*n, with only the
	 * first n elements filled with real data. To get back the original data,
	 * use <code>complexInverse</code> on the output of this method.
	 * 
	 * @param a
	 *            data to transform
	 * @param offa
	 *            index of the first element in array <code>a</code>
	 */
	public void realForwardFull(final float[] a, final int offa) {
		if (isPowerOfTwo) {
			realForward(a, offa);
			final int twon = 2 * n;
			int np = ConcurrencyUtils.getNumberOfProcessors();
			if ((np > 1) && (n / 2 > ConcurrencyUtils.getThreadsBeginN_1D_FFT_2Threads())) {
				Future[] futures = new Future[np];
				int k = n / 2 / np;
				for (int i = 0; i < np; i++) {
					final int startidx = i * k;
					final int stopidx;
					if (i == np - 1)
						stopidx = n / 2;
					else {
						stopidx = startidx + k;
					}
					futures[i] = ConcurrencyUtils.threadPool.submit(new Runnable() {
						public void run() {
							int idx1, idx2;
							for (int k = startidx; k < stopidx; k++) {
								idx1 = 2 * k;
								idx2 = offa + ((twon - idx1) % twon);
								a[idx2] = a[offa + idx1];
								a[idx2 + 1] = -a[offa + idx1 + 1];
							}
						}
					});
				}
				try {
					for (int j = 0; j < np; j++) {
						futures[j].get();
					}
				} catch (ExecutionException ex) {
					ex.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} else {
				int idx1, idx2;
				for (int k = 0; k < n / 2; k++) {
					idx1 = 2 * k;
					idx2 = offa + ((twon - idx1) % twon);
					a[idx2] = a[offa + idx1];
					a[idx2 + 1] = -a[offa + idx1 + 1];
				}
			}
			a[offa + n] = -a[offa + 1];
			a[offa + 1] = 0;
		} else {
			rfftf(a, offa);
			int twon = 2 * n;
			int m;
			if (n % 2 == 0) {
				m = n / 2;
			} else {
				m = (n + 1) / 2;
			}
			for (int k = 1; k < m; k++) {
				int idx1 = offa + twon - 2 * k;
				int idx2 = offa + 2 * k;
				a[idx1 + 1] = -a[idx2];
				a[idx1] = a[idx2 - 1];
			}
			for (int k = 1; k < n; k++) {
				int idx = offa + n - k;
				float tmp = a[idx + 1];
				a[idx + 1] = a[idx];
				a[idx] = tmp;
			}
			a[offa + 1] = 0;
		}
	}

	/**
	 * Computes 1D inverse DFT of real data leaving the result in <code>a</code>
	 * . The physical layout of the input data has to be as follows:
	 * 
	 * If n is even then:
	 * 
	 * <pre>
	 * a[2*k] = Re[k], 0&lt;=k&lt;n/2
	 * a[2*k+1] = Im[k], 0&lt;k&lt;n/2
	 * a[1] = Re[n/2]
	 * </pre>
	 * 
	 * If n is odd then:
	 * 
	 * <pre>
	 * a[2*k] = Re[k], 0&lt;=k&lt;(n+1)/2
	 * a[2*k+1] = Im[k], 0&lt;k&lt;(n-1)/2
	 * a[1] = Im[(n-1)/2]
	 * </pre>
	 * 
	 * This method computes only half of the elements of the real transform. The
	 * other half satisfies the symmetry condition. If you want the full real
	 * inverse transform, use <code>realInverseFull</code>.
	 * 
	 * @param a
	 *            data to transform
	 * 
	 * @param scale
	 *            if true then scaling is performed
	 * 
	 */
	public void realInverse(float[] a, boolean scale) {
		realInverse(a, 0, scale);
	}

	/**
	 * Computes 1D inverse DFT of real data leaving the result in <code>a</code>
	 * . The physical layout of the input data has to be as follows:
	 * 
	 * If n is even then:
	 * 
	 * <pre>
	 * a[2*k] = Re[k], 0&lt;=k&lt;n/2
	 * a[2*k+1] = Im[k], 0&lt;k&lt;n/2
	 * a[1] = Re[n/2]
	 * </pre>
	 * 
	 * If n is odd then:
	 * 
	 * <pre>
	 * a[2*k] = Re[k], 0&lt;=k&lt;(n+1)/2
	 * a[2*k+1] = Im[k], 0&lt;k&lt;(n-1)/2
	 * a[1] = Im[(n-1)/2]
	 * </pre>
	 * 
	 * This method computes only half of the elements of the real transform. The
	 * other half satisfies the symmetry condition. If you want the full real
	 * inverse transform, use <code>realInverseFull</code>.
	 * 
	 * @param a
	 *            data to transform
	 * @param offa
	 *            index of the first element in array <code>a</code>
	 * @param scale
	 *            if true then scaling is performed
	 * 
	 */
	public void realInverse(float[] a, int offa, boolean scale) {
		if (n == 1)
			return;

		if (isPowerOfTwo) {
			a[offa + 1] = (float) (0.5 * (a[offa] - a[offa + 1]));
			a[offa] -= a[offa + 1];
			if (n > 4) {
				rftfsub(n, a, offa, nc, w, nw);
				cftbsub(n, a, offa, ip, nw, w);
			} else if (n == 4) {
				cftxc020(a, offa);
			}
			if (scale) {
				scale(n / 2, a, offa, false);
			}
		} else {
			for (int k = 2; k < n; k++) {
				int idx = offa + k;
				float tmp = a[idx - 1];
				a[idx - 1] = a[idx];
				a[idx] = tmp;
			}
			rfftb(a, offa);
			if (scale) {
				scale(n, a, offa, false);
			}
		}

	}

	/**
	 * Computes 1D inverse DFT of real data leaving the result in <code>a</code>
	 * . This method computes the full real inverse transform, i.e. you will get
	 * the same result as from <code>complexInverse</code> called with all
	 * imaginary part equal 0. Because the result is stored in <code>a</code>,
	 * the size of the input array must greater or equal 2*n, with only the
	 * first n elements filled with real data.
	 * 
	 * @param a
	 *            data to transform
	 * @param scale
	 *            if true then scaling is performed
	 */
	public void realInverseFull(float[] a, boolean scale) {
		realInverseFull(a, 0, scale);
	}

	/**
	 * Computes 1D inverse DFT of real data leaving the result in <code>a</code>
	 * . This method computes the full real inverse transform, i.e. you will get
	 * the same result as from <code>complexInverse</code> called with all
	 * imaginary part equal 0. Because the result is stored in <code>a</code>,
	 * the size of the input array must greater or equal 2*n, with only the
	 * first n elements filled with real data.
	 * 
	 * @param a
	 *            data to transform
	 * @param offa
	 *            index of the first element in array <code>a</code>
	 * @param scale
	 *            if true then scaling is performed
	 */
	public void realInverseFull(final float[] a, final int offa, boolean scale) {
		if (isPowerOfTwo) {
			realInverse2(a, offa, scale);
			final int twon = 2 * n;
			int np = ConcurrencyUtils.getNumberOfProcessors();
			if ((np > 1) && (n / 2 > ConcurrencyUtils.getThreadsBeginN_1D_FFT_2Threads())) {
				Future[] futures = new Future[np];
				int k = n / 2 / np;
				for (int i = 0; i < np; i++) {
					final int startidx = i * k;
					final int stopidx;
					if (i == np - 1)
						stopidx = n / 2;
					else {
						stopidx = startidx + k;
					}
					futures[i] = ConcurrencyUtils.threadPool.submit(new Runnable() {
						public void run() {
							int idx1, idx2;
							for (int k = startidx; k < stopidx; k++) {
								idx1 = 2 * k;
								idx2 = offa + ((twon - idx1) % twon);
								a[idx2] = a[offa + idx1];
								a[idx2 + 1] = -a[offa + idx1 + 1];
							}
						}
					});
				}
				try {
					for (int j = 0; j < np; j++) {
						futures[j].get();
					}
				} catch (ExecutionException ex) {
					ex.printStackTrace();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			} else {
				int idx1, idx2;
				for (int k = 0; k < n / 2; k++) {
					idx1 = 2 * k;
					idx2 = offa + ((twon - idx1) % twon);
					a[idx2] = a[offa + idx1];
					a[idx2 + 1] = -a[offa + idx1 + 1];
				}
			}
			a[offa + n] = -a[offa + 1];
			a[offa + 1] = 0;
		} else {
			rfftf(a, offa);
			if (scale) {
				scale(n, a, offa, false);
			}
			int twon = 2 * n;
			int m;
			if (n % 2 == 0) {
				m = n / 2;
			} else {
				m = (n + 1) / 2;
			}
			for (int k = 1; k < m; k++) {
				int idx1 = offa + 2 * k;
				int idx2 = offa + twon - 2 * k;
				a[idx1] = -a[idx1];
				a[idx2 + 1] = -a[idx1];
				a[idx2] = a[idx1 - 1];
			}
			for (int k = 1; k < n; k++) {
				int idx = offa + n - k;
				float tmp = a[idx + 1];
				a[idx + 1] = a[idx];
				a[idx] = tmp;
			}
			a[offa + 1] = 0;
		}
	}

	protected void realInverse2(float[] a, int offa, boolean scale) {
		if (n == 1)
			return;

		if (isPowerOfTwo) {
			float xi;

			if (n > 4) {
				cftfsub(n, a, offa, ip, nw, w);
				rftbsub(n, a, offa, nc, w, nw);
			} else if (n == 4) {
				cftbsub(n, a, offa, ip, nw, w);
			}
			xi = a[offa] - a[offa + 1];
			a[offa] += a[offa + 1];
			a[offa + 1] = xi;
			if (scale) {
				scale(n, a, offa, false);
			}
		} else {
			rfftf(a, offa);
			for (int k = n - 1; k >= 2; k--) {
				int idx = offa + k;
				float tmp = a[idx];
				a[idx] = a[idx - 1];
				a[idx - 1] = tmp;
			}
			if (scale) {
				scale(n, a, offa, false);
			}
			int m;
			if (n % 2 == 0) {
				m = n / 2;
				for (int i = 1; i < m; i++) {
					int idx = offa + 2 * i + 1;
					a[idx] = -a[idx];
				}
			} else {
				m = (n - 1) / 2;
				for (int i = 0; i < m; i++) {
					int idx = offa + 2 * i + 1;
					a[idx] = -a[idx];
				}
			}

		}
	}

	/* -------- initializing routines -------- */

	/*---------------------------------------------------------
	   cffti: initialization of Complex FFT
	  --------------------------------------------------------*/
	void cffti() {
		if (n == 1)
			return;

		final int twon = 2 * n;
		final int fourn = 4 * n;
		final float twopi = (float) 6.283185307179586;
		float argh;
		int idot, ntry = 0, i, j;
		float argld;
		int i1, k1, l1, l2, ib;
		float fi;
		int ld, ii, nf, ip, nl, nq, nr;
		float arg;
		int ido, ipm;

		nl = n;
		nf = 0;
		j = 0;

		factorize_loop: while (true) {
			j++;
			if (j <= 4)
				ntry = factors[j - 1];
			else
				ntry += 2;
			do {
				nq = nl / ntry;
				nr = nl - ntry * nq;
				if (nr != 0)
					continue factorize_loop;
				nf++;
				wtable[nf + 1 + fourn] = ntry;
				nl = nq;
				if (ntry == 2 && nf != 1) {
					for (i = 2; i <= nf; i++) {
						ib = nf - i + 2;
						int idx = ib + fourn;
						wtable[idx + 1] = wtable[idx];
					}
					wtable[2 + fourn] = 2;
				}
			} while (nl != 1);
			break factorize_loop;
		}
		wtable[fourn] = n;
		wtable[1 + fourn] = nf;
		argh = twopi / (float) n;
		i = 1;
		l1 = 1;
		for (k1 = 1; k1 <= nf; k1++) {
			ip = (int) wtable[k1 + 1 + fourn];
			ld = 0;
			l2 = l1 * ip;
			ido = n / l2;
			idot = ido + ido + 2;
			ipm = ip - 1;
			for (j = 1; j <= ipm; j++) {
				i1 = i;
				wtable[i - 1 + twon] = 1;
				wtable[i + twon] = 0;
				ld += l1;
				fi = 0;
				argld = ld * argh;
				for (ii = 4; ii <= idot; ii += 2) {
					i += 2;
					fi += 1;
					arg = fi * argld;
					int idx = i + twon;
					wtable[idx - 1] = (float) Math.cos(arg);
					wtable[idx] = (float) Math.sin(arg);
				}
				if (ip > 5) {
					int idx1 = i1 + twon;
					int idx2 = i + twon;
					wtable[idx1 - 1] = wtable[idx2 - 1];
					wtable[idx1] = wtable[idx2];
				}
			}
			l1 = l2;
		}

	}

	void rffti() {

		if (n == 1)
			return;
		final int twon = 2 * n;
		final float twopi = (float) 6.283185307179586;
		float argh;
		int ntry = 0, i, j;
		float argld;
		int k1, l1, l2, ib;
		float fi;
		int ld, ii, nf, ip, nl, is, nq, nr;
		float arg;
		int ido, ipm;
		int nfm1;

		nl = n;
		nf = 0;
		j = 0;

		factorize_loop: while (true) {
			++j;
			if (j <= 4)
				ntry = factors[j - 1];
			else
				ntry += 2;
			do {
				nq = nl / ntry;
				nr = nl - ntry * nq;
				if (nr != 0)
					continue factorize_loop;
				++nf;
				wtable_r[nf + 1 + twon] = ntry;

				nl = nq;
				if (ntry == 2 && nf != 1) {
					for (i = 2; i <= nf; i++) {
						ib = nf - i + 2;
						int idx = ib + twon;
						wtable_r[idx + 1] = wtable_r[idx];
					}
					wtable_r[2 + twon] = 2;
				}
			} while (nl != 1);
			break factorize_loop;
		}
		wtable_r[twon] = n;
		wtable_r[1 + twon] = nf;
		argh = twopi / (float) (n);
		is = 0;
		nfm1 = nf - 1;
		l1 = 1;
		if (nfm1 == 0)
			return;
		for (k1 = 1; k1 <= nfm1; k1++) {
			ip = (int) wtable_r[k1 + 1 + twon];
			ld = 0;
			l2 = l1 * ip;
			ido = n / l2;
			ipm = ip - 1;
			for (j = 1; j <= ipm; ++j) {
				ld += l1;
				i = is;
				argld = (float) ld * argh;

				fi = 0;
				for (ii = 3; ii <= ido; ii += 2) {
					i += 2;
					fi += 1;
					arg = fi * argld;
					int idx = i + n;
					wtable_r[idx - 2] = (float) Math.cos(arg);
					wtable_r[idx - 1] = (float) Math.sin(arg);
				}
				is += ido;
			}
			l1 = l2;
		}
	}

	private void makewt(int nw) {
		int j, nwh, nw0, nw1;
		float delta, wn4r, wk1r, wk1i, wk3r, wk3i;
		float delta2, deltaj, deltaj3;

		ip[0] = nw;
		ip[1] = 1;
		if (nw > 2) {
			nwh = nw >> 1;
			delta = (float) (0.7853981633974483 / nwh);
			delta2 = delta * 2;
			wn4r = (float) Math.cos(delta * nwh);
			w[0] = 1;
			w[1] = wn4r;
			if (nwh == 4) {
				w[2] = (float) Math.cos(delta2);
				w[3] = (float) Math.sin(delta2);
			} else if (nwh > 4) {
				makeipt(nw);
				w[2] = (float) (0.5 / Math.cos(delta2));
				w[3] = (float) (0.5 / Math.cos(delta * 6));
				for (j = 4; j < nwh; j += 4) {
					deltaj = delta * j;
					deltaj3 = 3 * deltaj;
					w[j] = (float) Math.cos(deltaj);
					w[j + 1] = (float) Math.sin(deltaj);
					w[j + 2] = (float) Math.cos(deltaj3);
					w[j + 3] = (float) -Math.sin(deltaj3);
				}
			}
			nw0 = 0;
			while (nwh > 2) {
				nw1 = nw0 + nwh;
				nwh >>= 1;
				w[nw1] = 1;
				w[nw1 + 1] = wn4r;
				if (nwh == 4) {
					wk1r = w[nw0 + 4];
					wk1i = w[nw0 + 5];
					w[nw1 + 2] = wk1r;
					w[nw1 + 3] = wk1i;
				} else if (nwh > 4) {
					wk1r = w[nw0 + 4];
					wk3r = w[nw0 + 6];
					w[nw1 + 2] = (float) (0.5 / wk1r);
					w[nw1 + 3] = (float) (0.5 / wk3r);
					for (j = 4; j < nwh; j += 4) {
						int idx1 = nw0 + 2 * j;
						int idx2 = nw1 + j;
						wk1r = w[idx1];
						wk1i = w[idx1 + 1];
						wk3r = w[idx1 + 2];
						wk3i = w[idx1 + 3];
						w[idx2] = wk1r;
						w[idx2 + 1] = wk1i;
						w[idx2 + 2] = wk3r;
						w[idx2 + 3] = wk3i;
					}
				}
				nw0 = nw1;
			}
		}
	}

	private void makeipt(int nw) {
		int j, l, m, m2, p, q;

		ip[2] = 0;
		ip[3] = 16;
		m = 2;
		for (l = nw; l > 32; l >>= 2) {
			m2 = m << 1;
			q = m2 << 3;
			for (j = m; j < m2; j++) {
				p = ip[j] << 2;
				ip[m + j] = p;
				ip[m2 + j] = p + q;
			}
			m = m2;
		}
	}

	private void makect(int nc, float[] c, int startc) {
		int j, nch;
		float delta, deltaj;

		ip[1] = nc;
		if (nc > 1) {
			nch = nc >> 1;
			delta = (float) (0.7853981633974483 / nch);
			c[startc] = (float) Math.cos(delta * nch);
			c[startc + nch] = (float) (0.5 * c[startc]);
			for (j = 1; j < nch; j++) {
				deltaj = delta * j;
				c[startc + j] = (float) (0.5 * Math.cos(deltaj));
				c[startc + nc - j] = (float) (0.5 * Math.sin(deltaj));
			}
		}
	}

	/* -------- child routines -------- */

	/*---------------------------------------------------------
	   rfftf1: further processing of Real forward FFT
	  --------------------------------------------------------*/
	void rfftf(final float a[], final int offa) {
		if (n == 1)
			return;
		int i;
		int k1, l1, l2, na, kh, nf, ip, iw, ido, idl1;

		final float[] ch = new float[n];
		//		System.arraycopy(wtable_r, 0, ch, 0, n);
		final int twon = 2 * n;
		nf = (int) wtable_r[1 + twon];
		na = 1;
		l2 = n;
		iw = twon - 1;
		for (k1 = 1; k1 <= nf; ++k1) {
			kh = nf - k1;
			ip = (int) wtable_r[kh + 2 + twon];
			l1 = l2 / ip;
			ido = n / l2;
			idl1 = ido * l1;
			iw -= (ip - 1) * ido;
			na = 1 - na;
			if (ip == 4) {
				if (na == 0) {
					radf4(ido, l1, a, offa, ch, 0, iw);
				} else {
					radf4(ido, l1, ch, 0, a, offa, iw);
				}
			} else if (ip == 2) {
				if (na == 0) {
					radf2(ido, l1, a, offa, ch, 0, iw);
				} else {
					radf2(ido, l1, ch, 0, a, offa, iw);
				}
			} else if (ip == 3) {
				if (na == 0) {
					radf3(ido, l1, a, offa, ch, 0, iw);
				} else {
					radf3(ido, l1, ch, 0, a, offa, iw);
				}
			} else if (ip == 5) {
				if (na == 0) {
					radf5(ido, l1, a, offa, ch, 0, iw);
				} else {
					radf5(ido, l1, ch, 0, a, offa, iw);
				}
			} else {
				if (ido == 1)
					na = 1 - na;
				if (na == 0) {
					radfg(ido, ip, l1, idl1, a, offa, ch, 0, iw);
					na = 1;
				} else {
					radfg(ido, ip, l1, idl1, ch, 0, a, offa, iw);
					na = 0;
				}
			}
			l2 = l1;
		}
		if (na == 1)
			return;
		System.arraycopy(ch, 0, a, offa, n);
	}

	/*---------------------------------------------------------
	   rfftb1: further processing of Real backward FFT
	  --------------------------------------------------------*/
	void rfftb(final float a[], final int offa) {
		if (n == 1)
			return;
		int i;
		int k1, l1, l2, na, nf, ip, iw, ido, idl1;

		float[] ch = new float[n];
		//		System.arraycopy(wtable_r, 0, ch, 0, n);
		final int twon = 2 * n;
		nf = (int) wtable_r[1 + twon];
		na = 0;
		l1 = 1;
		iw = n;
		for (k1 = 1; k1 <= nf; k1++) {
			ip = (int) wtable_r[k1 + 1 + twon];
			l2 = ip * l1;
			ido = n / l2;
			idl1 = ido * l1;
			if (ip == 4) {
				if (na == 0) {
					radb4(ido, l1, a, offa, ch, 0, iw);
				} else {
					radb4(ido, l1, ch, 0, a, offa, iw);
				}
				na = 1 - na;
			} else if (ip == 2) {
				if (na == 0) {
					radb2(ido, l1, a, offa, ch, 0, iw);
				} else {
					radb2(ido, l1, ch, 0, a, offa, iw);
				}
				na = 1 - na;
			} else if (ip == 3) {
				if (na == 0) {
					radb3(ido, l1, a, offa, ch, 0, iw);
				} else {
					radb3(ido, l1, ch, 0, a, offa, iw);
				}
				na = 1 - na;
			} else if (ip == 5) {
				if (na == 0) {
					radb5(ido, l1, a, offa, ch, 0, iw);
				} else {
					radb5(ido, l1, ch, 0, a, offa, iw);
				}
				na = 1 - na;
			} else {
				if (na == 0) {
					radbg(ido, ip, l1, idl1, a, offa, ch, 0, iw);
				} else {
					radbg(ido, ip, l1, idl1, ch, 0, a, offa, iw);
				}
				if (ido == 1)
					na = 1 - na;
			}
			l1 = l2;
			iw += (ip - 1) * ido;
		}
		if (na == 0)
			return;
		System.arraycopy(ch, 0, a, offa, n);
	}

	/*-------------------------------------------------
	   radf2: Real FFT's forward processing of factor 2
	  -------------------------------------------------*/
	void radf2(final int ido, final int l1, final float in[], final int in_off, final float out[], final int out_off, final int offset) {
		int i, k, ic, idx0, idx1, idx2, idx3, idx4;
		float t1i, t1r, w1r, w1i;
		int iw1;
		iw1 = offset;
		idx0 = l1 * ido;
		idx1 = 2 * ido;
		for (k = 0; k < l1; k++) {
			int oidx1 = out_off + k * idx1;
			int oidx2 = oidx1 + idx1 - 1;
			int iidx1 = in_off + k * ido;
			int iidx2 = iidx1 + idx0;
			out[oidx1] = in[iidx1] + in[iidx2];
			out[oidx2] = in[iidx1] - in[iidx2];
		}
		if (ido < 2)
			return;
		if (ido != 2) {
			for (k = 0; k < l1; k++) {
				idx1 = k * ido;
				idx2 = 2 * idx1;
				idx3 = idx2 + ido;
				idx4 = idx1 + idx0;
				for (i = 2; i < ido; i += 2) {
					ic = ido - i;
					int widx1 = i - 1 + iw1;
					int oidx1 = out_off + i + idx2;
					int oidx2 = out_off + ic + idx3;
					int iidx1 = in_off + i + idx1;
					int iidx2 = in_off + i + idx4;

					float a1i = in[iidx1 - 1];
					float a1r = in[iidx1];
					float a2i = in[iidx2 - 1];
					float a2r = in[iidx2];

					w1r = wtable_r[widx1 - 1];
					w1i = wtable_r[widx1];

					t1r = w1r * a2i + w1i * a2r;
					t1i = w1r * a2r - w1i * a2i;

					out[oidx1] = a1r + t1i;
					out[oidx1 - 1] = a1i + t1r;

					out[oidx2] = t1i - a1r;
					out[oidx2 - 1] = a1i - t1r;
				}
			}
			if (ido % 2 == 1)
				return;
		}
		idx2 = 2 * idx1;
		for (k = 0; k < l1; k++) {
			idx1 = k * ido;
			int oidx1 = out_off + idx2 + ido;
			int iidx1 = in_off + ido - 1 + idx1;

			out[oidx1] = -in[iidx1 + idx0];
			out[oidx1 - 1] = in[iidx1];
		}
	}

	/*-------------------------------------------------
	   radb2: Real FFT's backward processing of factor 2
	  -------------------------------------------------*/
	void radb2(final int ido, final int l1, final float a[], final int offa, final float ch[], final int offch, final int offset) {
		int i, k, ic;
		float ti2, tr2, w1r, w1i;
		int iw1 = offset;

		for (k = 0; k < l1; k++) {
			int idx1 = k * ido;
			int idx2 = 2 * idx1;
			int idx3 = idx2 + ido;
			ch[offch + idx1] = a[offa + idx2] + a[offa + ido - 1 + idx3];
			ch[offch + idx1 + l1 * ido] = a[offa + idx2] - a[offa + ido - 1 + idx3];
		}
		if (ido < 2)
			return;
		if (ido != 2) {
			for (k = 0; k < l1; ++k) {
				int idx1 = k * ido;
				int idx2 = 2 * idx1;
				int idx3 = idx2 + ido;
				int idx4 = (k + l1) * ido;
				for (i = 2; i < ido; i += 2) {
					ic = ido - i;
					int idx5 = i - 1 + iw1;
					int idx6 = offch + i;
					int idx7 = offa + i;
					int idx8 = offa + ic;
					w1r = wtable_r[idx5 - 1];
					w1i = wtable_r[idx5];
					ch[idx6 - 1 + idx1] = a[idx7 - 1 + idx2] + a[idx8 - 1 + idx3];
					tr2 = a[idx7 - 1 + idx2] - a[idx8 - 1 + idx3];
					ch[idx6 + idx1] = a[idx7 + idx2] - a[idx8 + idx3];
					ti2 = a[idx7 + idx2] + a[idx8 + idx3];
					ch[idx6 - 1 + idx4] = w1r * tr2 - w1i * ti2;
					ch[idx6 + idx4] = w1r * ti2 + w1i * tr2;
				}
			}
			if (ido % 2 == 1)
				return;
		}
		int idx0 = l1 * ido;
		for (k = 0; k < l1; k++) {
			int idx1 = k * ido;
			int idx2 = 2 * idx1;
			ch[offch + ido - 1 + idx1] = 2 * a[offa + ido - 1 + idx2];
			ch[offch + ido - 1 + idx1 + idx0] = -2 * a[offa + idx2 + ido];
		}
	}

	/*-------------------------------------------------
	   radf3: Real FFT's forward processing of factor 3 
	  -------------------------------------------------*/
	void radf3(final int ido, final int l1, final float a[], final int offa, final float ch[], final int offch, final int offset) {
		final float taur = (float) -0.5;
		final float taui = (float) 0.866025403784439;
		int i, k, ic;
		float ci2, di2, di3, cr2, dr2, dr3, ti2, ti3, tr2, tr3, w1r, w2r, w1i, w2i;
		int iw1, iw2;
		iw1 = offset;
		iw2 = iw1 + ido;

		for (k = 0; k < l1; k++) {
			int idx1 = k * ido;
			int idx2 = l1 * ido;
			int idx3 = 2 * idx2;
			int idx4 = (3 * k + 1) * ido;
			int idx5 = offa + idx1;
			cr2 = a[idx5 + idx2] + a[idx5 + idx3];
			ch[offch + 3 * idx1] = a[idx5] + cr2;
			ch[offch + idx4 + ido] = taui * (a[idx5 + idx3] - a[idx5 + idx2]);
			ch[offch + ido - 1 + idx4] = a[idx5] + taur * cr2;
		}
		if (ido == 1)
			return;
		for (k = 0; k < l1; k++) {
			int idx3 = k * ido;
			int idx4 = 3 * idx3;
			int idx5 = (k + l1) * ido;
			int idx6 = idx5 + l1 * ido;
			int idx7 = (3 * k + 1) * ido;
			int idx8 = idx7 + ido;
			for (i = 2; i < ido; i += 2) {
				ic = ido - i;
				int idx1 = i - 1 + iw1;
				int idx2 = i - 1 + iw2;
				w1r = wtable_r[idx1 - 1];
				w1i = wtable_r[idx1];
				w2r = wtable_r[idx2 - 1];
				w2i = wtable_r[idx2];
				int idx9 = offa + i;
				int idx10 = offch + i;
				int idx11 = offch + ic;

				dr2 = w1r * a[idx9 - 1 + idx5] + w1i * a[idx9 + idx5];
				di2 = w1r * a[idx9 + idx5] - w1i * a[idx9 - 1 + idx5];
				dr3 = w2r * a[idx9 - 1 + idx6] + w2i * a[idx9 + idx6];
				di3 = w2r * a[idx9 + idx6] - w2i * a[idx9 - 1 + idx6];
				cr2 = dr2 + dr3;
				ci2 = di2 + di3;
				ch[idx10 - 1 + idx4] = a[idx9 - 1 + idx3] + cr2;
				ch[idx10 + idx4] = a[idx9 + idx3] + ci2;
				tr2 = a[idx9 - 1 + idx3] + taur * cr2;
				ti2 = a[idx9 + k * ido] + taur * ci2;
				tr3 = taui * (di2 - di3);
				ti3 = taui * (dr3 - dr2);
				ch[idx10 - 1 + idx8] = tr2 + tr3;
				ch[idx11 - 1 + idx7] = tr2 - tr3;
				ch[idx10 + idx8] = ti2 + ti3;
				ch[idx11 + idx7] = ti3 - ti2;
			}
		}
	}

	/*-------------------------------------------------
	   radb3: Real FFT's backward processing of factor 3
	  -------------------------------------------------*/
	void radb3(final int ido, final int l1, final float a[], final int offa, final float ch[], final int offch, final int offset) {
		final float taur = (float) -0.5;
		final float taui = (float) 0.866025403784439;
		int i, k, ic;
		float ci2, ci3, di2, di3, cr2, cr3, dr2, dr3, ti2, tr2, w1r, w2r, w1i, w2i;
		int iw1, iw2;
		iw1 = offset;
		iw2 = iw1 + ido;

		for (k = 0; k < l1; k++) {
			int idx1 = k * ido;
			int idx2 = 3 * idx1;
			int idx4 = (3 * k + 1) * ido;
			tr2 = 2 * a[offa + ido - 1 + idx4];
			cr2 = a[offa + idx2] + taur * tr2;
			ch[offch + idx1] = a[offa + idx2] + tr2;
			ci3 = 2 * taui * a[offa + (3 * k + 2) * ido];
			ch[offch + (k + l1) * ido] = cr2 - ci3;
			ch[offch + (k + 2 * l1) * ido] = cr2 + ci3;
		}
		if (ido == 1)
			return;
		for (k = 0; k < l1; k++) {
			int idx1 = k * ido;
			int idx2 = 3 * k * ido;
			int idx3 = (3 * k + 1) * ido;
			int idx4 = (3 * k + 2) * ido;
			int idx5 = (k + l1) * ido;
			int idx6 = (k + 2 * l1) * ido;
			for (i = 2; i < ido; i += 2) {
				ic = ido - i;
				int idx7 = offa + i;
				int idx8 = offa + ic;
				int idx9 = offch + i;
				tr2 = a[idx7 - 1 + idx4] + a[idx8 - 1 + idx3];
				cr2 = a[idx7 - 1 + idx2] + taur * tr2;
				ch[offch + i - 1 + idx1] = a[idx7 - 1 + idx2] + tr2;
				ti2 = a[idx7 + idx4] - a[idx8 + idx3];
				ci2 = a[idx7 + idx2] + taur * ti2;
				ch[offch + i + idx1] = a[idx7 + idx2] + ti2;
				cr3 = taui * (a[idx7 - 1 + idx4] - a[idx8 - 1 + idx3]);
				ci3 = taui * (a[idx7 + idx4] + a[idx8 + idx3]);
				dr2 = cr2 - ci3;
				dr3 = cr2 + ci3;
				di2 = ci2 + cr3;
				di3 = ci2 - cr3;
				int idx10 = i - 1 + iw1;
				int idx11 = i - 1 + iw2;
				w1r = wtable_r[idx10 - 1];
				w1i = wtable_r[idx10];
				w2r = wtable_r[idx11 - 1];
				w2i = wtable_r[idx11];

				ch[idx9 - 1 + idx5] = w1r * dr2 - w1i * di2;
				ch[idx9 + idx5] = w1r * di2 + w1i * dr2;
				ch[idx9 - 1 + idx6] = w2r * dr3 - w2i * di3;
				ch[idx9 + idx6] = w2r * di3 + w2i * dr3;
			}
		}
	}

	/*-------------------------------------------------
	   radf4: Real FFT's forward processing of factor 4
	  -------------------------------------------------*/
	void radf4(final int ido, final int l1, final float a[], final int offa, final float ch[], final int offch, final int offset) {
		final float hsqt2 = (float) 0.7071067811865475;
		int i, k, ic;
		float ci2, ci3, ci4, cr2, cr3, cr4, ti1, ti2, ti3, ti4, tr1, tr2, tr3, tr4, w1r, w1i, w2r, w2i, w3r, w3i;
		int iw1, iw2, iw3;
		iw1 = offset;
		iw2 = offset + ido;
		iw3 = iw2 + ido;
		for (k = 0; k < l1; k++) {
			int idx1 = k * ido;
			int idx2 = 4 * idx1;
			int idx3 = (k + l1) * ido;
			int idx4 = idx3 + l1 * ido;
			int idx5 = idx4 + l1 * ido;
			int idx6 = idx2 + ido;
			tr1 = a[offa + idx3] + a[offa + idx5];
			tr2 = a[offa + idx1] + a[offa + idx4];
			ch[offch + idx2] = tr1 + tr2;
			ch[offch + ido - 1 + idx6 + ido + ido] = tr2 - tr1;
			ch[offch + ido - 1 + idx6] = a[offa + k * ido] - a[offa + idx4];
			ch[offch + idx6 + ido] = a[offa + idx5] - a[offa + idx3];
		}
		if (ido < 2)
			return;
		if (ido != 2) {
			for (k = 0; k < l1; k++) {
				int idx1 = k * ido;
				int idx2 = idx1 + l1 * ido;
				int idx3 = idx2 + l1 * ido;
				int idx4 = idx3 + l1 * ido;
				int idx5 = 4 * idx1;
				int idx6 = idx5 + ido;
				int idx7 = idx6 + ido;
				int idx8 = idx7 + ido;
				for (i = 2; i < ido; i += 2) {
					ic = ido - i;
					int idx9 = i - 1 + iw1;
					int idx10 = i - 1 + iw2;
					int idx11 = i - 1 + iw3;
					w1r = wtable_r[idx9 - 1];
					w1i = wtable_r[idx9];
					w2r = wtable_r[idx10 - 1];
					w2i = wtable_r[idx10];
					w3r = wtable_r[idx11 - 1];
					w3i = wtable_r[idx11];
					int idx12 = offa + i;
					int idx13 = offch + i;
					int idx14 = offch + ic;
					cr2 = w1r * a[idx12 - 1 + idx2] + w1i * a[idx12 + idx2];
					ci2 = w1r * a[idx12 + idx2] - w1i * a[idx12 - 1 + idx2];
					cr3 = w2r * a[idx12 - 1 + idx3] + w2i * a[idx12 + idx3];
					ci3 = w2r * a[idx12 + idx3] - w2i * a[idx12 - 1 + idx3];
					cr4 = w3r * a[idx12 - 1 + idx4] + w3i * a[idx12 + idx4];
					ci4 = w3r * a[idx12 + idx4] - w3i * a[idx12 - 1 + idx4];
					tr1 = cr2 + cr4;
					tr4 = cr4 - cr2;
					ti1 = ci2 + ci4;
					ti4 = ci2 - ci4;
					ti2 = a[idx12 + idx1] + ci3;
					ti3 = a[idx12 + idx1] - ci3;
					tr2 = a[idx12 - 1 + idx1] + cr3;
					tr3 = a[idx12 - 1 + idx1] - cr3;
					ch[idx13 - 1 + idx5] = tr1 + tr2;
					ch[idx14 - 1 + idx8] = tr2 - tr1;
					ch[idx13 + idx5] = ti1 + ti2;
					ch[idx14 + idx8] = ti1 - ti2;
					ch[idx13 - 1 + idx7] = ti4 + tr3;
					ch[idx14 - 1 + idx6] = tr3 - ti4;
					ch[idx13 + idx7] = tr4 + ti3;
					ch[idx14 + idx6] = tr4 - ti3;
				}
			}
			if (ido % 2 == 1)
				return;
		}
		for (k = 0; k < l1; k++) {
			int idx1 = k * ido;
			int idx2 = 4 * idx1;
			int idx3 = idx1 + l1 * ido;
			int idx4 = idx3 + l1 * ido;
			int idx5 = idx4 + l1 * ido;
			int idx6 = idx2 + ido;
			int idx7 = idx6 + ido;
			int idx8 = idx7 + ido;
			int idx9 = offa + ido;
			int idx10 = offch + ido;
			ti1 = -hsqt2 * (a[idx9 - 1 + idx3] + a[idx9 - 1 + idx5]);
			tr1 = hsqt2 * (a[idx9 - 1 + idx3] - a[idx9 - 1 + idx5]);
			ch[idx10 - 1 + idx2] = tr1 + a[idx9 - 1 + idx1];
			ch[idx10 - 1 + idx7] = a[idx9 - 1 + idx1] - tr1;
			ch[offch + idx6] = ti1 - a[idx9 - 1 + idx4];
			ch[offch + idx8] = ti1 + a[idx9 - 1 + idx4];
		}
	}

	/*-------------------------------------------------
	   radb4: Real FFT's backward processing of factor 4
	  -------------------------------------------------*/
	void radb4(final int ido, final int l1, final float a[], final int offa, final float ch[], final int offch, final int offset) {
		final float sqrt2 = (float) 1.414213562373095;
		int i, k, ic;
		float ci2, ci3, ci4, cr2, cr3, cr4;
		float ti1, ti2, ti3, ti4, tr1, tr2, tr3, tr4, w1r, w1i, w2r, w2i, w3r, w3i;
		int iw1, iw2, iw3;
		iw1 = offset;
		iw2 = iw1 + ido;
		iw3 = iw2 + ido;

		for (k = 0; k < l1; k++) {
			int idx1 = k * ido;
			int idx2 = 4 * idx1;
			int idx3 = (k + l1) * ido;
			int idx4 = idx3 + l1 * ido;
			int idx5 = idx4 + l1 * ido;
			int idx6 = idx2 + ido;
			int idx7 = idx6 + ido;
			int idx8 = idx7 + ido;

			tr1 = a[offa + idx2] - a[offa + ido - 1 + idx8];
			tr2 = a[offa + idx2] + a[offa + ido - 1 + idx8];
			tr3 = a[offa + ido - 1 + idx6] + a[offa + ido - 1 + idx6];
			tr4 = a[offa + idx7] + a[offa + idx7];
			ch[offch + idx1] = tr2 + tr3;
			ch[offch + idx3] = tr1 - tr4;
			ch[offch + idx4] = tr2 - tr3;
			ch[offch + idx5] = tr1 + tr4;
		}
		if (ido < 2)
			return;
		if (ido != 2) {
			for (k = 0; k < l1; ++k) {
				int idx1 = k * ido;
				int idx2 = idx1 + l1 * ido;
				int idx3 = idx2 + l1 * ido;
				int idx4 = idx3 + l1 * ido;
				int idx5 = 4 * idx1;
				int idx6 = idx5 + ido;
				int idx7 = idx6 + ido;
				int idx8 = idx7 + ido;
				for (i = 2; i < ido; i += 2) {
					ic = ido - i;
					int idx9 = i - 1 + iw1;
					int idx10 = i - 1 + iw2;
					int idx11 = i - 1 + iw3;
					w1r = wtable_r[idx9 - 1];
					w1i = wtable_r[idx9];
					w2r = wtable_r[idx10 - 1];
					w2i = wtable_r[idx10];
					w3r = wtable_r[idx11 - 1];
					w3i = wtable_r[idx11];
					int idx12 = offa + i;
					int idx13 = offa + ic;
					int idx14 = offch + i;
					ti1 = a[idx12 + idx5] + a[idx13 + idx8];
					ti2 = a[idx12 + idx5] - a[idx13 + idx8];
					ti3 = a[idx12 + idx7] - a[idx13 + idx6];
					tr4 = a[idx12 + idx7] + a[idx13 + idx6];
					tr1 = a[idx12 - 1 + idx5] - a[idx13 - 1 + idx8];
					tr2 = a[idx12 - 1 + idx5] + a[idx13 - 1 + idx8];
					ti4 = a[idx12 - 1 + idx7] - a[idx13 - 1 + idx6];
					tr3 = a[idx12 - 1 + idx7] + a[idx13 - 1 + idx6];
					ch[idx14 - 1 + idx1] = tr2 + tr3;
					cr3 = tr2 - tr3;
					ch[idx14 + idx1] = ti2 + ti3;
					ci3 = ti2 - ti3;
					cr2 = tr1 - tr4;
					cr4 = tr1 + tr4;
					ci2 = ti1 + ti4;
					ci4 = ti1 - ti4;
					ch[idx14 - 1 + idx2] = w1r * cr2 - w1i * ci2;
					ch[idx14 + idx2] = w1r * ci2 + w1i * cr2;
					ch[idx14 - 1 + idx3] = w2r * cr3 - w2i * ci3;
					ch[idx14 + idx3] = w2r * ci3 + w2i * cr3;
					ch[idx14 - 1 + idx4] = w3r * cr4 - w3i * ci4;
					ch[idx14 + idx4] = w3r * ci4 + w3i * cr4;
				}
			}
			if (ido % 2 == 1)
				return;
		}
		for (k = 0; k < l1; k++) {
			int idx1 = k * ido;
			int idx2 = 4 * idx1;
			int idx3 = idx1 + l1 * ido;
			int idx4 = idx3 + l1 * ido;
			int idx5 = idx4 + l1 * ido;
			int idx6 = idx2 + ido;
			int idx7 = idx6 + ido;
			int idx8 = idx7 + ido;
			int idx9 = offa + ido;
			int idx10 = offch + ido;
			ti1 = a[offa + idx6] + a[offa + idx8];
			ti2 = a[offa + idx8] - a[offa + idx6];
			tr1 = a[idx9 - 1 + idx2] - a[idx9 - 1 + idx7];
			tr2 = a[idx9 - 1 + idx2] + a[idx9 - 1 + idx7];
			ch[idx10 - 1 + idx1] = tr2 + tr2;
			ch[idx10 - 1 + idx3] = sqrt2 * (tr1 - ti1);
			ch[idx10 - 1 + idx4] = ti2 + ti2;
			ch[idx10 - 1 + idx5] = -sqrt2 * (tr1 + ti1);
		}
	}

	/*-------------------------------------------------
	   radf5: Real FFT's forward processing of factor 5
	  -------------------------------------------------*/
	void radf5(final int ido, final int l1, final float a[], final int offa, final float ch[], final int offch, final int offset) {
		final float tr11 = (float) 0.309016994374947;
		final float ti11 = (float) 0.951056516295154;
		final float tr12 = (float) -0.809016994374947;
		final float ti12 = (float) 0.587785252292473;
		int i, k, ic;
		float ci2, di2, ci4, ci5, di3, di4, di5, ci3, cr2, cr3, dr2, dr3, dr4, dr5, cr5, cr4, ti2, ti3, ti5, ti4, tr2, tr3, tr4, tr5, w1r, w1i, w2r, w2i, w3r, w3i, w4r, w4i;
		int iw1, iw2, iw3, iw4;
		iw1 = offset;
		iw2 = iw1 + ido;
		iw3 = iw2 + ido;
		iw4 = iw3 + ido;

		for (k = 0; k < l1; k++) {
			int idx1 = k * ido;
			int idx2 = 5 * idx1;
			int idx3 = idx2 + ido;
			int idx4 = idx3 + ido;
			int idx5 = idx4 + ido;
			int idx6 = idx5 + ido;
			int idx7 = idx1 + l1 * ido;
			int idx8 = idx7 + l1 * ido;
			int idx9 = idx8 + l1 * ido;
			int idx10 = idx9 + l1 * ido;

			cr2 = a[offa + idx10] + a[offa + idx7];
			ci5 = a[offa + idx10] - a[offa + idx7];
			cr3 = a[offa + idx9] + a[offa + idx8];
			ci4 = a[offa + idx9] - a[offa + idx8];
			ch[offch + idx2] = a[offa + idx1] + cr2 + cr3;
			ch[offch + ido - 1 + idx3] = a[offa + idx1] + tr11 * cr2 + tr12 * cr3;
			ch[offch + idx4] = ti11 * ci5 + ti12 * ci4;
			ch[offch + ido - 1 + idx5] = a[offa + idx1] + tr12 * cr2 + tr11 * cr3;
			ch[offch + idx6] = ti12 * ci5 - ti11 * ci4;
		}
		if (ido == 1)
			return;
		for (k = 0; k < l1; ++k) {
			int idx1 = k * ido;
			int idx2 = 5 * idx1;
			int idx3 = idx2 + ido;
			int idx4 = idx3 + ido;
			int idx5 = idx4 + ido;
			int idx6 = idx5 + ido;
			int idx7 = idx1 + l1 * ido;
			int idx8 = idx7 + l1 * ido;
			int idx9 = idx8 + l1 * ido;
			int idx10 = idx9 + l1 * ido;
			for (i = 2; i < ido; i += 2) {
				int idx11 = i - 1 + iw1;
				int idx12 = i - 1 + iw2;
				int idx13 = i - 1 + iw3;
				int idx14 = i - 1 + iw4;
				w1r = wtable_r[idx11 - 1];
				w1i = wtable_r[idx11];
				w2r = wtable_r[idx12 - 1];
				w2i = wtable_r[idx12];
				w3r = wtable_r[idx13 - 1];
				w3i = wtable_r[idx13];
				w4r = wtable_r[idx14 - 1];
				w4i = wtable_r[idx14];
				ic = ido - i;
				int idx15 = offa + i;
				int idx16 = offch + i;
				int idx17 = offch + ic;
				dr2 = w1r * a[idx15 - 1 + idx7] + w1i * a[idx15 + idx7];
				di2 = w1r * a[idx15 + idx7] - w1i * a[idx15 - 1 + idx7];
				dr3 = w2r * a[idx15 - 1 + idx8] + w2i * a[idx15 + idx8];
				di3 = w2r * a[idx15 + idx8] - w2i * a[idx15 - 1 + idx8];
				dr4 = w3r * a[idx15 - 1 + idx9] + w3i * a[idx15 + idx9];
				di4 = w3r * a[idx15 + idx9] - w3i * a[idx15 - 1 + idx9];
				dr5 = w4r * a[idx15 - 1 + idx10] + w4i * a[idx15 + idx10];
				di5 = w4r * a[idx15 + idx10] - w4i * a[idx15 - 1 + idx10];
				cr2 = dr2 + dr5;
				ci5 = dr5 - dr2;
				cr5 = di2 - di5;
				ci2 = di2 + di5;
				cr3 = dr3 + dr4;
				ci4 = dr4 - dr3;
				cr4 = di3 - di4;
				ci3 = di3 + di4;
				ch[idx16 - 1 + idx2] = a[idx15 - 1 + idx1] + cr2 + cr3;
				ch[idx16 + idx2] = a[idx15 + idx1] + ci2 + ci3;
				tr2 = a[idx15 - 1 + idx1] + tr11 * cr2 + tr12 * cr3;
				ti2 = a[idx15 + idx1] + tr11 * ci2 + tr12 * ci3;
				tr3 = a[idx15 - 1 + idx1] + tr12 * cr2 + tr11 * cr3;
				ti3 = a[idx15 + idx1] + tr12 * ci2 + tr11 * ci3;
				tr5 = ti11 * cr5 + ti12 * cr4;
				ti5 = ti11 * ci5 + ti12 * ci4;
				tr4 = ti12 * cr5 - ti11 * cr4;
				ti4 = ti12 * ci5 - ti11 * ci4;
				ch[idx16 - 1 + idx4] = tr2 + tr5;
				ch[idx17 - 1 + idx3] = tr2 - tr5;
				ch[idx16 + idx4] = ti2 + ti5;
				ch[idx17 + idx3] = ti5 - ti2;
				ch[idx16 - 1 + idx6] = tr3 + tr4;
				ch[idx17 - 1 + idx5] = tr3 - tr4;
				ch[idx16 + idx6] = ti3 + ti4;
				ch[idx17 + idx5] = ti4 - ti3;
			}
		}
	}

	/*-------------------------------------------------
	   radb5: Real FFT's backward processing of factor 5
	  -------------------------------------------------*/
	void radb5(final int ido, final int l1, final float a[], final int offa, final float ch[], final int offch, final int offset) {
		final float tr11 = (float) 0.309016994374947;
		final float ti11 = (float) 0.951056516295154;
		final float tr12 = (float) -0.809016994374947;
		final float ti12 = (float) 0.587785252292473;
		int i, k, ic;
		float ci2, ci3, ci4, ci5, di3, di4, di5, di2, cr2, cr3, cr5, cr4, ti2, ti3, ti4, ti5, dr3, dr4, dr5, dr2, tr2, tr3, tr4, tr5, w1r, w1i, w2r, w2i, w3r, w3i, w4r, w4i;
		int iw1, iw2, iw3, iw4;
		iw1 = offset;
		iw2 = iw1 + ido;
		iw3 = iw2 + ido;
		iw4 = iw3 + ido;

		for (k = 0; k < l1; k++) {
			int idx1 = k * ido;
			int idx2 = 5 * idx1;
			int idx3 = idx2 + ido;
			int idx4 = idx3 + ido;
			int idx5 = idx4 + ido;
			int idx6 = idx5 + ido;
			int idx7 = idx1 + l1 * ido;
			int idx8 = idx7 + l1 * ido;
			int idx9 = idx8 + l1 * ido;
			int idx10 = idx9 + l1 * ido;

			ti5 = 2 * a[offa + idx4];
			ti4 = 2 * a[offa + idx6];
			tr2 = 2 * a[offa + ido - 1 + idx3];
			tr3 = 2 * a[offa + ido - 1 + idx5];
			ch[offch + idx1] = a[offa + idx2] + tr2 + tr3;
			cr2 = a[offa + idx2] + tr11 * tr2 + tr12 * tr3;
			cr3 = a[offa + idx2] + tr12 * tr2 + tr11 * tr3;
			ci5 = ti11 * ti5 + ti12 * ti4;
			ci4 = ti12 * ti5 - ti11 * ti4;
			ch[offch + idx7] = cr2 - ci5;
			ch[offch + idx8] = cr3 - ci4;
			ch[offch + idx9] = cr3 + ci4;
			ch[offch + idx10] = cr2 + ci5;
		}
		if (ido == 1)
			return;
		for (k = 0; k < l1; ++k) {
			int idx1 = k * ido;
			int idx2 = 5 * idx1;
			int idx3 = idx2 + ido;
			int idx4 = idx3 + ido;
			int idx5 = idx4 + ido;
			int idx6 = idx5 + ido;
			int idx7 = idx1 + l1 * ido;
			int idx8 = idx7 + l1 * ido;
			int idx9 = idx8 + l1 * ido;
			int idx10 = idx9 + l1 * ido;
			for (i = 2; i < ido; i += 2) {
				ic = ido - i;
				int idx11 = i - 1 + iw1;
				int idx12 = i - 1 + iw2;
				int idx13 = i - 1 + iw3;
				int idx14 = i - 1 + iw4;
				w1r = wtable_r[idx11 - 1];
				w1i = wtable_r[idx11];
				w2r = wtable_r[idx12 - 1];
				w2i = wtable_r[idx12];
				w3r = wtable_r[idx13 - 1];
				w3i = wtable_r[idx13];
				w4r = wtable_r[idx14 - 1];
				w4i = wtable_r[idx14];
				int idx15 = offa + i;
				int idx16 = offa + ic;
				int idx17 = offch + i;
				ti5 = a[idx15 + idx4] + a[idx16 + idx3];
				ti2 = a[idx15 + idx4] - a[idx16 + idx3];
				ti4 = a[idx15 + idx6] + a[idx16 + idx5];
				ti3 = a[idx15 + idx6] - a[idx16 + idx5];
				tr5 = a[idx15 - 1 + idx4] - a[idx16 - 1 + idx3];
				tr2 = a[idx15 - 1 + idx4] + a[idx16 - 1 + idx3];
				tr4 = a[idx15 - 1 + idx6] - a[idx16 - 1 + idx5];
				tr3 = a[idx15 - 1 + idx6] + a[idx16 - 1 + idx5];
				ch[idx17 - 1 + idx1] = a[idx15 - 1 + idx2] + tr2 + tr3;
				ch[idx17 + idx1] = a[idx15 + idx2] + ti2 + ti3;
				cr2 = a[idx15 - 1 + idx2] + tr11 * tr2 + tr12 * tr3;

				ci2 = a[idx15 + idx2] + tr11 * ti2 + tr12 * ti3;
				cr3 = a[idx15 - 1 + idx2] + tr12 * tr2 + tr11 * tr3;

				ci3 = a[idx15 + idx2] + tr12 * ti2 + tr11 * ti3;
				cr5 = ti11 * tr5 + ti12 * tr4;
				ci5 = ti11 * ti5 + ti12 * ti4;
				cr4 = ti12 * tr5 - ti11 * tr4;
				ci4 = ti12 * ti5 - ti11 * ti4;
				dr3 = cr3 - ci4;
				dr4 = cr3 + ci4;
				di3 = ci3 + cr4;
				di4 = ci3 - cr4;
				dr5 = cr2 + ci5;
				dr2 = cr2 - ci5;
				di5 = ci2 - cr5;
				di2 = ci2 + cr5;
				ch[idx17 - 1 + idx7] = w1r * dr2 - w1i * di2;
				ch[idx17 + idx7] = w1r * di2 + w1i * dr2;
				ch[idx17 - 1 + idx8] = w2r * dr3 - w2i * di3;
				ch[idx17 + idx8] = w2r * di3 + w2i * dr3;
				ch[idx17 - 1 + idx9] = w3r * dr4 - w3i * di4;
				ch[idx17 + idx9] = w3r * di4 + w3i * dr4;
				ch[idx17 - 1 + idx10] = w4r * dr5 - w4i * di5;
				ch[idx17 + idx10] = w4r * di5 + w4i * dr5;
			}
		}
	}

	/*---------------------------------------------------------
	   radfg: Real FFT's forward processing of general factor
	  --------------------------------------------------------*/
	void radfg(final int ido, final int ip, final int l1, final int idl1, final float in[], final int in_off, final float out[], final int out_off, final int offset) {
		final float twopi = (float) 6.283185307179586;
		int idij, ipph, i, j, k, l, j2, ic, jc, lc, ik, is, nbd;
		float dc2, ai1, ai2, ar1, ar2, ds2, dcp, arg, dsp, ar1h, ar2h, w1r, w1i;
		int iw1 = offset;

		arg = twopi / (float) ip;
		dcp = (float) Math.cos(arg);
		dsp = (float) Math.sin(arg);
		ipph = (ip + 1) / 2;
		nbd = (ido - 1) / 2;
		if (ido != 1) {
			for (ik = 0; ik < idl1; ik++)
				out[out_off + ik] = in[in_off + ik];
			for (j = 1; j < ip; j++) {
				int idx1 = j * l1 * ido;
				for (k = 0; k < l1; k++) {
					int idx2 = k * ido + idx1;
					out[out_off + idx2] = in[in_off + idx2];
				}
			}
			if (nbd <= l1) {
				is = -ido;
				for (j = 1; j < ip; j++) {
					is += ido;
					idij = is - 1;
					int idx1 = j * l1 * ido;
					for (i = 2; i < ido; i += 2) {
						idij += 2;
						int idx2 = idij + iw1;
						int idx4 = in_off + i;
						int idx5 = out_off + i;
						w1r = wtable_r[idx2 - 1];
						w1i = wtable_r[idx2];
						for (k = 0; k < l1; k++) {
							int idx3 = k * ido + idx1;
							int oidx1 = idx5 + idx3;
							int iidx1 = idx4 + idx3;
							float i1i = in[iidx1 - 1];
							float i1r = in[iidx1];

							out[oidx1 - 1] = w1r * i1i + w1i * i1r;
							out[oidx1] = w1r * i1r - w1i * i1i;
						}
					}
				}
			} else {
				is = -ido;
				for (j = 1; j < ip; j++) {
					is += ido;
					int idx1 = j * l1 * ido;
					for (k = 0; k < l1; k++) {
						idij = is - 1;
						int idx3 = k * ido + idx1;
						for (i = 2; i < ido; i += 2) {
							idij += 2;
							int idx2 = idij + iw1;
							w1r = wtable_r[idx2 - 1];
							w1i = wtable_r[idx2];
							int oidx1 = out_off + i + idx3;
							int iidx1 = in_off + i + idx3;
							float i1i = in[iidx1 - 1];
							float i1r = in[iidx1];

							out[oidx1 - 1] = w1r * i1i + w1i * i1r;
							out[oidx1] = w1r * i1r - w1i * i1i;
						}
					}
				}
			}
			if (nbd >= l1) {
				for (j = 1; j < ipph; j++) {
					jc = ip - j;
					int idx1 = j * l1 * ido;
					int idx2 = jc * l1 * ido;
					for (k = 0; k < l1; k++) {
						int idx3 = k * ido + idx1;
						int idx4 = k * ido + idx2;
						for (i = 2; i < ido; i += 2) {
							int idx5 = in_off + i;
							int idx6 = out_off + i;
							int iidx1 = idx5 + idx3;
							int iidx2 = idx5 + idx4;
							int oidx1 = idx6 + idx3;
							int oidx2 = idx6 + idx4;
							float o1i = out[oidx1 - 1];
							float o1r = out[oidx1];
							float o2i = out[oidx2 - 1];
							float o2r = out[oidx2];

							in[iidx1 - 1] = o1i + o2i;
							in[iidx1] = o1r + o2r;

							in[iidx2 - 1] = o1r - o2r;
							in[iidx2] = o2i - o1i;
						}
					}
				}
			} else {
				for (j = 1; j < ipph; j++) {
					jc = ip - j;
					int idx1 = j * l1 * ido;
					int idx2 = jc * l1 * ido;
					for (i = 2; i < ido; i += 2) {
						int idx5 = in_off + i;
						int idx6 = out_off + i;
						for (k = 0; k < l1; k++) {
							int idx3 = k * ido + idx1;
							int idx4 = k * ido + idx2;
							int iidx1 = idx5 + idx3;
							int iidx2 = idx5 + idx4;
							int oidx1 = idx6 + idx3;
							int oidx2 = idx6 + idx4;
							float o1i = out[oidx1 - 1];
							float o1r = out[oidx1];
							float o2i = out[oidx2 - 1];
							float o2r = out[oidx2];

							in[iidx1 - 1] = o1i + o2i;
							in[iidx1] = o1r + o2r;
							in[iidx2 - 1] = o1r - o2r;
							in[iidx2] = o2i - o1i;
						}
					}
				}
			}
		} else {
			System.arraycopy(out, out_off, in, in_off, idl1);
		}
		for (j = 1; j < ipph; j++) {
			jc = ip - j;
			int idx1 = j * l1 * ido;
			int idx2 = jc * l1 * ido;
			for (k = 0; k < l1; k++) {
				int idx3 = k * ido + idx1;
				int idx4 = k * ido + idx2;
				int oidx1 = out_off + idx3;
				int oidx2 = out_off + idx4;
				float o1r = out[oidx1];
				float o2r = out[oidx2];

				in[in_off + idx3] = o1r + o2r;
				in[in_off + idx4] = o2r - o1r;
			}
		}

		ar1 = 1;
		ai1 = 0;
		int idx0 = (ip - 1) * idl1;
		for (l = 1; l < ipph; l++) {
			lc = ip - l;
			ar1h = dcp * ar1 - dsp * ai1;
			ai1 = dcp * ai1 + dsp * ar1;
			ar1 = ar1h;
			int idx1 = l * idl1;
			int idx2 = lc * idl1;
			for (ik = 0; ik < idl1; ik++) {
				int idx3 = out_off + ik;
				int idx4 = in_off + ik;
				out[idx3 + idx1] = in[idx4] + ar1 * in[idx4 + idl1];
				out[idx3 + idx2] = ai1 * in[idx4 + idx0];
			}
			dc2 = ar1;
			ds2 = ai1;
			ar2 = ar1;
			ai2 = ai1;
			for (j = 2; j < ipph; j++) {
				jc = ip - j;
				ar2h = dc2 * ar2 - ds2 * ai2;
				ai2 = dc2 * ai2 + ds2 * ar2;
				ar2 = ar2h;
				int idx3 = j * idl1;
				int idx4 = jc * idl1;
				for (ik = 0; ik < idl1; ik++) {
					int idx5 = out_off + ik;
					int idx6 = in_off + ik;
					out[idx5 + idx1] += ar2 * in[idx6 + idx3];
					out[idx5 + idx2] += ai2 * in[idx6 + idx4];
				}
			}
		}
		for (j = 1; j < ipph; j++) {
			int idx1 = j * idl1;
			for (ik = 0; ik < idl1; ik++) {
				out[out_off + ik] += in[in_off + ik + idx1];
			}
		}

		if (ido >= l1) {
			for (k = 0; k < l1; k++) {
				int idx1 = k * ido;
				int idx2 = idx1 * ip;
				for (i = 0; i < ido; i++) {
					in[in_off + i + idx2] = out[out_off + i + idx1];
				}
			}
		} else {
			for (i = 0; i < ido; i++) {
				for (k = 0; k < l1; k++) {
					int idx1 = k * ido;
					in[in_off + i + idx1 * ip] = out[out_off + i + idx1];
				}
			}
		}
		int idx01 = ip * ido;
		for (j = 1; j < ipph; j++) {
			jc = ip - j;
			j2 = 2 * j;
			int idx1 = j * l1 * ido;
			int idx2 = jc * l1 * ido;
			int idx3 = j2 * ido;
			for (k = 0; k < l1; k++) {
				int idx4 = k * ido;
				int idx5 = idx4 + idx1;
				int idx6 = idx4 + idx2;
				int idx7 = k * idx01;
				in[in_off + ido - 1 + idx3 - ido + idx7] = out[out_off + idx5];
				in[in_off + idx3 + idx7] = out[out_off + idx6];
			}
		}
		if (ido == 1)
			return;
		if (nbd >= l1) {
			for (j = 1; j < ipph; j++) {
				jc = ip - j;
				j2 = 2 * j;
				int idx1 = j * l1 * ido;
				int idx2 = jc * l1 * ido;
				int idx3 = j2 * ido;
				for (k = 0; k < l1; k++) {
					int idx4 = k * idx01;
					int idx5 = k * ido;
					for (i = 2; i < ido; i += 2) {
						ic = ido - i;
						int idx6 = in_off + i;
						int idx7 = in_off + ic;
						int idx8 = out_off + i;
						int iidx1 = idx6 + idx3 + idx4;
						int iidx2 = idx7 + idx3 - ido + idx4;
						int oidx1 = idx8 + idx5 + idx1;
						int oidx2 = idx8 + idx5 + idx2;
						float o1i = out[oidx1 - 1];
						float o1r = out[oidx1];
						float o2i = out[oidx2 - 1];
						float o2r = out[oidx2];

						in[iidx1 - 1] = o1i + o2i;
						in[iidx2 - 1] = o1i - o2i;
						in[iidx1] = o1r + o2r;
						in[iidx2] = o2r - o1r;
					}
				}
			}
		} else {
			for (j = 1; j < ipph; j++) {
				jc = ip - j;
				j2 = 2 * j;
				int idx1 = j * l1 * ido;
				int idx2 = jc * l1 * ido;
				int idx3 = j2 * ido;
				for (i = 2; i < ido; i += 2) {
					ic = ido - i;
					int idx6 = in_off + i;
					int idx7 = in_off + ic;
					int idx8 = out_off + i;
					for (k = 0; k < l1; k++) {
						int idx4 = k * idx01;
						int idx5 = k * ido;
						int iidx1 = idx6 + idx3 + idx4;
						int iidx2 = idx7 + idx3 - ido + idx4;
						int oidx1 = idx8 + idx5 + idx1;
						int oidx2 = idx8 + idx5 + idx2;
						float o1i = out[oidx1 - 1];
						float o1r = out[oidx1];
						float o2i = out[oidx2 - 1];
						float o2r = out[oidx2];

						in[iidx1 - 1] = o1i + o2i;
						in[iidx2 - 1] = o1i - o2i;
						in[iidx1] = o1r + o2r;
						in[iidx2] = o2r - o1r;
					}
				}
			}
		}
	}

	/*---------------------------------------------------------
	   radbg: Real FFT's backward processing of general factor
	  --------------------------------------------------------*/
	void radbg(final int ido, final int ip, final int l1, final int idl1, final float in[], final int in_off, final float out[], final int out_off, final int offset) {
		final float twopi = (float) 6.283185307179586;
		int idij, ipph, i, j, k, l, j2, ic, jc, lc, ik, is;
		float dc2, ai1, ai2, ar1, ar2, ds2, w1r, w1i;
		int nbd;
		float dcp, arg, dsp, ar1h, ar2h;
		int iw1 = offset;

		arg = twopi / (float) ip;
		dcp = (float) Math.cos(arg);
		dsp = (float) Math.sin(arg);
		nbd = (ido - 1) / 2;
		ipph = (ip + 1) / 2;
		int idx0 = ip * ido;
		if (ido >= l1) {
			for (k = 0; k < l1; k++) {
				int idx1 = k * ido;
				int idx2 = k * idx0;
				for (i = 0; i < ido; i++) {
					out[out_off + i + idx1] = in[in_off + i + idx2];
				}
			}
		} else {
			for (i = 0; i < ido; i++) {
				int idx1 = out_off + i;
				int idx2 = in_off + i;
				for (k = 0; k < l1; k++) {
					out[idx1 + k * ido] = in[idx2 + k * idx0];
				}
			}
		}
		int iidx0 = in_off + ido - 1;
		for (j = 1; j < ipph; j++) {
			jc = ip - j;
			j2 = 2 * j;
			int idx1 = j * l1 * ido;
			int idx2 = jc * l1 * ido;
			int idx3 = j2 * ido;
			for (k = 0; k < l1; k++) {
				int idx4 = k * ido;
				int idx5 = idx4 * ip;
				int iidx1 = iidx0 + idx3 + idx5 - ido;
				int iidx2 = in_off + idx3 + idx5;
				float i1r = in[iidx1];
				float i2r = in[iidx2];

				out[out_off + idx4 + idx1] = i1r + i1r;
				out[out_off + idx4 + idx2] = i2r + i2r;
			}
		}

		if (ido != 1) {
			if (nbd >= l1) {
				for (j = 1; j < ipph; j++) {
					jc = ip - j;
					int idx1 = j * l1 * ido;
					int idx2 = jc * l1 * ido;
					int idx3 = 2 * j * ido;
					for (k = 0; k < l1; k++) {
						int idx4 = k * ido + idx1;
						int idx5 = k * ido + idx2;
						int idx6 = k * ip * ido + idx3;
						for (i = 2; i < ido; i += 2) {
							ic = ido - i;
							int idx7 = out_off + i;
							int idx8 = in_off + ic;
							int idx9 = in_off + i;
							int oidx1 = idx7 + idx4;
							int oidx2 = idx7 + idx5;
							int iidx1 = idx9 + idx6;
							int iidx2 = idx8 + idx6 - ido;
							float a1i = in[iidx1 - 1];
							float a1r = in[iidx1];
							float a2i = in[iidx2 - 1];
							float a2r = in[iidx2];

							out[oidx1 - 1] = a1i + a2i;
							out[oidx2 - 1] = a1i - a2i;
							out[oidx1] = a1r - a2r;
							out[oidx2] = a1r + a2r;
						}
					}
				}
			} else {
				for (j = 1; j < ipph; j++) {
					jc = ip - j;
					int idx1 = j * l1 * ido;
					int idx2 = jc * l1 * ido;
					int idx3 = 2 * j * ido;
					for (i = 2; i < ido; i += 2) {
						ic = ido - i;
						int idx7 = out_off + i;
						int idx8 = in_off + ic;
						int idx9 = in_off + i;
						for (k = 0; k < l1; k++) {
							int idx4 = k * ido + idx1;
							int idx5 = k * ido + idx2;
							int idx6 = k * ip * ido + idx3;
							int oidx1 = idx7 + idx4;
							int oidx2 = idx7 + idx5;
							int iidx1 = idx9 + idx6;
							int iidx2 = idx8 + idx6 - ido;
							float a1i = in[iidx1 - 1];
							float a1r = in[iidx1];
							float a2i = in[iidx2 - 1];
							float a2r = in[iidx2];

							out[oidx1 - 1] = a1i + a2i;
							out[oidx2 - 1] = a1i - a2i;
							out[oidx1] = a1r - a2r;
							out[oidx2] = a1r + a2r;
						}
					}
				}
			}
		}

		ar1 = 1;
		ai1 = 0;
		int idx01 = (ip - 1) * idl1;
		for (l = 1; l < ipph; l++) {
			lc = ip - l;
			ar1h = dcp * ar1 - dsp * ai1;
			ai1 = dcp * ai1 + dsp * ar1;
			ar1 = ar1h;
			int idx1 = l * idl1;
			int idx2 = lc * idl1;
			for (ik = 0; ik < idl1; ik++) {
				int idx3 = in_off + ik;
				int idx4 = out_off + ik;
				in[idx3 + idx1] = out[idx4] + ar1 * out[idx4 + idl1];
				in[idx3 + idx2] = ai1 * out[idx4 + idx01];
			}
			dc2 = ar1;
			ds2 = ai1;
			ar2 = ar1;
			ai2 = ai1;
			for (j = 2; j < ipph; j++) {
				jc = ip - j;
				ar2h = dc2 * ar2 - ds2 * ai2;
				ai2 = dc2 * ai2 + ds2 * ar2;
				ar2 = ar2h;
				int idx5 = j * idl1;
				int idx6 = jc * idl1;
				for (ik = 0; ik < idl1; ik++) {
					int idx7 = in_off + ik;
					int idx8 = out_off + ik;
					in[idx7 + idx1] += ar2 * out[idx8 + idx5];
					in[idx7 + idx2] += ai2 * out[idx8 + idx6];
				}
			}
		}
		for (j = 1; j < ipph; j++) {
			int idx1 = j * idl1;
			for (ik = 0; ik < idl1; ik++) {
				int idx2 = out_off + ik;
				out[idx2] += out[idx2 + idx1];
			}
		}
		for (j = 1; j < ipph; j++) {
			jc = ip - j;
			int idx1 = j * l1 * ido;
			int idx2 = jc * l1 * ido;
			for (k = 0; k < l1; k++) {
				int idx3 = k * ido;
				int oidx1 = out_off + idx3;
				int iidx1 = in_off + idx3 + idx1;
				int iidx2 = in_off + idx3 + idx2;
				float i1r = in[iidx1];
				float i2r = in[iidx2];

				out[oidx1 + idx1] = i1r - i2r;
				out[oidx1 + idx2] = i1r + i2r;
			}
		}

		if (ido == 1)
			return;
		if (nbd >= l1) {
			for (j = 1; j < ipph; j++) {
				jc = ip - j;
				int idx1 = j * l1 * ido;
				int idx2 = jc * l1 * ido;
				for (k = 0; k < l1; k++) {
					int idx3 = k * ido;
					for (i = 2; i < ido; i += 2) {
						int idx4 = out_off + i;
						int idx5 = in_off + i;
						int oidx1 = idx4 + idx3 + idx1;
						int oidx2 = idx4 + idx3 + idx2;
						int iidx1 = idx5 + idx3 + idx1;
						int iidx2 = idx5 + idx3 + idx2;
						float i1i = in[iidx1 - 1];
						float i1r = in[iidx1];
						float i2i = in[iidx2 - 1];
						float i2r = in[iidx2];

						out[oidx1 - 1] = i1i - i2r;
						out[oidx2 - 1] = i1i + i2r;
						out[oidx1] = i1r + i2i;
						out[oidx2] = i1r - i2i;
					}
				}
			}
		} else {
			for (j = 1; j < ipph; j++) {
				jc = ip - j;
				int idx1 = j * l1 * ido;
				int idx2 = jc * l1 * ido;
				for (i = 2; i < ido; i += 2) {
					int idx4 = out_off + i;
					int idx5 = in_off + i;
					for (k = 0; k < l1; k++) {
						int idx3 = k * ido;
						int oidx1 = idx4 + idx3 + idx1;
						int oidx2 = idx4 + idx3 + idx2;
						int iidx1 = idx5 + idx3 + idx1;
						int iidx2 = idx5 + idx3 + idx2;
						float i1i = in[iidx1 - 1];
						float i1r = in[iidx1];
						float i2i = in[iidx2 - 1];
						float i2r = in[iidx2];

						out[oidx1 - 1] = i1i - i2r;
						out[oidx2 - 1] = i1i + i2r;
						out[oidx1] = i1r + i2i;
						out[oidx2] = i1r - i2i;
					}
				}
			}
		}
		System.arraycopy(out, out_off, in, in_off, idl1);
		for (j = 1; j < ip; j++) {
			int idx1 = j * l1 * ido;
			for (k = 0; k < l1; k++) {
				int idx2 = k * ido + idx1;
				in[in_off + idx2] = out[out_off + idx2];
			}
		}
		if (nbd <= l1) {
			is = -ido;
			for (j = 1; j < ip; j++) {
				is += ido;
				idij = is - 1;
				int idx1 = j * l1 * ido;
				for (i = 2; i < ido; i += 2) {
					idij += 2;
					int idx2 = idij + iw1;
					w1r = wtable_r[idx2 - 1];
					w1i = wtable_r[idx2];
					int idx4 = in_off + i;
					int idx5 = out_off + i;
					for (k = 0; k < l1; k++) {
						int idx3 = k * ido + idx1;
						int iidx1 = idx4 + idx3;
						int oidx1 = idx5 + idx3;
						float o1i = out[oidx1 - 1];
						float o1r = out[oidx1];

						in[iidx1 - 1] = w1r * o1i - w1i * o1r;
						in[iidx1] = w1r * o1r + w1i * o1i;
					}
				}
			}
		} else {
			is = -ido;
			for (j = 1; j < ip; j++) {
				is += ido;
				int idx1 = j * l1 * ido;
				for (k = 0; k < l1; k++) {
					idij = is - 1;
					int idx3 = k * ido + idx1;
					for (i = 2; i < ido; i += 2) {
						idij += 2;
						int idx2 = idij + iw1;
						w1r = wtable_r[idx2 - 1];
						w1i = wtable_r[idx2];
						int idx4 = in_off + i;
						int idx5 = out_off + i;
						int iidx1 = idx4 + idx3;
						int oidx1 = idx5 + idx3;
						float o1i = out[oidx1 - 1];
						float o1r = out[oidx1];

						in[iidx1 - 1] = w1r * o1i - w1i * o1r;
						in[iidx1] = w1r * o1r + w1i * o1i;

					}
				}
			}
		}
	}

	/*---------------------------------------------------------
	   cfftf1: further processing of Complex forward FFT
	  --------------------------------------------------------*/
	void cfftf(float a[], int offa, int isign) {
		int idot, i;
		int k1, l1, l2;
		int na, nf, ip, iw, ido, idl1;
		int[] nac = new int[1];
		final int twon = 2 * n;

		int iw1, iw2;
		float[] ch = new float[twon];

		iw1 = twon;
		iw2 = 4 * n;
		//		System.arraycopy(wtable, 0, ch, 0, twon);

		nac[0] = 0;

		nf = (int) wtable[1 + iw2];
		na = 0;
		l1 = 1;
		iw = iw1;
		for (k1 = 2; k1 <= nf + 1; k1++) {
			ip = (int) wtable[k1 + iw2];
			l2 = ip * l1;
			ido = n / l2;
			idot = ido + ido;
			idl1 = idot * l1;
			if (ip == 4) {
				if (na == 0) {
					passf4(idot, l1, a, offa, ch, 0, iw, isign);
				} else {
					passf4(idot, l1, ch, 0, a, offa, iw, isign);
				}
				na = 1 - na;
			} else if (ip == 2) {
				if (na == 0) {
					passf2(idot, l1, a, offa, ch, 0, iw, isign);
				} else {
					passf2(idot, l1, ch, 0, a, offa, iw, isign);
				}
				na = 1 - na;
			} else if (ip == 3) {
				if (na == 0) {
					passf3(idot, l1, a, offa, ch, 0, iw, isign);
				} else {
					passf3(idot, l1, ch, 0, a, offa, iw, isign);
				}
				na = 1 - na;
			} else if (ip == 5) {
				if (na == 0) {
					passf5(idot, l1, a, offa, ch, 0, iw, isign);
				} else {
					passf5(idot, l1, ch, 0, a, offa, iw, isign);
				}
				na = 1 - na;
			} else {
				if (na == 0) {
					passfg(nac, idot, ip, l1, idl1, a, offa, ch, 0, iw, isign);
				} else {
					passfg(nac, idot, ip, l1, idl1, ch, 0, a, offa, iw, isign);
				}
				if (nac[0] != 0)
					na = 1 - na;
			}
			l1 = l2;
			iw += (ip - 1) * idot;
		}
		if (na == 0)
			return;
		System.arraycopy(ch, 0, a, offa, twon);

	}

	/*----------------------------------------------------------------------
	   passf2: Complex FFT's forward/backward processing of factor 2;
	   isign is +1 for backward and -1 for forward transforms
	  ----------------------------------------------------------------------*/

	void passf2(final int ido, final int l1, final float in[], final int in_off, final float out[], final int out_off, final int offset, final int isign) {
		int i, k;
		float t1i, t1r;
		int iw1;
		iw1 = offset;
		int idx = ido * l1;
		if (ido <= 2) {
			for (k = 0; k < l1; k++) {
				int idx0 = k * ido;
				int oidx1 = out_off + idx0;
				int oidx2 = oidx1 + idx;
				int iidx1 = in_off + 2 * idx0;
				int iidx2 = iidx1 + ido;
				float a1r = in[iidx1];
				float a1i = in[iidx1 + 1];
				float a2r = in[iidx2];
				float a2i = in[iidx2 + 1];

				out[oidx1] = a1r + a2r;
				out[oidx1 + 1] = a1i + a2i;

				out[oidx2] = a1r - a2r;
				out[oidx2 + 1] = a1i - a2i;
			}
		} else {
			for (k = 0; k < l1; k++) {
				for (i = 0; i < ido - 1; i += 2) {
					int idx0 = k * ido;
					int oidx1 = out_off + i + idx0;
					int oidx2 = oidx1 + idx;
					int iidx1 = in_off + i + 2 * idx0;
					int iidx2 = iidx1 + ido;
					int widx1 = i + iw1;
					float a1r = in[iidx1];
					float a1i = in[iidx1 + 1];
					float a2r = in[iidx2];
					float a2i = in[iidx2 + 1];

					out[oidx1] = a1r + a2r;
					out[oidx1 + 1] = a1i + a2i;

					t1r = a1r - a2r;
					t1i = a1i - a2i;
					float w1r = wtable[widx1];
					float w1i = isign * wtable[widx1 + 1];

					out[oidx2] = w1r * t1r - w1i * t1i;
					out[oidx2 + 1] = w1r * t1i + w1i * t1r;
				}
			}
		}
	}

	/*----------------------------------------------------------------------
	   passf3: Complex FFT's forward/backward processing of factor 3;
	   isign is +1 for backward and -1 for forward transforms
	  ----------------------------------------------------------------------*/
	void passf3(final int ido, final int l1, final float in[], final int in_off, final float out[], final int out_off, final int offset, final int isign) {
		final float taur = (float) -0.5;
		final float taui = (float) 0.866025403784439;
		int i, k;
		float ci2, ci3, di2, di3, cr2, cr3, dr2, dr3, ti2, tr2;
		int iw1, iw2;

		iw1 = offset;
		iw2 = iw1 + ido;

		final int idxt = l1 * ido;

		if (ido == 2) {
			for (k = 1; k <= l1; k++) {
				int idx1 = in_off + (3 * k - 2) * ido;
				int idx2 = idx1 + ido;
				int idx3 = idx1 - ido;
				int idx4 = out_off + (k - 1) * ido;
				int idx5 = idx4 + idxt;
				int idx6 = idx5 + idxt;
				float a1r = in[idx1];
				float a1i = in[idx1 + 1];
				float a2r = in[idx2];
				float a2i = in[idx2 + 1];
				float a3r = in[idx3];
				float a3i = in[idx3 + 1];

				tr2 = a1r + a2r;
				cr2 = a3r + taur * tr2;
				out[idx4] = in[idx3] + tr2;

				ti2 = a1i + a2i;
				ci2 = a3i + taur * ti2;
				out[idx4 + 1] = a3i + ti2;

				cr3 = isign * taui * (a1r - a2r);
				ci3 = isign * taui * (a1i - a2i);
				out[idx5] = cr2 - ci3;
				out[idx6] = cr2 + ci3;
				out[idx5 + 1] = ci2 + cr3;
				out[idx6 + 1] = ci2 - cr3;
			}
		} else {
			for (k = 1; k <= l1; k++) {
				for (i = 0; i < ido - 1; i += 2) {
					int idx1 = in_off + i + (3 * k - 2) * ido;
					int idx2 = idx1 + ido;
					int idx3 = idx1 - ido;
					int idx4 = out_off + i + (k - 1) * ido;
					int idx5 = idx4 + idxt;
					int idx6 = idx5 + idxt;
					int idx7 = i + iw1;
					int idx8 = i + iw2;

					float a1r = in[idx1];
					float a1i = in[idx1 + 1];
					float a2r = in[idx2];
					float a2i = in[idx2 + 1];
					float a3r = in[idx3];
					float a3i = in[idx3 + 1];

					tr2 = a1r + a2r;
					cr2 = a3r + taur * tr2;
					out[idx4] = a3r + tr2;
					ti2 = a1i + a2i;
					ci2 = a3i + taur * ti2;
					out[idx4 + 1] = a3i + ti2;
					cr3 = isign * taui * (a1r - a2r);
					ci3 = isign * taui * (a1i - a2i);
					dr2 = cr2 - ci3;
					dr3 = cr2 + ci3;
					di2 = ci2 + cr3;
					di3 = ci2 - cr3;
					float w1r = wtable[idx7];
					float w1i = isign * wtable[idx7 + 1];
					float w2r = wtable[idx8];
					float w2i = isign * wtable[idx8 + 1];
					out[idx5 + 1] = w1r * di2 + w1i * dr2;
					out[idx5] = w1r * dr2 - w1i * di2;
					out[idx6 + 1] = w2r * di3 + w2i * dr3;
					out[idx6] = w2r * dr3 - w2i * di3;
				}
			}
		}
	}

	/*----------------------------------------------------------------------
	   passf4: Complex FFT's forward/backward processing of factor 4;
	   isign is +1 for backward and -1 for forward transforms
	  ----------------------------------------------------------------------*/
	void passf4(final int ido, final int l1, final float a[], final int offa, final float ch[], final int offch, final int offset, final int isign) {
		int i, k;
		float ci2, ci3, ci4, cr2, cr3, cr4, ti1, ti2, ti3, ti4, tr1, tr2, tr3, tr4;
		int iw1, iw2, iw3;
		iw1 = offset;
		iw2 = iw1 + ido;
		iw3 = iw2 + ido;

		int idxt2 = l1 * ido;
		if (ido == 2) {
			for (k = 0; k < l1; k++) {
				int idxt1 = k * ido;
				int idx1 = offa + 4 * idxt1 + 1;
				int idx2 = idx1 + ido;
				int idx3 = idx2 + ido;
				int idx4 = idx3 + ido;
				int idx5 = offch + idxt1;
				int idx6 = idx5 + idxt2;
				int idx7 = idx6 + idxt2;
				int idx8 = idx7 + idxt2;

				float a1r = a[idx1];
				float a1i = a[idx1 - 1];
				float a2r = a[idx2];
				float a2i = a[idx2 - 1];
				float a3r = a[idx3];
				float a3i = a[idx3 - 1];
				float a4r = a[idx4];
				float a4i = a[idx4 - 1];

				ti1 = a1r - a3r;
				ti2 = a1r + a3r;
				tr4 = a4r - a2r;
				ti3 = a2r + a4r;
				tr1 = a1i - a3i;
				tr2 = a1i + a3i;
				ti4 = a2i - a4i;
				tr3 = a2i + a4i;
				ch[idx5] = tr2 + tr3;
				ch[idx7] = tr2 - tr3;
				ch[idx5 + 1] = ti2 + ti3;
				ch[idx7 + 1] = ti2 - ti3;
				ch[idx6] = tr1 + isign * tr4;
				ch[idx8] = tr1 - isign * tr4;
				ch[idx6 + 1] = ti1 + isign * ti4;
				ch[idx8 + 1] = ti1 - isign * ti4;
			}
		} else {
			for (k = 0; k < l1; k++) {
				for (i = 0; i < ido - 1; i += 2) {
					int idxt1 = k * ido;
					int idx1 = offa + i + 1 + 4 * idxt1;
					int idx2 = idx1 + ido;
					int idx3 = idx2 + ido;
					int idx4 = idx3 + ido;
					int idx5 = offch + i + idxt1;
					int idx6 = idx5 + idxt2;
					int idx7 = idx6 + idxt2;
					int idx8 = idx7 + idxt2;
					int idx9 = i + iw1;
					int idx10 = i + iw2;
					int idx11 = i + iw3;

					float a1r = a[idx1];
					float a1i = a[idx1 - 1];
					float a2r = a[idx2];
					float a2i = a[idx2 - 1];
					float a3r = a[idx3];
					float a3i = a[idx3 - 1];
					float a4r = a[idx4];
					float a4i = a[idx4 - 1];

					ti1 = a1r - a3r;
					ti2 = a1r + a3r;
					ti3 = a2r + a4r;
					tr4 = a4r - a2r;
					tr1 = a1i - a3i;
					tr2 = a1i + a3i;
					ti4 = a2i - a4i;
					tr3 = a2i + a4i;

					ch[idx5] = tr2 + tr3;
					cr3 = tr2 - tr3;
					ch[idx5 + 1] = ti2 + ti3;
					ci3 = ti2 - ti3;
					cr2 = tr1 + isign * tr4;
					cr4 = tr1 - isign * tr4;
					ci2 = ti1 + isign * ti4;
					ci4 = ti1 - isign * ti4;
					float w1r = wtable[idx9];
					float w1i = isign * wtable[idx9 + 1];
					float w2r = wtable[idx10];
					float w2i = isign * wtable[idx10 + 1];
					float w3r = wtable[idx11];
					float w3i = isign * wtable[idx11 + 1];

					ch[idx6] = w1r * cr2 - w1i * ci2;
					ch[idx6 + 1] = w1r * ci2 + w1i * cr2;
					ch[idx7] = w2r * cr3 - w2i * ci3;
					ch[idx7 + 1] = w2r * ci3 + w2i * cr3;
					ch[idx8] = w3r * cr4 - w3i * ci4;
					ch[idx8 + 1] = w3r * ci4 + w3i * cr4;
				}
			}
		}
	}

	/*----------------------------------------------------------------------
	   passf5: Complex FFT's forward/backward processing of factor 5;
	   isign is +1 for backward and -1 for forward transforms
	  ----------------------------------------------------------------------*/
	void passf5(final int ido, final int l1, final float a[], final int offa, final float ch[], final int offch, final int offset, final int isign)
	/*isign==-1 for forward transform and+1 for backward transform*/
	{
		final float tr11 = (float) 0.309016994374947;
		final float ti11 = (float) 0.951056516295154;
		final float tr12 = (float) -0.809016994374947;
		final float ti12 = (float) 0.587785252292473;
		int i, k;
		float ci2, ci3, ci4, ci5, di3, di4, di5, di2, cr2, cr3, cr5, cr4, ti2, ti3, ti4, ti5, dr3, dr4, dr5, dr2, tr2, tr3, tr4, tr5;
		int iw1, iw2, iw3, iw4;

		iw1 = offset;
		iw2 = iw1 + ido;
		iw3 = iw2 + ido;
		iw4 = iw3 + ido;

		int idxt2 = l1 * ido;

		if (ido == 2) {
			for (k = 1; k <= l1; ++k) {
				int idx1 = offa + (5 * k - 4) * ido + 1;
				int idx2 = idx1 + ido;
				int idx3 = idx1 - ido;
				int idx4 = idx2 + ido;
				int idx5 = idx4 + ido;
				int idx6 = offch + (k - 1) * ido;
				int idx7 = idx6 + idxt2;
				int idx8 = idx7 + idxt2;
				int idx9 = idx8 + idxt2;
				int idx10 = idx9 + idxt2;

				float a1r = a[idx1];
				float a1i = a[idx1 - 1];
				float a2r = a[idx2];
				float a2i = a[idx2 - 1];
				float a3r = a[idx3];
				float a3i = a[idx3 - 1];
				float a4r = a[idx4];
				float a4i = a[idx4 - 1];
				float a5r = a[idx5];
				float a5i = a[idx5 - 1];

				ti5 = a1r - a5r;
				ti2 = a1r + a5r;
				ti4 = a2r - a4r;
				ti3 = a2r + a4r;
				tr5 = a1i - a5i;
				tr2 = a1i + a5i;
				tr4 = a2i - a4i;
				tr3 = a2i + a4i;
				ch[idx6] = a3i + tr2 + tr3;
				ch[idx6 + 1] = a3r + ti2 + ti3;
				cr2 = a3i + tr11 * tr2 + tr12 * tr3;
				ci2 = a3r + tr11 * ti2 + tr12 * ti3;
				cr3 = a3i + tr12 * tr2 + tr11 * tr3;
				ci3 = a3r + tr12 * ti2 + tr11 * ti3;
				cr5 = isign * (ti11 * tr5 + ti12 * tr4);
				ci5 = isign * (ti11 * ti5 + ti12 * ti4);
				cr4 = isign * (ti12 * tr5 - ti11 * tr4);
				ci4 = isign * (ti12 * ti5 - ti11 * ti4);
				ch[idx7] = cr2 - ci5;
				ch[idx10] = cr2 + ci5;
				ch[idx7 + 1] = ci2 + cr5;
				ch[idx8 + 1] = ci3 + cr4;
				ch[idx8] = cr3 - ci4;
				ch[idx9] = cr3 + ci4;
				ch[idx9 + 1] = ci3 - cr4;
				ch[idx10 + 1] = ci2 - cr5;
			}
		} else {
			for (k = 1; k <= l1; k++) {
				for (i = 0; i < ido - 1; i += 2) {
					int idx1 = offa + i + 1 + (k * 5 - 4) * ido;
					int idx2 = idx1 + ido;
					int idx3 = idx1 - ido;
					int idx4 = idx2 + ido;
					int idx5 = idx4 + ido;
					int idx6 = offch + i + (k - 1) * ido;
					int idx7 = idx6 + idxt2;
					int idx8 = idx7 + idxt2;
					int idx9 = idx8 + idxt2;
					int idx10 = idx9 + idxt2;
					int idx11 = i + iw1;
					int idx12 = i + iw2;
					int idx13 = i + iw3;
					int idx14 = i + iw4;

					float a1r = a[idx1];
					float a1i = a[idx1 - 1];
					float a2r = a[idx2];
					float a2i = a[idx2 - 1];
					float a3r = a[idx3];
					float a3i = a[idx3 - 1];
					float a4r = a[idx4];
					float a4i = a[idx4 - 1];
					float a5r = a[idx5];
					float a5i = a[idx5 - 1];

					ti5 = a1r - a5r;
					ti2 = a1r + a5r;
					ti4 = a2r - a4r;
					ti3 = a2r + a4r;
					tr5 = a1i - a5i;
					tr2 = a1i + a5i;
					tr4 = a2i - a4i;
					tr3 = a2i + a4i;
					ch[idx6] = a3i + tr2 + tr3;
					ch[idx6 + 1] = a3r + ti2 + ti3;
					cr2 = a3i + tr11 * tr2 + tr12 * tr3;

					ci2 = a3r + tr11 * ti2 + tr12 * ti3;
					cr3 = a3i + tr12 * tr2 + tr11 * tr3;

					ci3 = a3r + tr12 * ti2 + tr11 * ti3;
					cr5 = isign * (ti11 * tr5 + ti12 * tr4);
					ci5 = isign * (ti11 * ti5 + ti12 * ti4);
					cr4 = isign * (ti12 * tr5 - ti11 * tr4);
					ci4 = isign * (ti12 * ti5 - ti11 * ti4);
					dr3 = cr3 - ci4;
					dr4 = cr3 + ci4;
					di3 = ci3 + cr4;
					di4 = ci3 - cr4;
					dr5 = cr2 + ci5;
					dr2 = cr2 - ci5;
					di5 = ci2 - cr5;
					di2 = ci2 + cr5;
					float w1r = wtable[idx11];
					float w1i = isign * wtable[idx11 + 1];
					float w2r = wtable[idx12];
					float w2i = isign * wtable[idx12 + 1];
					float w3r = wtable[idx13];
					float w3i = isign * wtable[idx13 + 1];
					float w4r = wtable[idx14];
					float w4i = isign * wtable[idx14 + 1];

					ch[idx7] = w1r * dr2 - w1i * di2;
					ch[idx7 + 1] = w1r * di2 + w1i * dr2;
					ch[idx8] = w2r * dr3 - w2i * di3;
					ch[idx8 + 1] = w2r * di3 + w2i * dr3;
					ch[idx9] = w3r * dr4 - w3i * di4;
					ch[idx9 + 1] = w3r * di4 + w3i * dr4;
					ch[idx10] = w4r * dr5 - w4i * di5;
					ch[idx10 + 1] = w4r * di5 + w4i * dr5;
				}
			}
		}
	}

	//	/*----------------------------------------------------------------------
	//	   passf5: Complex FFT's forward/backward processing of factor 6;
	//	   isign is +1 for backward and -1 for forward transforms
	//	  ----------------------------------------------------------------------*/
	//	void passf6(final int ido, final int l1, final float a[], final int offa, final float ch[], final int offch, final int offset, final int isign)
	//	/*isign==-1 for forward transform and+1 for backward transform*/
	//	{
	//		float tau = isign * Math.sqrt(3.0) / 2.0;
	//		int i, k;
	//		float ci2, ci3, ci4, ci5, di3, di4, di5, di2, cr2, cr3, cr5, cr4, ti1, tr1, ti2, ti3, ti4, ti5, dr3, dr4, dr5, dr2, tr2, tr3, tr4, tr5;
	//		int iw1, iw2, iw3, iw4, iw5;
	//
	//		iw1 = offset;
	//		iw2 = iw1 + ido;
	//		iw3 = iw2 + ido;
	//		iw4 = iw3 + ido;
	//		iw5 = iw4 + ido;
	//
	//		int idxt2 = l1 * ido;
	//		for (k = 1; k <= l1; k++) {
	//			for (i = 0; i < ido - 1; i += 2) {
	//				int idx1 = offa + i + 1 + (k * 6 - 5) * ido;
	//				int idx2 = idx1 + ido;
	//				int idx3 = idx1 - ido;
	//				int idx4 = idx2 + ido;
	//				int idx5 = idx4 + ido;
	//				int idx6 = idx5 + ido;
	//				
	//				int idx7 = offch + i + (k - 1) * ido;
	//				int idx8 = idx7 + idxt2;
	//				int idx9 = idx8 + idxt2;
	//				int idx10 = idx9 + idxt2;
	//				int idx11 = idx10 + idxt2;
	//				int idx12 = i + iw1;
	//				int idx13 = i + iw2;
	//				int idx14 = i + iw3;
	//				int idx15 = i + iw4;
	//				int idx16 = i + iw5;
	//				
	//				float a1r = a[idx1];
	//				float a1i = a[idx1 - 1];
	//				float a2r = a[idx2];
	//				float a2i = a[idx2 - 1];
	//				float a3r = a[idx3];
	//				float a3i = a[idx3 - 1];
	//				float a4r = a[idx4];
	//				float a4i = a[idx4 - 1];
	//				float a5r = a[idx5];
	//				float a5i = a[idx5 - 1];
	//				float a6r = a[idx6];
	//				float a6i = a[idx6 - 1];
	//				
	//				float w1r = wtable[idx12];
	//				float w1i = isign * wtable[idx12 + 1];
	//				float w2r = wtable[idx13];
	//				float w2i = isign * wtable[idx13 + 1];
	//				float w3r = wtable[idx14];
	//				float w3i = isign * wtable[idx14 + 1];
	//				float w4r = wtable[idx15];
	//				float w4i = isign * wtable[idx15 + 1];
	//				float w5r = wtable[idx16];
	//				float w5i = isign * wtable[idx16 + 1];
	//				
	//				ti5 = a1r - a5r;
	//				ti2 = a1r + a5r;
	//				ti4 = a2r - a4r;
	//				ti3 = a2r + a4r;
	//				tr5 = a1i - a5i;
	//				tr2 = a1i + a5i;
	//				tr4 = a2i - a4i;
	//				tr3 = a2i + a4i;
	//				cr2 = a3i + tr11 * tr2 + tr12 * tr3;
	//
	//				ci2 = a3r + tr11 * ti2 + tr12 * ti3;
	//				cr3 = a3i + tr12 * tr2 + tr11 * tr3;
	//
	//				ci3 = a3r + tr12 * ti2 + tr11 * ti3;
	//				cr5 = isign * (ti11 * tr5 + ti12 * tr4);
	//				ci5 = isign * (ti11 * ti5 + ti12 * ti4);
	//				cr4 = isign * (ti12 * tr5 - ti11 * tr4);
	//				ci4 = isign * (ti12 * ti5 - ti11 * ti4);
	//				dr3 = cr3 - ci4;
	//				dr4 = cr3 + ci4;
	//				di3 = ci3 + cr4;
	//				di4 = ci3 - cr4;
	//				dr5 = cr2 + ci5;
	//				dr2 = cr2 - ci5;
	//				di5 = ci2 - cr5;
	//				di2 = ci2 + cr5;
	//				
	//				ch[idx7] = a3i + tr2 + tr3;
	//				ch[idx7 + 1] = a3r + ti2 + ti3;
	//				ch[idx8] = w1r * dr2 - w1i * di2;
	//				ch[idx8 + 1] = w1r * di2 + w1i * dr2;
	//				ch[idx9] = w2r * dr3 - w2i * di3;
	//				ch[idx9 + 1] = w2r * di3 + w2i * dr3;
	//				ch[idx10] = w3r * dr4 - w3i * di4;
	//				ch[idx10 + 1] = w3r * di4 + w3i * dr4;
	//				ch[idx11] = w4r * dr5 - w4i * di5;
	//				ch[idx11 + 1] = w4r * di5 + w4i * dr5;
	//			}
	//		}
	//	}

	/*----------------------------------------------------------------------
	   passfg: Complex FFT's forward/backward processing of general factor;
	   isign is +1 for backward and -1 for forward transforms
	  ----------------------------------------------------------------------*/
	void passfg(final int nac[], final int ido, final int ip, final int l1, final int idl1, final float a[], final int offa, final float ch[], final int offch, final int offset, final int isign) {
		int idij, idlj, idot, ipph, i, j, k, l, jc, lc, ik, idj, idl, inc, idp;
		float w1r, w1i, w2i, w2r;
		int iw1;

		iw1 = offset;
		idot = ido / 2;
		ipph = (ip + 1) / 2;
		idp = ip * ido;
		if (ido >= l1) {
			for (j = 1; j < ipph; j++) {
				jc = ip - j;
				int idxt1 = j * ido;
				int idxt2 = jc * ido;
				for (k = 0; k < l1; k++) {
					int idxt0 = k * ido;
					int idxt3 = idxt0 + idxt1 * l1;
					int idxt4 = idxt0 + idxt2 * l1;
					int idxt5 = idxt0 * ip;
					for (i = 0; i < ido; i++) {
						int idx1 = offa + i + idxt1 + idxt5;
						int idx2 = offa + i + idxt2 + idxt5;
						int idx3 = offch + i;
						ch[idx3 + idxt3] = a[idx1] + a[idx2];
						ch[idx3 + idxt4] = a[idx1] - a[idx2];
					}
				}
			}
			for (k = 0; k < l1; k++) {
				int idxt1 = k * ido;
				int idxt2 = idxt1 * ip;
				for (i = 0; i < ido; i++) {
					ch[offch + i + idxt1] = a[offa + i + idxt2];
				}
			}
		} else {
			for (j = 1; j < ipph; j++) {
				jc = ip - j;
				int idxt1 = j * l1 * ido;
				int idxt2 = jc * l1 * ido;
				int idxt3 = j * ido;
				int idxt4 = jc * ido;
				for (i = 0; i < ido; i++) {
					for (k = 0; k < l1; k++) {
						int idxt5 = k * ido;
						int idxt6 = idxt5 * ip;
						int idx7 = offch + i;
						int idx8 = offa + i;
						ch[idx7 + idxt5 + idxt1] = a[idx8 + idxt3 + idxt6] + a[idx8 + idxt4 + idxt6];
						ch[idx7 + idxt5 + idxt2] = a[idx8 + idxt3 + idxt6] - a[idx8 + idxt4 + idxt6];
					}
				}
			}
			for (i = 0; i < ido; i++) {
				for (k = 0; k < l1; k++) {
					int idxt1 = k * ido;
					ch[offch + i + idxt1] = a[offa + i + idxt1 * ip];
				}
			}
		}

		idl = 2 - ido;
		inc = 0;
		int idxt0 = (ip - 1) * idl1;
		for (l = 1; l < ipph; l++) {
			lc = ip - l;
			idl += ido;
			int idxt1 = l * idl1;
			int idxt2 = lc * idl1;
			int idxt3 = idl + iw1;
			w1r = wtable[idxt3 - 2];
			w1i = isign * wtable[idxt3 - 1];
			for (ik = 0; ik < idl1; ik++) {
				int idx1 = offa + ik;
				int idx2 = offch + ik;
				a[idx1 + idxt1] = ch[idx2] + w1r * ch[idx2 + idl1];
				a[idx1 + idxt2] = w1i * ch[idx2 + idxt0];
			}
			idlj = idl;
			inc += ido;
			for (j = 2; j < ipph; j++) {
				jc = ip - j;
				idlj += inc;
				if (idlj > idp)
					idlj -= idp;
				int idxt4 = idlj + iw1;
				w2r = wtable[idxt4 - 2];
				w2i = isign * wtable[idxt4 - 1];
				int idxt5 = j * idl1;
				int idxt6 = jc * idl1;
				for (ik = 0; ik < idl1; ik++) {
					int idx1 = offa + ik;
					int idx2 = offch + ik;
					a[idx1 + idxt1] += w2r * ch[idx2 + idxt5];
					a[idx1 + idxt2] += w2i * ch[idx2 + idxt6];
				}
			}
		}
		for (j = 1; j < ipph; j++) {
			int idxt1 = j * idl1;
			for (ik = 0; ik < idl1; ik++) {
				int idx1 = offch + ik;
				ch[idx1] += ch[idx1 + idxt1];
			}
		}
		for (j = 1; j < ipph; j++) {
			jc = ip - j;
			int idx1 = j * idl1;
			int idx2 = jc * idl1;
			for (ik = 1; ik < idl1; ik += 2) {
				int idx3 = offch + ik;
				int idx4 = offa + ik;
				ch[idx3 - 1 + idx1] = a[idx4 - 1 + idx1] - a[idx4 + idx2];
				ch[idx3 - 1 + idx2] = a[idx4 - 1 + idx1] + a[idx4 + idx2];
				ch[idx3 + idx1] = a[idx4 + idx1] + a[idx4 - 1 + idx2];
				ch[idx3 + idx2] = a[idx4 + idx1] - a[idx4 - 1 + idx2];
			}
		}
		nac[0] = 1;
		if (ido == 2)
			return;
		nac[0] = 0;
		System.arraycopy(ch, offch, a, offa, idl1);
		int idx0 = l1 * ido;
		for (j = 1; j < ip; j++) {
			int idx2 = j * idx0;
			for (k = 0; k < l1; k++) {
				int idx5 = k * ido;
				int idx3 = offch + idx5;
				int idx4 = offa + idx5;
				a[idx4 + idx2] = ch[idx3 + idx2];
				a[idx4 + idx2 + 1] = ch[idx3 + idx2 + 1];
			}
		}
		if (idot <= l1) {
			idij = 0;
			for (j = 1; j < ip; j++) {
				idij += 2;
				int idx1 = j * l1 * ido;
				for (i = 3; i < ido; i += 2) {
					idij += 2;
					int idx2 = idij + iw1 - 1;
					w1r = wtable[idx2 - 1];
					w1i = isign * wtable[idx2];
					for (k = 0; k < l1; k++) {
						int idx3 = offa + i + k * ido;
						int idx4 = offch + i + k * ido;
						a[idx3 - 1 + idx1] = w1r * ch[idx4 - 1 + idx1] - w1i * ch[idx4 + idx1];
						a[idx3 + idx1] = w1r * ch[idx4 + idx1] + w1i * ch[idx4 - 1 + idx1];
					}
				}
			}
		} else {
			idj = 2 - ido;
			for (j = 1; j < ip; j++) {
				idj += ido;
				int idx1 = j * l1 * ido;
				for (k = 0; k < l1; k++) {
					idij = idj;
					for (i = 3; i < ido; i += 2) {
						idij += 2;
						int idx2 = idij - 1 + iw1;
						int idx3 = offa + i + k * ido;
						int idx4 = offch + i + k * ido;
						w1r = wtable[idx2 - 1];
						w1i = isign * wtable[idx2];
						a[idx3 - 1 + idx1] = w1r * ch[idx4 - 1 + idx1] - w1i * ch[idx4 + idx1];
						a[idx3 + idx1] = w1r * ch[idx4 + idx1] + w1i * ch[idx4 - 1 + idx1];
					}
				}
			}
		}
	}

	private void cftfsub(int n, float[] a, int offa, int[] ip, int nw, float[] w) {
		if (n > 8) {
			if (n > 32) {
				cftf1st(n, a, offa, w, nw - (n >> 2));
				if ((ConcurrencyUtils.getNumberOfProcessors() > 1) && (n > ConcurrencyUtils.getThreadsBeginN_1D_FFT_2Threads())) {
					cftrec4_th(n, a, offa, nw, w);
				} else if (n > 512) {
					cftrec4(n, a, offa, nw, w);
				} else if (n > 128) {
					cftleaf(n, 1, a, offa, nw, w);
				} else {
					cftfx41(n, a, offa, nw, w);
				}
				bitrv2(n, ip, a, offa);
			} else if (n == 32) {
				cftf161(a, offa, w, nw - 8);
				bitrv216(a, offa);
			} else {
				cftf081(a, offa, w, 0);
				bitrv208(a, offa);
			}
		} else if (n == 8) {
			cftf040(a, offa);
		} else if (n == 4) {
			cftxb020(a, offa);
		}
	}

	private void cftbsub(int n, float[] a, int offa, int[] ip, int nw, float[] w) {
		if (n > 8) {
			if (n > 32) {
				cftb1st(n, a, offa, w, nw - (n >> 2));
				if ((ConcurrencyUtils.getNumberOfProcessors() > 1) && (n > ConcurrencyUtils.getThreadsBeginN_1D_FFT_2Threads())) {
					cftrec4_th(n, a, offa, nw, w);
				} else if (n > 512) {
					cftrec4(n, a, offa, nw, w);
				} else if (n > 128) {
					cftleaf(n, 1, a, offa, nw, w);
				} else {
					cftfx41(n, a, offa, nw, w);
				}
				bitrv2conj(n, ip, a, offa);
			} else if (n == 32) {
				cftf161(a, offa, w, nw - 8);
				bitrv216neg(a, offa);
			} else {
				cftf081(a, offa, w, 0);
				bitrv208neg(a, offa);
			}
		} else if (n == 8) {
			cftb040(a, offa);
		} else if (n == 4) {
			cftxb020(a, offa);
		}
	}

	private void bitrv2(int n, int[] ip, float[] a, int offa) {
		int j, j1, k, k1, l, m, nh, nm;
		float xr, xi, yr, yi;
		int idx0, idx1, idx2;

		m = 1;
		for (l = n >> 2; l > 8; l >>= 2) {
			m <<= 1;
		}
		nh = n >> 1;
		nm = 4 * m;
		if (l == 8) {
			for (k = 0; k < m; k++) {
				idx0 = 4 * k;
				for (j = 0; j < k; j++) {
					j1 = 4 * j + 2 * ip[m + k];
					k1 = idx0 + 2 * ip[m + j];
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 -= nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nh;
					k1 += 2;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 += nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += 2;
					k1 += nh;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 -= nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nh;
					k1 -= 2;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 += nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
				}
				k1 = idx0 + 2 * ip[m + k];
				j1 = k1 + 2;
				k1 += nh;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = a[idx1 + 1];
				yr = a[idx2];
				yi = a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 += nm;
				k1 += 2 * nm;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = a[idx1 + 1];
				yr = a[idx2];
				yi = a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 += nm;
				k1 -= nm;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = a[idx1 + 1];
				yr = a[idx2];
				yi = a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 -= 2;
				k1 -= nh;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = a[idx1 + 1];
				yr = a[idx2];
				yi = a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 += nh + 2;
				k1 += nh + 2;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = a[idx1 + 1];
				yr = a[idx2];
				yi = a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 -= nh - nm;
				k1 += 2 * nm - 2;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = a[idx1 + 1];
				yr = a[idx2];
				yi = a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
			}
		} else {
			for (k = 0; k < m; k++) {
				idx0 = 4 * k;
				for (j = 0; j < k; j++) {
					j1 = 4 * j + ip[m + k];
					k1 = idx0 + ip[m + j];
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nh;
					k1 += 2;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += 2;
					k1 += nh;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nh;
					k1 -= 2;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = a[idx1 + 1];
					yr = a[idx2];
					yi = a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
				}
				k1 = idx0 + ip[m + k];
				j1 = k1 + 2;
				k1 += nh;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = a[idx1 + 1];
				yr = a[idx2];
				yi = a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 += nm;
				k1 += nm;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = a[idx1 + 1];
				yr = a[idx2];
				yi = a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
			}
		}
	}

	private void bitrv2conj(int n, int[] ip, float[] a, int offa) {
		int j, j1, k, k1, l, m, nh, nm;
		float xr, xi, yr, yi;
		int idx0, idx1, idx2;

		m = 1;
		for (l = n >> 2; l > 8; l >>= 2) {
			m <<= 1;
		}
		nh = n >> 1;
		nm = 4 * m;
		if (l == 8) {
			for (k = 0; k < m; k++) {
				idx0 = 4 * k;
				for (j = 0; j < k; j++) {
					j1 = 4 * j + 2 * ip[m + k];
					k1 = idx0 + 2 * ip[m + j];
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 -= nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nh;
					k1 += 2;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 += nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += 2;
					k1 += nh;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 -= nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nh;
					k1 -= 2;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 += nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= 2 * nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
				}
				k1 = idx0 + 2 * ip[m + k];
				j1 = k1 + 2;
				k1 += nh;
				idx1 = offa + j1;
				idx2 = offa + k1;
				a[idx1 - 1] = -a[idx1 - 1];
				xr = a[idx1];
				xi = -a[idx1 + 1];
				yr = a[idx2];
				yi = -a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				a[idx2 + 3] = -a[idx2 + 3];
				j1 += nm;
				k1 += 2 * nm;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = -a[idx1 + 1];
				yr = a[idx2];
				yi = -a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 += nm;
				k1 -= nm;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = -a[idx1 + 1];
				yr = a[idx2];
				yi = -a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 -= 2;
				k1 -= nh;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = -a[idx1 + 1];
				yr = a[idx2];
				yi = -a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 += nh + 2;
				k1 += nh + 2;
				idx1 = offa + j1;
				idx2 = offa + k1;
				xr = a[idx1];
				xi = -a[idx1 + 1];
				yr = a[idx2];
				yi = -a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				j1 -= nh - nm;
				k1 += 2 * nm - 2;
				idx1 = offa + j1;
				idx2 = offa + k1;
				a[idx1 - 1] = -a[idx1 - 1];
				xr = a[idx1];
				xi = -a[idx1 + 1];
				yr = a[idx2];
				yi = -a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				a[idx2 + 3] = -a[idx2 + 3];
			}
		} else {
			for (k = 0; k < m; k++) {
				idx0 = 4 * k;
				for (j = 0; j < k; j++) {
					j1 = 4 * j + ip[m + k];
					k1 = idx0 + ip[m + j];
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nh;
					k1 += 2;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += 2;
					k1 += nh;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 += nm;
					k1 += nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nh;
					k1 -= 2;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
					j1 -= nm;
					k1 -= nm;
					idx1 = offa + j1;
					idx2 = offa + k1;
					xr = a[idx1];
					xi = -a[idx1 + 1];
					yr = a[idx2];
					yi = -a[idx2 + 1];
					a[idx1] = yr;
					a[idx1 + 1] = yi;
					a[idx2] = xr;
					a[idx2 + 1] = xi;
				}
				k1 = idx0 + ip[m + k];
				j1 = k1 + 2;
				k1 += nh;
				idx1 = offa + j1;
				idx2 = offa + k1;
				a[idx1 - 1] = -a[idx1 - 1];
				xr = a[idx1];
				xi = -a[idx1 + 1];
				yr = a[idx2];
				yi = -a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				a[idx2 + 3] = -a[idx2 + 3];
				j1 += nm;
				k1 += nm;
				idx1 = offa + j1;
				idx2 = offa + k1;
				a[idx1 - 1] = -a[idx1 - 1];
				xr = a[idx1];
				xi = -a[idx1 + 1];
				yr = a[idx2];
				yi = -a[idx2 + 1];
				a[idx1] = yr;
				a[idx1 + 1] = yi;
				a[idx2] = xr;
				a[idx2 + 1] = xi;
				a[idx2 + 3] = -a[idx2 + 3];
			}
		}
	}

	private void bitrv216(float[] a, int offa) {
		float x1r, x1i, x2r, x2i, x3r, x3i, x4r, x4i, x5r, x5i, x7r, x7i, x8r, x8i, x10r, x10i, x11r, x11i, x12r, x12i, x13r, x13i, x14r, x14i;

		x1r = a[offa + 2];
		x1i = a[offa + 3];
		x2r = a[offa + 4];
		x2i = a[offa + 5];
		x3r = a[offa + 6];
		x3i = a[offa + 7];
		x4r = a[offa + 8];
		x4i = a[offa + 9];
		x5r = a[offa + 10];
		x5i = a[offa + 11];
		x7r = a[offa + 14];
		x7i = a[offa + 15];
		x8r = a[offa + 16];
		x8i = a[offa + 17];
		x10r = a[offa + 20];
		x10i = a[offa + 21];
		x11r = a[offa + 22];
		x11i = a[offa + 23];
		x12r = a[offa + 24];
		x12i = a[offa + 25];
		x13r = a[offa + 26];
		x13i = a[offa + 27];
		x14r = a[offa + 28];
		x14i = a[offa + 29];
		a[offa + 2] = x8r;
		a[offa + 3] = x8i;
		a[offa + 4] = x4r;
		a[offa + 5] = x4i;
		a[offa + 6] = x12r;
		a[offa + 7] = x12i;
		a[offa + 8] = x2r;
		a[offa + 9] = x2i;
		a[offa + 10] = x10r;
		a[offa + 11] = x10i;
		a[offa + 14] = x14r;
		a[offa + 15] = x14i;
		a[offa + 16] = x1r;
		a[offa + 17] = x1i;
		a[offa + 20] = x5r;
		a[offa + 21] = x5i;
		a[offa + 22] = x13r;
		a[offa + 23] = x13i;
		a[offa + 24] = x3r;
		a[offa + 25] = x3i;
		a[offa + 26] = x11r;
		a[offa + 27] = x11i;
		a[offa + 28] = x7r;
		a[offa + 29] = x7i;
	}

	private void bitrv216neg(float[] a, int offa) {
		float x1r, x1i, x2r, x2i, x3r, x3i, x4r, x4i, x5r, x5i, x6r, x6i, x7r, x7i, x8r, x8i, x9r, x9i, x10r, x10i, x11r, x11i, x12r, x12i, x13r, x13i, x14r, x14i, x15r, x15i;

		x1r = a[offa + 2];
		x1i = a[offa + 3];
		x2r = a[offa + 4];
		x2i = a[offa + 5];
		x3r = a[offa + 6];
		x3i = a[offa + 7];
		x4r = a[offa + 8];
		x4i = a[offa + 9];
		x5r = a[offa + 10];
		x5i = a[offa + 11];
		x6r = a[offa + 12];
		x6i = a[offa + 13];
		x7r = a[offa + 14];
		x7i = a[offa + 15];
		x8r = a[offa + 16];
		x8i = a[offa + 17];
		x9r = a[offa + 18];
		x9i = a[offa + 19];
		x10r = a[offa + 20];
		x10i = a[offa + 21];
		x11r = a[offa + 22];
		x11i = a[offa + 23];
		x12r = a[offa + 24];
		x12i = a[offa + 25];
		x13r = a[offa + 26];
		x13i = a[offa + 27];
		x14r = a[offa + 28];
		x14i = a[offa + 29];
		x15r = a[offa + 30];
		x15i = a[offa + 31];
		a[offa + 2] = x15r;
		a[offa + 3] = x15i;
		a[offa + 4] = x7r;
		a[offa + 5] = x7i;
		a[offa + 6] = x11r;
		a[offa + 7] = x11i;
		a[offa + 8] = x3r;
		a[offa + 9] = x3i;
		a[offa + 10] = x13r;
		a[offa + 11] = x13i;
		a[offa + 12] = x5r;
		a[offa + 13] = x5i;
		a[offa + 14] = x9r;
		a[offa + 15] = x9i;
		a[offa + 16] = x1r;
		a[offa + 17] = x1i;
		a[offa + 18] = x14r;
		a[offa + 19] = x14i;
		a[offa + 20] = x6r;
		a[offa + 21] = x6i;
		a[offa + 22] = x10r;
		a[offa + 23] = x10i;
		a[offa + 24] = x2r;
		a[offa + 25] = x2i;
		a[offa + 26] = x12r;
		a[offa + 27] = x12i;
		a[offa + 28] = x4r;
		a[offa + 29] = x4i;
		a[offa + 30] = x8r;
		a[offa + 31] = x8i;
	}

	private void bitrv208(float[] a, int offa) {
		float x1r, x1i, x3r, x3i, x4r, x4i, x6r, x6i;

		x1r = a[offa + 2];
		x1i = a[offa + 3];
		x3r = a[offa + 6];
		x3i = a[offa + 7];
		x4r = a[offa + 8];
		x4i = a[offa + 9];
		x6r = a[offa + 12];
		x6i = a[offa + 13];
		a[offa + 2] = x4r;
		a[offa + 3] = x4i;
		a[offa + 6] = x6r;
		a[offa + 7] = x6i;
		a[offa + 8] = x1r;
		a[offa + 9] = x1i;
		a[offa + 12] = x3r;
		a[offa + 13] = x3i;
	}

	private void bitrv208neg(float[] a, int offa) {
		float x1r, x1i, x2r, x2i, x3r, x3i, x4r, x4i, x5r, x5i, x6r, x6i, x7r, x7i;

		x1r = a[offa + 2];
		x1i = a[offa + 3];
		x2r = a[offa + 4];
		x2i = a[offa + 5];
		x3r = a[offa + 6];
		x3i = a[offa + 7];
		x4r = a[offa + 8];
		x4i = a[offa + 9];
		x5r = a[offa + 10];
		x5i = a[offa + 11];
		x6r = a[offa + 12];
		x6i = a[offa + 13];
		x7r = a[offa + 14];
		x7i = a[offa + 15];
		a[offa + 2] = x7r;
		a[offa + 3] = x7i;
		a[offa + 4] = x3r;
		a[offa + 5] = x3i;
		a[offa + 6] = x5r;
		a[offa + 7] = x5i;
		a[offa + 8] = x1r;
		a[offa + 9] = x1i;
		a[offa + 10] = x6r;
		a[offa + 11] = x6i;
		a[offa + 12] = x2r;
		a[offa + 13] = x2i;
		a[offa + 14] = x4r;
		a[offa + 15] = x4i;
	}

	private void cftf1st(int n, float[] a, int offa, float[] w, int startw) {
		int j, j0, j1, j2, j3, k, m, mh;
		float wn4r, csc1, csc3, wk1r, wk1i, wk3r, wk3i, wd1r, wd1i, wd3r, wd3i;
		float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i, y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i;
		int idx0, idx1, idx2, idx3, idx4, idx5;
		mh = n >> 3;
		m = 2 * mh;
		j1 = m;
		j2 = j1 + m;
		j3 = j2 + m;
		idx1 = offa + j1;
		idx2 = offa + j2;
		idx3 = offa + j3;
		x0r = a[offa] + a[idx2];
		x0i = a[offa + 1] + a[idx2 + 1];
		x1r = a[offa] - a[idx2];
		x1i = a[offa + 1] - a[idx2 + 1];
		x2r = a[idx1] + a[idx3];
		x2i = a[idx1 + 1] + a[idx3 + 1];
		x3r = a[idx1] - a[idx3];
		x3i = a[idx1 + 1] - a[idx3 + 1];
		a[offa] = x0r + x2r;
		a[offa + 1] = x0i + x2i;
		a[idx1] = x0r - x2r;
		a[idx1 + 1] = x0i - x2i;
		a[idx2] = x1r - x3i;
		a[idx2 + 1] = x1i + x3r;
		a[idx3] = x1r + x3i;
		a[idx3 + 1] = x1i - x3r;
		wn4r = w[startw + 1];
		csc1 = w[startw + 2];
		csc3 = w[startw + 3];
		wd1r = 1;
		wd1i = 0;
		wd3r = 1;
		wd3i = 0;
		k = 0;
		for (j = 2; j < mh - 2; j += 4) {
			k += 4;
			idx4 = startw + k;
			wk1r = csc1 * (wd1r + w[idx4]);
			wk1i = csc1 * (wd1i + w[idx4 + 1]);
			wk3r = csc3 * (wd3r + w[idx4 + 2]);
			wk3i = csc3 * (wd3i + w[idx4 + 3]);
			wd1r = w[idx4];
			wd1i = w[idx4 + 1];
			wd3r = w[idx4 + 2];
			wd3i = w[idx4 + 3];
			j1 = j + m;
			j2 = j1 + m;
			j3 = j2 + m;
			idx1 = offa + j1;
			idx2 = offa + j2;
			idx3 = offa + j3;
			idx5 = offa + j;
			x0r = a[idx5] + a[idx2];
			x0i = a[idx5 + 1] + a[idx2 + 1];
			x1r = a[idx5] - a[idx2];
			x1i = a[idx5 + 1] - a[idx2 + 1];
			y0r = a[idx5 + 2] + a[idx2 + 2];
			y0i = a[idx5 + 3] + a[idx2 + 3];
			y1r = a[idx5 + 2] - a[idx2 + 2];
			y1i = a[idx5 + 3] - a[idx2 + 3];
			x2r = a[idx1] + a[idx3];
			x2i = a[idx1 + 1] + a[idx3 + 1];
			x3r = a[idx1] - a[idx3];
			x3i = a[idx1 + 1] - a[idx3 + 1];
			y2r = a[idx1 + 2] + a[idx3 + 2];
			y2i = a[idx1 + 3] + a[idx3 + 3];
			y3r = a[idx1 + 2] - a[idx3 + 2];
			y3i = a[idx1 + 3] - a[idx3 + 3];
			a[idx5] = x0r + x2r;
			a[idx5 + 1] = x0i + x2i;
			a[idx5 + 2] = y0r + y2r;
			a[idx5 + 3] = y0i + y2i;
			a[idx1] = x0r - x2r;
			a[idx1 + 1] = x0i - x2i;
			a[idx1 + 2] = y0r - y2r;
			a[idx1 + 3] = y0i - y2i;
			x0r = x1r - x3i;
			x0i = x1i + x3r;
			a[idx2] = wk1r * x0r - wk1i * x0i;
			a[idx2 + 1] = wk1r * x0i + wk1i * x0r;
			x0r = y1r - y3i;
			x0i = y1i + y3r;
			a[idx2 + 2] = wd1r * x0r - wd1i * x0i;
			a[idx2 + 3] = wd1r * x0i + wd1i * x0r;
			x0r = x1r + x3i;
			x0i = x1i - x3r;
			a[idx3] = wk3r * x0r + wk3i * x0i;
			a[idx3 + 1] = wk3r * x0i - wk3i * x0r;
			x0r = y1r + y3i;
			x0i = y1i - y3r;
			a[idx3 + 2] = wd3r * x0r + wd3i * x0i;
			a[idx3 + 3] = wd3r * x0i - wd3i * x0r;
			j0 = m - j;
			j1 = j0 + m;
			j2 = j1 + m;
			j3 = j2 + m;
			idx0 = offa + j0;
			idx1 = offa + j1;
			idx2 = offa + j2;
			idx3 = offa + j3;
			x0r = a[idx0] + a[idx2];
			x0i = a[idx0 + 1] + a[idx2 + 1];
			x1r = a[idx0] - a[idx2];
			x1i = a[idx0 + 1] - a[idx2 + 1];
			y0r = a[idx0 - 2] + a[idx2 - 2];
			y0i = a[idx0 - 1] + a[idx2 - 1];
			y1r = a[idx0 - 2] - a[idx2 - 2];
			y1i = a[idx0 - 1] - a[idx2 - 1];
			x2r = a[idx1] + a[idx3];
			x2i = a[idx1 + 1] + a[idx3 + 1];
			x3r = a[idx1] - a[idx3];
			x3i = a[idx1 + 1] - a[idx3 + 1];
			y2r = a[idx1 - 2] + a[idx3 - 2];
			y2i = a[idx1 - 1] + a[idx3 - 1];
			y3r = a[idx1 - 2] - a[idx3 - 2];
			y3i = a[idx1 - 1] - a[idx3 - 1];
			a[idx0] = x0r + x2r;
			a[idx0 + 1] = x0i + x2i;
			a[idx0 - 2] = y0r + y2r;
			a[idx0 - 1] = y0i + y2i;
			a[idx1] = x0r - x2r;
			a[idx1 + 1] = x0i - x2i;
			a[idx1 - 2] = y0r - y2r;
			a[idx1 - 1] = y0i - y2i;
			x0r = x1r - x3i;
			x0i = x1i + x3r;
			a[idx2] = wk1i * x0r - wk1r * x0i;
			a[idx2 + 1] = wk1i * x0i + wk1r * x0r;
			x0r = y1r - y3i;
			x0i = y1i + y3r;
			a[idx2 - 2] = wd1i * x0r - wd1r * x0i;
			a[idx2 - 1] = wd1i * x0i + wd1r * x0r;
			x0r = x1r + x3i;
			x0i = x1i - x3r;
			a[idx3] = wk3i * x0r + wk3r * x0i;
			a[idx3 + 1] = wk3i * x0i - wk3r * x0r;
			x0r = y1r + y3i;
			x0i = y1i - y3r;
			a[offa + j3 - 2] = wd3i * x0r + wd3r * x0i;
			a[offa + j3 - 1] = wd3i * x0i - wd3r * x0r;
		}
		wk1r = csc1 * (wd1r + wn4r);
		wk1i = csc1 * (wd1i + wn4r);
		wk3r = csc3 * (wd3r - wn4r);
		wk3i = csc3 * (wd3i - wn4r);
		j0 = mh;
		j1 = j0 + m;
		j2 = j1 + m;
		j3 = j2 + m;
		idx0 = offa + j0;
		idx1 = offa + j1;
		idx2 = offa + j2;
		idx3 = offa + j3;
		x0r = a[idx0 - 2] + a[idx2 - 2];
		x0i = a[idx0 - 1] + a[idx2 - 1];
		x1r = a[idx0 - 2] - a[idx2 - 2];
		x1i = a[idx0 - 1] - a[idx2 - 1];
		x2r = a[idx1 - 2] + a[idx3 - 2];
		x2i = a[idx1 - 1] + a[idx3 - 1];
		x3r = a[idx1 - 2] - a[idx3 - 2];
		x3i = a[idx1 - 1] - a[idx3 - 1];
		a[idx0 - 2] = x0r + x2r;
		a[idx0 - 1] = x0i + x2i;
		a[idx1 - 2] = x0r - x2r;
		a[idx1 - 1] = x0i - x2i;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		a[idx2 - 2] = wk1r * x0r - wk1i * x0i;
		a[idx2 - 1] = wk1r * x0i + wk1i * x0r;
		x0r = x1r + x3i;
		x0i = x1i - x3r;
		a[idx3 - 2] = wk3r * x0r + wk3i * x0i;
		a[idx3 - 1] = wk3r * x0i - wk3i * x0r;
		x0r = a[idx0] + a[idx2];
		x0i = a[idx0 + 1] + a[idx2 + 1];
		x1r = a[idx0] - a[idx2];
		x1i = a[idx0 + 1] - a[idx2 + 1];
		x2r = a[idx1] + a[idx3];
		x2i = a[idx1 + 1] + a[idx3 + 1];
		x3r = a[idx1] - a[idx3];
		x3i = a[idx1 + 1] - a[idx3 + 1];
		a[idx0] = x0r + x2r;
		a[idx0 + 1] = x0i + x2i;
		a[idx1] = x0r - x2r;
		a[idx1 + 1] = x0i - x2i;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		a[idx2] = wn4r * (x0r - x0i);
		a[idx2 + 1] = wn4r * (x0i + x0r);
		x0r = x1r + x3i;
		x0i = x1i - x3r;
		a[idx3] = -wn4r * (x0r + x0i);
		a[idx3 + 1] = -wn4r * (x0i - x0r);
		x0r = a[idx0 + 2] + a[idx2 + 2];
		x0i = a[idx0 + 3] + a[idx2 + 3];
		x1r = a[idx0 + 2] - a[idx2 + 2];
		x1i = a[idx0 + 3] - a[idx2 + 3];
		x2r = a[idx1 + 2] + a[idx3 + 2];
		x2i = a[idx1 + 3] + a[idx3 + 3];
		x3r = a[idx1 + 2] - a[idx3 + 2];
		x3i = a[idx1 + 3] - a[idx3 + 3];
		a[idx0 + 2] = x0r + x2r;
		a[idx0 + 3] = x0i + x2i;
		a[idx1 + 2] = x0r - x2r;
		a[idx1 + 3] = x0i - x2i;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		a[idx2 + 2] = wk1i * x0r - wk1r * x0i;
		a[idx2 + 3] = wk1i * x0i + wk1r * x0r;
		x0r = x1r + x3i;
		x0i = x1i - x3r;
		a[idx3 + 2] = wk3i * x0r + wk3r * x0i;
		a[idx3 + 3] = wk3i * x0i - wk3r * x0r;
	}

	private void cftb1st(int n, float[] a, int offa, float[] w, int startw) {
		int j, j0, j1, j2, j3, k, m, mh;
		float wn4r, csc1, csc3, wk1r, wk1i, wk3r, wk3i, wd1r, wd1i, wd3r, wd3i;
		float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i, y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i;
		int idx0, idx1, idx2, idx3, idx4, idx5;
		mh = n >> 3;
		m = 2 * mh;
		j1 = m;
		j2 = j1 + m;
		j3 = j2 + m;
		idx1 = offa + j1;
		idx2 = offa + j2;
		idx3 = offa + j3;

		x0r = a[offa] + a[idx2];
		x0i = -a[offa + 1] - a[idx2 + 1];
		x1r = a[offa] - a[idx2];
		x1i = -a[offa + 1] + a[idx2 + 1];
		x2r = a[idx1] + a[idx3];
		x2i = a[idx1 + 1] + a[idx3 + 1];
		x3r = a[idx1] - a[idx3];
		x3i = a[idx1 + 1] - a[idx3 + 1];
		a[offa] = x0r + x2r;
		a[offa + 1] = x0i - x2i;
		a[idx1] = x0r - x2r;
		a[idx1 + 1] = x0i + x2i;
		a[idx2] = x1r + x3i;
		a[idx2 + 1] = x1i + x3r;
		a[idx3] = x1r - x3i;
		a[idx3 + 1] = x1i - x3r;
		wn4r = w[startw + 1];
		csc1 = w[startw + 2];
		csc3 = w[startw + 3];
		wd1r = 1;
		wd1i = 0;
		wd3r = 1;
		wd3i = 0;
		k = 0;
		for (j = 2; j < mh - 2; j += 4) {
			k += 4;
			idx4 = startw + k;
			wk1r = csc1 * (wd1r + w[idx4]);
			wk1i = csc1 * (wd1i + w[idx4 + 1]);
			wk3r = csc3 * (wd3r + w[idx4 + 2]);
			wk3i = csc3 * (wd3i + w[idx4 + 3]);
			wd1r = w[idx4];
			wd1i = w[idx4 + 1];
			wd3r = w[idx4 + 2];
			wd3i = w[idx4 + 3];
			j1 = j + m;
			j2 = j1 + m;
			j3 = j2 + m;
			idx1 = offa + j1;
			idx2 = offa + j2;
			idx3 = offa + j3;
			idx5 = offa + j;
			x0r = a[idx5] + a[idx2];
			x0i = -a[idx5 + 1] - a[idx2 + 1];
			x1r = a[idx5] - a[offa + j2];
			x1i = -a[idx5 + 1] + a[idx2 + 1];
			y0r = a[idx5 + 2] + a[idx2 + 2];
			y0i = -a[idx5 + 3] - a[idx2 + 3];
			y1r = a[idx5 + 2] - a[idx2 + 2];
			y1i = -a[idx5 + 3] + a[idx2 + 3];
			x2r = a[idx1] + a[idx3];
			x2i = a[idx1 + 1] + a[idx3 + 1];
			x3r = a[idx1] - a[idx3];
			x3i = a[idx1 + 1] - a[idx3 + 1];
			y2r = a[idx1 + 2] + a[idx3 + 2];
			y2i = a[idx1 + 3] + a[idx3 + 3];
			y3r = a[idx1 + 2] - a[idx3 + 2];
			y3i = a[idx1 + 3] - a[idx3 + 3];
			a[idx5] = x0r + x2r;
			a[idx5 + 1] = x0i - x2i;
			a[idx5 + 2] = y0r + y2r;
			a[idx5 + 3] = y0i - y2i;
			a[idx1] = x0r - x2r;
			a[idx1 + 1] = x0i + x2i;
			a[idx1 + 2] = y0r - y2r;
			a[idx1 + 3] = y0i + y2i;
			x0r = x1r + x3i;
			x0i = x1i + x3r;
			a[idx2] = wk1r * x0r - wk1i * x0i;
			a[idx2 + 1] = wk1r * x0i + wk1i * x0r;
			x0r = y1r + y3i;
			x0i = y1i + y3r;
			a[idx2 + 2] = wd1r * x0r - wd1i * x0i;
			a[idx2 + 3] = wd1r * x0i + wd1i * x0r;
			x0r = x1r - x3i;
			x0i = x1i - x3r;
			a[idx3] = wk3r * x0r + wk3i * x0i;
			a[idx3 + 1] = wk3r * x0i - wk3i * x0r;
			x0r = y1r - y3i;
			x0i = y1i - y3r;
			a[idx3 + 2] = wd3r * x0r + wd3i * x0i;
			a[idx3 + 3] = wd3r * x0i - wd3i * x0r;
			j0 = m - j;
			j1 = j0 + m;
			j2 = j1 + m;
			j3 = j2 + m;
			idx0 = offa + j0;
			idx1 = offa + j1;
			idx2 = offa + j2;
			idx3 = offa + j3;
			x0r = a[idx0] + a[idx2];
			x0i = -a[idx0 + 1] - a[idx2 + 1];
			x1r = a[idx0] - a[idx2];
			x1i = -a[idx0 + 1] + a[idx2 + 1];
			y0r = a[idx0 - 2] + a[idx2 - 2];
			y0i = -a[idx0 - 1] - a[idx2 - 1];
			y1r = a[idx0 - 2] - a[idx2 - 2];
			y1i = -a[idx0 - 1] + a[idx2 - 1];
			x2r = a[idx1] + a[idx3];
			x2i = a[idx1 + 1] + a[idx3 + 1];
			x3r = a[idx1] - a[idx3];
			x3i = a[idx1 + 1] - a[idx3 + 1];
			y2r = a[idx1 - 2] + a[idx3 - 2];
			y2i = a[idx1 - 1] + a[idx3 - 1];
			y3r = a[idx1 - 2] - a[idx3 - 2];
			y3i = a[idx1 - 1] - a[idx3 - 1];
			a[idx0] = x0r + x2r;
			a[idx0 + 1] = x0i - x2i;
			a[idx0 - 2] = y0r + y2r;
			a[idx0 - 1] = y0i - y2i;
			a[idx1] = x0r - x2r;
			a[idx1 + 1] = x0i + x2i;
			a[idx1 - 2] = y0r - y2r;
			a[idx1 - 1] = y0i + y2i;
			x0r = x1r + x3i;
			x0i = x1i + x3r;
			a[idx2] = wk1i * x0r - wk1r * x0i;
			a[idx2 + 1] = wk1i * x0i + wk1r * x0r;
			x0r = y1r + y3i;
			x0i = y1i + y3r;
			a[idx2 - 2] = wd1i * x0r - wd1r * x0i;
			a[idx2 - 1] = wd1i * x0i + wd1r * x0r;
			x0r = x1r - x3i;
			x0i = x1i - x3r;
			a[idx3] = wk3i * x0r + wk3r * x0i;
			a[idx3 + 1] = wk3i * x0i - wk3r * x0r;
			x0r = y1r - y3i;
			x0i = y1i - y3r;
			a[idx3 - 2] = wd3i * x0r + wd3r * x0i;
			a[idx3 - 1] = wd3i * x0i - wd3r * x0r;
		}
		wk1r = csc1 * (wd1r + wn4r);
		wk1i = csc1 * (wd1i + wn4r);
		wk3r = csc3 * (wd3r - wn4r);
		wk3i = csc3 * (wd3i - wn4r);
		j0 = mh;
		j1 = j0 + m;
		j2 = j1 + m;
		j3 = j2 + m;
		idx0 = offa + j0;
		idx1 = offa + j1;
		idx2 = offa + j2;
		idx3 = offa + j3;
		x0r = a[idx0 - 2] + a[idx2 - 2];
		x0i = -a[idx0 - 1] - a[idx2 - 1];
		x1r = a[idx0 - 2] - a[idx2 - 2];
		x1i = -a[idx0 - 1] + a[idx2 - 1];
		x2r = a[idx1 - 2] + a[idx3 - 2];
		x2i = a[idx1 - 1] + a[idx3 - 1];
		x3r = a[idx1 - 2] - a[idx3 - 2];
		x3i = a[idx1 - 1] - a[idx3 - 1];
		a[idx0 - 2] = x0r + x2r;
		a[idx0 - 1] = x0i - x2i;
		a[idx1 - 2] = x0r - x2r;
		a[idx1 - 1] = x0i + x2i;
		x0r = x1r + x3i;
		x0i = x1i + x3r;
		a[idx2 - 2] = wk1r * x0r - wk1i * x0i;
		a[idx2 - 1] = wk1r * x0i + wk1i * x0r;
		x0r = x1r - x3i;
		x0i = x1i - x3r;
		a[idx3 - 2] = wk3r * x0r + wk3i * x0i;
		a[idx3 - 1] = wk3r * x0i - wk3i * x0r;
		x0r = a[idx0] + a[idx2];
		x0i = -a[idx0 + 1] - a[idx2 + 1];
		x1r = a[idx0] - a[idx2];
		x1i = -a[idx0 + 1] + a[idx2 + 1];
		x2r = a[idx1] + a[idx3];
		x2i = a[idx1 + 1] + a[idx3 + 1];
		x3r = a[idx1] - a[idx3];
		x3i = a[idx1 + 1] - a[idx3 + 1];
		a[idx0] = x0r + x2r;
		a[idx0 + 1] = x0i - x2i;
		a[idx1] = x0r - x2r;
		a[idx1 + 1] = x0i + x2i;
		x0r = x1r + x3i;
		x0i = x1i + x3r;
		a[idx2] = wn4r * (x0r - x0i);
		a[idx2 + 1] = wn4r * (x0i + x0r);
		x0r = x1r - x3i;
		x0i = x1i - x3r;
		a[idx3] = -wn4r * (x0r + x0i);
		a[idx3 + 1] = -wn4r * (x0i - x0r);
		x0r = a[idx0 + 2] + a[idx2 + 2];
		x0i = -a[idx0 + 3] - a[idx2 + 3];
		x1r = a[idx0 + 2] - a[idx2 + 2];
		x1i = -a[idx0 + 3] + a[idx2 + 3];
		x2r = a[idx1 + 2] + a[idx3 + 2];
		x2i = a[idx1 + 3] + a[idx3 + 3];
		x3r = a[idx1 + 2] - a[idx3 + 2];
		x3i = a[idx1 + 3] - a[idx3 + 3];
		a[idx0 + 2] = x0r + x2r;
		a[idx0 + 3] = x0i - x2i;
		a[idx1 + 2] = x0r - x2r;
		a[idx1 + 3] = x0i + x2i;
		x0r = x1r + x3i;
		x0i = x1i + x3r;
		a[idx2 + 2] = wk1i * x0r - wk1r * x0i;
		a[idx2 + 3] = wk1i * x0i + wk1r * x0r;
		x0r = x1r - x3i;
		x0i = x1i - x3r;
		a[idx3 + 2] = wk3i * x0r + wk3r * x0i;
		a[idx3 + 3] = wk3i * x0i - wk3r * x0r;
	}

	private void cftrec4_th(final int n, final float[] a, final int offa, final int nw, final float[] w) {
		int i;
		int idiv4, m, nthread;
		int idx = 0;
		nthread = 2;
		idiv4 = 0;
		m = n >> 1;
		if (n > ConcurrencyUtils.getThreadsBeginN_1D_FFT_4Threads()) {
			nthread = 4;
			idiv4 = 1;
			m >>= 1;
		}
		Future[] futures = new Future[nthread];
		final int glob_m = m;
		for (i = 0; i < nthread; i++) {
			final int loc_offa = offa + i * m;
			if (i != idiv4) {
				futures[idx++] = ConcurrencyUtils.threadPool.submit(new Runnable() {
					public void run() {
						int isplt, j, k, m;
						int idx1 = loc_offa + glob_m;
						m = n;
						while (m > 512) {
							m >>= 2;
							cftmdl1(m, a, idx1 - m, w, nw - (m >> 1));
						}
						cftleaf(m, 1, a, idx1 - m, nw, w);
						k = 0;
						int idx2 = loc_offa - m;
						for (j = glob_m - m; j > 0; j -= m) {
							k++;
							isplt = cfttree(m, j, k, a, loc_offa, nw, w);
							cftleaf(m, isplt, a, idx2 + j, nw, w);
						}
					}
				});
			} else {
				futures[idx++] = ConcurrencyUtils.threadPool.submit(new Runnable() {
					public void run() {
						int isplt, j, k, m;
						int idx1 = loc_offa + glob_m;
						k = 1;
						m = n;
						while (m > 512) {
							m >>= 2;
							k <<= 2;
							cftmdl2(m, a, idx1 - m, w, nw - m);
						}
						cftleaf(m, 0, a, idx1 - m, nw, w);
						k >>= 1;
						int idx2 = loc_offa - m;
						for (j = glob_m - m; j > 0; j -= m) {
							k++;
							isplt = cfttree(m, j, k, a, loc_offa, nw, w);
							cftleaf(m, isplt, a, idx2 + j, nw, w);
						}
					}
				});
			}
		}
		try {
			for (int j = 0; j < nthread; j++) {
				futures[j].get();
			}
		} catch (ExecutionException ex) {
			ex.printStackTrace();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void cftrec4(int n, float[] a, int offa, int nw, float[] w) {
		int isplt, j, k, m;

		m = n;
		int idx1 = offa + n;
		while (m > 512) {
			m >>= 2;
			cftmdl1(m, a, idx1 - m, w, nw - (m >> 1));
		}
		cftleaf(m, 1, a, idx1 - m, nw, w);
		k = 0;
		int idx2 = offa - m;
		for (j = n - m; j > 0; j -= m) {
			k++;
			isplt = cfttree(m, j, k, a, offa, nw, w);
			cftleaf(m, isplt, a, idx2 + j, nw, w);
		}
	}

	private int cfttree(int n, int j, int k, float[] a, int offa, int nw, float[] w) {
		int i, isplt, m;
		int idx1 = offa - n;
		if ((k & 3) != 0) {
			isplt = k & 1;
			if (isplt != 0) {
				cftmdl1(n, a, idx1 + j, w, nw - (n >> 1));
			} else {
				cftmdl2(n, a, idx1 + j, w, nw - n);
			}
		} else {
			m = n;
			for (i = k; (i & 3) == 0; i >>= 2) {
				m <<= 2;
			}
			isplt = i & 1;
			int idx2 = offa + j;
			if (isplt != 0) {
				while (m > 128) {
					cftmdl1(m, a, idx2 - m, w, nw - (m >> 1));
					m >>= 2;
				}
			} else {
				while (m > 128) {
					cftmdl2(m, a, idx2 - m, w, nw - m);
					m >>= 2;
				}
			}
		}
		return isplt;
	}

	private void cftleaf(int n, int isplt, float[] a, int offa, int nw, float[] w) {
		if (n == 512) {
			cftmdl1(128, a, offa, w, nw - 64);
			cftf161(a, offa, w, nw - 8);
			cftf162(a, offa + 32, w, nw - 32);
			cftf161(a, offa + 64, w, nw - 8);
			cftf161(a, offa + 96, w, nw - 8);
			cftmdl2(128, a, offa + 128, w, nw - 128);
			cftf161(a, offa + 128, w, nw - 8);
			cftf162(a, offa + 160, w, nw - 32);
			cftf161(a, offa + 192, w, nw - 8);
			cftf162(a, offa + 224, w, nw - 32);
			cftmdl1(128, a, offa + 256, w, nw - 64);
			cftf161(a, offa + 256, w, nw - 8);
			cftf162(a, offa + 288, w, nw - 32);
			cftf161(a, offa + 320, w, nw - 8);
			cftf161(a, offa + 352, w, nw - 8);
			if (isplt != 0) {
				cftmdl1(128, a, offa + 384, w, nw - 64);
				cftf161(a, offa + 480, w, nw - 8);
			} else {
				cftmdl2(128, a, offa + 384, w, nw - 128);
				cftf162(a, offa + 480, w, nw - 32);
			}
			cftf161(a, offa + 384, w, nw - 8);
			cftf162(a, offa + 416, w, nw - 32);
			cftf161(a, offa + 448, w, nw - 8);
		} else {
			cftmdl1(64, a, offa, w, nw - 32);
			cftf081(a, offa, w, nw - 8);
			cftf082(a, offa + 16, w, nw - 8);
			cftf081(a, offa + 32, w, nw - 8);
			cftf081(a, offa + 48, w, nw - 8);
			cftmdl2(64, a, offa + 64, w, nw - 64);
			cftf081(a, offa + 64, w, nw - 8);
			cftf082(a, offa + 80, w, nw - 8);
			cftf081(a, offa + 96, w, nw - 8);
			cftf082(a, offa + 112, w, nw - 8);
			cftmdl1(64, a, offa + 128, w, nw - 32);
			cftf081(a, offa + 128, w, nw - 8);
			cftf082(a, offa + 144, w, nw - 8);
			cftf081(a, offa + 160, w, nw - 8);
			cftf081(a, offa + 176, w, nw - 8);
			if (isplt != 0) {
				cftmdl1(64, a, offa + 192, w, nw - 32);
				cftf081(a, offa + 240, w, nw - 8);
			} else {
				cftmdl2(64, a, offa + 192, w, nw - 64);
				cftf082(a, offa + 240, w, nw - 8);
			}
			cftf081(a, offa + 192, w, nw - 8);
			cftf082(a, offa + 208, w, nw - 8);
			cftf081(a, offa + 224, w, nw - 8);
		}
	}

	private void cftmdl1(int n, float[] a, int offa, float[] w, int startw) {
		int j, j0, j1, j2, j3, k, m, mh;
		float wn4r, wk1r, wk1i, wk3r, wk3i;
		float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;
		int idx0, idx1, idx2, idx3, idx4, idx5;

		mh = n >> 3;
		m = 2 * mh;
		j1 = m;
		j2 = j1 + m;
		j3 = j2 + m;
		idx1 = offa + j1;
		idx2 = offa + j2;
		idx3 = offa + j3;
		x0r = a[offa] + a[idx2];
		x0i = a[offa + 1] + a[idx2 + 1];
		x1r = a[offa] - a[idx2];
		x1i = a[offa + 1] - a[idx2 + 1];
		x2r = a[idx1] + a[idx3];
		x2i = a[idx1 + 1] + a[idx3 + 1];
		x3r = a[idx1] - a[idx3];
		x3i = a[idx1 + 1] - a[idx3 + 1];
		a[offa] = x0r + x2r;
		a[offa + 1] = x0i + x2i;
		a[idx1] = x0r - x2r;
		a[idx1 + 1] = x0i - x2i;
		a[idx2] = x1r - x3i;
		a[idx2 + 1] = x1i + x3r;
		a[idx3] = x1r + x3i;
		a[idx3 + 1] = x1i - x3r;
		wn4r = w[startw + 1];
		k = 0;
		for (j = 2; j < mh; j += 2) {
			k += 4;
			idx4 = startw + k;
			wk1r = w[idx4];
			wk1i = w[idx4 + 1];
			wk3r = w[idx4 + 2];
			wk3i = w[idx4 + 3];
			j1 = j + m;
			j2 = j1 + m;
			j3 = j2 + m;
			idx1 = offa + j1;
			idx2 = offa + j2;
			idx3 = offa + j3;
			idx5 = offa + j;
			x0r = a[idx5] + a[idx2];
			x0i = a[idx5 + 1] + a[idx2 + 1];
			x1r = a[idx5] - a[idx2];
			x1i = a[idx5 + 1] - a[idx2 + 1];
			x2r = a[idx1] + a[idx3];
			x2i = a[idx1 + 1] + a[idx3 + 1];
			x3r = a[idx1] - a[idx3];
			x3i = a[idx1 + 1] - a[idx3 + 1];
			a[idx5] = x0r + x2r;
			a[idx5 + 1] = x0i + x2i;
			a[idx1] = x0r - x2r;
			a[idx1 + 1] = x0i - x2i;
			x0r = x1r - x3i;
			x0i = x1i + x3r;
			a[idx2] = wk1r * x0r - wk1i * x0i;
			a[idx2 + 1] = wk1r * x0i + wk1i * x0r;
			x0r = x1r + x3i;
			x0i = x1i - x3r;
			a[idx3] = wk3r * x0r + wk3i * x0i;
			a[idx3 + 1] = wk3r * x0i - wk3i * x0r;
			j0 = m - j;
			j1 = j0 + m;
			j2 = j1 + m;
			j3 = j2 + m;
			idx0 = offa + j0;
			idx1 = offa + j1;
			idx2 = offa + j2;
			idx3 = offa + j3;
			x0r = a[idx0] + a[idx2];
			x0i = a[idx0 + 1] + a[idx2 + 1];
			x1r = a[idx0] - a[idx2];
			x1i = a[idx0 + 1] - a[idx2 + 1];
			x2r = a[idx1] + a[idx3];
			x2i = a[idx1 + 1] + a[idx3 + 1];
			x3r = a[idx1] - a[idx3];
			x3i = a[idx1 + 1] - a[idx3 + 1];
			a[idx0] = x0r + x2r;
			a[idx0 + 1] = x0i + x2i;
			a[idx1] = x0r - x2r;
			a[idx1 + 1] = x0i - x2i;
			x0r = x1r - x3i;
			x0i = x1i + x3r;
			a[idx2] = wk1i * x0r - wk1r * x0i;
			a[idx2 + 1] = wk1i * x0i + wk1r * x0r;
			x0r = x1r + x3i;
			x0i = x1i - x3r;
			a[idx3] = wk3i * x0r + wk3r * x0i;
			a[idx3 + 1] = wk3i * x0i - wk3r * x0r;
		}
		j0 = mh;
		j1 = j0 + m;
		j2 = j1 + m;
		j3 = j2 + m;
		idx0 = offa + j0;
		idx1 = offa + j1;
		idx2 = offa + j2;
		idx3 = offa + j3;
		x0r = a[idx0] + a[idx2];
		x0i = a[idx0 + 1] + a[idx2 + 1];
		x1r = a[idx0] - a[idx2];
		x1i = a[idx0 + 1] - a[idx2 + 1];
		x2r = a[idx1] + a[idx3];
		x2i = a[idx1 + 1] + a[idx3 + 1];
		x3r = a[idx1] - a[idx3];
		x3i = a[idx1 + 1] - a[idx3 + 1];
		a[idx0] = x0r + x2r;
		a[idx0 + 1] = x0i + x2i;
		a[idx1] = x0r - x2r;
		a[idx1 + 1] = x0i - x2i;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		a[idx2] = wn4r * (x0r - x0i);
		a[idx2 + 1] = wn4r * (x0i + x0r);
		x0r = x1r + x3i;
		x0i = x1i - x3r;
		a[idx3] = -wn4r * (x0r + x0i);
		a[idx3 + 1] = -wn4r * (x0i - x0r);
	}

	private void cftmdl2(int n, float[] a, int offa, float[] w, int startw) {
		int j, j0, j1, j2, j3, k, kr, m, mh;
		float wn4r, wk1r, wk1i, wk3r, wk3i, wd1r, wd1i, wd3r, wd3i;
		float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i, y0r, y0i, y2r, y2i;
		int idx0, idx1, idx2, idx3, idx4, idx5, idx6;

		mh = n >> 3;
		m = 2 * mh;
		wn4r = w[startw + 1];
		j1 = m;
		j2 = j1 + m;
		j3 = j2 + m;
		idx1 = offa + j1;
		idx2 = offa + j2;
		idx3 = offa + j3;
		x0r = a[offa] - a[idx2 + 1];
		x0i = a[offa + 1] + a[idx2];
		x1r = a[offa] + a[idx2 + 1];
		x1i = a[offa + 1] - a[idx2];
		x2r = a[idx1] - a[idx3 + 1];
		x2i = a[idx1 + 1] + a[idx3];
		x3r = a[idx1] + a[idx3 + 1];
		x3i = a[idx1 + 1] - a[idx3];
		y0r = wn4r * (x2r - x2i);
		y0i = wn4r * (x2i + x2r);
		a[offa] = x0r + y0r;
		a[offa + 1] = x0i + y0i;
		a[idx1] = x0r - y0r;
		a[idx1 + 1] = x0i - y0i;
		y0r = wn4r * (x3r - x3i);
		y0i = wn4r * (x3i + x3r);
		a[idx2] = x1r - y0i;
		a[idx2 + 1] = x1i + y0r;
		a[idx3] = x1r + y0i;
		a[idx3 + 1] = x1i - y0r;
		k = 0;
		kr = 2 * m;
		for (j = 2; j < mh; j += 2) {
			k += 4;
			idx4 = startw + k;
			wk1r = w[idx4];
			wk1i = w[idx4 + 1];
			wk3r = w[idx4 + 2];
			wk3i = w[idx4 + 3];
			kr -= 4;
			idx5 = startw + kr;
			wd1i = w[idx5];
			wd1r = w[idx5 + 1];
			wd3i = w[idx5 + 2];
			wd3r = w[idx5 + 3];
			j1 = j + m;
			j2 = j1 + m;
			j3 = j2 + m;
			idx1 = offa + j1;
			idx2 = offa + j2;
			idx3 = offa + j3;
			idx6 = offa + j;
			x0r = a[idx6] - a[idx2 + 1];
			x0i = a[idx6 + 1] + a[idx2];
			x1r = a[idx6] + a[idx2 + 1];
			x1i = a[idx6 + 1] - a[idx2];
			x2r = a[idx1] - a[idx3 + 1];
			x2i = a[idx1 + 1] + a[idx3];
			x3r = a[idx1] + a[idx3 + 1];
			x3i = a[idx1 + 1] - a[idx3];
			y0r = wk1r * x0r - wk1i * x0i;
			y0i = wk1r * x0i + wk1i * x0r;
			y2r = wd1r * x2r - wd1i * x2i;
			y2i = wd1r * x2i + wd1i * x2r;
			a[idx6] = y0r + y2r;
			a[idx6 + 1] = y0i + y2i;
			a[idx1] = y0r - y2r;
			a[idx1 + 1] = y0i - y2i;
			y0r = wk3r * x1r + wk3i * x1i;
			y0i = wk3r * x1i - wk3i * x1r;
			y2r = wd3r * x3r + wd3i * x3i;
			y2i = wd3r * x3i - wd3i * x3r;
			a[idx2] = y0r + y2r;
			a[idx2 + 1] = y0i + y2i;
			a[idx3] = y0r - y2r;
			a[idx3 + 1] = y0i - y2i;
			j0 = m - j;
			j1 = j0 + m;
			j2 = j1 + m;
			j3 = j2 + m;
			idx0 = offa + j0;
			idx1 = offa + j1;
			idx2 = offa + j2;
			idx3 = offa + j3;
			x0r = a[idx0] - a[idx2 + 1];
			x0i = a[idx0 + 1] + a[idx2];
			x1r = a[idx0] + a[idx2 + 1];
			x1i = a[idx0 + 1] - a[idx2];
			x2r = a[idx1] - a[idx3 + 1];
			x2i = a[idx1 + 1] + a[idx3];
			x3r = a[idx1] + a[idx3 + 1];
			x3i = a[idx1 + 1] - a[idx3];
			y0r = wd1i * x0r - wd1r * x0i;
			y0i = wd1i * x0i + wd1r * x0r;
			y2r = wk1i * x2r - wk1r * x2i;
			y2i = wk1i * x2i + wk1r * x2r;
			a[idx0] = y0r + y2r;
			a[idx0 + 1] = y0i + y2i;
			a[idx1] = y0r - y2r;
			a[idx1 + 1] = y0i - y2i;
			y0r = wd3i * x1r + wd3r * x1i;
			y0i = wd3i * x1i - wd3r * x1r;
			y2r = wk3i * x3r + wk3r * x3i;
			y2i = wk3i * x3i - wk3r * x3r;
			a[idx2] = y0r + y2r;
			a[idx2 + 1] = y0i + y2i;
			a[idx3] = y0r - y2r;
			a[idx3 + 1] = y0i - y2i;
		}
		wk1r = w[startw + m];
		wk1i = w[startw + m + 1];
		j0 = mh;
		j1 = j0 + m;
		j2 = j1 + m;
		j3 = j2 + m;
		idx0 = offa + j0;
		idx1 = offa + j1;
		idx2 = offa + j2;
		idx3 = offa + j3;
		x0r = a[idx0] - a[idx2 + 1];
		x0i = a[idx0 + 1] + a[idx2];
		x1r = a[idx0] + a[idx2 + 1];
		x1i = a[idx0 + 1] - a[idx2];
		x2r = a[idx1] - a[idx3 + 1];
		x2i = a[idx1 + 1] + a[idx3];
		x3r = a[idx1] + a[idx3 + 1];
		x3i = a[idx1 + 1] - a[idx3];
		y0r = wk1r * x0r - wk1i * x0i;
		y0i = wk1r * x0i + wk1i * x0r;
		y2r = wk1i * x2r - wk1r * x2i;
		y2i = wk1i * x2i + wk1r * x2r;
		a[idx0] = y0r + y2r;
		a[idx0 + 1] = y0i + y2i;
		a[idx1] = y0r - y2r;
		a[idx1 + 1] = y0i - y2i;
		y0r = wk1i * x1r - wk1r * x1i;
		y0i = wk1i * x1i + wk1r * x1r;
		y2r = wk1r * x3r - wk1i * x3i;
		y2i = wk1r * x3i + wk1i * x3r;
		a[idx2] = y0r - y2r;
		a[idx2 + 1] = y0i - y2i;
		a[idx3] = y0r + y2r;
		a[idx3 + 1] = y0i + y2i;
	}

	private void cftfx41(int n, float[] a, int offa, int nw, float[] w) {
		if (n == 128) {
			cftf161(a, offa, w, nw - 8);
			cftf162(a, offa + 32, w, nw - 32);
			cftf161(a, offa + 64, w, nw - 8);
			cftf161(a, offa + 96, w, nw - 8);
		} else {
			cftf081(a, offa, w, nw - 8);
			cftf082(a, offa + 16, w, nw - 8);
			cftf081(a, offa + 32, w, nw - 8);
			cftf081(a, offa + 48, w, nw - 8);
		}
	}

	private void cftf161(float[] a, int offa, float[] w, int startw) {
		float wn4r, wk1r, wk1i, x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i, y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i, y4r, y4i, y5r, y5i, y6r, y6i, y7r, y7i, y8r, y8i, y9r, y9i, y10r, y10i, y11r, y11i, y12r, y12i, y13r, y13i, y14r, y14i, y15r, y15i;

		wn4r = w[startw + 1];
		wk1r = w[startw + 2];
		wk1i = w[startw + 3];

		x0r = a[offa] + a[offa + 16];
		x0i = a[offa + 1] + a[offa + 17];
		x1r = a[offa] - a[offa + 16];
		x1i = a[offa + 1] - a[offa + 17];
		x2r = a[offa + 8] + a[offa + 24];
		x2i = a[offa + 9] + a[offa + 25];
		x3r = a[offa + 8] - a[offa + 24];
		x3i = a[offa + 9] - a[offa + 25];
		y0r = x0r + x2r;
		y0i = x0i + x2i;
		y4r = x0r - x2r;
		y4i = x0i - x2i;
		y8r = x1r - x3i;
		y8i = x1i + x3r;
		y12r = x1r + x3i;
		y12i = x1i - x3r;
		x0r = a[offa + 2] + a[offa + 18];
		x0i = a[offa + 3] + a[offa + 19];
		x1r = a[offa + 2] - a[offa + 18];
		x1i = a[offa + 3] - a[offa + 19];
		x2r = a[offa + 10] + a[offa + 26];
		x2i = a[offa + 11] + a[offa + 27];
		x3r = a[offa + 10] - a[offa + 26];
		x3i = a[offa + 11] - a[offa + 27];
		y1r = x0r + x2r;
		y1i = x0i + x2i;
		y5r = x0r - x2r;
		y5i = x0i - x2i;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		y9r = wk1r * x0r - wk1i * x0i;
		y9i = wk1r * x0i + wk1i * x0r;
		x0r = x1r + x3i;
		x0i = x1i - x3r;
		y13r = wk1i * x0r - wk1r * x0i;
		y13i = wk1i * x0i + wk1r * x0r;
		x0r = a[offa + 4] + a[offa + 20];
		x0i = a[offa + 5] + a[offa + 21];
		x1r = a[offa + 4] - a[offa + 20];
		x1i = a[offa + 5] - a[offa + 21];
		x2r = a[offa + 12] + a[offa + 28];
		x2i = a[offa + 13] + a[offa + 29];
		x3r = a[offa + 12] - a[offa + 28];
		x3i = a[offa + 13] - a[offa + 29];
		y2r = x0r + x2r;
		y2i = x0i + x2i;
		y6r = x0r - x2r;
		y6i = x0i - x2i;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		y10r = wn4r * (x0r - x0i);
		y10i = wn4r * (x0i + x0r);
		x0r = x1r + x3i;
		x0i = x1i - x3r;
		y14r = wn4r * (x0r + x0i);
		y14i = wn4r * (x0i - x0r);
		x0r = a[offa + 6] + a[offa + 22];
		x0i = a[offa + 7] + a[offa + 23];
		x1r = a[offa + 6] - a[offa + 22];
		x1i = a[offa + 7] - a[offa + 23];
		x2r = a[offa + 14] + a[offa + 30];
		x2i = a[offa + 15] + a[offa + 31];
		x3r = a[offa + 14] - a[offa + 30];
		x3i = a[offa + 15] - a[offa + 31];
		y3r = x0r + x2r;
		y3i = x0i + x2i;
		y7r = x0r - x2r;
		y7i = x0i - x2i;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		y11r = wk1i * x0r - wk1r * x0i;
		y11i = wk1i * x0i + wk1r * x0r;
		x0r = x1r + x3i;
		x0i = x1i - x3r;
		y15r = wk1r * x0r - wk1i * x0i;
		y15i = wk1r * x0i + wk1i * x0r;
		x0r = y12r - y14r;
		x0i = y12i - y14i;
		x1r = y12r + y14r;
		x1i = y12i + y14i;
		x2r = y13r - y15r;
		x2i = y13i - y15i;
		x3r = y13r + y15r;
		x3i = y13i + y15i;
		a[offa + 24] = x0r + x2r;
		a[offa + 25] = x0i + x2i;
		a[offa + 26] = x0r - x2r;
		a[offa + 27] = x0i - x2i;
		a[offa + 28] = x1r - x3i;
		a[offa + 29] = x1i + x3r;
		a[offa + 30] = x1r + x3i;
		a[offa + 31] = x1i - x3r;
		x0r = y8r + y10r;
		x0i = y8i + y10i;
		x1r = y8r - y10r;
		x1i = y8i - y10i;
		x2r = y9r + y11r;
		x2i = y9i + y11i;
		x3r = y9r - y11r;
		x3i = y9i - y11i;
		a[offa + 16] = x0r + x2r;
		a[offa + 17] = x0i + x2i;
		a[offa + 18] = x0r - x2r;
		a[offa + 19] = x0i - x2i;
		a[offa + 20] = x1r - x3i;
		a[offa + 21] = x1i + x3r;
		a[offa + 22] = x1r + x3i;
		a[offa + 23] = x1i - x3r;
		x0r = y5r - y7i;
		x0i = y5i + y7r;
		x2r = wn4r * (x0r - x0i);
		x2i = wn4r * (x0i + x0r);
		x0r = y5r + y7i;
		x0i = y5i - y7r;
		x3r = wn4r * (x0r - x0i);
		x3i = wn4r * (x0i + x0r);
		x0r = y4r - y6i;
		x0i = y4i + y6r;
		x1r = y4r + y6i;
		x1i = y4i - y6r;
		a[offa + 8] = x0r + x2r;
		a[offa + 9] = x0i + x2i;
		a[offa + 10] = x0r - x2r;
		a[offa + 11] = x0i - x2i;
		a[offa + 12] = x1r - x3i;
		a[offa + 13] = x1i + x3r;
		a[offa + 14] = x1r + x3i;
		a[offa + 15] = x1i - x3r;
		x0r = y0r + y2r;
		x0i = y0i + y2i;
		x1r = y0r - y2r;
		x1i = y0i - y2i;
		x2r = y1r + y3r;
		x2i = y1i + y3i;
		x3r = y1r - y3r;
		x3i = y1i - y3i;
		a[offa] = x0r + x2r;
		a[offa + 1] = x0i + x2i;
		a[offa + 2] = x0r - x2r;
		a[offa + 3] = x0i - x2i;
		a[offa + 4] = x1r - x3i;
		a[offa + 5] = x1i + x3r;
		a[offa + 6] = x1r + x3i;
		a[offa + 7] = x1i - x3r;
	}

	private void cftf162(float[] a, int offa, float[] w, int startw) {
		float wn4r, wk1r, wk1i, wk2r, wk2i, wk3r, wk3i, x0r, x0i, x1r, x1i, x2r, x2i, y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i, y4r, y4i, y5r, y5i, y6r, y6i, y7r, y7i, y8r, y8i, y9r, y9i, y10r, y10i, y11r, y11i, y12r, y12i, y13r, y13i, y14r, y14i, y15r, y15i;

		wn4r = w[startw + 1];
		wk1r = w[startw + 4];
		wk1i = w[startw + 5];
		wk3r = w[startw + 6];
		wk3i = -w[startw + 7];
		wk2r = w[startw + 8];
		wk2i = w[startw + 9];
		x1r = a[offa] - a[offa + 17];
		x1i = a[offa + 1] + a[offa + 16];
		x0r = a[offa + 8] - a[offa + 25];
		x0i = a[offa + 9] + a[offa + 24];
		x2r = wn4r * (x0r - x0i);
		x2i = wn4r * (x0i + x0r);
		y0r = x1r + x2r;
		y0i = x1i + x2i;
		y4r = x1r - x2r;
		y4i = x1i - x2i;
		x1r = a[offa] + a[offa + 17];
		x1i = a[offa + 1] - a[offa + 16];
		x0r = a[offa + 8] + a[offa + 25];
		x0i = a[offa + 9] - a[offa + 24];
		x2r = wn4r * (x0r - x0i);
		x2i = wn4r * (x0i + x0r);
		y8r = x1r - x2i;
		y8i = x1i + x2r;
		y12r = x1r + x2i;
		y12i = x1i - x2r;
		x0r = a[offa + 2] - a[offa + 19];
		x0i = a[offa + 3] + a[offa + 18];
		x1r = wk1r * x0r - wk1i * x0i;
		x1i = wk1r * x0i + wk1i * x0r;
		x0r = a[offa + 10] - a[offa + 27];
		x0i = a[offa + 11] + a[offa + 26];
		x2r = wk3i * x0r - wk3r * x0i;
		x2i = wk3i * x0i + wk3r * x0r;
		y1r = x1r + x2r;
		y1i = x1i + x2i;
		y5r = x1r - x2r;
		y5i = x1i - x2i;
		x0r = a[offa + 2] + a[offa + 19];
		x0i = a[offa + 3] - a[offa + 18];
		x1r = wk3r * x0r - wk3i * x0i;
		x1i = wk3r * x0i + wk3i * x0r;
		x0r = a[offa + 10] + a[offa + 27];
		x0i = a[offa + 11] - a[offa + 26];
		x2r = wk1r * x0r + wk1i * x0i;
		x2i = wk1r * x0i - wk1i * x0r;
		y9r = x1r - x2r;
		y9i = x1i - x2i;
		y13r = x1r + x2r;
		y13i = x1i + x2i;
		x0r = a[offa + 4] - a[offa + 21];
		x0i = a[offa + 5] + a[offa + 20];
		x1r = wk2r * x0r - wk2i * x0i;
		x1i = wk2r * x0i + wk2i * x0r;
		x0r = a[offa + 12] - a[offa + 29];
		x0i = a[offa + 13] + a[offa + 28];
		x2r = wk2i * x0r - wk2r * x0i;
		x2i = wk2i * x0i + wk2r * x0r;
		y2r = x1r + x2r;
		y2i = x1i + x2i;
		y6r = x1r - x2r;
		y6i = x1i - x2i;
		x0r = a[offa + 4] + a[offa + 21];
		x0i = a[offa + 5] - a[offa + 20];
		x1r = wk2i * x0r - wk2r * x0i;
		x1i = wk2i * x0i + wk2r * x0r;
		x0r = a[offa + 12] + a[offa + 29];
		x0i = a[offa + 13] - a[offa + 28];
		x2r = wk2r * x0r - wk2i * x0i;
		x2i = wk2r * x0i + wk2i * x0r;
		y10r = x1r - x2r;
		y10i = x1i - x2i;
		y14r = x1r + x2r;
		y14i = x1i + x2i;
		x0r = a[offa + 6] - a[offa + 23];
		x0i = a[offa + 7] + a[offa + 22];
		x1r = wk3r * x0r - wk3i * x0i;
		x1i = wk3r * x0i + wk3i * x0r;
		x0r = a[offa + 14] - a[offa + 31];
		x0i = a[offa + 15] + a[offa + 30];
		x2r = wk1i * x0r - wk1r * x0i;
		x2i = wk1i * x0i + wk1r * x0r;
		y3r = x1r + x2r;
		y3i = x1i + x2i;
		y7r = x1r - x2r;
		y7i = x1i - x2i;
		x0r = a[offa + 6] + a[offa + 23];
		x0i = a[offa + 7] - a[offa + 22];
		x1r = wk1i * x0r + wk1r * x0i;
		x1i = wk1i * x0i - wk1r * x0r;
		x0r = a[offa + 14] + a[offa + 31];
		x0i = a[offa + 15] - a[offa + 30];
		x2r = wk3i * x0r - wk3r * x0i;
		x2i = wk3i * x0i + wk3r * x0r;
		y11r = x1r + x2r;
		y11i = x1i + x2i;
		y15r = x1r - x2r;
		y15i = x1i - x2i;
		x1r = y0r + y2r;
		x1i = y0i + y2i;
		x2r = y1r + y3r;
		x2i = y1i + y3i;
		a[offa] = x1r + x2r;
		a[offa + 1] = x1i + x2i;
		a[offa + 2] = x1r - x2r;
		a[offa + 3] = x1i - x2i;
		x1r = y0r - y2r;
		x1i = y0i - y2i;
		x2r = y1r - y3r;
		x2i = y1i - y3i;
		a[offa + 4] = x1r - x2i;
		a[offa + 5] = x1i + x2r;
		a[offa + 6] = x1r + x2i;
		a[offa + 7] = x1i - x2r;
		x1r = y4r - y6i;
		x1i = y4i + y6r;
		x0r = y5r - y7i;
		x0i = y5i + y7r;
		x2r = wn4r * (x0r - x0i);
		x2i = wn4r * (x0i + x0r);
		a[offa + 8] = x1r + x2r;
		a[offa + 9] = x1i + x2i;
		a[offa + 10] = x1r - x2r;
		a[offa + 11] = x1i - x2i;
		x1r = y4r + y6i;
		x1i = y4i - y6r;
		x0r = y5r + y7i;
		x0i = y5i - y7r;
		x2r = wn4r * (x0r - x0i);
		x2i = wn4r * (x0i + x0r);
		a[offa + 12] = x1r - x2i;
		a[offa + 13] = x1i + x2r;
		a[offa + 14] = x1r + x2i;
		a[offa + 15] = x1i - x2r;
		x1r = y8r + y10r;
		x1i = y8i + y10i;
		x2r = y9r - y11r;
		x2i = y9i - y11i;
		a[offa + 16] = x1r + x2r;
		a[offa + 17] = x1i + x2i;
		a[offa + 18] = x1r - x2r;
		a[offa + 19] = x1i - x2i;
		x1r = y8r - y10r;
		x1i = y8i - y10i;
		x2r = y9r + y11r;
		x2i = y9i + y11i;
		a[offa + 20] = x1r - x2i;
		a[offa + 21] = x1i + x2r;
		a[offa + 22] = x1r + x2i;
		a[offa + 23] = x1i - x2r;
		x1r = y12r - y14i;
		x1i = y12i + y14r;
		x0r = y13r + y15i;
		x0i = y13i - y15r;
		x2r = wn4r * (x0r - x0i);
		x2i = wn4r * (x0i + x0r);
		a[offa + 24] = x1r + x2r;
		a[offa + 25] = x1i + x2i;
		a[offa + 26] = x1r - x2r;
		a[offa + 27] = x1i - x2i;
		x1r = y12r + y14i;
		x1i = y12i - y14r;
		x0r = y13r - y15i;
		x0i = y13i + y15r;
		x2r = wn4r * (x0r - x0i);
		x2i = wn4r * (x0i + x0r);
		a[offa + 28] = x1r - x2i;
		a[offa + 29] = x1i + x2r;
		a[offa + 30] = x1r + x2i;
		a[offa + 31] = x1i - x2r;
	}

	private void cftf081(float[] a, int offa, float[] w, int startw) {
		float wn4r, x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i, y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i, y4r, y4i, y5r, y5i, y6r, y6i, y7r, y7i;

		wn4r = w[startw + 1];
		x0r = a[offa] + a[offa + 8];
		x0i = a[offa + 1] + a[offa + 9];
		x1r = a[offa] - a[offa + 8];
		x1i = a[offa + 1] - a[offa + 9];
		x2r = a[offa + 4] + a[offa + 12];
		x2i = a[offa + 5] + a[offa + 13];
		x3r = a[offa + 4] - a[offa + 12];
		x3i = a[offa + 5] - a[offa + 13];
		y0r = x0r + x2r;
		y0i = x0i + x2i;
		y2r = x0r - x2r;
		y2i = x0i - x2i;
		y1r = x1r - x3i;
		y1i = x1i + x3r;
		y3r = x1r + x3i;
		y3i = x1i - x3r;
		x0r = a[offa + 2] + a[offa + 10];
		x0i = a[offa + 3] + a[offa + 11];
		x1r = a[offa + 2] - a[offa + 10];
		x1i = a[offa + 3] - a[offa + 11];
		x2r = a[offa + 6] + a[offa + 14];
		x2i = a[offa + 7] + a[offa + 15];
		x3r = a[offa + 6] - a[offa + 14];
		x3i = a[offa + 7] - a[offa + 15];
		y4r = x0r + x2r;
		y4i = x0i + x2i;
		y6r = x0r - x2r;
		y6i = x0i - x2i;
		x0r = x1r - x3i;
		x0i = x1i + x3r;
		x2r = x1r + x3i;
		x2i = x1i - x3r;
		y5r = wn4r * (x0r - x0i);
		y5i = wn4r * (x0r + x0i);
		y7r = wn4r * (x2r - x2i);
		y7i = wn4r * (x2r + x2i);
		a[offa + 8] = y1r + y5r;
		a[offa + 9] = y1i + y5i;
		a[offa + 10] = y1r - y5r;
		a[offa + 11] = y1i - y5i;
		a[offa + 12] = y3r - y7i;
		a[offa + 13] = y3i + y7r;
		a[offa + 14] = y3r + y7i;
		a[offa + 15] = y3i - y7r;
		a[offa] = y0r + y4r;
		a[offa + 1] = y0i + y4i;
		a[offa + 2] = y0r - y4r;
		a[offa + 3] = y0i - y4i;
		a[offa + 4] = y2r - y6i;
		a[offa + 5] = y2i + y6r;
		a[offa + 6] = y2r + y6i;
		a[offa + 7] = y2i - y6r;
	}

	private void cftf082(float[] a, int offa, float[] w, int startw) {
		float wn4r, wk1r, wk1i, x0r, x0i, x1r, x1i, y0r, y0i, y1r, y1i, y2r, y2i, y3r, y3i, y4r, y4i, y5r, y5i, y6r, y6i, y7r, y7i;

		wn4r = w[startw + 1];
		wk1r = w[startw + 2];
		wk1i = w[startw + 3];
		y0r = a[offa] - a[offa + 9];
		y0i = a[offa + 1] + a[offa + 8];
		y1r = a[offa] + a[offa + 9];
		y1i = a[offa + 1] - a[offa + 8];
		x0r = a[offa + 4] - a[offa + 13];
		x0i = a[offa + 5] + a[offa + 12];
		y2r = wn4r * (x0r - x0i);
		y2i = wn4r * (x0i + x0r);
		x0r = a[offa + 4] + a[offa + 13];
		x0i = a[offa + 5] - a[offa + 12];
		y3r = wn4r * (x0r - x0i);
		y3i = wn4r * (x0i + x0r);
		x0r = a[offa + 2] - a[offa + 11];
		x0i = a[offa + 3] + a[offa + 10];
		y4r = wk1r * x0r - wk1i * x0i;
		y4i = wk1r * x0i + wk1i * x0r;
		x0r = a[offa + 2] + a[offa + 11];
		x0i = a[offa + 3] - a[offa + 10];
		y5r = wk1i * x0r - wk1r * x0i;
		y5i = wk1i * x0i + wk1r * x0r;
		x0r = a[offa + 6] - a[offa + 15];
		x0i = a[offa + 7] + a[offa + 14];
		y6r = wk1i * x0r - wk1r * x0i;
		y6i = wk1i * x0i + wk1r * x0r;
		x0r = a[offa + 6] + a[offa + 15];
		x0i = a[offa + 7] - a[offa + 14];
		y7r = wk1r * x0r - wk1i * x0i;
		y7i = wk1r * x0i + wk1i * x0r;
		x0r = y0r + y2r;
		x0i = y0i + y2i;
		x1r = y4r + y6r;
		x1i = y4i + y6i;
		a[offa] = x0r + x1r;
		a[offa + 1] = x0i + x1i;
		a[offa + 2] = x0r - x1r;
		a[offa + 3] = x0i - x1i;
		x0r = y0r - y2r;
		x0i = y0i - y2i;
		x1r = y4r - y6r;
		x1i = y4i - y6i;
		a[offa + 4] = x0r - x1i;
		a[offa + 5] = x0i + x1r;
		a[offa + 6] = x0r + x1i;
		a[offa + 7] = x0i - x1r;
		x0r = y1r - y3i;
		x0i = y1i + y3r;
		x1r = y5r - y7r;
		x1i = y5i - y7i;
		a[offa + 8] = x0r + x1r;
		a[offa + 9] = x0i + x1i;
		a[offa + 10] = x0r - x1r;
		a[offa + 11] = x0i - x1i;
		x0r = y1r + y3i;
		x0i = y1i - y3r;
		x1r = y5r + y7r;
		x1i = y5i + y7i;
		a[offa + 12] = x0r - x1i;
		a[offa + 13] = x0i + x1r;
		a[offa + 14] = x0r + x1i;
		a[offa + 15] = x0i - x1r;
	}

	private void cftf040(float[] a, int offa) {
		float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

		x0r = a[offa] + a[offa + 4];
		x0i = a[offa + 1] + a[offa + 5];
		x1r = a[offa] - a[offa + 4];
		x1i = a[offa + 1] - a[offa + 5];
		x2r = a[offa + 2] + a[offa + 6];
		x2i = a[offa + 3] + a[offa + 7];
		x3r = a[offa + 2] - a[offa + 6];
		x3i = a[offa + 3] - a[offa + 7];
		a[offa] = x0r + x2r;
		a[offa + 1] = x0i + x2i;
		a[offa + 2] = x1r - x3i;
		a[offa + 3] = x1i + x3r;
		a[offa + 4] = x0r - x2r;
		a[offa + 5] = x0i - x2i;
		a[offa + 6] = x1r + x3i;
		a[offa + 7] = x1i - x3r;
	}

	private void cftb040(float[] a, int offa) {
		float x0r, x0i, x1r, x1i, x2r, x2i, x3r, x3i;

		x0r = a[offa] + a[offa + 4];
		x0i = a[offa + 1] + a[offa + 5];
		x1r = a[offa] - a[offa + 4];
		x1i = a[offa + 1] - a[offa + 5];
		x2r = a[offa + 2] + a[offa + 6];
		x2i = a[offa + 3] + a[offa + 7];
		x3r = a[offa + 2] - a[offa + 6];
		x3i = a[offa + 3] - a[offa + 7];
		a[offa] = x0r + x2r;
		a[offa + 1] = x0i + x2i;
		a[offa + 2] = x1r + x3i;
		a[offa + 3] = x1i - x3r;
		a[offa + 4] = x0r - x2r;
		a[offa + 5] = x0i - x2i;
		a[offa + 6] = x1r - x3i;
		a[offa + 7] = x1i + x3r;
	}

	private void cftx020(float[] a, int offa) {
		float x0r, x0i;
		x0r = a[offa] - a[offa + 2];
		x0i = -a[offa + 1] + a[offa + 3];
		a[offa] += a[offa + 2];
		a[offa + 1] += a[offa + 3];
		a[offa + 2] = x0r;
		a[offa + 3] = x0i;
	}

	private void cftxb020(float[] a, int offa) {
		float x0r, x0i;

		x0r = a[offa] - a[offa + 2];
		x0i = a[offa + 1] - a[offa + 3];
		a[offa] += a[offa + 2];
		a[offa + 1] += a[offa + 3];
		a[offa + 2] = x0r;
		a[offa + 3] = x0i;
	}

	private void cftxc020(float[] a, int offa) {
		float x0r, x0i;
		x0r = a[offa] - a[offa + 2];
		x0i = a[offa + 1] + a[offa + 3];
		a[offa] += a[offa + 2];
		a[offa + 1] -= a[offa + 3];
		a[offa + 2] = x0r;
		a[offa + 3] = x0i;
	}

	private void rftfsub(int n, float[] a, int offa, int nc, float[] c, int startc) {
		int j, k, kk, ks, m;
		float wkr, wki, xr, xi, yr, yi;
		int idx1, idx2;

		m = n >> 1;
		ks = 2 * nc / m;
		kk = 0;
		for (j = 2; j < m; j += 2) {
			k = n - j;
			kk += ks;
			wkr = (float) (0.5 - c[startc + nc - kk]);
			wki = c[startc + kk];
			idx1 = offa + j;
			idx2 = offa + k;
			xr = a[idx1] - a[idx2];
			xi = a[idx1 + 1] + a[idx2 + 1];
			yr = wkr * xr - wki * xi;
			yi = wkr * xi + wki * xr;
			a[idx1] -= yr;
			a[idx1 + 1] = yi - a[idx1 + 1];
			a[idx2] += yr;
			a[idx2 + 1] = yi - a[idx2 + 1];
		}
		a[offa + m + 1] = -a[offa + m + 1];
	}

	private void rftbsub(int n, float[] a, int offa, int nc, float[] c, int startc) {
		int j, k, kk, ks, m;
		float wkr, wki, xr, xi, yr, yi;
		int idx1, idx2;

		m = n >> 1;
		ks = 2 * nc / m;
		kk = 0;
		for (j = 2; j < m; j += 2) {
			k = n - j;
			kk += ks;
			wkr = (float) (0.5 - c[startc + nc - kk]);
			wki = c[startc + kk];
			idx1 = offa + j;
			idx2 = offa + k;
			xr = a[idx1] - a[idx2];
			xi = a[idx1 + 1] + a[idx2 + 1];
			yr = wkr * xr - wki * xi;
			yi = wkr * xi + wki * xr;
			a[idx1] -= yr;
			a[idx1 + 1] -= yi;
			a[idx2] += yr;
			a[idx2 + 1] -= yi;
		}
	}

	private void scale(final float m, final float[] a, int offa, boolean complex) {
		final float norm = (float) (1.0 / m);
		int locn;
		if (complex) {
			locn = 2 * n;
		} else {
			locn = n;
		}
		int np = ConcurrencyUtils.getNumberOfProcessors();
		if ((np > 1) && (locn >= ConcurrencyUtils.getThreadsBeginN_1D_FFT_2Threads())) {
			final int k = locn / np;
			Future[] futures = new Future[np];
			for (int i = 0; i < np; i++) {
				final int startIdx = offa + i * k;
				final int stopIdx;
				if (i == np - 1) {
					stopIdx = offa + locn;
				} else {
					stopIdx = startIdx + k;
				}
				futures[i] = ConcurrencyUtils.threadPool.submit(new Runnable() {

					public void run() {
						for (int i = startIdx; i < stopIdx; i++) {
							a[i] *= norm;
						}
					}
				});
			}
			try {
				for (int j = 0; j < np; j++) {
					futures[j].get();
				}
			} catch (ExecutionException ex) {
				ex.printStackTrace();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		} else {
			for (int i = offa; i < offa + locn; i++) {
				a[i] *= norm;
			}

		}
	}
}
