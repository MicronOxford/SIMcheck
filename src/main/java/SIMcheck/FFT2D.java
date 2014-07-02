/*
 *  Copyright (c) 2013, Graeme Ball,
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
import ij.measure.Calibration;
import ij.process.*;
import ij.gui.Roi;
import ij.gui.OvalRoi;
import ij.plugin.filter.GaussianBlur;

/** Improved FFT extends the ImageJ 2D FHT class, adding extra methods for:
 * calculation of phase image, suppression of low frequencies.
 * @author Graeme Ball <graemeball@gmail.com>
 */
public class FFT2D extends FHT {
    
    private static final double ERROR = 0.000001d;  // tolerance for == 0.0d

    public FFT2D(ImageProcessor ip){
        super(ip);
    }
    
    /** Return real component image of Fourier transform. */
    public ImageProcessor getComplexReal() {
        ImageStack complexFourier = super.getComplexTransform();
        return complexFourier.getProcessor(1);
    }

    /** Return imaginary component image of Fourier transform. */
    public ImageProcessor getComplexImag() {
        ImageStack complexFourier = super.getComplexTransform();
        return complexFourier.getProcessor(2);
    }

    /** Return absolute value image of Fourier transform (no log scaling). */
    public ImageProcessor getComplexAbs() {
        ImageStack complexFourier = super.getComplexTransform();
        FloatProcessor fpReal =
            (FloatProcessor)complexFourier.getProcessor(1).convertToFloat();
        float[] realPix = (float[])fpReal.getPixels();
        FloatProcessor fpImag =
            (FloatProcessor)complexFourier.getProcessor(2).convertToFloat();
        float[] imagPix = (float[])fpImag.getPixels();
        float[] absPix = new float[realPix.length];
        for(int i=0; i<realPix.length; i++) {
            absPix[i] = (float)Math.sqrt((double)(realPix[i] * realPix[i] + 
                                            imagPix[i] * imagPix[i]));
        }
        int w = fpReal.getWidth();
        int h = fpReal.getHeight();
        FloatProcessor absFp = new FloatProcessor(w, h, absPix);
        return (ImageProcessor)absFp;
    }

    /** Return phase image of Fourier transform (radians). */
    public ImageProcessor getComplexPhase() {
        ImageStack complexFourier = super.getComplexTransform();
        FloatProcessor fpReal =
            (FloatProcessor)complexFourier.getProcessor(1).convertToFloat();
        float[] realPix = (float[])fpReal.getPixels();
        FloatProcessor fpImag =
            (FloatProcessor)complexFourier.getProcessor(2).convertToFloat();
        float[] imagPix = (float[])fpImag.getPixels();
        float[] phasePix = new float[realPix.length];
        for (int i=0; i<realPix.length; i++) {
            phasePix[i] = calcPhase(realPix[i], imagPix[i]);
        }
        int w = fpReal.getWidth();
        int h = fpReal.getHeight();
        FloatProcessor phaseFp = new FloatProcessor(w, h, phasePix);
        return (ImageProcessor)phaseFp;
    }

    /** 
     * Pad a slice to a square image of size equal to the nearest
     * power of 2. Copied from ImageJ's FFT.java. 
     */
    public static ImageProcessor pad(ImageProcessor ip, int padSize) {
        ImageProcessor ip2 = ip.createProcessor(padSize, padSize);
        ip2.setValue(0);
        ip2.fill();
        ip2.insert(ip, 0, 0);
        Undo.reset();
        return ip2;
    }

    /** 
     * Apply Guassian window function to suppress high-freq tiling artifacts.
     * @param ip for slice to process 
     * @param pc percentile (0 - 1) of image width / height for window
     */
    public static ImageProcessor gaussWindow(
            ImageProcessor ip, double pc) {
        int nx = ip.getWidth();
        int ny = ip.getHeight();
        int winx = (int)(pc * (double)nx);
        int winy = (int)(pc * (double)ny);
        double gaussWidthX =  0.25d * winx;
        double gaussWidthY =  0.25d * winy;
        FloatProcessor fp = (FloatProcessor)ip.duplicate().convertToFloat();
        FloatProcessor winIp = (FloatProcessor)fp.duplicate();
        float[] winPix = (float[])winIp.getPixels();
        int npix = winPix.length;
        for (int i = 0; i < npix; i++) {
            int x = i % nx;
            int y = i / nx;
            if (x < winx || x >= nx - winx || y < winy || y >= ny - winy) {
                winPix[i] = 0.0f;
            } else {
                winPix[i] = 1.0f;
            }
        }
        ImagePlus winImp = new ImagePlus("gaussWin", winIp);
        GaussianBlur gblur = new GaussianBlur();
        gblur.showProgress(false);
        gblur.blurFloat(winIp, gaussWidthX, gaussWidthY, 0.002);
        FloatProcessor winFp = (FloatProcessor)winImp.getProcessor();
        float[] wini = (float[])winFp.getPixels();
        float[] fpix = (float[])fp.getPixels();
        for (int i = 0; i < npix; i++) {
            float pixi = fpix[i] * wini[i];
            fpix[i] = pixi;
        }
        return (ImageProcessor)fp;
    }

