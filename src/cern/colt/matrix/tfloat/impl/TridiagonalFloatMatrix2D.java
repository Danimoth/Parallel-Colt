/*
Copyright (C) 1999 CERN - European Organization for Nuclear Research.
Permission to use, copy, modify, distribute and sell this software and its documentation for any purpose 
is hereby granted without fee, provided that the above copyright notice appear in all copies and 
that both that copyright notice and this permission notice appear in supporting documentation. 
CERN makes no representations about the suitability of this software for any purpose. 
It is provided "as is" without expressed or implied warranty.
 */
package cern.colt.matrix.tfloat.impl;

import cern.colt.matrix.tfloat.FloatMatrix1D;
import cern.colt.matrix.tfloat.FloatMatrix2D;

/**
 * Tridiagonal 2-d matrix holding <tt>float</tt> elements. First see the <a
 * href="package-summary.html">package summary</a> and javadoc <a
 * href="package-tree.html">tree view</a> to get the broad picture.
 * <p>
 * <b>Implementation:</b> TODO.
 * 
 * @author wolfgang.hoschek@cern.ch
 * @version 0.9, 04/14/2000
 */
public class TridiagonalFloatMatrix2D extends WrapperFloatMatrix2D {
    private static final long serialVersionUID = 1L;

    /*
     * The non zero elements of the matrix: {lower, diagonal, upper}.
     */
    protected float[] values;

    /*
     * The startIndexes and number of non zeros: {lowerStart, diagonalStart,
     * upperStart, values.length, lowerNonZeros, diagonalNonZeros,
     * upperNonZeros}. lowerStart = 0 diagonalStart = lowerStart + lower.length
     * upperStart = diagonalStart + diagonal.length
     */
    protected int[] dims;

    protected static final int NONZERO = 4;

    // protected float diagonal[];
    // protected float lower[];
    // protected float upper[];

    // protected int diagonalNonZeros;
    // protected int lowerNonZeros;
    // protected int upperNonZeros;
    // protected int N;
    /**
     * Constructs a matrix with a copy of the given values. <tt>values</tt> is
     * required to have the form <tt>values[row][column]</tt> and have exactly
     * the same number of columns in every row.
     * <p>
     * The values are copied. So subsequent changes in <tt>values</tt> are not
     * reflected in the matrix, and vice-versa.
     * 
     * @param values
     *            The values to be filled into the new matrix.
     * @throws IllegalArgumentException
     *             if
     *             <tt>for any 1 &lt;= row &lt; values.length: values[row].length != values[row-1].length</tt>
     *             .
     */
    public TridiagonalFloatMatrix2D(float[][] values) {
        this(values.length, values.length == 0 ? 0 : values[0].length);
        assign(values);
    }

    /**
     * Constructs a matrix with a given number of rows and columns. All entries
     * are initially <tt>0</tt>.
     * 
     * @param rows
     *            the number of rows the matrix shall have.
     * @param columns
     *            the number of columns the matrix shall have.
     * @throws IllegalArgumentException
     *             if
     *             <tt>rows<0 || columns<0 || (float)columns*rows > Integer.MAX_VALUE</tt>
     *             .
     */
    public TridiagonalFloatMatrix2D(int rows, int columns) {
        super(null);
        setUp(rows, columns);

        int d = Math.min(rows, columns);
        int u = d - 1;
        int l = d - 1;
        if (rows > columns)
            l++;
        if (rows < columns)
            u++;

        values = new float[l + d + u]; // {lower, diagonal, upper}
        int[] dimensions = { 0, l, l + d, l + d + u, 0, 0, 0 }; // {lowerStart,
        // diagonalStart,
        // upperStart,
        // values.length,
        // lowerNonZeros,
        // diagonalNonZeros,
        // upperNonZeros}
        dims = dimensions;

        // diagonal = new float[d];
        // lower = new float[l];
        // upper = new float[u];

        // diagonalNonZeros = 0;
        // lowerNonZeros = 0;
        // upperNonZeros = 0;
    }

