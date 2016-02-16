/*  Copyright (c) 2015, Graeme Ball and Micron Oxford,
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
import ij.plugin.*;
import ij.process.*;
import ij.gui.GenericDialog;

import net.imagej.ImageJ;
import org.scijava.Context;

import net.imglib2.img.Img;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.type.numeric.integer.UnsignedShortType;

/** This plugin converts a SIM image to a pseudo-wide-field image by averaging
 * phases and angles. Assumes OMX V2 CPZAT input channel order.
 *
 * Note: this plugin has a totally unrelated functionality in that also
 * allows a max projection, which is used by the Rec_ModContrastMap to assess
 * whether *any* angle is saturated in the raw data (should probably be
 * refactored!)
 *
 * @author Graeme Ball <graemeball@gmail.com>
 **/
public class Util_SItoPseudoWidefield implements PlugIn {

    public static final String name = "Raw SI to Pseudo-Widefield";
    public static final String TLA = "PWF";

    // parameter fields
    public int phases = 5;
    public int angles = 3;
    public boolean doNormalize = true;

    private static ImagePlus projImg = null;  // intermediate & final results
    public enum ProjMode { AVG, MAX }  // can do average or max projection
    // TODO: refactor -- AVG and MAX projection details different / unexpected

    @Override
    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        // TODO: option for padding to reconstructed result size for comparison
        GenericDialog gd = new GenericDialog(name);
        gd.addMessage("Requires SI raw data in OMX (CPZAT) order.");
        gd.addNumericField("Angles", angles, 0);
        gd.addNumericField("Phases", phases, 0);
        gd.addCheckbox("Intensity normalisation (simple ratio correction)",
                doNormalize);
        gd.showDialog();
        if (gd.wasCanceled()) return;
        if(gd.wasOKed()){
            angles = (int)gd.getNextNumber();
            phases = (int)gd.getNextNumber();
            doNormalize = gd.getNextBoolean();
        }
        if (!I1l.stackDivisibleBy(imp, phases * angles)) {
            IJ.showMessage( "SI to Pseudo-Widefield",
                    "Error: stack size not consistent with phases/angles.");
            return;
        }
        if (doNormalize) {
            projImg = exec(I1l.normalizeImp(imp), phases, angles, ProjMode.AVG);
        } else {
            projImg = exec(imp, phases, angles, ProjMode.AVG);
        }
        IJ.run("Brightness/Contrast...");
        projImg.show();
        String withRIN = "";
        if (doNormalize) {
            withRIN = " (with Ratio Intensity Normalization)";
        }
        IJ.log("Pseudo-Widefield from raw SI data" + withRIN + ".");
    }

    /** Execute plugin functionality: raw SI data to pseudo-widefield.
     * @param imp input raw SI data ImagePlus
     * @param phases number of phases
     * @param angles number of angles
     * @param m projection mode: AVG or MAX
     * @return ImagePlus with all phases and angles averaged
     */
    public ImagePlus exec(ImagePlus imp, int phases, int angles, ProjMode m) {
        ImagePlus impCopy = imp.duplicate();
        this.angles = angles;
        this.phases = phases;
        int channels = imp.getNChannels();
        int Zplanes = imp.getNSlices();
        int frames = imp.getNFrames();
        Zplanes = Zplanes/(phases*angles);  // take phase & angle out of Z
        IJ.run("Conversions...", " ");  // TODO: reset option state when done..
        new StackConverter(impCopy).convertToGray32();
        projImg = projectPandA(impCopy, channels, Zplanes, frames, m);
        // projectPandA result in projImg; Zplanes reduced by phases*angles
        if (m == ProjMode.AVG) {
            // AVG projection results 16-bit, resized x2 in XY
            new StackConverter(projImg).convertToGray16();  // TODO: was original?
            Img<UnsignedShortType> img = ImageJFunctions.wrapShort(projImg);

            double[] scaleFactors;
            if (channels > 1)
                scaleFactors = new double[] {2, 2, 1, 1};
            else
                scaleFactors = new double[] {2, 2, 1};

            final Context context = (Context) IJ.runPlugIn("org.scijava.Context", "");
            final ImageJ ijc = new ImageJ(context);
            NLinearInterpolatorFactory<UnsignedShortType> interpolation
                = new NLinearInterpolatorFactory<UnsignedShortType>();
            Img<UnsignedShortType> out = ijc.op().image().scale(img, scaleFactors,
                                                                interpolation);

            String newTitle = I1l.makeTitle(imp, TLA);
            projImg = ImageJFunctions.wrap(out, newTitle).duplicate();

            I1l.copyCal(imp, projImg);
            Calibration cal = projImg.getCalibration();
            cal.pixelWidth /= 2;
            cal.pixelHeight /= 2;
            projImg.hide();
        } else {
            // MAX proj used in MCM needs 32-bit result, *NOT* resized x2
            I1l.copyCal(imp, projImg);
        }
        if (channels > 1) {
            CompositeImage ci = new CompositeImage(projImg);
            ci.setMode(IJ.GRAYSCALE);
            return (ImagePlus)ci;
        } else {
            return projImg;
        }
    }

    /** Projection e.g. 5 phases, 3 angles for each CZT. **/
    private ImagePlus projectPandA(ImagePlus imp, int nc, int nz, int nt, ProjMode m) {
        ImageStack stack = imp.getStack();
        ImageStack PAset = new ImageStack(imp.getWidth(), imp.getHeight());
        ImageStack avStack = new ImageStack(imp.getWidth(), imp.getHeight());
        int sliceIn = 0;
        int sliceOut = 0;
        // loop through in desired (PA)CZT output order, and project slices when we have a PA set
        int PAsetSize = phases * angles;
        IJ.showStatus("Averaging over phases and angles");
        for (int t = 1; t <= nt; t++) {
            for (int z = 1; z <= nz; z++) {
                IJ.showProgress(z, nz);
                for (int c = 1; c <= nc; c++) {
                    for (int a = 1; a <= angles; a++) {
                        for (int p = 1; p <= phases; p++) {
                            sliceIn = (t - 1) * (nc * phases * nz * angles);
                            sliceIn += (a - 1) * (nc * phases * nz);
                            sliceIn += (z - 1) * (nc * phases);
                            sliceIn += (p - 1) * nc;
                            sliceIn += c;
                            sliceOut++;
                            ImageProcessor ip = stack.getProcessor(sliceIn);
                            PAset.addSlice(null, stack.getProcessor(sliceIn));
                            if ((p * a == PAsetSize)) {
                                switch (m)
                                {
                                  case AVG:
                                      ip = avSlices(imp, PAset, PAsetSize);
                                      break;
                                  case MAX:
                                      ip = maxSlices(imp, PAset, PAsetSize);
                                      break;
                                  default:
                                      ip = avSlices(imp, PAset, PAsetSize);
                                }
                                for (int slice = PAsetSize; slice >= 1; slice--) {
                                    PAset.deleteSlice(slice);
                                }
                                sliceOut++;
                                avStack.addSlice(String.valueOf(sliceOut), ip);
                            }
                        }
                    }
                }
            }
        }
        projImg = new ImagePlus("projImg", avStack);
        projImg.setDimensions(nc, nz, nt);
        int centralZ = nz / 2;
        projImg.setZ(centralZ);
        projImg.setC(1);
        projImg.setT(1);
        projImg.setOpenAsHyperStack(true);
        return projImg;
    }

    /** Return FloatProcessor with average of slices in stack. */
    private static ImageProcessor avSlices(
            ImagePlus imp, ImageStack stack, int slices) {
        int width = imp.getWidth();
        int height = imp.getHeight();
        int len = width * height;
        FloatProcessor oip = new FloatProcessor(width, height);
        float[] avpixels = (float[])oip.getPixels();
        for (int slice = 1; slice <= slices; slice++){
            FloatProcessor fp = (FloatProcessor)stack.getProcessor(slice).convertToFloat();
            float[] fpixels = (float[])fp.getPixels();
            for (int i = 0; i < len; i++) {
                avpixels[i] += fpixels[i];
            }
        }
        float fslices = (float)slices;
        for (int i = 0; i < len; i++) {
            avpixels[i] /= fslices;
        }
        oip = new FloatProcessor(width, height, avpixels, null);
        return oip;
    }

    /** Return (ImageProcessor)FloatProcessor with max of slices in stack. */
    private static ImageProcessor maxSlices(
            ImagePlus imp, ImageStack stack, int slices) {
        int width = imp.getWidth();
        int height = imp.getHeight();
        int len = width * height;
        FloatProcessor fpOut = new FloatProcessor(width, height);
        float[] maxPixels = null;
        for (int slice = 1; slice <= slices; slice++){
            FloatProcessor fp = (FloatProcessor)stack.getProcessor(slice).convertToFloat();
            float[] fpixels = (float[])fp.getPixels();
            if (slice == 1) {
                maxPixels = fpixels;
            }
            for (int i = 0; i < len; i++) {
                if (fpixels[i] > maxPixels[i]) {
                    maxPixels[i] = fpixels[i];
                }
            }
        }
        fpOut = new FloatProcessor(width, height, maxPixels, null);
        return fpOut;
    }

    /** Interactive test method. */
    public static void main(String[] args) {
        // TODO: automatic tests for private methods
        new ImageJ();
        TestData.raw.show();
        // interactive tests of exec (AVG and MAX projections)
        ImagePlus impTest = ij.WindowManager.getCurrentImage();
        // 1. AVG
        Util_SItoPseudoWidefield si2wf = new Util_SItoPseudoWidefield();
        ImagePlus impAvg = si2wf.exec(impTest, 5, 3, ProjMode.AVG);
        impAvg.setTitle("impAvg");
        impAvg.show();
        // 2. AVG
        ImagePlus impMax = si2wf.exec(impTest, 5, 3, ProjMode.MAX);
        impMax.setTitle("impMax");
        impMax.show();
        IJ.runPlugIn(Util_SItoPseudoWidefield.class.getName(), "");
    }
}
