/* ***** BEGIN LICENSE BLOCK *****
 * 
 * CSparse: a Concise Sparse matrix package.
 * Copyright (c) 2006, Timothy A. Savis.
 * http://www.cise.ufl.edu/research/sparse/CSparse
 *
 * -------------------------------------------------------------------------
 * 
 * CSparseJ is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1f of the License, or (at your option) any later version.
 *
 * CSparseJ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public
 * License along with this Module; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA
 *
 * ***** ENS LICENSE BLOCK ***** */

package edu.emory.mathcs.csparsej.tfloat;

import edu.emory.mathcs.csparsej.tfloat.Scs_common.Scs;

/**
 * Find nonzero pattern of x=L\b for sparse L and b.
 * 
 * @author Piotr Wendykier (piotr.wendykier@gmail.com)
 * 
 */
public class Scs_reach {

    /**
     * Finds a nonzero pattern of x=L\b for sparse L and b.
     * 
     * @param G
     *            graph to search (G.p modified, then restored)
     * @param B
     *            right hand side, b = B(:,k)
     * @param k
     *            use kth column of B
     * @param xi
     *            size 2*n, output in xi[top..n-1]
     * @param pinv
     *            mapping of rows to columns of G, ignored if null
     * @return top, -1 on error
     */
    public static int cs_reach(Scs G, Scs B, int k, int[] xi, int[] pinv) {
        int p, n, top, Bp[], Bi[], Gp[];
        if (!Scs_util.CS_CSC(G) || !Scs_util.CS_CSC(B) || xi == null)
            return (-1); /* check inputs */
        n = G.n;
        Bp = B.p;
        Bi = B.i;
        Gp = G.p;
        top = n;
        for (p = Bp[k]; p < Bp[k + 1]; p++) {
            if (!Scs_util.CS_MARKES(Gp, Bi[p])) /* start a dfs at unmarked node i */
            {
                top = Scs_dfs.cs_dfs(Bi[p], G, top, xi, 0, xi, n, pinv, 0);
            }
        }
        for (p = top; p < n; p++)
            Scs_util.CS_MARK(Gp, xi[p]); /* restore G */
        return (top);
    }

}