    /**
     * Sets all cells to the state specified by <tt>value</tt>.
     * 
     * @param value
     *            the value to be filled into the cells.
     * @return <tt>this</tt> (for convenience only).
     */
    @Override
    public FloatMatrix2D assign(float value) {
        // overriden for performance only
        if (value == 0) {
            for (int i = values.length; --i >= 0;)
                values[i] = 0;
            for (int i = dims.length; --i >= NONZERO;)
                dims[i] = 0;

            // for (int i=diagonal.length; --i >= 0; ) diagonal[i]=0;
            // for (int i=upper.length; --i >= 0; ) upper[i]=0;
            // for (int i=lower.length; --i >= 0; ) lower[i]=0;

            // diagonalNonZeros = 0;
            // lowerNonZeros = 0;
            // upperNonZeros = 0;
        } else
            super.assign(value);
        return this;
    }

    @Override
    public FloatMatrix2D assign(final cern.colt.function.tfloat.FloatFunction function) {
        if (function instanceof cern.jet.math.tfloat.FloatMult) { // x[i] = mult*x[i]
            final float alpha = ((cern.jet.math.tfloat.FloatMult) function).multiplicator;
            if (alpha == 1)
                return this;
            if (alpha == 0)
                return assign(0);
            if (alpha != alpha)
                return assign(alpha); // the funny definition of isNaN(). This
            // should better not happen.

            /*
             * float[] vals = values.elements(); for (int j=values.size(); --j >=
             * 0; ) { vals[j] *= alpha; }
             */

            forEachNonZero(new cern.colt.function.tfloat.IntIntFloatFunction() {
                public float apply(int i, int j, float value) {
                    return function.apply(value);
                }
            });
        } else {
            super.assign(function);
        }
        return this;
    }

    /**
     * Replaces all cell values of the receiver with the values of another
     * matrix. Both matrices must have the same number of rows and columns. If
     * both matrices share the same cells (as is the case if they are views
     * derived from the same matrix) and intersect in an ambiguous way, then
     * replaces <i>as if</i> using an intermediate auxiliary deep copy of
     * <tt>other</tt>.
     * 
     * @param source
     *            the source matrix to copy from (may be identical to the
     *            receiver).
     * @return <tt>this</tt> (for convenience only).
     * @throws IllegalArgumentException
     *             if
     *             <tt>columns() != source.columns() || rows() != source.rows()</tt>
     */
    @Override
    public FloatMatrix2D assign(FloatMatrix2D source) {
        // overriden for performance only
        if (source == this)
            return this; // nothing to do
        checkShape(source);

        if (source instanceof TridiagonalFloatMatrix2D) {
            // quickest
            TridiagonalFloatMatrix2D other = (TridiagonalFloatMatrix2D) source;

            System.arraycopy(other.values, 0, this.values, 0, this.values.length);
            System.arraycopy(other.dims, 0, this.dims, 0, this.dims.length);
            return this;
        }

        if (source instanceof SparseRCFloatMatrix2D || source instanceof SparseFloatMatrix2D) {
            assign(0);
            source.forEachNonZero(new cern.colt.function.tfloat.IntIntFloatFunction() {
                public float apply(int i, int j, float value) {
                    setQuick(i, j, value);
                    return value;
                }
            });
            return this;
        }

        return super.assign(source);
    }

