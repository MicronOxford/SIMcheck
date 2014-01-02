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
import ij.*;
import ij.plugin.LutLoader;
import ij.process.*;
import ij.measure.Calibration;

import java.awt.Polygon;
import java.awt.image.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

/** 
 * I1l (<b>I</b>mageJ <b>1</b> <b>l</b>ibrary) is a class containing static
 * utility methods for ImageJ1 and java array / math. How good is your font?
 * @author Graeme Ball <graemeball@gmail.com>
 */
public final class I1l {
    
    /** Utility class should not be instantiated. */
    private I1l() {}
    
    /** 
     * Add two 1D float arrays of the same length, element by element.
     * @param f1 first input array
     * @param f2 second input array
     * @return float[] array of same length as first input
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
     * @return Anscome-transformed 2D array same shape as input
     */
    public static float[][] anscombe(float[][] ab) {
        int nb = ab[0].length;
        int na = ab.length;
        float[][] abAns = new float[na][nb];
        for (int a = 0; a < na; a++) {
            for (int b = 0; b < nb; b++){
                abAns[a][b] = (float)( 
                        (2 * Math.sqrt((double)ab[a][b])) + (3 / 8));
            }
        }
        return abAns;
    }

    /** 
     * Apply a LUT to an ImagePlus and set its display range for meaningful
     * interpretation.
     * @param  imp ImagePlus to which LUT will be applied
     * @param  cm a java awt IndexColorModel (the LUT)
     * @param  displayRange min and max displayed values
     */
    public static void applyLUT(ImagePlus imp, 
            IndexColorModel cm, double[] displayRange ) {
        if (imp.isComposite() && 
                ((CompositeImage)imp).getMode() == CompositeImage.GRAYSCALE) {
            CompositeImage cimp = (CompositeImage)imp;
            cimp.setMode(CompositeImage.COLOR);
            int saveC = cimp.getChannel();
            for (int c = 1; c <= cimp.getNChannels(); c++) {
                cimp.setC(c);
                cimp.setChannelColorModel(cm);
            }
            imp.setC(saveC);
        } else {
            ImageProcessor ip = imp.getChannelProcessor();
            if (imp.isComposite()) {
                ((CompositeImage)imp).setChannelColorModel(cm);
            } else {
                ip.setColorModel(cm);
            }
            if (imp.getStackSize()>1) {
                imp.getStack().setColorModel(cm);
            }
        }
        imp.setDisplayRange(displayRange[0], displayRange[1]);
    }

    /** 
     * Fill ImageStack passed in from the input 2D array sp, where outer dim 
     * represents slice number, inner dim is pixels (linearized XY slice).
     * The ImageStack will be filled with 32-bit floats.
     * @param sp 2D array of floats (1st dim = slice, 2nd dim = pixels)
     * @param width i.e. X extent of 2D slices
     * @param height i.e. Y extent of 2D slices
     * @param labelBase string used to prefix new slice labels
     * @return stack containing floats
     */
    public static ImageStack arr2stack(float[][] sp, int width, int height, 
            String labelBase) {
        ImageStack stack = new ImageStack(width, height);
        int slices = sp.length;
        for (int s = 0; s < slices; s++) {
            FloatProcessor fp = new FloatProcessor(width, height, sp[s]);
            String sliceLabel = labelBase + " slice " + (s + 1);
            stack.addSlice(sliceLabel, fp);
        }
        return stack;
    }

    /** Convert red, green and blue ByteProcessors to a single ColorProcessor */    
    public static ImageProcessor bytes2RGB(ByteProcessor bpRed,                                   
                                           ByteProcessor bpGreen,                                  
                                           ByteProcessor bpBlue) {                                 
        // assumes ByteProcessors all same size...                                  
        ImageStack RGBset = new ImageStack(bpRed.getWidth(), bpRed.getHeight());    
        RGBset.addSlice("R", bpRed);                                                 
        RGBset.addSlice("G", bpGreen);                                                                           
        RGBset.addSlice("B", bpBlue);                                                                  
        ImagePlus tempImp = new ImagePlus("Temp", RGBset);                               
        ImageConverter ic = new ImageConverter(tempImp);                             
        ic.convertRGBStackToRGB();                                                  
        return tempImp.getStack().getProcessor(1);                                  
    }

