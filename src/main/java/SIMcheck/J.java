/*  
 *  Copyright (c) 2015, Graeme Ball.
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

import java.math.*;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;

/** 
 * A class containing static utility methods for Java.
 * @author Graeme Ball <graemeball@gmail.com>
 */
public class J {
    
    private static final double APPROX_EQ_TOL = 0.05d;  // i.e. 5% tolerance
    
    /** Utility class should not be instantiated. */
    private J() {}

    /** 
     * Add two 1D float arrays of the same length, element by element.
     * @param f1 first input array
     * @param f2 second input array
     * @return new float[] array of same length as first input
     */
    public static float[] add(float[] f1, float[] f2) {
        int len = f1.length;
        float[] result = new float[len];
        for (int i = 0; i < len; i++) {
            result[i] = f1[i] + f2[i];
        }
        return result;
    }
    
    /** 
     * Apply an Anscombe variance stabilizing transform to a 2D float array:
     * <pre>x-&gt;[2*sqrt(x)]+3/8</pre>
     * @param ab 2D array of floats 
     * @return new Anscome-transformed 2D array same shape as input
     */
    public static float[][] anscombe(float[][] ab) {
        int nb = ab[0].length;
        int na = ab.length;
        float[][] abAns = new float[na][nb];
        for (int a = 0; a < na; a++) {
            for (int b = 0; b < nb; b++){
                abAns[a][b] = (float)( 
                        (2 * Math.sqrt((double)ab[a][b])) + (3.0d / 8));
            }
        }
        return abAns;
    }

    /** Return true if d1 and d2 equal within APPROX_EQ_TOL. */
    public static boolean approxEq(double d1, double d2) {
        return Math.abs((d1 - d2) * 2 / (d1 + d2)) < APPROX_EQ_TOL;
    }

    /** Return new array of arr2 concatenated onto the end of arr1. */
    public static int[] cat(int[] arr1, int[] arr2) {
        int[] result = new int[arr1.length + arr2.length];
        System.arraycopy(arr1, 0, result, 0, arr1.length);
        System.arraycopy(arr2, 0, result, arr1.length, arr2.length);
        return result;
    }

    /** Return new array of arr2 concatenated onto the end of arr1. */
    public static String[] cat(String[] arr1, String[] arr2) {
        String[] result = new String[arr1.length + arr2.length];
        System.arraycopy(arr1, 0, result, 0, arr1.length);
        System.arraycopy(arr2, 0, result, arr1.length, arr2.length);
        return result;
    }
    
    /** Return closest even integer to input n (round down). */
    public static int closestEven(int n) {
        return n % 2 == 0 ? n : n - 1;
    }
    
    /** Convert double array to float array. */
    public static float[] d2f(double[] d) {
        if (d == null) {
            return null; // Or throw an exception?
        }
        float[] output = new float[d.length];
        for (int i = 0; i < d.length; i++) {
            output[i] = (float)d[i];
        }
        return output;
    }
    
    /** Convert double to string (2 sig figs, round up). */
    public static String d2s(double dStat) {
        BigDecimal bd = new BigDecimal(dStat);
        if (Math.abs(dStat) >= 100.0d) {
            bd = bd.setScale(0, RoundingMode.HALF_UP);
        } else {
            bd = bd.round(new MathContext(ResultSet.STAT_SIG_FIGS));
        }
        bd.stripTrailingZeros();
        return bd.toString();
    }
    
