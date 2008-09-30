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

package edu.emory.mathcs.jtransforms.dht;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import edu.emory.mathcs.utils.ConcurrencyUtils;

/**
 * Computes 2D Discrete Hartley Transform (DHT) of real, double precision data.
 * The sizes of both dimensions must be power-of-two numbers. This is a parallel
 * implementation optimized for SMP systems.
 * 
 * @author Piotr Wendykier (piotr.wendykier@gmail.com)
 * 
 */
public class DoubleDHT_2D {

    private int n1;

    private int n2;

    private int[] ip;

    private double[] w;

    private double[] t;

    private DoubleDHT_1D dhtn2, dhtn1;

    private int oldNthread;

    private int nt;

    /**
     * Creates new instance of DoubleDHT_2D.
     * 
     * @param n1
     *            number of rows - must be a power-of-two number
     * @param n2
     *            number of columns - must be a power-of-two number
     */
    public DoubleDHT_2D(int n1, int n2) {
        if (!ConcurrencyUtils.isPowerOf2(n1) || !ConcurrencyUtils.isPowerOf2(n2))
            throw new IllegalArgumentException("n1, n2 must be power of two numbers");
        if (n1 <= 1 || n2 <= 1) {
            throw new IllegalArgumentException("n1, n2 must be greater than 1");
        }
        this.n1 = n1;
        this.n2 = n2;
        this.ip = new int[2 + (int) Math.ceil(Math.sqrt(Math.max(n1 / 2, n2 / 2)))];
        this.w = new double[(int) Math.ceil(Math.max(n1 * 1.5, n2 * 1.5))];
        dhtn2 = new DoubleDHT_1D(n2, ip, w);
        dhtn1 = new DoubleDHT_1D(n1, ip, w);
        oldNthread = ConcurrencyUtils.getNumberOfProcessors();
        nt = 4 * oldNthread * n1;
        if (n2 == 2 * oldNthread) {
            nt >>= 1;
        } else if (n2 < 2 * oldNthread) {
            nt >>= 2;
        }
        t = new double[nt];
    }

    /**
     * Computes 2D real, forward DHT leaving the result in <code>a</code>. The
     * data is stored in 1D array in row-major order.
     * 
     * @param a
     *            data to transform
     */
    public void forward(double[] a) {
        int nthread, n, nw, nc, i;

        n = n1;
        if (n < n2) {
            n = n2;
        }
        nw = ip[0];
        if (n > (nw << 2)) {
            nw = n >> 2;
            makewt(nw);
        }
        nc = ip[1];
        if (n > nc) {
            nc = n;
            makect(nc, w, nw);
        }
        nthread = ConcurrencyUtils.getNumberOfProcessors();
        if (nthread != oldNthread) {
            nt = 4 * nthread * n1;
            if (n2 == 2 * nthread) {
                nt >>= 1;
            } else if (n2 < 2 * nthread) {
                nt >>= 2;
            }
            t = new double[nt];
            oldNthread = nthread;
        }
        if ((nthread > 1) && (n1 * n2 >= ConcurrencyUtils.getThreadsBeginN_2D())) {
            ddxt2d_subth(-1, a, true);
            ddxt2d0_subth(-1, a, true);
        } else {
            ddxt2d_sub(-1, a, true);
            for (i = 0; i < n1; i++) {
                dhtn2.forward(a, i * n2);
            }
        }
        y_transform(a);
    }

    /**
     * Computes 2D real, forward DHT leaving the result in <code>a</code>. The
     * data is stored in 2D array.
     * 
     * @param a
     *            data to transform
     */
    public void forward(double[][] a) {
        int nthread, n, nw, nc, i;

        n = n1;
        if (n < n2) {
            n = n2;
        }
        nw = ip[0];
        if (n > (nw << 2)) {
            nw = n >> 2;
            makewt(nw);
        }
        nc = ip[1];
        if (n > nc) {
            nc = n;
            makect(nc, w, nw);
        }
        nthread = ConcurrencyUtils.getNumberOfProcessors();
        if (nthread != oldNthread) {
            nt = 4 * nthread * n1;
            if (n2 == 2 * nthread) {
                nt >>= 1;
            } else if (n2 < 2 * nthread) {
                nt >>= 2;
            }
            t = new double[nt];
            oldNthread = nthread;
        }
        if ((nthread > 1) && (n1 * n2 >= ConcurrencyUtils.getThreadsBeginN_2D())) {
            ddxt2d_subth(-1, a, true);
            ddxt2d0_subth(-1, a, true);
        } else {
            ddxt2d_sub(-1, a, true);
            for (i = 0; i < n1; i++) {
                dhtn2.forward(a[i]);
            }
        }
        y_transform(a);
    }

