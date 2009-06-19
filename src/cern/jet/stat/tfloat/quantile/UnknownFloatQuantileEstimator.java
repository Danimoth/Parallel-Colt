/*
Copyright (C) 1999 CERN - European Organization for Nuclear Research.
Permission to use, copy, modify, distribute and sell this software and its documentation for any purpose 
is hereby granted without fee, provided that the above copyright notice appear in all copies and 
that both that copyright notice and this permission notice appear in supporting documentation. 
CERN makes no representations about the suitability of this software for any purpose. 
It is provided "as is" without expressed or implied warranty.
 */
package cern.jet.stat.tfloat.quantile;

import cern.colt.list.tfloat.FloatArrayList;
import cern.colt.list.tobject.ObjectArrayList;
import cern.jet.random.tfloat.engine.FloatRandomEngine;
import cern.jet.random.tfloat.sampling.WeightedFloatRandomSampler;
import cern.jet.stat.Utils;

/**
 * Approximate quantile finding algorithm for unknown <tt>N</tt> requiring only
 * one pass and little main memory; computes quantiles over a sequence of
 * <tt>float</tt> elements. This algorithm requires at most two times the memory
 * of a corresponding approx. quantile finder knowing <tt>N</tt>.
 * 
 * <p>
 * Needs as input the following parameters:
 * <p>
 * <dt>1. <tt>quantiles</tt> - the number of quantiles to be computed.
 * <dt>2. <tt>epsilon</tt> - the allowed approximation error on quantiles. The
 * approximation guarantee of this algorithm is explicit.
 * 
 * <p>
 * It is also possible to couple the approximation algorithm with random
 * sampling to further reduce memory requirements. With sampling, the
 * approximation guarantees are explicit but probabilistic, i.e. they apply with
 * respect to a (user controlled) confidence parameter "delta".
 * 
 * <dt>3. <tt>delta</tt> - the probability allowed that the approximation error
 * fails to be smaller than epsilon. Set <tt>delta</tt> to zero for explicit non
 * probabilistic guarantees.
 * 
 * You usually don't instantiate quantile finders by using the constructor.
 * Instead use the factory <tt>QuantileFinderFactor</tt> to do so. It will set
 * up the right parametrization for you.
 * 
 * <p>
 * After Gurmeet Singh Manku, Sridhar Rajagopalan and Bruce G. Lindsay, Random
 * Sampling Techniques for Space Efficient Online Computation of Order
 * Statistics of Large Datasets. Accepted for Proc. of the 1999 ACM SIGMOD Int.
 * Conf. on Management of Data, Paper (soon) available <A
 * HREF="http://www-cad.eecs.berkeley.edu/~manku"> here</A>.
 * 
 * @author wolfgang.hoschek@cern.ch
 * @version 1.0, 09/24/99
 * @see FloatQuantileFinderFactory
 * @see KnownApproximateFloatQuantileFinder
 */
class UnknownFloatQuantileEstimator extends FloatQuantileEstimator {
    /**
     * 
     */
    private static final long serialVersionUID = 1L;

    protected int currentTreeHeight;

    final protected int treeHeightStartingSampling;

    protected WeightedFloatRandomSampler sampler;

    protected float precomputeEpsilon;

    /**
     * Constructs an approximate quantile finder with b buffers, each having k
     * elements.
     * 
     * @param b
     *            the number of buffers
     * @param k
     *            the number of elements per buffer
     * @param h
     *            the tree height at which sampling shall start.
     * @param precomputeEpsilon
     *            the epsilon for which quantiles shall be precomputed; set this
     *            value <=0.0 if nothing shall be precomputed.
     * @param generator
     *            a uniform random number generator.
     */
    public UnknownFloatQuantileEstimator(int b, int k, int h, float precomputeEpsilon, FloatRandomEngine generator) {
        this.sampler = new WeightedFloatRandomSampler(1, generator);
        setUp(b, k);
        this.treeHeightStartingSampling = h;
        this.precomputeEpsilon = precomputeEpsilon;
        this.clear();
    }

