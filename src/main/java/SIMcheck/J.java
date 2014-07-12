/*  
 *  Copyright (c) 2013, Graeme Ball.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see http://www.gnu.org/licenses/ .
 */

package SIMcheck;

/** 
 * J is a class containing static utility methods for java (i.e. methods
 * that would be nice to have built into the language and standard libs).
 * @author Graeme Ball <graemeball@gmail.com>
 */
public class J {
    
    private static final double APPROX_EQ_TOL = 0.05d;  // i.e. 5% tolerance
    
    /** Utility class should not be instantiated. */
    private J() {}
    
    /** Return true if d1 and d2 equal within APPROX_EQ_TOL. */
    public static boolean approxEq(double d1, double d2) {
        return Math.abs((d1 - d2) * 2 / (d1 + d2)) < APPROX_EQ_TOL;
    }

}