    /**
     * Computes 2D real, inverse DHT leaving the result in <code>a</code>. The
     * data is stored in 1D array in row-major order.
     * 
     * @param a
     *            data to transform
     * @param scale
     *            if true then scaling is performed
     */
    public void inverse(double[] a, boolean scale) {
        int nthread, n, nw, nc, i;

        n = n1;
        if (n < n2) {
            n = n2;
        }
        nw = ip[0];
        if (n > (nw << 2)) {
            nw = n >> 2;
            makewt(nw);
        }
        nc = ip[1];
        if (n > nc) {
            nc = n;
            makect(nc, w, nw);
        }
        nthread = ConcurrencyUtils.getNumberOfProcessors();
        if (nthread != oldNthread) {
            nt = 4 * nthread * n1;
            if (n2 == 2 * nthread) {
                nt >>= 1;
            } else if (n2 < 2 * nthread) {
                nt >>= 2;
            }
            t = new double[nt];
            oldNthread = nthread;
        }
        if ((nthread > 1) && (n1 * n2 >= ConcurrencyUtils.getThreadsBeginN_2D())) {
            ddxt2d_subth(1, a, scale);
            ddxt2d0_subth(1, a, scale);
        } else {
            ddxt2d_sub(1, a, scale);
            for (i = 0; i < n1; i++) {
                dhtn2.inverse(a, i * n2, scale);
            }
        }
        y_transform(a);
    }

    /**
     * Computes 2D real, inverse DHT leaving the result in <code>a</code>. The
     * data is stored in 2D array.
     * 
     * @param a
     *            data to transform
     * @param scale
     *            if true then scaling is performed
     */
    public void inverse(double[][] a, boolean scale) {
        int nthread, n, nw, nc, i;

        n = n1;
        if (n < n2) {
            n = n2;
        }
        nw = ip[0];
        if (n > (nw << 2)) {
            nw = n >> 2;
            makewt(nw);
        }
        nc = ip[1];
        if (n > nc) {
            nc = n;
            makect(nc, w, nw);
        }
        nthread = ConcurrencyUtils.getNumberOfProcessors();
        if (nthread != oldNthread) {
            nt = 4 * nthread * n1;
            if (n2 == 2 * nthread) {
                nt >>= 1;
            } else if (n2 < 2 * nthread) {
                nt >>= 2;
            }
            t = new double[nt];
            oldNthread = nthread;
        }
        if ((nthread > 1) && (n1 * n2 >= ConcurrencyUtils.getThreadsBeginN_2D())) {
            ddxt2d_subth(1, a, scale);
            ddxt2d0_subth(1, a, scale);
        } else {
            ddxt2d_sub(1, a, scale);
            for (i = 0; i < n1; i++) {
                dhtn2.inverse(a[i], scale);
            }
        }
        y_transform(a);
    }

    /* -------- child routines -------- */