    /** FFT a slice, returning new fht object. **/
    public static FHT fftSlice(ImageProcessor ip, ImagePlus imp) {
        FHT fht = new FHT(ip);
        fht.originalWidth = imp.getWidth();
        fht.originalHeight = imp.getHeight();
        fht.originalBitDepth = imp.getBitDepth();
        fht.setShowProgress(false);
        fht.transform();
        return fht;
    }

    /** 
     * 2D FFT the entire hyperstack for this imp (in the XY plane).
     * @return new ImagePlus after 2D FFT
     **/
    public static ImagePlus fftImp(ImagePlus impIn) {
        return fftImp(impIn, 0.01d);
    }
    
    /**
     * 2D FFT hyperstack, specify window function size as input size fraction.
     */
    public static ImagePlus fftImp(ImagePlus impIn, double winFraction) {
        ImagePlus imp = impIn.duplicate();
        Calibration cal = impIn.getCalibration();
        int width = imp.getWidth();
        int height = imp.getHeight();
        int channels = imp.getNChannels();
        int Zplanes = imp.getNSlices();
        int frames = imp.getNFrames();
        int currentSlice = imp.getSlice();
        ImageStack stack = imp.getStack();
        int slices = stack.getSize();
        int paddedSize = calcPadSize(imp);  // padding requirement
        if (paddedSize != width || paddedSize != height) {
            width = paddedSize;
            height = paddedSize;
            cal.pixelWidth *= (double)width / paddedSize;
            cal.pixelHeight *= (double)height / paddedSize;
        }
        imp.setCalibration(cal);
        ImageStack stackF = new ImageStack(width, height);
        double progress = 0;
        FHT fht = null;
        for (int slice = 1; slice <= slices; slice++) {
            // calculate FFT / power spectrum
            // NB: ImagePlus has code to deal with power spectrum display.
            //     FFT.java stores FHT transform result *and* original image.
            // See: ij/plugin/FFT.java & ij/ImagePlus.java (search FHT & FFT)
            //      ij/process/FHT.java
            ImageProcessor ip = stack.getProcessor(slice);
            if (Math.abs(winFraction) > ERROR) {
                ip = FFT2D.gaussWindow(ip, winFraction);
            }
            ip = FFT2D.pad(ip, paddedSize);  
            fht = FFT2D.fftSlice(ip, imp);
            ImageProcessor ps = fht.getPowerSpectrum();
            stackF.addSlice(String.valueOf(slice), ps);  // FFT power spectrum
            progress += (double) slice / (double) slices;
            IJ.showProgress(progress); 
        }
        String title = "FFT2D_" + impIn.getTitle();
        ImagePlus impF = new ImagePlus(title, stackF);
        impF.copyScale(imp);
        impF.setProperty("FHT", fht);
        impF.setDimensions(channels, Zplanes, frames);
        impF.setSlice(currentSlice);
        impF.setOpenAsHyperStack(true);
        return impF;
    }
    
    

    /** 
     * Calculate the padded width and height for an ImagePlus to be
     * Fourier-transformed. Copied from ImageJ's FFT.java 
     */
    public static int calcPadSize(ImagePlus imp) {
        int originalWidth = imp.getWidth();
        int originalHeight = imp.getHeight();
        int size = Math.max(originalWidth, originalHeight);
        int padSize = 2;
        while (padSize < size) 
            padSize *= 2;
        return padSize;
    }

    /** 
     * Calculate the padded width and height for an ImagePlus to be
     * Fourier-transformed. Copied from ImageJ's FFT.java 
     */
    public static int calcPadSize(ImageProcessor ip) {
        int originalWidth = ip.getWidth();
        int originalHeight = ip.getHeight();
        int size = Math.max(originalWidth, originalHeight);
        int padSize = 2;
        while (padSize < size) 
            padSize *= 2;
        return padSize;
    }

