/* 
 * Copyright (c) 2013, Graeme Ball and Micron Oxford,
 * University of Oxford, Department of Biochemistry.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
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
import ij.plugin.*;
import ij.process.*;
import ij.measure.*;
import ij.gui.*;

import java.awt.Color;
import java.awt.image.IndexColorModel;

/** 
 * This plugin plots FFTs of reconstructed stacks with resolution rings.
 * @author Graeme Ball <graemeball@gmail.com>
 * @see ij.plugin.FFT
 * @see ij.process.FHT
 */
public class Rec_FourierPlots implements PlugIn, Executable {

    public static final String name = "Reconstructed Data Fourier Plots";
    public static final String TLA1 = "FTL";  // Fourier Transform Lateral
    public static final String TLA2 = "FTR";  // FT Radial profile
    public static final String TLA3 = "FTO";  // FT Orthogonal (XZ)
    private ResultSet results = new ResultSet(name);
    private static final IndexColorModel fourierLUT = 
            I1l.loadLut("SIMcheck/SIMcheckFourier.lut");
    
    // parameter fields
    public double[] resolutions = {0.10, 0.12, 0.15, 0.2, 0.3, 0.6};
    public double blurRadius = 6.0d;  // default for 512x512
    public double winFraction = 0.06d;  // window function size, 0-1
    // TODO: refactor & remove default winFraction from here
    
    // options
    public boolean manualCutoff = false;  // manual noise cut-off?
    public boolean noCutoff = false;  // no noise cut-off?
    public boolean applyWinFunc = true;  // apply window function?
    public boolean autoScale = true;  // re-scale FFT to mode->max?
    public boolean showAxial = true;  // show axial FFT?
    public boolean blurAndLUT = false;  // blur & apply false color LUT?
    
    private double[] channelMinima = null;
    
