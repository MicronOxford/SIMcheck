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
 *  along with this program.  If not, see http://www.gnu.org/licenses/ .         
 */

package SIMcheck;

import ij.*;
import ij.process.*;
import ij.plugin.*;
import ij.plugin.HyperStackConverter;
import ij.gui.GenericDialog;
import java.awt.image.IndexColorModel;

/** This plugin displays a modulation contrast map for raw SI data. 
 * For each channel, displaying the result with a LUT which is also shown. 
 * Started as an ImageJ implementation of Rainer Kaufmann's original idea and 
 * MATLAB code, modified to calculate Modulation Contrast-to-Noise Ratio.
 * @author Graeme Ball <graemeball@gmail.com>
 * 
 * <pre>
 * ***** A description of Rainer's algorithm *****
 *  1. bin 2x2 in XY to reduce noise
 *  2. get a user-defined noise region
 *  3. calculate modulation contrast mc - for each angle, Z, T 
 *      mc has stack/5 slices - i.e. 5 phases used for calc)
 *      for 1:k  (k is num Z used to calculate mod contrast?)
 *        mc = mcft / av noise mc
 *      where "mcft" is a function to calculate modulation contrast:-
 *        mcft = K+A / K-A
 *        K = fft result @ sideband location (location hard-coded)
 *        A = fft: abs(sideband-center) + abs(sideband+center) ?
 *  4. calculate modulation contrast mcz 
 *     - angles averaged, single Z-plane
 *  5. calculate modulation contrast mczk 
 *     - max proj of result for 2k+1 Z-planes? (angles averaged)
 *  6. show modulation contrast maps
 *  
 *  Additional info (NB. Rainer used MATLAB with the DIPimage package)-
 *      sideband location in FFT with zero freq at mid-point: 
 *         lut = [2 5 7 10 12 15 17; 1 2 3 4 5 6 7];
 *         i.e. using FFT with zero-freq at 0 and ZP = 15, 1 2 3 4 5 6 7 
 *      K = double(bft(:,:,lut(1,k)));                                                  
 *      A = double(abs(bft(:,:,lut(1,k)-lut(2,k))))
 *          + double(abs(bft(:,:,lut(1,k)+lut(2,k))));
 *      bmc = (K+A)./(K-A);
 *  
 * ***** My modifications *****
 * 
 *  I have calculated a Modulation Contrast-to-Noise Ratio (MCNR), which is: 
 *        1st order freq mod amplitude / av noise amplitude 
 *    where noise amplitude approximated to be equal to highest freq component.
 * </pre>
 */
public class Raw_ModContrast implements PlugIn, EProcessor {

    String name = "Raw Data Modulation Contrast (MCN)";
    ResultSet results = new ResultSet(name);
    private static final IndexColorModel mcnrLUT = 
            I1l.loadLut("SIMcheck/MCNR.lut");
    
    // parameter fields
    public int phases = 5;
    public int angles = 3;
    public double[] displayRange = {0.0, 24.0};
    public int zw = 1;  // combine (2*zw)+1 Z-planes for FFT (noise reduction)
    public boolean doRawFourier = false;  // raw phase Fourier instead of mcnr
    
    @Override
    public void run(String arg) {
        ImagePlus imp; 
        imp = IJ.getImage();
        GenericDialog gd = new GenericDialog("Raw Data Modulation Contrast");
        gd.addNumericField("Angles", angles, 1);
        gd.addNumericField("Phases", phases, 1);
        gd.addNumericField("Z window half-width", zw, 1);
        gd.addCheckbox("Raw Fourier (central Z)", false);
        gd.showDialog();
        if (gd.wasCanceled()) {
            return;
        } else {
            angles = (int)gd.getNextNumber();
            phases = (int)gd.getNextNumber();
            zw = (int)gd.getNextNumber();
            doRawFourier = gd.getNextBoolean();
            results = exec(imp);
            results.report();
        }
    }

