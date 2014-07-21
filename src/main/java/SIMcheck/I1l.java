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
import ij.plugin.ChannelSplitter;
import ij.process.*;
import ij.measure.Calibration;

import java.awt.Color;
import java.awt.Font;
import java.awt.Polygon;
import java.awt.image.*;
import java.io.IOException;
import java.io.InputStream;

/** 
 * I1l (<b>I</b>mageJ <b>1</b> <b>l</b>ibrary) is a class containing static
 * utility methods for ImageJ1. How good is your font?
 * @author Graeme Ball <graemeball@gmail.com>
 */
public final class I1l {
    
    /** Utility class should not be instantiated. */
    private I1l() {}
    
    /** 
     * Apply a LUT to an ImagePlus and set its display range for meaningful
     * interpretation.
     * @param  imp ImagePlus to which LUT will be applied
     * @param  cm a java awt IndexColorModel (the LUT)
     * @param  displayRange min and max displayed values
     */
    public static void applyLUT(ImagePlus imp, 
            IndexColorModel cm, double[] displayRange ) {
        if (imp.isComposite()) {
            CompositeImage cimp = (CompositeImage)imp;
            cimp.setMode(CompositeImage.COLOR);
            int saveC = cimp.getChannel();
            for (int c = 1; c <= cimp.getNChannels(); c++) {
                cimp.setC(c);
                cimp.setChannelColorModel(cm);
                cimp.setDisplayRange(displayRange[0], displayRange[1]);
            }
            imp.setC(saveC);
        } else {
            if (imp.getStackSize() > 1) {
                ImageStack stack = imp.getStack();
                stack.setColorModel(cm);
                imp.setStack(stack);
            } else {
                ImageProcessor ip = imp.getProcessor();
                ip.setColorModel(cm);
                imp.setProcessor(ip);
            }
            imp.setDisplayRange(displayRange[0], displayRange[1]);
        }
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

    /** Calculate angle in radians CCW from E using given x, y coords. */
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
                                                                                
    /** Collect and return an array of title Strings from all windows. */
    public static String[] collectTitles() {
        int[] wList = WindowManager.getIDList();
        String[] titles = new String[wList.length];
        for (int i = 0; i < wList.length; i++) {
            titles[i] = WindowManager.getImage(wList[i]).getTitle();
        }
        return titles;
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
    
    /** Divide two FloatProcessors of identical dimensions, fpN / fpD. */
    public static FloatProcessor div(FloatProcessor fpN, FloatProcessor fpD) {
        FloatProcessor result = (FloatProcessor)fpN.duplicate();
        float[] pixN = (float[])fpN.getPixels();
        float[] pixD = (float[])fpD.getPixels();
        result.setPixels((Object)JM.div(pixN, pixD));
        return result;
    }
    
    /** Draw size 12 white text at the top left of the image. */
    public static void drawLabel(ImagePlus imp, String text) {
        IJ.setForegroundColor(255, 255, 255);
        ImageProcessor ip = imp.getProcessor();
        ip.setFont(new Font("SansSerif", Font.PLAIN, 12));
        ip.setColor(Color.WHITE);
        ip.drawString(text, 5, 17);
        imp.setProcessor(ip);
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
    
    /** Get ImageStatistics for a single channel of an ImagePlus. */
    public static ImageStatistics getStatsForChannel(
            ImagePlus imp, int channel) {
        ImagePlus impC = I1l.copyChannel(imp, channel);
        return impC.getStatistics();
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
    
    /** Normalize fluctuations in inner 'b' dim average intensity. */
    public static float[][] normalizeInner(float[][] ab) {
        int blen = ab[0].length;
        int alen = ab.length;
        float[][] abOut = new float[alen][blen];
        float bav = 0.0f;
        for (int a = 0; a < alen; a++) {
            bav += JM.mean(ab[a]);
        }
        bav /= alen;
        for (int a = 0; a < alen; a++) {
            float scaleFactor = bav / JM.mean(ab[a]);
            for (int b = 0; b < blen; b++) {
                abOut[a][b] = (float)(ab[a][b] * scaleFactor);
            }
        }
        return abOut;
    }
    
    /** Rescale an 8-bit stack to range 0-255 for input range min to max. */
    public static void rescale8bitToMinMax(
            ImagePlus imp, double min, double max) {
        int nSlices = imp.getStackSize();
        for (int s = 1; s <= nSlices; s++) {
            imp.setSlice(s);
            ByteProcessor bp = (ByteProcessor)imp.getProcessor();
            ImageProcessor ip = 
                    (ImageProcessor)setBPminMax(bp, (int)min, (int)max, 255);
            imp.setProcessor(ip);
        }
    }
    
    /** Set ByteProcessor range min to inMax to output range 0 to outMax. */
    public static ByteProcessor setBPminMax(ByteProcessor bp, 
            int min, int inMax, int outMax) {
        if (min < 0 || inMax > 255 || outMax > 255) {
            throw new IllegalArgumentException("invalid min or max for 8-bit");
        }
        byte[] bpix = (byte[])bp.getPixels();
        int range = inMax - min; 
        for (int i = 0; i < bpix.length; i++) {
            int scaledPix = (int)bpix[i] & 0xff;
            if (scaledPix > inMax) {
                scaledPix = inMax;
            }
            scaledPix -= min;
            if (scaledPix < 0) {
                scaledPix = 0;
            }
            scaledPix = (int)(outMax * ((double)scaledPix / range));
            bpix[i] = (byte)scaledPix;
        }
        bp.setPixels(bpix);
        return bp;
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
                result = JM.cat(result, new int[] {stackSliceNo(dimPositions)});
            }
        } else {
            // start from highest dim: evaluate 1st non-closed dim encountered
            for (int a = args.length - 3; a > 0; a -= 3) {
                if (args[a + 1] < args[a + 2]) {
                    do {
                        int temp = args[a + 2];
                        args[a + 2] = args[a + 1];
                        result = JM.cat(result, sliceList(args));  // RECURSE
                        args[a + 2] = temp;
                        args[a + 1]++;
                    } while (args[a + 1] <= args[a + 2]);
                    break;  // stop when we have evaluated one non-closed dim
                }
            }
        }
        return result;   
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

    /** Subtract per-channel mode value from all slices in a hyperstack. */
    public static void subtractMode(ImagePlus imp) {
        ImagePlus[] imps = ChannelSplitter.split(imp);
        for (int c = 0; c < imps.length; c++) {
            double dmode = new StackStatistics(imps[c]).dmode;
            IJ.run(imps[c], "Subtract...", "value=" + dmode + " stack");
        }
        imp.setStack(mergeChannels(imp.getTitle(), imps).getStack());
    }
    
    /** Subtract per-slice mode from all slices in a hyperstack. */
    public static void subtractPerSliceMode(ImagePlus imp) {
        for (int s = 1; s <= imp.getStackSize(); s++) {
            ImageProcessor ip = imp.getStack().getProcessor(s);
            ImageStatistics stats = ip.getStatistics();
            double dmode = stats.dmode;
            imp.setSliceWithoutUpdate(s);
            IJ.run(imp, "Subtract...", "value=" + dmode + " slice");
        }
    }

    /** Interactive test method. */
    public static void main(String[] args) {
        System.out.println("Testing I1l.java");
        ImagePlus impRecon = IJ.openImage("src/test/resources/TestRecon.tif");
        impRecon.show();
        // test feature roi stats
        ImageStatistics rawStats = impRecon.getStatistics();
        IJ.log("raw min, mean, max = " + rawStats.min + ", " + rawStats.mean
                + ", " + rawStats.max);
        ImageStatistics roiStats = featStats(impRecon.getProcessor());
        IJ.log("roi min, mean, max = " + roiStats.min + ", " + roiStats.mean
                + ", " + roiStats.max);
        // test subtractPerSliceMode
        ImagePlus impSub = impRecon.duplicate();
        I1l.subtractPerSliceMode(impSub);
        impSub.show();
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