    /** Calculate angle in radians using given x, y coords */
    public static double calcAngle(double x, double y) {
        if (x > 0) {
            return Math.atan(y / x);
        } else if (x < 0 && y >= 0) {
            return Math.atan(y / x + Math.PI);
        } else if (x < 0 && y < 0) {
            return Math.atan(y / x - Math.PI);
        } else if (x == 0 && y > 0) {
            return Math.PI / 2;
        } else if (x == 0 && y < 0) {
            return -Math.PI / 2;
        } else {
            return 0.0d;
        }
    }
    
    /** Calculate radial position (frequency) of FFT result */
    public static double calcFourierR(int x, int y, int w, int h, 
            Calibration cal) {
        // from ImageJ's ImagePlus.getFFTLocation method
        double r = Math.sqrt((x - w / 2.0) * (x - w / 2.0) 
                + (y - h / 2.0) * (y - h / 2.0));    
        if (r < 1.0) r = 1.0;   
        r = (w / r) * cal.pixelWidth;
        return r;
    }
    
    /** Concatenate stack2 onto the end of stack 1. */
    public static ImageStack cat(ImageStack stack1, ImageStack stack2) {
        int width = stack1.getWidth();
        int height = stack1.getHeight();
        if (stack2.getWidth() != width || stack2.getHeight() != height) {
            throw new IllegalArgumentException("stack1, stack2 dim mismatch.");
        }
        // TODO, should check stack pixel type as well...
        ImageStack result = new ImageStack(width, height);
        for (int s = 1; s <= stack1.getSize(); s++) {
            result.addSlice(stack1.getProcessor(s));
        }
        for (int s = 1; s <= stack2.getSize(); s++) {
            result.addSlice(stack2.getProcessor(s));
        }
        return result;
    }                                                                           
                                                                                
    /** Concatenate arr2 to the end of arr1. */
    public static int[] cat(int[] arr1, int[] arr2) {
        int[] result = new int[arr1.length + arr2.length];
        System.arraycopy(arr1, 0, result, 0, arr1.length);
        System.arraycopy(arr2, 0, result, arr1.length, arr2.length);
        return result;
    }
        
    /** Collect and return an array of title Strings from all windows. */
    static String[] collectTitles() {
        int[] wList = WindowManager.getIDList();
        if (wList==null) {
            IJ.noImage();
            return null;
        }else{
            String[] titles = new String[wList.length];
            for (int i = 0; i < wList.length; i++) {
                titles[i] = WindowManager.getImage(wList[i]).getTitle();
            }
            return titles;
        }
    }

    /** Copy src calibrations to dest. */
    public static void copyCal(ImagePlus src, ImagePlus dest) {
        Calibration cal = src.getCalibration();
        dest.setCalibration(cal);
    }
    
    /** For channel varies fastest, return single channel of the input. */
    public static ImagePlus copyChannel(ImagePlus imp, int channel) {
        int stackSize = imp.getStackSize();
        int channels = imp.getNChannels();
        ImageStack nuStack = new ImageStack(imp.getWidth(),imp.getHeight());
        for (int slice = 1; slice <= stackSize; slice++) {
            if ((slice % channels == channel) || 
                    ((channel == channels) && (slice % channels == 0))) {
                nuStack.addSlice(imp.getStack().getProcessor(slice));
            }
        }
        String nuTitle = imp.getShortTitle() + "_Ch" + Integer.toString(channel);
        ImagePlus imp2 = new ImagePlus(nuTitle, nuStack);
        imp2.setDimensions(1, imp.getNSlices(), imp.getNFrames());  // 1 channel
        return imp2;
    }