    /**
     * Not yet commented.
     */
    @Override
    protected FloatBuffer[] buffersToCollapse() {
        FloatBuffer[] fullBuffers = bufferSet._getFullOrPartialBuffers();

        sortAscendingByLevel(fullBuffers);

        // if there is only one buffer at the lowest level, then increase its
        // level so that there are at least two at the lowest level.
        int minLevel = fullBuffers[1].level();
        if (fullBuffers[0].level() < minLevel) {
            fullBuffers[0].level(minLevel);
        }

        return bufferSet._getFullOrPartialBuffersWithLevel(minLevel);
    }

    /**
     * Removes all elements from the receiver. The receiver will be empty after
     * this call returns, and its memory requirements will be close to zero.
     */
    @Override
    public synchronized void clear() {
        super.clear();
        this.currentTreeHeight = 1;
        this.sampler.setWeight(1);
    }

    /**
     * Returns a deep copy of the receiver.
     * 
     * @return a deep copy of the receiver.
     */
    @Override
    public Object clone() {
        UnknownFloatQuantileEstimator copy = (UnknownFloatQuantileEstimator) super.clone();
        if (this.sampler != null)
            copy.sampler = (WeightedFloatRandomSampler) copy.sampler.clone();
        return copy;
    }

    /**
     * Not yet commented.
     */
    @Override
    protected void newBuffer() {
        currentBufferToFill = bufferSet._getFirstEmptyBuffer();
        if (currentBufferToFill == null)
            throw new RuntimeException("Oops, no empty buffer.");

        currentBufferToFill.level(currentTreeHeight - 1);
        currentBufferToFill.weight(sampler.getWeight());
    }

    /**
     * Not yet commented.
     */
    @Override
    protected void postCollapse(FloatBuffer[] toCollapse) {
        if (toCollapse.length == bufferSet.b()) { // delta for unknown finder
            currentTreeHeight++;
            if (currentTreeHeight >= treeHeightStartingSampling) {
                sampler.setWeight(sampler.getWeight() * 2);
            }
        }
    }

    /**
     * Computes the specified quantile elements over the values previously
     * added.
     * 
     * @param phis
     *            the quantiles for which elements are to be computed. Each phi
     *            must be in the interval (0.0,1.0]. <tt>phis</tt> must be
     *            sorted ascending.
     * @return the approximate quantile elements.
     */
    @Override
    public FloatArrayList quantileElements(FloatArrayList phis) {
        if (precomputeEpsilon <= 0.0)
            return super.quantileElements(phis);

        int quantilesToPrecompute = (int) Utils.epsilonCeiling(1.0 / precomputeEpsilon);
        /*
         * if (phis.size() > quantilesToPrecompute) { // illegal use case! // we
         * compute results, but loose explicit approximation guarantees. return
         * super.quantileElements(phis); }
         */

        // select that quantile from the precomputed set that corresponds to a
        // position closest to phi.
        phis = phis.copy();
        float e = precomputeEpsilon;
        for (int index = phis.size(); --index >= 0;) {
            float phi = phis.get(index);
            int i = (int) Math.round(((2.0 * phi / e) - 1.0) / 2.0); // finds
            // closest
            i = Math.min(quantilesToPrecompute - 1, Math.max(0, i));
            float augmentedPhi = (e / 2.0f) * (1 + 2 * i);
            phis.set(index, augmentedPhi);
        }

        return super.quantileElements(phis);
    }

    /**
     * Not yet commented.
     */
    @Override
    protected boolean sampleNextElement() {
        return sampler.sampleNextElement();
    }

    /**
     * To do. This could faster be done without sorting (min and second min).
     */
    protected static void sortAscendingByLevel(FloatBuffer[] fullBuffers) {
        new ObjectArrayList(fullBuffers).quickSortFromTo(0, fullBuffers.length - 1, new java.util.Comparator() {
            public int compare(Object o1, Object o2) {
                int l1 = ((FloatBuffer) o1).level();
                int l2 = ((FloatBuffer) o2).level();
                return l1 < l2 ? -1 : l1 == l2 ? 0 : +1;
            }
        });
    }

    /**
     * Returns a String representation of the receiver.
     */
    @Override
    public String toString() {
        StringBuffer buf = new StringBuffer(super.toString());
        buf.setLength(buf.length() - 1);
        return buf + ", h=" + currentTreeHeight + ", hStartSampling=" + treeHeightStartingSampling
                + ", precomputeEpsilon=" + precomputeEpsilon + ")";
    }
}