    @Override
    public FloatMatrix2D assign(final FloatMatrix2D y, cern.colt.function.tfloat.FloatFloatFunction function) {
        checkShape(y);

        if (function instanceof cern.jet.math.tfloat.FloatPlusMultSecond) { // x[i] = x[i] +
            // alpha*y[i]
            final float alpha = ((cern.jet.math.tfloat.FloatPlusMultSecond) function).multiplicator;
            if (alpha == 0)
                return this; // nothing to do
            y.forEachNonZero(new cern.colt.function.tfloat.IntIntFloatFunction() {
                public float apply(int i, int j, float value) {
                    setQuick(i, j, getQuick(i, j) + alpha * value);
                    return value;
                }
            });
            return this;
        }

        if (function == cern.jet.math.tfloat.FloatFunctions.mult) { // x[i] = x[i] *
            // y[i]
            forEachNonZero(new cern.colt.function.tfloat.IntIntFloatFunction() {
                public float apply(int i, int j, float value) {
                    setQuick(i, j, getQuick(i, j) * y.getQuick(i, j));
                    return value;
                }
            });
            return this;
        }

        if (function == cern.jet.math.tfloat.FloatFunctions.div) { // x[i] = x[i] /
            // y[i]
            forEachNonZero(new cern.colt.function.tfloat.IntIntFloatFunction() {
                public float apply(int i, int j, float value) {
                    setQuick(i, j, getQuick(i, j) / y.getQuick(i, j));
                    return value;
                }
            });
            return this;
        }

        return super.assign(y, function);
    }

    @Override
    public FloatMatrix2D forEachNonZero(final cern.colt.function.tfloat.IntIntFloatFunction function) {
        for (int kind = 0; kind <= 2; kind++) {
            int i = 0, j = 0;
            switch (kind) {
            case 0: {
                i = 1;
            } // lower
                // case 1: { } // diagonal
            case 2: {
                j = 1;
            } // upper
            }
            int low = dims[kind];
            int high = dims[kind + 1];

            for (int k = low; k < high; k++, i++, j++) {
                float value = values[k];
                if (value != 0) {
                    float r = function.apply(i, j, value);
                    if (r != value) {
                        if (r == 0)
                            dims[kind + NONZERO]++; // one non zero more
                        values[k] = r;
                    }
                }
            }
        }
        return this;
    }

    /**
     * Returns the content of this matrix if it is a wrapper; or <tt>this</tt>
     * otherwise. Override this method in wrappers.
     */
    @Override
    protected FloatMatrix2D getContent() {
        return this;
    }

    /**
     * Returns the matrix cell value at coordinate <tt>[row,column]</tt>.
     * 
     * <p>
     * Provided with invalid parameters this method may return invalid objects
     * without throwing any exception. <b>You should only use this method when
     * you are absolutely sure that the coordinate is within bounds.</b>
     * Precondition (unchecked):
     * <tt>0 &lt;= column &lt; columns() && 0 &lt;= row &lt; rows()</tt>.
     * 
     * @param row
     *            the index of the row-coordinate.
     * @param column
     *            the index of the column-coordinate.
     * @return the value at the specified coordinate.
     */
    @Override
    public float getQuick(int row, int column) {
        int i = row;
        int j = column;

        int k = j - i + 1;
        int q = i;
        if (k == 0)
            q = j; // lower diagonal

        if (k >= 0 && k <= 2) {
            return values[dims[k] + q];
        }
        return 0;

        // int k = -1;
        // int q = 0;

        // if (i==j) { k=0; q=i; }
        // if (i==j+1) { k=1; q=j; }
        // if (i==j-1) { k=2; q=i; }

        // if (k<0) return 0;
        // return values[dims[k]+q];

        // if (i==j) return diagonal[i];
        // if (i==j+1) return lower[j];
        // if (i==j-1) return upper[i];

        // return 0;
    }

    /**
     * Construct and returns a new empty matrix <i>of the same dynamic type</i>
     * as the receiver, having the specified number of rows and columns. For
     * example, if the receiver is an instance of type
     * <tt>DenseFloatMatrix2D</tt> the new matrix must also be of type
     * <tt>DenseFloatMatrix2D</tt>, if the receiver is an instance of type
     * <tt>SparseFloatMatrix2D</tt> the new matrix must also be of type
     * <tt>SparseFloatMatrix2D</tt>, etc. In general, the new matrix should have
     * internal parametrization as similar as possible.
     * 
     * @param rows
     *            the number of rows the matrix shall have.
     * @param columns
     *            the number of columns the matrix shall have.
     * @return a new empty matrix of the same dynamic type.
     */
    @Override
    public FloatMatrix2D like(int rows, int columns) {
        return new TridiagonalFloatMatrix2D(rows, columns);
    }