    /** Copy stack dimensions (channels & frames if hyperstack) & position. */
    public static void copyStackDims(ImagePlus src, ImagePlus dest) {
        if (src.isHyperStack()) {
            dest.setOpenAsHyperStack(true);
            int destSlices = dest.getStackSize() / 
                    (src.getNChannels() * src.getNFrames()); 
            int destCurrentSlice = src.getCurrentSlice() * 
                    (destSlices / src.getNSlices());
            dest.setDimensions(
                    src.getNChannels(), 
                    destSlices,
                    src.getNFrames());
            dest.setPosition(
                    src.getChannel(),
                    destCurrentSlice,
                    src.getFrame());   
        } else {
            int currentSlice = src.getCurrentSlice();
            dest.setSlice(currentSlice);
        }
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
    
    /** Euclidean distance */
    public static double dist(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2) / 2);
    }
    
    /** Divide each element of a float array by a float. */
    public static float[] div(float[] f, float div) {
        int len = f.length;
        float[] result = new float[len];
        for (int i = 0; i < f.length; i++) {
            result[i] = (float)f[i] / div;
        }
        return result;
    }

    /** Divide two float arrays of identical dimensions, fN / fD. */
    public static float[] div(float[] fN, float[] fD) {
        int nPix = fN.length;
        float[] result = new float[nPix];
        for (int p = 0; p < nPix; p++) {
            result[p] = fN[p] / fD[p];
        }
        return result;
    }

    /** Divide two FloatProcessors of identical dimensions, fpN / fpD. */
    public static FloatProcessor div(FloatProcessor fpN, FloatProcessor fpD) {
        FloatProcessor result = (FloatProcessor)fpN.duplicate();
        float[] pixN = (float[])fpN.getPixels();
        float[] pixD = (float[])fpD.getPixels();
        result.setPixels((Object)div(pixN, pixD));
        return result;
    }
    
    /** Convert primitive Float array to primitive Double array. */
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
    
    /** Return ImageStatistics for auto-thresholded features in this ip. */
    public static ImageStatistics featStats(ImageProcessor ip) {
        ImageProcessor maskIp = ip.duplicate();
        maskIp = maskIp.convertToByte(true);
        maskIp.setAutoThreshold("Triangle", true, ImageProcessor.RED_LUT);
        ImagePlus imp = new ImagePlus("I1l.featStats slice", maskIp);
        IJ.run(imp, "Convert to Mask", "method=Triangle background=Dark black");
        ImageProcessor maskedIp = ip.duplicate();
        maskedIp.setMask(maskIp);
        return maskedIp.getStatistics();
    }
    
    /** Filter peaks based on Fourier radial position (freq) rMin to rMax */
    public static Polygon filterPeaksRadial(Polygon peaks, Calibration cal, 
            int w, int h, double rMin, double rMax) {
        int npeaks = peaks.npoints;
        Polygon filteredPeaks = new Polygon();
        for (int p = 0; p < npeaks; p++) {
            double r = I1l.calcFourierR(peaks.xpoints[p], peaks.ypoints[p], 
                    w, h, cal);
            if (r >= rMin && r <= rMax) {
                filteredPeaks.addPoint(peaks.xpoints[p], peaks.ypoints[p]);
            }
        }
        return filteredPeaks;
    }
    
    /** Load a LUT from a file. (NB. getClass is non-static) */
    public static IndexColorModel loadLut(String LUTfile) {
        IndexColorModel cm = null;
        InputStream is = I1l.class.getClassLoader().getResourceAsStream(LUTfile);
        if (is == null) {
            IJ.log("! InputStream null for " + LUTfile);
        } else {
            try {
                cm = LutLoader.open(is);
            } catch (IOException e) {
                IJ.log("! error opening InputStream while loading LUT");
                IJ.error("" + e);
            }
            if (cm == null) {
                IJ.log("! error loading LUT - IndexColorModel is null");
            }
        }
        return cm;
    }
    
    /** 
     * Make a new title using old (short) title of the ImagePlus
     * with a specified suffix.
     * @param imp existing ImagePlus from which new title root obtained
     * @param suffix string (an additional _ will be added before suffix)
     * @return new title
     */
    public static String makeTitle(ImagePlus imp, String suffix) {
        String[] titleTokens = imp.getTitle().split("\\.");
        int ntok = titleTokens.length;
        String nuTitle = "";
        String ext = "";
        if (ntok > 1) {
            ext += titleTokens[ntok - 1];
            ntok -= 1;
        }
        for (int i = 0; i < ntok; i++) {
            nuTitle += titleTokens[i];
        }
        nuTitle = nuTitle + "_" + suffix + "." + ext;
        return WindowManager.makeUniqueName(nuTitle);
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

    /** Average an array of doubles, ignoring NaN entries */
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

    /** Find median of an array of ints */
    public static int median(int[] m) {                                             
        Arrays.sort(m);                                                         
        int middle = m.length / 2;                                              
        if (m.length % 2 == 1) {                                                
            return m[middle];                                                   
        } else {                                                                
            return (m[middle - 1] + m[middle]) / 2;                          
        }                                                                       
    }

    /** Merge identically-dimensioned single-channel images, channel fastest. */ 
    public static ImagePlus mergeChannels(String title, ImagePlus imps[]) {
        // TODO: check imps.length >= 1 and all imps identical
        int width = imps[0].getWidth();
        int height = imps[0].getHeight();
        int nc = imps.length;  // one imp per channel
        int nz = imps[0].getNSlices();
        int nt = imps[0].getNFrames();
        int slices = imps[0].getStackSize();
        ImageStack nuStack = new ImageStack(width, height);
        for (int s = 1; s <= slices; s++) {
            for (int c = 1; c <= nc; c++) {
                nuStack.addSlice(imps[c - 1].getStack().getProcessor(s));
            }
        }
        ImagePlus imp2 = new ImagePlus(title, nuStack);
        imp2.setDimensions(nc, nz, nt);
        return imp2;
    }
    
    /** Calculate the minimum of float array. */
    public static float min(float[] f) {
        float min = f[0];
        for (int i = 0; i < f.length; i++) {
            if (f[i] < min) min = f[i];
        }
        return min;
    }
    
    /** Normalize fluctuations in inner 'b' dim average intensity. */
    public static float[][] normalizeInner(float[][] ab) {
        int blen = ab[0].length;
        int alen = ab.length;
        float[][] abOut = new float[alen][blen];
        float bav = 0.0f;
        for (int a = 0; a < alen; a++) {
            bav += mean(ab[a]);
        }
        bav /= alen;
        for (int a = 0; a < alen; a++) {
            float scaleFactor = bav / mean(ab[a]);
            for (int b = 0; b < blen; b++) {
                abOut[a][b] = (float)(ab[a][b] * scaleFactor);
            }
        }
        return abOut;
    }
    
    /** Convert array to string for printing. */
    public static String prn(double[] arr) {
        String arrString = "";
        for (int i = 0; i < arr.length; i++) {
            arrString += " " + arr[i];
        }
        return arrString;
    }
    
    /** Convert array to string for printing. */
    public static String prn(float[] arr) {
        String arrString = "";
        for (int i = 0; i < arr.length; i++) {
            arrString += " " + arr[i];
        }
        return arrString;
    }
    
    
    /** Convert array to string for printing. */
    public static String prn(long[] arr) {
        String arrString = "";
        for (int i = 0; i < arr.length; i++) {
            arrString += " " + arr[i];
        }
        return arrString;
    }
    
    /** Convert array to string for printing. */
    public static String prn(int[] arr) {
        String arrString = "";
        for (int i = 0; i < arr.length; i++) {
            arrString += " " + arr[i];
        }
        return arrString;
    }
    
    /**
     * Return array of slice numbers for given ranges over N dimensions. 
     * This method is recursive, starting at highest/rightmost dimension and 
     * working left.
     * @param inArgs taking each dimension in turn, arguments required are: 
     *        dim1total, dim1start, dim1end, ... dimNtotal, dimNstart, dimNend.
     *        Order of dimensions fastest(1) to slowest(N) varying as in stack.
     *        Ranges are closed, i.e. inclusive.
     * @return list of (hyper)stack slice numbers, where dimensions are ordered
     *         as in the input args
     */
    public static int[] sliceList(int ... inArgs) {
        int[] args = inArgs.clone();  // work with local copy of input args
        // First check args are valid
        if (args.length < 3 || args.length % 3 != 0) {
            throw new IllegalArgumentException("got " + args.length + " args,"
                    + " but require arg triples: total, first, last");
        }
        for (int i = 0; i < args.length; i += 3) {
            if (args[i + 1] > args[i]) {
                throw new IllegalArgumentException(
                        "invalid slice number " + args[i + 1] + "/" + args[i]);
            }
            if (args[i + 2] > args[i]) {
                throw new IllegalArgumentException(
                        "invalid slice number " + args[i + 2] + "/" + args[i]);
            }
            if (args[i + 2] < args[i + 1]) {
                throw new IllegalArgumentException(
                        "invalid range " + args[i + 1] + "-" + args[i + 2]);
            }
        }
        int[] result = new int[0];
        boolean baseCase = true;
        for (int a = args.length - 3; a > 0; a -= 3) {
            // if any dims but the first are not closed, base case false
            if (args[a + 2] - args[a + 1] != 0) {
                baseCase = false;
            }
        }
        if (baseCase) {
            // base case - only first dim range is open - build a slice list
            for (int i = args[1]; i <= args[2]; i++) {
                // stackSliceNo() requires position, size pairs                   
                int[] dimPositions = new int[2 * args.length / 3];                
                int dimPosIndex = 0;  
                dimPositions[dimPosIndex++] = i;
                dimPositions[dimPosIndex++] = args[0];
                for (int a = 3; a < args.length; a += 3) {
                    dimPositions[dimPosIndex++] = args[a + 1];        
                    dimPositions[dimPosIndex++] = args[a];        
                }
                result = cat(result, new int[] {stackSliceNo(dimPositions)});
            }
        } else {
            // start from highest dim: evaluate 1st non-closed dim encountered
            for (int a = args.length - 3; a > 0; a -= 3) {
                if (args[a + 1] < args[a + 2]) {
                    do {
                        int temp = args[a + 2];
                        args[a + 2] = args[a + 1];
                        result = cat(result, sliceList(args));  // RECURSE
                        args[a + 2] = temp;
                        args[a + 1]++;
                    } while (args[a + 1] <= args[a + 2]);
                    break;  // stop when we have evaluated one non-closed dim
                }
            }
        }
        return result;   
    }
    
    /** Square each element of a float array. */                                
    public static float[] sq(float[] f) {                                       
        int len = f.length;                                                     
        float[] sq = new float[len];                                           
        for (int i = 0; i < len; i++) {                                         
            sq[i] = f[i] * f[i];                                               
        }                                                                       
        return sq;                                                             
    }
    
    /** Take the square root of each element of a float array. */               
    public static float[] sqrt(float[] f) {                                     
        int len = f.length;                                                     
        float[] sqrt = new float[len];                                         
        for (int i = 0; i < len; i++) {                                         
            sqrt[i] = (float)Math.sqrt(f[i]);                                  
        }                                                                       
        return sqrt;                                                           
    }

    /** 
     * Return a 2D float array with outer dim representing slice numbers,
     * inner dim pixels from a linearized 2D XY slice (32-bit float only).
     */
    public static float[][] stack2arr(ImageStack stack, int[] sliceList) {
        int width = stack.getWidth();
        int height = stack.getHeight();
        float[][] sp = new float[sliceList.length][width * height];
        for (int s = 0; s < sliceList.length; s++){
            FloatProcessor fp = 
                    (FloatProcessor)stack.getProcessor(sliceList[s]).convertToFloat();
            float[] fpixels = (float[])fp.getPixels();
            System.arraycopy(fpixels, 0, sp[s], 0, width * height);
        }
        return sp;
    }
    
    /** Check the imp stack is divisible by n (i.e. check dimensionality). */
    public static boolean stackDivisibleBy(ImagePlus imp, int n) {
        int stackSize = imp.getStackSize();
        return (stackSize != 0) && (stackSize % n == 0);
    }
    

    /** Return mean value of auto-thresholded features in impIn. */
    public static double stackFeatMean(ImagePlus impIn) {
        ImagePlus imp = impIn.duplicate();
        double stackFeatMean = 0;
        int ns = imp.getNSlices();
        for (int s = 1; s <= ns; s++) {
            imp.setSlice(s);
            ImageProcessor ip =  imp.getProcessor();
            ImageProcessor maskIp = ip.duplicate();
            maskIp = maskIp.convertToByte(true);
            maskIp.setAutoThreshold("Triangle", true, ImageProcessor.RED_LUT);
            ImagePlus tImp = new ImagePlus("I1l.featStats slice", maskIp);
            IJ.run(tImp, "Convert to Mask", "method=Triangle background=Dark black");
            ImageProcessor maskedIp = ip.duplicate();
            maskedIp.setMask(maskIp);
            stackFeatMean += maskedIp.getStatistics().mean;
            imp.setProcessor(maskedIp);
        }
        return stackFeatMean / ns;  // FIXME, account for feat pix / slice
    }

    /** 
     * Convert position / dimsize pairs into correct hyperstack slice number.
     * Arbitrary number of dimensions in order given (fastest varying first).
     * @param args dim1pos, dim1siz, dim2pos, dim2siz, ... dimNpos, dimNsiz
     * @return int sliceNo
     */
    public static int stackSliceNo(int ... args) {
        int sliceNo = 0;  // for result
        if ((args.length % 2) != 0) {
            throw new IllegalArgumentException(
                    "require dimpos, dimsiz **pairs**; "
                    + "but " + args.length + " arguments given");
        } else {
            for (int i = 0; i < args.length; i++) {
                if (i % 2 != 0 && (args[i-1] < 1 || args[i - 1] > args[i])) {
                    throw new IllegalArgumentException(
                            "position " + args[i - 1]
                            + " is outside of range 1-" + args[i]);
                }
            }
            for (int i = 0; i < args.length; i++) {
                /* e.g. for Channels, then Zplanes, then Time:-
                 * sliceNo += c;  // fastest-varying dim
                 * sliceNo += (z - 1) * nc;
                 * sliceNo += (t - 1) * (nz * nc);
                 */
                if (i % 2 == 0) {
                    int pos = args[i];
                    if (i == 0) {
                        // 1st, fastest-varying dim is simply added
                        sliceNo += pos;
                    } else {
                        // for points prior to pos, add product of all
                        // faster-varying dims
                        int addToSliceNo = pos - 1;
                        for (int j = i - 1; j > 0; j -= 2) {
                            // for dims j varying varying faster than i
                            int dimSizej = args[j]; 
                            addToSliceNo *= dimSizej;
                        }
                        sliceNo += addToSliceNo;
                    }
                }
            }
            return sliceNo;
        }
    }

    /** Subtract value from each element of a float array. */
    public static float[] sub(float[] f, float val) {
        for (int i = 0; i < f.length; i++) {
            f[i] = f[i] - val;
        }
        return f;
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
    
    /** Test method. */
    public static void main(String[] args) {
        System.out.println("Testing I1l.java");
        ImagePlus wfTest = IJ.openImage("/Users/graemeb/Documents/InTray/SIMcheck/CURRENT_EXAMPLE_FILES/V3_Blaze_medium_DAPI_mismatch_w5_SIR_C3-WF_TEST.tif");
        // test feature roi stats
        ImageStatistics rawStats = wfTest.getStatistics();
        IJ.log("raw min, mean, max = " + rawStats.min + ", " + rawStats.mean
                + ", " + rawStats.max);
        ImageStatistics roiStats = featStats(wfTest.getProcessor());
        IJ.log("roi min, mean, max = " + roiStats.min + ", " + roiStats.mean
                + ", " + roiStats.max);
    }
}

///// useful code snippets commented out below /////

/*
    // time a function fn()
    long tstart = 0;
    tstart = System.currentTimeMillis();
    fn();
    long tTotal = System.currentTimeMillis() - tstart;
    IJ.log("total time for fn: " + Long.toString(tTotal);
*/