    /** Execute plugin functionality: calculate modulation contrast or raw
     * Fourier transform over phases.
     * @param imps input raw SI data ImagePlus should be first imp
     * @return ResultSet containing either modulation contrast-to-noise,
     *         or raw Fourier for central Z
     */
    public ResultSet exec(ImagePlus... imps) {
        ImagePlus imp = imps[0].duplicate();
        IJ.showStatus("Calculating modulation contrast...");
        int nc = imp.getNChannels();
        int nz = imp.getNSlices();
        int nt = imp.getNFrames();
        if ((nz % (phases * angles)) != 0) {
            String message = "Input error: raw SI data stack"
                    + " must be a multiple of phases*angles\n\n";
            message += "                      got: " + Integer.toString(phases)
                    + "*" + Integer.toString(angles);
            message += ", but Nslices = " + Integer.toString(nz);
            IJ.showMessage(message);
            return null;
        } else {
            nz /= phases * angles; // take phases & angles out of nz
        }
        int width = imp.getWidth();
        int height = imp.getHeight();
        ImageStack SIMstack = imp.getImageStack(); 
        ImagePlus impResult = new ImagePlus(); 
        ImageStack resultStack = new ImageStack(width, height); 
        FloatProcessor fp = new FloatProcessor(width, height); 

        /* Calculate modulation contrast-to-noise ratio for each slice:-
         * - Fourier transform over phases within sliding Z window
         * - data are arranged in the order: CPZAT
         * - uses 2D float arrays: Z window of phases outer, linearized XY inner
         */  
        int slice = 0; // for progress bar
        for (int t = 1; t <= nt; t++) {
            for (int c = 1; c <= nc; c++) {
                ImageStack tempStackAZ = new ImageStack(width, height); 
                for (int a = 1; a <= angles; a++) {
                    int phStart = 1;
                    int phEnd = phases; 
                    if (doRawFourier) {
                        // raw Fourier transform over phases for Z window
                        int zStart = (nz / 2) + 1 - zw;
                        int zEnd = (nz / 2) + 1 + zw;
                        int vlen = phases * ((2 * zw) + 1);
                        float[][] frqPix = dftSliceWindow(vlen, 
                                nc, c, phStart, phEnd, nz, zStart, zEnd,
                                a, nt, t, SIMstack);
                        String sliceLabel = "DFT - C" + c + "/T" + t + "/A" + a;
                        tempStackAZ = I1l.arr2stack(frqPix, width, height, sliceLabel);
                    } else {
                        int vlen = phases * (zw + 1);  // vector of phases
                        int zStart = 1;
                        int zEnd = 1 + zw;
                        for (int z = 1; z <= nz; z++) {
                            String sliceLabel = "MCNR - C" + c + "/Z" + z
                                    + "/A" + a + "/T" + t;
                            IJ.showStatus(sliceLabel);
                            if (z == 1) {
                                float[][] frqPix = dftSliceWindow(vlen, 
                                        nc, c, phStart, phEnd, nz, zStart, zEnd,
                                        a, nt, t, SIMstack);
                                fp = calcModContrast(frqPix, fp);
                                tempStackAZ.addSlice(sliceLabel, fp);
                            } else if ((z > 1) && (z <= (zw + 1))) {
                                // add ZP set(s) to the end
                                vlen = vlen + phases;
                                zEnd++;
                                float[][] frqPix = dftSliceWindow(vlen, 
                                        nc, c, phStart, phEnd, nz, zStart, zEnd,
                                        a, nt, t, SIMstack);
                                fp = calcModContrast(frqPix, fp);
                                tempStackAZ.addSlice(sliceLabel, fp);
                            } else if ((z > zw + 1) && (z <= nz - zw)) {
                                // add a ZP set to end and remove 1 from start
                                zStart++;
                                zEnd++;
                                float[][] frqPix = dftSliceWindow(vlen, 
                                        nc, c, phStart, phEnd, nz, zStart, zEnd,
                                        a, nt, t, SIMstack);
                                fp = calcModContrast(frqPix, fp);
                                tempStackAZ.addSlice(sliceLabel, fp);
                            } else if (z > nz - zw) {
                                // remove ZP set from start (no more new ZP)
                                vlen = vlen - phases;
                                zStart++;
                                float[][] frqPix = dftSliceWindow(vlen, 
                                        nc, c, phStart, phEnd, nz, zStart, zEnd,
                                        a, nt, t, SIMstack);
                                fp = calcModContrast(frqPix, fp);
                                tempStackAZ.addSlice(sliceLabel, fp);
                            } else {
                                throw new RuntimeException(
                                        "MCNR calc index error.");
                            }
                            IJ.showProgress(slice, nt * nc * angles * nz);
                            slice++; 
                        }
                    }
                    if (doRawFourier) {
                        resultStack = I1l.cat(resultStack, tempStackAZ);
                    }
                }
                if (!doRawFourier) {
                    resultStack = averageAngles(tempStackAZ,
                            nz, resultStack);
                }
            }
        }
        String newTitle;
        if (doRawFourier) {
            newTitle = I1l.makeTitle(imps[0], "PFT");  
        } else {
            newTitle = I1l.makeTitle(imps[0], "MCN");  
        }
        if (doRawFourier) {
            int fourierLen = phases * ((2 * zw) + 1);
            impResult.setStack(resultStack, nc, fourierLen * angles, nt);
        } else {
            impResult.setStack(resultStack, nc, nz, nt);
        }
        impResult.setTitle(newTitle);
        HyperStackConverter shuffler = new HyperStackConverter();
        shuffler.shuffle(impResult, 2); // our ZCT -> IJ's CZT order
        if (!doRawFourier) {
            int centralZ = nz / 2;
            impResult.setZ(centralZ);
        } else {
            impResult.setZ(calcOrderPos(1, phases * zw));  // 1st order, 1st A
        }
        impResult.setC(1);
        impResult.setT(1);
        impResult.setOpenAsHyperStack(true);
        if (!doRawFourier) {
            I1l.applyLUT(impResult, mcnrLUT, displayRange);
            results.addImp("modulation contrast-to-noise ratio image", 
                    impResult);
            results.addInfo("Modulation contrast-to-noise ratio (MCNR)",
                    "color LUT display shows MCNR value,\n"
                    + "   purple is inadequate (3 or less), red is an"
                    + " acceptable value of 6+, orange is good,\n"
                    + "   yellow-white is very good-excellent).");
            for (int c = 1; c <= nc; c++) {
                ImagePlus impC = I1l.copyChannel(impResult, c);
                double featMCNR = I1l.stackFeatMean(impC);
                results.addStat("C" + c + " estimated feature MCNR = ", 
                        featMCNR);
                results.addStat("C" + c + " estimated Wiener optimum = ", 
                        estimWiener(featMCNR));
            }
        } else {
            IJ.run(impResult, "Enhance Contrast", "saturated=0.35");
            results.addImp("raw Fourier transforms of phases", 
                    impResult);
            results.addInfo("Raw Fourier transforms of phases for central Z",
                    "Z dimension now corresponds to frequency / order,\n"
                    + "  from 0 order (low freq) to highest freq,"
                    + " back to low freq; for each angle in turn.");
        }
        return results;
    }
    
