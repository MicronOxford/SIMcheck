/*  
 *  Copyright (c) 2013, Graeme Ball and Micron Oxford,                          
 *  University of Oxford, Department of Biochemistry.
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

import ij.IJ;

/** 1D Discrete Fourier Transform for multithreaded processing. NB: no
 * log scaling, lowest freq at point 0, symmetric about point (n-1)/2 based on:
 * <a href="http://nayuki.eigenstate.org/res/how-to-implement-the-discrete-fourier-transform/Dft.java"> Dft.java</a>,
 *  Nayuki Minase.
 */
public class DFT1D implements Runnable {
    // TODO: change the runnable to a separate inner class?

    private float[][] inPix;   // input array of [vector][pixel]
    private float[][] outPix;  // output array of [vector][pixel]
    private int pstart = 0;    // first pixel of half-open range to process
    private int pend = 0;      // last + 1 pixel of half-open range to process
    private int vlen = 0;      // length of data vector to be transformed
    private double[] cosco;
    private double[] sinco;

    /** Constructor calculates and caches Fourier coefficients for speed-up. */
    DFT1D(int vlen) {
        this.vlen = vlen;
        this.cosco = new double[vlen * vlen];
        this.sinco = new double[vlen * vlen];
        for (int k = 0; k < vlen; k++) { // For each output element
            for (int t = 0; t < vlen; t++) { // For each input element
                cosco[t * k] = Math.cos(2 * Math.PI * t * k / vlen);
                sinco[t * k] = Math.sin(2 * Math.PI * t * k / vlen);
            }
        }
    }

    /** Set input and output 2D arrays, and pixel range to process. */
    void setData(final float[][] pin, int pstart, int pend, float[][] pout) {
        this.inPix = pin;
        this.outPix = pout;
        this.pstart = pstart;
        this.pend = pend;
    }
    
    /** Run 1D DFT - NOT NORMALLY USED, but called by dftOuter method. */
    @Override
    public void run() {
        double[] re = new double[vlen];
        for (int p = pstart; p < pend; p++) {  
            for (int i = 0; i < vlen; i++) {  // i is input vector, p is pixel
                re[i] = (double)inPix[i][p]; 
            }
            double[] im = new double[vlen];
            double[] dftRe = new double[vlen];
            double[] dftIm = new double[vlen];
            // do DFT
            for (int j = 0; j < vlen; j++) { // for each output element
                double sumreal = 0;
                double sumimag = 0;
                for (int i = 0; i < vlen; i++) { // for each input element
                    sumreal += re[i] * cosco[i * j] + im[i] * sinco[i * j];
                    sumimag += im[i] * cosco[i * j] - re[i] * sinco[i * j];
                }
                dftRe[j] = sumreal;
                dftIm[j] = sumimag;
            }
            // return power spectrum (NB. no logarithmic scaling)
            for (int j = 0; j < vlen; j++) {
                outPix[j][p] = (float)Math.sqrt(
                        Math.pow(dftRe[j], 2) + Math.pow(dftIm[j], 2));
            }
        }
    }

    /** 
     * Calculate the power spectrum using multithreaded 1D DFT, where outer 
     * dim is vector to be transformed, inner dim is intensity (XY pixels).
     * @param  vPix 2D array of floats (1st dim = vector, 2nd dim = pixels)
     * @return ftPix 2D array holding non-log-scaled power spectrum
     */
    public static float[][] dftOuter(final float[][] vPix) {
        int npix = vPix[0].length;
        int vlen = vPix.length;
        float[][] ftPix = new float[vlen][npix];
        /* Multithreading: decided against using threadpool / Executor 
         * so we can initialize DFT1D only once per thread and benefit from
         * coefficient caching. ftPix result is accessed by all threads
         * without locking or synchronization, but each thread writes to
         * a distinct piece of the final whole result.
         */
        int nthreads = Runtime.getRuntime().availableProcessors() * 2;
        int chunkSize = npix / nthreads;
        Thread[] workers = new Thread[nthreads];
        for (int t = 0; t < nthreads; t++) {
            int pstart = t * chunkSize;
            int pend = (t + 1) * chunkSize;
            if (t == nthreads - 1) {
                pend = npix;  // last thread takes up rounding error slack
            }
            DFT1D dft = new DFT1D(vlen);
            dft.setData(vPix, pstart, pend, ftPix);
            Thread worker = new Thread(dft);
            worker.setName("DFT thread " + t);
            workers[t] = worker;
            worker.start();
        }
        // make sure all threads finish before returning
        for (int t = 0; t < nthreads; t++) {
            try {
                workers[t].join();
            } catch(InterruptedException e) {
                IJ.log("DFT worker thread " + t + "was interrupted");
            }
        }
        return ftPix;
    }
}