    /** Return Euclidean distance between (x1, y1) and (x2, y2). */
    public static double dist(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) / 2);
    }

    /** Divide each element of a float array by a float (return new array). */
    public static float[] div(float[] f, float div) {
        int len = f.length;
        float[] result = new float[len];
        for (int i = 0; i < f.length; i++) {
            result[i] = (float)f[i] / div;
        }
        return result;
    }
    

    /** Divide two float arrays fN, fD of identical dimensions element-wise. */
    public static float[] div(float[] fN, float[] fD) {
        int nPix = fN.length;
        float[] result = new float[nPix];
        for (int p = 0; p < nPix; p++) {
            result[p] = fN[p] / fD[p];
        }
        return result;
    }

    /** Convert primitive float array to primitive double array. */
    public static double[] f2d(float[] f) {
        if (f == null)
        {
            return null; // Or throw an exception?
        }
        double[] output = new double[f.length];
        for (int i = 0; i < f.length; i++)
        {
            output[i] = f[i];
        }
        return output;
    }

    /** Calculate the maximum of float array. */
    public static float max(float[] f) {
        float max = 0;
        for (int i = 0; i < f.length; i++) {
            if (f[i] > max) max = f[i];
        }
        return max;
    }

    /** Find index of maximum in float array. */
    public static int maxIndex(float[] f) {
        float max = f[0];
        int maxIndex = 0;
        int flen = f.length;
        for (int i = 0; i < flen; i++) {
            if (f[i] > max) {
                max = f[i];
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    /** Average an array of doubles, ignoring NaN entries. */
    public static double mean(double[] d) {
        int len = d.length;
        double total = 0.0d;
        int nNonNan = 0;  // :-)
        for (int i = 0; i < len; i++) {
            if (!Double.isNaN(d[i])) {
                total += d[i];
                nNonNan++;
            }
        }
        return total / nNonNan;
    }

    /** Calculate the mean of a float array. */
    public static float mean(float[] f) {
        float mean = 0;
        int len = f.length;
        float flen = (float)len;
        for (int i = 0; i < len; i++) {
            mean += f[i] / flen;
        }
        return mean;
    }

    /** Find median of an array of ints. */
    public static int median(int[] m) {                                             
        Arrays.sort(m);                                                         
        int middle = m.length / 2;                                              
        if (m.length % 2 == 1) {                                                
            return m[middle];                                                   
        } else {                                                                
            return (m[middle - 1] + m[middle]) / 2;                          
        }                                                                       
    }

    /** Calculate the minimum of float array. */
    public static float min(float[] f) {
        float min = f[0];
        for (int i = 0; i < f.length; i++) {
            if (f[i] < min) min = f[i];
        }
        return min;
    }

    /** Multiply each element of a float array by a float (return new arr). */
    public static float[] mult(float[] f, float factor) {
        int len = f.length;
        float[] result = new float[len];
        for (int i = 0; i < f.length; i++) {
            result[i] = (float)f[i] * factor;
        }
        return result;
    }
    
    /** Return a string containing n repeats of charString. */
    public static String nChars(int n, String charString) {
        return new String(new char[n]).replace("\0", charString);
    }
    
    /** Print object as "\n"-terminated String to standard out. */
    public static void out(Object o) {
        System.out.println(o.toString());
    }

    /** Square each element of a float array (return new array). */                                
    public static float[] sq(float[] f) {                                       
        int len = f.length;                                                     
        float[] sq = new float[len];                                           
        for (int i = 0; i < len; i++) {                                         
            sq[i] = f[i] * f[i];                                               
        }                                                                       
        return sq;                                                             
    }

    /** Take square root of each element of a float array (return new arr). */               
    public static float[] sqrt(float[] f) {                                     
        int len = f.length;                                                     
        float[] sqrt = new float[len];                                         
        for (int i = 0; i < len; i++) {                                         
            sqrt[i] = (float)Math.sqrt(f[i]);                                  
        }                                                                       
        return sqrt;                                                           
    }

    /** Return a timestamp String: yyyy/MM/dd HH:mm:ss */
    public static String timestamp() {
        Date date = Calendar.getInstance().getTime();
        return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(date);
    }

    /** Calculate the variance of a double array.  */
    public static double variance(double[] d) {
        double variance = 0;
        double mean = 0;
        for (int i = 0; i < d.length; i++) {
            mean += (double)d[i] / (double)d.length;
        }
        for (int i = 0; i < d.length; i++) {
            variance += ((d[i] - mean) * (d[i] - mean)) / (double)d.length;
        }
        return variance;
    }

    /** Calculate the variance of a float array.  */
    public static float variance(float[] f) {
        float variance = 0;
        float mean = 0;
        for (int i = 0; i < f.length; i++) {
            mean += (float)f[i] / (float)f.length;
        }
        for (int i = 0; i < f.length; i++) {
            variance += ((f[i] - mean) * (f[i] - mean)) / (float)f.length;
        }
        return variance;
    }

}