    /**
     * Construct and returns a new 1-d matrix <i>of the corresponding dynamic
     * type</i>, entirelly independent of the receiver. For example, if the
     * receiver is an instance of type <tt>DenseFloatMatrix2D</tt> the new
     * matrix must be of type <tt>DenseFloatMatrix1D</tt>, if the receiver is an
     * instance of type <tt>SparseFloatMatrix2D</tt> the new matrix must be of
     * type <tt>SparseFloatMatrix1D</tt>, etc.
     * 
     * @param size
     *            the number of cells the matrix shall have.
     * @return a new matrix of the corresponding dynamic type.
     */
    @Override
    public FloatMatrix1D like1D(int size) {
        return new SparseFloatMatrix1D(size);
    }

    /**
     * Sets the matrix cell at coordinate <tt>[row,column]</tt> to the specified
     * value.
     * 
     * <p>
     * Provided with invalid parameters this method may access illegal indexes
     * without throwing any exception. <b>You should only use this method when
     * you are absolutely sure that the coordinate is within bounds.</b>
     * Precondition (unchecked):
     * <tt>0 &lt;= column &lt; columns() && 0 &lt;= row &lt; rows()</tt>.
     * 
     * @param row
     *            the index of the row-coordinate.
     * @param column
     *            the index of the column-coordinate.
     * @param value
     *            the value to be filled into the specified cell.
     */
    @Override
    public void setQuick(int row, int column, float value) {
        int i = row;
        int j = column;

        boolean isZero = (value == 0);

        int k = j - i + 1;
        int q = i;
        if (k == 0)
            q = j; // lower diagonal

        if (k >= 0 && k <= 2) {
            int index = dims[k] + q;
            if (values[index] != 0) {
                if (isZero)
                    dims[k + NONZERO]--; // one nonZero less
            } else {
                if (!isZero)
                    dims[k + NONZERO]++; // one nonZero more
            }
            values[index] = value;
            return;
        }

        if (!isZero)
            throw new IllegalArgumentException("Can't store non-zero value to non-tridiagonal coordinate: row=" + row
                    + ", column=" + column + ", value=" + value);

        // int k = -1;
        // int q = 0;

        // if (i==j) { k=0; q=i; } // diagonal
        // if (i==j+1) { k=1; q=j; } // lower diagonal
        // if (i==j-1) { k=2; q=i; } // upper diagonal

        // if (k>0) {
        // int index = dims[k]+q;
        // if (values[index]!=0) {
        // if (isZero) dims[k+NONZERO]--; // one nonZero less
        // }
        // else {
        // if (!isZero) dims[k+NONZERO]++; // one nonZero more
        // }
        // values[index] = value;
        // return;
        // }

        // if (!isZero) throw new IllegalArgumentException("Can't store non-zero
        // value to non-tridiagonal coordinate: row="+row+", column="+column+",
        // value="+value);

        // if (i==j) {
        // if (diagonal[i]!=0) {
        // if (isZero) diagonalNonZeros--;
        // }
        // else {
        // if (!isZero) diagonalNonZeros++;
        // }
        // diagonal[i] = value;
        // return;
        // }

        // if (i==j+1) {
        // if (lower[j]!=0) {
        // if (isZero) lowerNonZeros--;
        // }
        // else {
        // if (!isZero) lowerNonZeros++;
        // }
        // lower[j] = value;
        // return;
        // }

        // if (i==j-1) {
        // if (upper[i]!=0) {
        // if (isZero) upperNonZeros--;
        // }
        // else {
        // if (!isZero) upperNonZeros++;
        // }
        // upper[i] = value;
        // return;
        // }

        // if (!isZero) throw new IllegalArgumentException("Can't store non-zero
        // value to non-tridiagonal coordinate: row="+row+", column="+column+",
        // value="+value);
    }