    /** Estimate optimum Wiener filter setting for reconstruction using MCNR. */
    double estimWiener(double mcnr) {
        /* Wiener filter parameter inversely proportional to noise variance
         * using dataset where MCNR = 4.6 sigma^2 has optimal Wiener = 0.04
         *   x / 4.6^2  = 0.004, => x = 0.085 and Wiener = 0.085 / (MCNR^2)
         */
        return 0.170d / (mcnr * mcnr);  // 0.0085 doubled based on Wiener series
    }
    
    /** Calculate DFT for window orthogonal to an XY slice. */
    float[][] dftSliceWindow(int vlen, int nc, int c, int phStart, int phEnd, 
            int nz, int zStart, int zEnd, int a, int nt, int t, ImageStack stack) {
        int[] sliceList = I1l.sliceList(nc, c, c, phases, phStart, phEnd, 
                nz, zStart, zEnd, angles, a, a, nt, t, t);
        float[][] vp = I1l.stack2arr(stack, sliceList);
        vp = I1l.anscombe(vp);
        vp = I1l.normalizeInner(vp);
        vp = DFT1D.dftOuter(vp);
        return vp;
    }

    /** Calculate position of different orders/bands in frequency space.
     * vlen is nphases * (2*zw + 1), covering 2pi radians per nphases.
     * @return array index of order, first occurrence, counting from 0
     */
    int calcOrderPos(int order, int vlen) {
        return vlen * order / phases;
    }