    @Override
    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        GenericDialog gd = new GenericDialog(name);
        imp.getWidth();
        gd.addCheckbox("Manual noise cut-off?", manualCutoff);
        gd.addCheckbox("No noise cut-off?", noCutoff);
        gd.addCheckbox("Window function**", applyWinFunc);
        gd.addCheckbox("Auto-scale FFT (mode-max)", autoScale);
        gd.addCheckbox("Show axial FFT", showAxial);
        gd.addCheckbox("Blur & false-color LUT", blurAndLUT);
        gd.addMessage("** suppress edge artifacts");
        gd.showDialog();
        if (gd.wasOKed()) {
            this.manualCutoff = gd.getNextBoolean();
            this.noCutoff = gd.getNextBoolean();
            this.applyWinFunc = gd.getNextBoolean();
            this.autoScale = gd.getNextBoolean();
            this.showAxial = gd.getNextBoolean();
            this.blurAndLUT = gd.getNextBoolean();
            if (!applyWinFunc) {
                winFraction = 0.0d;
            }
            if (manualCutoff) {
                this.channelMinima = new double[imp.getNChannels()];
                SIMcheck_.specifyBackgrounds(
                        channelMinima, "Set noise cut-off:");
            }
	        results = exec(imp);
	        results.report();
        }
    }

    /** 
     * Execute plugin functionality: for reconstructed and ortho-resliced,
     * 2D FFT each slice and draw resolution rings (optional blur+LUT).
     * @param imps reconstructed data ImagePlus should be first imp
     * @return ResultSet containing FFT imp, ortho FFT imp, radial profile plot 
     */
    public ResultSet exec(ImagePlus... imps) {
        // TODO: check we have micron calibrations before continuing..
        Calibration cal = imps[0].getCalibration();
        ImagePlus imp2 = null;
        if (noCutoff) {
            imp2 = imps[0].duplicate();
            IJ.run("Conversions...", " ");
            IJ.run(imp2, "16-bit", "");
        } else if (manualCutoff) {
            imp2 = Util_RescaleTo16bit.exec(imps[0].duplicate(), channelMinima);
        } else {
            imp2 = Util_RescaleTo16bit.exec(imps[0].duplicate());
        }
        IJ.showStatus("Fourier transforming z-sections (lateral view)");
        ImagePlus impF = FFT2D.fftImp(imp2, winFraction);
        blurRadius *= (double)impF.getWidth() / 512.0d;
        IJ.showStatus("Blurring & rescaling z-sections (lateral view)");
        autoscaleSlices(impF);
        impF = gaussBlur(impF);
        if (imps[0].isComposite()) {
            impF = new CompositeImage(impF);
        }
        rescaleToStackMax(impF);
        setLUT(impF);
        impF = overlayResRings(impF, cal);
        I1l.copyStackDims(imps[0], impF);
        impF.setTitle(I1l.makeTitle(imps[0], TLA1));
        results.addImp("Fourier Transform Lateral (XY), showing resolution" +
                " rings (in Microns)", impF);
        // radial profile of lateral FFT
        ImagePlus radialProfiles = makeRadialProfiles(impF);
        radialProfiles.setTitle(I1l.makeTitle(imps[0], TLA2));
        results.addImp("Fourier Transform Radial profile "
                + "(lateral, central Z)", radialProfiles);
        /// for orthogonal (axial) view, reslice first
        if (showAxial) {
            new StackConverter(imp2).convertToGray32();  // for OrthoReslicer
            OrthoReslicer orthoReslicer = new OrthoReslicer();
            ImagePlus impOrtho = imp2.duplicate();
            impOrtho = orthoReslicer.exec(impOrtho, false);
            impOrtho = I1l.takeCentralZ(impOrtho);
            Calibration calOrtho = impOrtho.getCalibration();
            IJ.showStatus("Fourier transforming z-sections (orthogonal view)");
            ImagePlus impOrthoF = FFT2D.fftImp(impOrtho, winFraction);
            IJ.showStatus("Blurring & rescaling z-sections (orthogonal view)");
            autoscaleSlices(impOrthoF);
            impOrthoF = resizeAndPad(impOrthoF, cal);
            impOrthoF = gaussBlur(impOrthoF);
            // TODO, for multi-frame images, ensure impOrthoF is composite
            rescaleToStackMax(impOrthoF);
            setLUT(impOrthoF);
            calOrtho.pixelHeight = calOrtho.pixelWidth;  // after resizeAndPad
            impOrthoF = overlayResRings(impOrthoF, calOrtho);
            I1l.copyStackDims(imps[0], impOrthoF);
            impOrthoF.setPosition(1, impF.getNSlices() / 2, 1);
            impOrthoF.setTitle(I1l.makeTitle(imps[0], TLA3));
            results.addImp("Fourier Transform Orthogonal (XZ)", impOrthoF);
        }
        impF.setPosition(1, impF.getNSlices() / 2, 1);
        results.addInfo("How to interpret", 
            " Fourier plots highlight potential artifacts and indicate"
            + "effective resolution:"
            + "  - spots in Fourier spectrum indicate periodic patterns"
            + "  - flat Fourier spectrum (plateau in radial profile) indicates"
            + " lack of real high frequency signal and poor resolution"
            + "  - asymmetric FFT indicates angle-specific decrease in"
            + " resolution due to: angle-to-angle intensity variations,"
            + " angle-specific illumination pattern ('k0') fit error, or"
            + " angle-specific z-modulation issues");
        results.addInfo("About the Fourier plots",
                "By default reconstructed data cropped to mode; "
                + " window function applied to reduce edge artifacts prior"
                + "to FFT; FFT slices are normalized (mode-max); and target"
                + "rings (overlay) are added to translate frequency to"
                + " spatial resolution. Optionally results may be blurred"
                + " and a color Look-Up Table applied to highlight slope /"
                + " flatness of Fourier spectrum.");
        return results;
    }
    
    /** Gaussian-blur result, or not, based on blurAndLUT option field. */
    private ImagePlus gaussBlur(ImagePlus imp) {
        if (blurAndLUT) {
            imp = I1l.gaussBlur2D(imp, blurRadius);
        }
        return imp;
    }
    
    /** Rescale 8-bit imp 0-255 for input min or mode (autoscale) to max. */
    private void autoscaleSlices(ImagePlus imp) {
        int ns = imp.getStackSize();
        for (int s = 1; s <= ns; s++) {
            imp.setSlice(s);
            ImageProcessor ip = imp.getProcessor();
            ImageStatistics stats = imp.getProcessor().getStatistics();
            int min = (int)stats.min;
            if (autoScale) {
                min = (int)stats.mode;
            }
            int max = (int)stats.max;
            ByteProcessor bp = (ByteProcessor)imp.getProcessor();
            ip = (ImageProcessor)I1l.setBPminMax(bp, min, max, 255);
            imp.setProcessor(ip);
        }
    }
    
    /** Rescale 8-bit image to fill up to max 255. */
    private void rescaleToStackMax(ImagePlus imp) {
        int nc = imp.getNChannels();
        ImagePlus[] imps = new ImagePlus[nc];
        for (int c = 0; c < nc; c++) {
            imps[c] = I1l.copyChannel(imp, c + 1);
            StackStatistics stats = new StackStatistics(imps[c]);
            int ns = imps[c].getStackSize();
            for (int s = 1; s <= ns; s++) {
                imps[c].setSlice(s);
                ByteProcessor bp = (ByteProcessor)imps[c].getProcessor();
                bp = I1l.setBPminMax(bp, 0, (int)stats.max, 255);
                imps[c].setProcessor((ImageProcessor)bp);
            }
        }
        imp.setStack(I1l.mergeChannels("impsMerged", imps).getStack());
    }
    
    /** Use color LUT, or not, according to blurAndLUT option field. */
    private void setLUT(ImagePlus imp) {
        if (blurAndLUT) {
            double[] displayRange = {0.0d, 255.0d};  // show all
            I1l.applyLUT(imp, fourierLUT, displayRange);
        } else {
            if (imp.isComposite()) {
                CompositeImage ci = (CompositeImage)imp;
                ci.setMode(IJ.GRAYSCALE);
            }
        }
    }

    /** Resize imp for same y pixel size as in cal & pad square with 0s. */
    private ImagePlus resizeAndPad(ImagePlus imp, Calibration cal) {
        int width = imp.getHeight();
        int height = imp.getHeight();
        int depth = imp.getNSlices();
        double rescaleFactor = cal.pixelHeight / cal.pixelDepth;
        int rescaledHeight = (int)((double)height * rescaleFactor);
        IJ.run(imp, "Scale...", 
                "x=1.0 y=" + rescaleFactor 
                + " z=1.0 width=" + width 
                + " height=" + rescaledHeight
                + " depth=" + depth + " interpolation=Bilinear"
                + " average process create title=impOrthoResized");
        ImagePlus imp2 = IJ.getImage();  // TODO: refactor
        imp2.hide();
        int slices = imp2.getStackSize();
        ImageStack stack = imp2.getStack();
        ImageStack padStack = new ImageStack(width, height, 
                imp.getStackSize());
        for (int s = 1; s <= slices; s++) {
            ImageProcessor ip = stack.getProcessor(s);
            int insertStart = width * (((height - rescaledHeight) / 2) + 1);
            int insertEnd = insertStart + width * rescaledHeight;
            ImageProcessor pip = new ByteProcessor(width, height);  // to pad
            byte[] pix = (byte[])((ByteProcessor)ip).getPixels();
            byte[] padpix = new byte[width * height];
            for (int i = insertStart; i < insertEnd; i++) {
                padpix[i] = pix[i - insertStart];
            }
            pip.setPixels(padpix);
            padStack.setProcessor(pip, s);
        }
        imp2.setStack(padStack);
        I1l.copyCal(imp, imp2);
        imp2.setProperty("FHT", imp.getProperty("FHT"));
        return imp2;
    }
    
    /** Make radial profile plot for each channel at central Z. */
    private ImagePlus makeRadialProfiles(ImagePlus imp) {
        int nc = imp.getNChannels();
        int nz = imp.getNSlices();
        ImagePlus[] profiles = new ImagePlus[nc];
        Radial_Profile radialProfiler = new Radial_Profile();
        for (int c = 1; c <= nc; c++) {
            imp.setPosition(c, nz / 2, 1);
            ImageProcessor ip = imp.getProcessor();
            ImagePlus impC = new ImagePlus("impC" + c, ip);
            impC.setProperty("FHT", "F");  // tell radialProfiler it's Fourier
            I1l.copyCal(imp, impC);
            profiles[c - 1] = radialProfiler.exec(impC);
        }
        ImagePlus impProfiles = I1l.mergeChannels(
                "radial profiles", profiles);
        return impProfiles;
    }
    
    /** 
     * Overlay resolution rings on each slice of a Fourier ImagePlus. 
     *  NB. raw (non-FFT) imp is required for original calibrations.
     */
    private ImagePlus overlayResRings(ImagePlus Fimp, Calibration cal) {
        String unit = cal.getUnit();
        if (!(unit.startsWith(""+IJ.micronSymbol) || unit.startsWith("u") 
                || unit.startsWith("micro"))) {
            IJ.log("  ! warning - non-micron calibration (" + unit
                    + ") - cannot plot resolutions");
        } else {
            int width = Fimp.getWidth();
            int height = Fimp.getHeight();
            double pixWidth = cal.pixelWidth;
            double pixHeight = cal.pixelHeight;
            int fontSize = Fimp.getProcessor().getFont().getSize();
            Overlay resOverlay = new Overlay();
            for (int ring = 0; ring < resolutions.length; ring++) {
                // res = pixel size * width/radius (i.e. 2cycles)
                double dresX = ((double) width * pixWidth 
                		/ resolutions[ring]);
                int resX = (int) dresX;
                double dresY = ((double) height * pixHeight 
                		/ resolutions[ring]);
                int resY = (int) dresY;
                // NB. referring to *display* X & Y - could be e.g. true Z
                int ovalW = resX * 2;
                int ovalH = resY * 2;
                int topLeftX = width / 2 + 1 - ovalW / 2;
                int topLeftY = height / 2 + 1 - ovalH / 2;
                OvalRoi currentOval = new OvalRoi(topLeftX, topLeftY, 
                        ovalW, ovalH);
                currentOval.setStrokeColor(Color.WHITE);
                currentOval.setStrokeWidth(1.0);
                resOverlay.add(currentOval);
                TextRoi currentRes = new TextRoi(
                        (int) (width / 2 - fontSize),
                        (int) (height / 2 - 2 * (-0.5 + (ring + 1) % 2)
                                * (ovalH / 2) - fontSize),
                        Double.toString(resolutions[ring]));
                currentRes.setStrokeColor(Color.WHITE);
                resOverlay.add(currentRes);
            }
            Fimp.setOverlay(resOverlay);
        }
        return Fimp;
    }
    
    /** main method for testing */
    public static void main(String[] args) {
        new ImageJ();
        ImagePlus impTest = TestData.recon;
        impTest.show();
        OrthoReslicer orthoReslicer = new OrthoReslicer();
        ImagePlus impOrtho = orthoReslicer.exec(impTest, false);
        impOrtho.show();
        IJ.log("impOrtho height/width after reslice: " + impOrtho.getHeight() 
        		+ "/" + impOrtho.getWidth());
        ImagePlus impOrthoF = FFT2D.fftImp(impOrtho.duplicate());
        impOrthoF.show();
        int width = impOrtho.getWidth();
        int height = impOrtho.getHeight();
        ImageStack windowedStack = new ImageStack(width, height);
        int paddedSize = FFT2D.calcPadSize(impOrtho);
        if (paddedSize != width || paddedSize != height) {
            width = paddedSize;
            height = paddedSize;
        }
        ImageStack paddedStack = new ImageStack(width, height);
        for (int s = 1; s <= impOrtho.getStackSize(); s++) {
            ImageProcessor ip = impOrtho.getStack().getProcessor(s);
            ImageProcessor wp = FFT2D.gaussWindow(ip.duplicate(), 0.125d);
            windowedStack.addSlice(wp);
            ImageProcessor pp = FFT2D.pad(wp.duplicate(), paddedSize); 
            paddedStack.addSlice(pp);
        }
        ImagePlus impWindowed = new ImagePlus();
        impWindowed.setStack(windowedStack);
        impWindowed.setTitle("windowed");
        impWindowed.show();
        ImagePlus impPadded = new ImagePlus();
        impPadded.setStack(paddedStack);
        impPadded.setTitle("padded");
        impPadded.show();
        IJ.selectWindow(impTest.getID());
        IJ.runPlugIn(Rec_FourierPlots.class.getName(), "");
    }
}