    /** Filter low/offset frequencies from the Fourier transform result. */
    public static ImageProcessor filterLow(FloatProcessor fp,
                                    double centralRadius,
                                    double lineHalfWidth) {
        int width = fp.getWidth();
        int height = fp.getHeight();
        int lineHW = (int)(lineHalfWidth*(double)width);
        FloatProcessor mask = new FloatProcessor(width, height);
        mask.setColor(1);
        mask.fill();  // set image to 1 then mask regions using 0-1 below
        mask.setColor(0);
        if (lineHalfWidth > 0) {
            // blank out the vertical stripe
            Roi verticalStripe = new Roi((double)(width/2 - lineHW), (double)0, 
                                            (double)2*lineHW, (double)(height-1));
            mask.draw(verticalStripe);
            mask.fill(verticalStripe);
            // blank out the horizontal stripe
            Roi horizontalStripe = new Roi((double)0, (double)(height/2 - lineHW),
                                            (double)(width-1), (double)2*lineHW);
            mask.draw(horizontalStripe);
            mask.fill(horizontalStripe);
        }
        // suppress low freq / zero order
        int blobRad = (int)(centralRadius*(double)width);
        OvalRoi centralBlob = new OvalRoi((double)(width/2 - blobRad),
                                            (double)(height/2 - blobRad),
                                            (double)(blobRad*2),
                                            (double)(blobRad*2));
        mask.draw((Roi)centralBlob);
        mask.fill((Roi)centralBlob);
        GaussianBlur smudge = new GaussianBlur();
        smudge.blurFloat((FloatProcessor)mask, 3.0, 3.0, 0.01);
        // multiply input fp with smoothed mask
        float[] fpPix = (float[])fp.getPixels();
        float[] maskPix = (float[])mask.getPixels();
        for (int i=0; i<fpPix.length; i++){
            fp.setf(i, fpPix[i] * maskPix[i]);
        }
        return (ImageProcessor)fp;
    }

    /** Calculate phase using real + imaginary components. **/
    static float calcPhase(float re, float im) {
        if (re > 0) {
            return (float)Math.atan((double)im/re);
        }else if ((re < 0) && (im >= 0)) {
            return (float)(Math.atan((double)im/re) + Math.PI);
        }else if ((re < 0) && (im < 0)) {
            return (float)(Math.atan((double)im/re) - Math.PI);
        }else if ((re == 0) && (im > 0)) {
            return (float)Math.PI/2;
        }else if ((re == 0) && (im < 0)) {
            return (float)-Math.PI/2;
        }else{
            return (float)0;
        }
    }
    
    /** Test method. */
    public static void main(String[] args) {
        System.out.println("Testing FFT2D.java");
        // create x-gradient test image
        int nx = 300;
        int ny = 200;
        FloatProcessor fp = new FloatProcessor(nx, ny);
        float[] pixels = new float[nx * ny];
        int xpos = 0;
        float imMax = 32000.0f;
        for (int p = 0; p < pixels.length; p++) {
            pixels[p] = imMax * (float) xpos / nx;
            xpos++;
            if (xpos == nx) {
                xpos = 0;
            }
        }
        fp.setPixels(pixels);
        ImagePlus impRaw = new ImagePlus("gradient_raw", fp);
        impRaw.show();
        FloatProcessor fp2 = new FloatProcessor(nx, ny);
        fp2 = (FloatProcessor) FFT2D.gaussWindow(fp.duplicate(), 0.125d);
        ImagePlus impWinFunc = new ImagePlus("gradient_win", fp2);
        impWinFunc.show();
        FloatProcessor fp3 = new FloatProcessor(nx, ny);
        fp3 = (FloatProcessor)FFT2D.pad(fp2.duplicate(), FFT2D.calcPadSize(impWinFunc));
        ImagePlus impPadWin = new ImagePlus("gradient_win_pad", fp3);
        impPadWin.show();
        ImagePlus wfTest = IJ.openImage("/Users/graemeb/Documents/InTray/SIMcheck/CURRENT_EXAMPLE_FILES/V3_Blaze_medium_DAPI_mismatch_w5_SIR_C3-WF_TEST.tif");
        FloatProcessor fpWFtest = (FloatProcessor)wfTest.getProcessor();
        fpWFtest = (FloatProcessor)FFT2D.gaussWindow(fpWFtest.duplicate(), 0.125d);
        ImagePlus impWFtestResult = new ImagePlus("WindowFunctionTest", fpWFtest);
        impWFtestResult.copyScale(wfTest);
        impWFtestResult.show();
        FFT2D.fftImp(impWFtestResult).show();
    }
}