    @Override
    public FloatMatrix1D zMult(FloatMatrix1D y, FloatMatrix1D z, float alpha, float beta, final boolean transposeA) {
        int m = rows;
        int n = columns;
        if (transposeA) {
            m = columns;
            n = rows;
        }

        boolean ignore = (z == null);
        if (z == null)
            z = new DenseFloatMatrix1D(m);

        if (!(this.isNoView && y instanceof DenseFloatMatrix1D && z instanceof DenseFloatMatrix1D)) {
            return super.zMult(y, z, alpha, beta, transposeA);
        }

        if (n != y.size() || m > z.size())
            throw new IllegalArgumentException("Incompatible args: "
                    + ((transposeA ? viewDice() : this).toStringShort()) + ", " + y.toStringShort() + ", "
                    + z.toStringShort());

        if (!ignore)
            z.assign(cern.jet.math.tfloat.FloatFunctions.mult(beta / alpha));

        DenseFloatMatrix1D zz = (DenseFloatMatrix1D) z;
        final float[] zElements = zz.elements;
        final int zStride = zz.stride();
        final int zi = (int) z.index(0);

        DenseFloatMatrix1D yy = (DenseFloatMatrix1D) y;
        final float[] yElements = yy.elements;
        final int yStride = yy.stride();
        final int yi = (int) y.index(0);

        if (yElements == null || zElements == null)
            throw new InternalError();

        forEachNonZero(new cern.colt.function.tfloat.IntIntFloatFunction() {
            public float apply(int i, int j, float value) {
                if (transposeA) {
                    int tmp = i;
                    i = j;
                    j = tmp;
                }
                zElements[zi + zStride * i] += value * yElements[yi + yStride * j];
                // z.setQuick(row,z.getQuick(row) + value * y.getQuick(column));
                // System.out.println("["+i+","+j+"]-->"+value);
                return value;
            }
        });

        if (alpha != 1)
            z.assign(cern.jet.math.tfloat.FloatFunctions.mult(alpha));
        return z;
    }

    @Override
    public FloatMatrix2D zMult(FloatMatrix2D B, FloatMatrix2D C, final float alpha, float beta,
            final boolean transposeA, boolean transposeB) {
        if (transposeB)
            B = B.viewDice();
        int m = rows;
        int n = columns;
        if (transposeA) {
            m = columns;
            n = rows;
        }
        int p = B.columns();
        boolean ignore = (C == null);
        if (C == null)
            C = new DenseFloatMatrix2D(m, p);

        if (B.rows() != n)
            throw new IllegalArgumentException("Matrix2D inner dimensions must agree:" + toStringShort() + ", "
                    + (transposeB ? B.viewDice() : B).toStringShort());
        if (C.rows() != m || C.columns() != p)
            throw new IllegalArgumentException("Incompatibel result matrix: " + toStringShort() + ", "
                    + (transposeB ? B.viewDice() : B).toStringShort() + ", " + C.toStringShort());
        if (this == C || B == C)
            throw new IllegalArgumentException("Matrices must not be identical");

        if (!ignore)
            C.assign(cern.jet.math.tfloat.FloatFunctions.mult(beta));

        // cache views
        final FloatMatrix1D[] Brows = new FloatMatrix1D[n];
        for (int i = n; --i >= 0;)
            Brows[i] = B.viewRow(i);
        final FloatMatrix1D[] Crows = new FloatMatrix1D[m];
        for (int i = m; --i >= 0;)
            Crows[i] = C.viewRow(i);

        final cern.jet.math.tfloat.FloatPlusMultSecond fun = cern.jet.math.tfloat.FloatPlusMultSecond.plusMult(0);

        forEachNonZero(new cern.colt.function.tfloat.IntIntFloatFunction() {
            public float apply(int i, int j, float value) {
                fun.multiplicator = value * alpha;
                if (!transposeA)
                    Crows[i].assign(Brows[j], fun);
                else
                    Crows[j].assign(Brows[i], fun);
                return value;
            }
        });

        return C;
    }
}