    private void ddxt2d_subth(final int isgn, final double[] a, final boolean scale) {
        int nthread, nt, i;

        int np = ConcurrencyUtils.getNumberOfProcessors();
        nthread = np;
        nt = 4 * n1;
        if (n2 == 2 * np) {
            nt >>= 1;
        } else if (n2 < 2 * np) {
            nthread = n2;
            nt >>= 2;
        }
        final int nthread_f = nthread;
        Future[] futures = new Future[nthread];

        for (i = 0; i < nthread; i++) {
            final int n0 = i;
            final int startt = nt * i;
            futures[i] = ConcurrencyUtils.threadPool.submit(new Runnable() {
                public void run() {
                    int i, j, idx1, idx2;
                    if (n2 > 2 * nthread_f) {
                        if (isgn == -1) {
                            for (j = 4 * n0; j < n2; j += 4 * nthread_f) {
                                for (i = 0; i < n1; i++) {
                                    idx1 = i * n2 + j;
                                    idx2 = startt + n1 + i;
                                    t[startt + i] = a[idx1];
                                    t[idx2] = a[idx1 + 1];
                                    t[idx2 + n1] = a[idx1 + 2];
                                    t[idx2 + 2 * n1] = a[idx1 + 3];
                                }
                                dhtn1.forward(t, startt);
                                dhtn1.forward(t, startt + n1);
                                dhtn1.forward(t, startt + 2 * n1);
                                dhtn1.forward(t, startt + 3 * n1);
                                for (i = 0; i < n1; i++) {
                                    idx1 = i * n2 + j;
                                    idx2 = startt + n1 + i;
                                    a[idx1] = t[startt + i];
                                    a[idx1 + 1] = t[idx2];
                                    a[idx1 + 2] = t[idx2 + n1];
                                    a[idx1 + 3] = t[idx2 + 2 * n1];
                                }
                            }
                        } else {
                            for (j = 4 * n0; j < n2; j += 4 * nthread_f) {
                                for (i = 0; i < n1; i++) {
                                    idx1 = i * n2 + j;
                                    idx2 = startt + n1 + i;
                                    t[startt + i] = a[idx1];
                                    t[idx2] = a[idx1 + 1];
                                    t[idx2 + n1] = a[idx1 + 2];
                                    t[idx2 + 2 * n1] = a[idx1 + 3];
                                }
                                dhtn1.inverse(t, startt, scale);
                                dhtn1.inverse(t, startt + n1, scale);
                                dhtn1.inverse(t, startt + 2 * n1, scale);
                                dhtn1.inverse(t, startt + 3 * n1, scale);
                                for (i = 0; i < n1; i++) {
                                    idx1 = i * n2 + j;
                                    idx2 = startt + n1 + i;
                                    a[idx1] = t[startt + i];
                                    a[idx1 + 1] = t[idx2];
                                    a[idx1 + 2] = t[idx2 + n1];
                                    a[idx1 + 3] = t[idx2 + 2 * n1];
                                }
                            }
                        }
                    } else if (n2 == 2 * nthread_f) {
                        for (i = 0; i < n1; i++) {
                            idx1 = i * n2 + 2 * n0;
                            idx2 = startt + i;
                            t[idx2] = a[idx1];
                            t[idx2 + n1] = a[idx1 + 1];
                        }
                        if (isgn == -1) {
                            dhtn1.forward(t, startt);
                            dhtn1.forward(t, startt + n1);
                        } else {
                            dhtn1.inverse(t, startt, scale);
                            dhtn1.inverse(t, startt + n1, scale);
                        }
                        for (i = 0; i < n1; i++) {
                            idx1 = i * n2 + 2 * n0;
                            idx2 = startt + i;
                            a[idx1] = t[idx2];
                            a[idx1 + 1] = t[idx2 + n1];
                        }
                    } else if (n2 == nthread_f) {
                        for (i = 0; i < n1; i++) {
                            t[startt + i] = a[i * n2 + n0];
                        }
                        if (isgn == -1) {
                            dhtn1.forward(t, startt);
                        } else {
                            dhtn1.inverse(t, startt, scale);
                        }
                        for (i = 0; i < n1; i++) {
                            a[i * n2 + n0] = t[startt + i];
                        }
                    }
                }
            });
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

    private void ddxt2d_subth(final int isgn, final double[][] a, final boolean scale) {
        int nthread, nt, i;

        int np = ConcurrencyUtils.getNumberOfProcessors();
        nthread = np;
        nt = 4 * n1;
        if (n2 == 2 * np) {
            nt >>= 1;
        } else if (n2 < 2 * np) {
            nthread = n2;
            nt >>= 2;
        }
        final int nthread_f = nthread;
        Future[] futures = new Future[nthread];

        for (i = 0; i < nthread; i++) {
            final int n0 = i;
            final int startt = nt * i;
            futures[i] = ConcurrencyUtils.threadPool.submit(new Runnable() {
                public void run() {
                    int i, j, idx2;
                    if (n2 > 2 * nthread_f) {
                        if (isgn == -1) {
                            for (j = 4 * n0; j < n2; j += 4 * nthread_f) {
                                for (i = 0; i < n1; i++) {
                                    idx2 = startt + n1 + i;
                                    t[startt + i] = a[i][j];
                                    t[idx2] = a[i][j + 1];
                                    t[idx2 + n1] = a[i][j + 2];
                                    t[idx2 + 2 * n1] = a[i][j + 3];
                                }
                                dhtn1.forward(t, startt);
                                dhtn1.forward(t, startt + n1);
                                dhtn1.forward(t, startt + 2 * n1);
                                dhtn1.forward(t, startt + 3 * n1);
                                for (i = 0; i < n1; i++) {
                                    idx2 = startt + n1 + i;
                                    a[i][j] = t[startt + i];
                                    a[i][j + 1] = t[idx2];
                                    a[i][j + 2] = t[idx2 + n1];
                                    a[i][j + 3] = t[idx2 + 2 * n1];
                                }
                            }
                        } else {
                            for (j = 4 * n0; j < n2; j += 4 * nthread_f) {
                                for (i = 0; i < n1; i++) {
                                    idx2 = startt + n1 + i;
                                    t[startt + i] = a[i][j];
                                    t[idx2] = a[i][j + 1];
                                    t[idx2 + n1] = a[i][j + 2];
                                    t[idx2 + 2 * n1] = a[i][j + 3];
                                }
                                dhtn1.inverse(t, startt, scale);
                                dhtn1.inverse(t, startt + n1, scale);
                                dhtn1.inverse(t, startt + 2 * n1, scale);
                                dhtn1.inverse(t, startt + 3 * n1, scale);
                                for (i = 0; i < n1; i++) {
                                    idx2 = startt + n1 + i;
                                    a[i][j] = t[startt + i];
                                    a[i][j + 1] = t[idx2];
                                    a[i][j + 2] = t[idx2 + n1];
                                    a[i][j + 3] = t[idx2 + 2 * n1];
                                }
                            }
                        }
                    } else if (n2 == 2 * nthread_f) {
                        for (i = 0; i < n1; i++) {
                            idx2 = startt + i;
                            t[idx2] = a[i][2 * n0];
                            t[idx2 + n1] = a[i][2 * n0 + 1];
                        }
                        if (isgn == -1) {
                            dhtn1.forward(t, startt);
                            dhtn1.forward(t, startt + n1);
                        } else {
                            dhtn1.inverse(t, startt, scale);
                            dhtn1.inverse(t, startt + n1, scale);
                        }
                        for (i = 0; i < n1; i++) {
                            idx2 = startt + i;
                            a[i][2 * n0] = t[idx2];
                            a[i][2 * n0 + 1] = t[idx2 + n1];
                        }
                    } else if (n2 == nthread_f) {
                        for (i = 0; i < n1; i++) {
                            t[startt + i] = a[i][n0];
                        }
                        if (isgn == -1) {
                            dhtn1.forward(t, startt);
                        } else {
                            dhtn1.inverse(t, startt, scale);
                        }
                        for (i = 0; i < n1; i++) {
                            a[i][n0] = t[startt + i];
                        }
                    }
                }
            });
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

    private void ddxt2d0_subth(final int isgn, final double[] a, final boolean scale) {
        final int nthread;
        int i;
        int np = ConcurrencyUtils.getNumberOfProcessors();
        if (np > n1) {
            nthread = n1;
        } else {
            nthread = np;
        }

        Future[] futures = new Future[nthread];

        for (i = 0; i < nthread; i++) {
            final int n0 = i;
            futures[i] = ConcurrencyUtils.threadPool.submit(new Runnable() {

                public void run() {
                    if (isgn == -1) {
                        for (int i = n0; i < n1; i += nthread) {
                            dhtn2.forward(a, i * n2);
                        }
                    } else {
                        for (int i = n0; i < n1; i += nthread) {
                            dhtn2.inverse(a, i * n2, scale);
                        }
                    }
                }
            });
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

    private void ddxt2d0_subth(final int isgn, final double[][] a, final boolean scale) {
        final int nthread;
        int i;
        int np = ConcurrencyUtils.getNumberOfProcessors();
        if (np > n1) {
            nthread = n1;
        } else {
            nthread = np;
        }

        Future[] futures = new Future[nthread];

        for (i = 0; i < nthread; i++) {
            final int n0 = i;
            futures[i] = ConcurrencyUtils.threadPool.submit(new Runnable() {

                public void run() {
                    if (isgn == -1) {
                        for (int i = n0; i < n1; i += nthread) {
                            dhtn2.forward(a[i]);
                        }
                    } else {
                        for (int i = n0; i < n1; i += nthread) {
                            dhtn2.inverse(a[i], scale);
                        }
                    }
                }
            });
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

    private void ddxt2d_sub(int isgn, double[] a, boolean scale) {
        int i, j, idx1, idx2;

        if (n2 > 2) {
            if (isgn == -1) {
                for (j = 0; j < n2; j += 4) {
                    for (i = 0; i < n1; i++) {
                        idx1 = i * n2 + j;
                        idx2 = n1 + i;
                        t[i] = a[idx1];
                        t[idx2] = a[idx1 + 1];
                        t[idx2 + n1] = a[idx1 + 2];
                        t[idx2 + 2 * n1] = a[idx1 + 3];
                    }
                    dhtn1.forward(t, 0);
                    dhtn1.forward(t, n1);
                    dhtn1.forward(t, 2 * n1);
                    dhtn1.forward(t, 3 * n1);
                    for (i = 0; i < n1; i++) {
                        idx1 = i * n2 + j;
                        idx2 = n1 + i;
                        a[idx1] = t[i];
                        a[idx1 + 1] = t[idx2];
                        a[idx1 + 2] = t[idx2 + n1];
                        a[idx1 + 3] = t[idx2 + 2 * n1];
                    }
                }
            } else {
                for (j = 0; j < n2; j += 4) {
                    for (i = 0; i < n1; i++) {
                        idx1 = i * n2 + j;
                        idx2 = n1 + i;
                        t[i] = a[idx1];
                        t[idx2] = a[idx1 + 1];
                        t[idx2 + n1] = a[idx1 + 2];
                        t[idx2 + 2 * n1] = a[idx1 + 3];
                    }
                    dhtn1.inverse(t, 0, scale);
                    dhtn1.inverse(t, n1, scale);
                    dhtn1.inverse(t, 2 * n1, scale);
                    dhtn1.inverse(t, 3 * n1, scale);
                    for (i = 0; i < n1; i++) {
                        idx1 = i * n2 + j;
                        idx2 = n1 + i;
                        a[idx1] = t[i];
                        a[idx1 + 1] = t[idx2];
                        a[idx1 + 2] = t[idx2 + n1];
                        a[idx1 + 3] = t[idx2 + 2 * n1];
                    }
                }
            }
        } else if (n2 == 2) {
            for (i = 0; i < n1; i++) {
                idx1 = i * n2;
                t[i] = a[idx1];
                t[n1 + i] = a[idx1 + 1];
            }
            if (isgn == -1) {
                dhtn1.forward(t, 0);
                dhtn1.forward(t, n1);
            } else {
                dhtn1.inverse(t, 0, scale);
                dhtn1.inverse(t, n1, scale);
            }
            for (i = 0; i < n1; i++) {
                idx1 = i * n2;
                a[idx1] = t[i];
                a[idx1 + 1] = t[n1 + i];
            }
        }
    }

    private void ddxt2d_sub(int isgn, double[][] a, boolean scale) {
        int i, j, idx2;

        if (n2 > 2) {
            if (isgn == -1) {
                for (j = 0; j < n2; j += 4) {
                    for (i = 0; i < n1; i++) {
                        idx2 = n1 + i;
                        t[i] = a[i][j];
                        t[idx2] = a[i][j + 1];
                        t[idx2 + n1] = a[i][j + 2];
                        t[idx2 + 2 * n1] = a[i][j + 3];
                    }
                    dhtn1.forward(t, 0);
                    dhtn1.forward(t, n1);
                    dhtn1.forward(t, 2 * n1);
                    dhtn1.forward(t, 3 * n1);
                    for (i = 0; i < n1; i++) {
                        idx2 = n1 + i;
                        a[i][j] = t[i];
                        a[i][j + 1] = t[idx2];
                        a[i][j + 2] = t[idx2 + n1];
                        a[i][j + 3] = t[idx2 + 2 * n1];
                    }
                }
            } else {
                for (j = 0; j < n2; j += 4) {
                    for (i = 0; i < n1; i++) {
                        idx2 = n1 + i;
                        t[i] = a[i][j];
                        t[idx2] = a[i][j + 1];
                        t[idx2 + n1] = a[i][j + 2];
                        t[idx2 + 2 * n1] = a[i][j + 3];
                    }
                    dhtn1.inverse(t, 0, scale);
                    dhtn1.inverse(t, n1, scale);
                    dhtn1.inverse(t, 2 * n1, scale);
                    dhtn1.inverse(t, 3 * n1, scale);
                    for (i = 0; i < n1; i++) {
                        idx2 = n1 + i;
                        a[i][j] = t[i];
                        a[i][j + 1] = t[idx2];
                        a[i][j + 2] = t[idx2 + n1];
                        a[i][j + 3] = t[idx2 + 2 * n1];
                    }
                }
            }
        } else if (n2 == 2) {
            for (i = 0; i < n1; i++) {
                t[i] = a[i][0];
                t[n1 + i] = a[i][1];
            }
            if (isgn == -1) {
                dhtn1.forward(t, 0);
                dhtn1.forward(t, n1);
            } else {
                dhtn1.inverse(t, 0, scale);
                dhtn1.inverse(t, n1, scale);
            }
            for (i = 0; i < n1; i++) {
                a[i][0] = t[i];
                a[i][1] = t[n1 + i];
            }
        }
    }

    /* -------- initializing routines -------- */

    private void makewt(int nw) {
        int j, nwh, nw0, nw1;
        double delta, wn4r, wk1r, wk1i, wk3r, wk3i;

        ip[0] = nw;
        ip[1] = 1;
        if (nw > 2) {
            nwh = nw >> 1;
            delta = Math.atan(1.0) / nwh;
            wn4r = Math.cos(delta * nwh);
            w[0] = 1;
            w[1] = wn4r;
            if (nwh == 4) {
                w[2] = Math.cos(delta * 2);
                w[3] = Math.sin(delta * 2);
            } else if (nwh > 4) {
                makeipt(nw);
                w[2] = 0.5 / Math.cos(delta * 2);
                w[3] = 0.5 / Math.cos(delta * 6);
                for (j = 4; j < nwh; j += 4) {
                    w[j] = Math.cos(delta * j);
                    w[j + 1] = Math.sin(delta * j);
                    w[j + 2] = Math.cos(3 * delta * j);
                    w[j + 3] = -Math.sin(3 * delta * j);
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
                    w[nw1 + 2] = 0.5 / wk1r;
                    w[nw1 + 3] = 0.5 / wk3r;
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

    private void makect(int nc, double[] c, int startc) {
        int j, nch;
        double delta;

        ip[1] = nc;
        if (nc > 1) {
            nch = nc >> 1;
            delta = Math.atan(1.0) / nch;
            c[startc] = Math.cos(delta * nch);
            c[startc + nch] = 0.5 * c[startc];
            for (j = 1; j < nch; j++) {
                c[startc + j] = 0.5 * Math.cos(delta * j);
                c[startc + nc - j] = 0.5 * Math.sin(delta * j);
            }
        }
    }

    private void y_transform(double[] a) {
        int mRow, mCol;
        double A, B, C, D, E;
        for (int row = 0; row <= n1 / 2; row++) {
            for (int col = 0; col <= n2 / 2; col++) {
                mRow = (n1 - row) % n1;
                mCol = (n2 - col) % n2;
                A = a[row * n2 + col];
                B = a[mRow * n2 + col];
                C = a[row * n2 + mCol];
                D = a[mRow * n2 + mCol];
                E = ((A + D) - (B + C)) / 2;
                a[row * n2 + col] = A - E;
                a[mRow * n2 + col] = B + E;
                a[row * n2 + mCol] = C + E;
                a[mRow * n2 + mCol] = D - E;
            }
        }
    }

    private void y_transform(double[][] a) {
        int mRow, mCol;
        double A, B, C, D, E;
        for (int row = 0; row <= n1 / 2; row++) {
            for (int col = 0; col <= n2 / 2; col++) {
                mRow = (n1 - row) % n1;
                mCol = (n2 - col) % n2;
                A = a[row][col];
                B = a[mRow][col];
                C = a[row][mCol];
                D = a[mRow][mCol];
                E = ((A + D) - (B + C)) / 2;
                a[row][col] = A - E;
                a[mRow][col] = B + E;
                a[row][mCol] = C + E;
                a[mRow][mCol] = D - E;
            }
        }
    }

}