    /** Calculate raw modulation contrast values for array[freq][pixels],
     * where freq is SI illumination frequency from Fourier transform of
     * phases, and pixels are from a linearized 2D XY array.
     */
    FloatProcessor calcModContrast(float[][] freqPix, FloatProcessor fp) {
        /* SI illumination 1st order Contrast-to-Noise Ratio (CNR1):- 
         * CNR12 = (k1/k0) / (N0/k0) = k1/N0 
         * where k1 is the amplitude of the 1st order modulation 
         * k0 is the amplitude of the zero order (constant) signal 
         * N0 is the amplitude of the noise in the zero order signal 
         * & using the approximation that N0 and the highest frequency 
         * amplitude are equivalent
         */
        int vlen = freqPix.length;
        int npix = freqPix[0].length;
        int noiseSlice = vlen / 2;
        int order1pos = calcOrderPos(1, vlen);
        int order2pos = calcOrderPos(2, vlen);
        float[] order1pix = new float[npix]; 
        float[] order2pix = new float[npix]; 
        System.arraycopy(freqPix[order1pos], 0, order1pix, 0, npix);
        System.arraycopy(freqPix[order2pos], 0, order2pix, 0, npix);
        float noiseStdev = (float)Math.sqrt(I1l.variance(freqPix[noiseSlice]));
        float[] modContrast = I1l.add(I1l.sq(order1pix), I1l.sq(order2pix));
        modContrast = I1l.sqrt(modContrast);
        modContrast = I1l.div(modContrast, noiseStdev);
        fp.setPixels(modContrast); 
        return fp;
    }

    /** Average mod contrast for different angles. */
    ImageStack averageAngles(ImageStack stackAZ, int nz, ImageStack avStack) {
        int width = stackAZ.getWidth();
        int height = stackAZ.getHeight();
        for (int z = 1; z <= nz; z++) {
            FloatProcessor avFp = new FloatProcessor(width, height);
            float[] newpixels = (float[]) avFp.getPixels();
            for (int a = 1; a <= angles; a++) {
                int slice = z + (nz * (a - 1));
                FloatProcessor fp = (FloatProcessor)stackAZ.getProcessor(slice);
                float[] fpixels = (float[]) fp.getPixels();
                newpixels = I1l.add(newpixels, fpixels);
            }
            newpixels = I1l.div(newpixels, angles);
            avFp.setPixels(newpixels);
            avStack.addSlice(avFp);
        }
        return avStack;
    }

    public static void main (String[] args) {
        Raw_ModContrast raw_mcnr = new Raw_ModContrast();
        System.out.println("Testing calcOrderPos()");
        System.out.println("  calcOrderPos(0, 15) -> 0? " 
                + raw_mcnr.calcOrderPos(0, 15));
        System.out.println("  calcOrderPos(1, 15) -> 3? " 
                + raw_mcnr.calcOrderPos(1, 15));
        System.out.println("  calcOrderPos(1, 10) -> 2? " 
                + raw_mcnr.calcOrderPos(1, 10));
        System.out.println("  calcOrderPos(2, 15) -> 6? " 
                + raw_mcnr.calcOrderPos(2, 15));
        System.out.println("  calcOrderPos(2, 10) -> 4? " 
                + raw_mcnr.calcOrderPos(2, 10));
    }
}
