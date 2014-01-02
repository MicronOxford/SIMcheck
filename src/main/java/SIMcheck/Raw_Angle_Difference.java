/*  Copyright (c) 2013, Graeme Ball and Micron Oxford,
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
import ij.plugin.*;
import ij.process.*;
import ij.gui.GenericDialog;

/** This plugin converts raw SI data into pseudo-wide-field stack with each
 * angle normalized, false-colored (Cyan=A1, Magenta=A2, Yellow=A3), and
 * combined into a single slice. False-coloring shows up differences between
 * angles, which may be due to drift, "floaties" (small objects floating within
 * the sample), or illumination differences between angles. In the absence of 
 * differences, images ought to appear white/gray!
 * @author Graeme Ball <graemeball@gmail.com>
 */
public class Raw_Angle_Difference implements PlugIn, EProcessor {

    String name = "Raw Data Motion Check";
    ResultSet results = new ResultSet(name);

    // parameter fields
    public int phases = 5;
    public int angles = 3;

    @Override
    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        GenericDialog gd = new GenericDialog(name);
        gd.addMessage("Requires SI raw data in API OMX (CPZAT) order.");
        gd.addNumericField("Angles", angles, 1);
        gd.addNumericField("Phases", phases, 1);
        gd.showDialog();
        if (gd.wasCanceled()) return;
        if (gd.wasOKed()) {
            // update angles and phases according to numeric fields
            angles = (int)gd.getNextNumber();
            phases = (int)gd.getNextNumber();
        }
        if(!I1l.stackDivisibleBy(imp, phases * angles)){
            IJ.showMessage( "Raw Data Motion Check",
                    "Error: stack size not consistent with phases/angles.");
            return;
        }
        results = exec(imp);
        results.report();
    }

    /** Execute plugin functionality: false-color angles to show disparities.
     * @param imps raw SI data ImagePlus should be first imp
     * @return ResultSet containing merge of false-colored angles
     */
    public ResultSet exec(ImagePlus... imps) {
        ImagePlus imp = imps[0];
        IJ.showStatus("Raw data motion check...");
        if (angles != 3) {
            IJ.showMessage("Raw Data Motion Check",
                    "This plugin only works for 3 angles currently.");
        } else {
            int nc = imp.getNChannels();
            int nz = imp.getNSlices();
            int nt = imp.getNFrames();
            nz = nz / (phases * angles);  // take phase & angle out of Z

            // factors to normalize angles to highest intensity
            double[][] normFactors = new double[3][3];
            // total intensities for each [angle][channel] (max 3x3)
            double[][] totalIntens = new double[3][3];

            ImagePlus projImp = averagePhase(imp, nc, nz, nt, totalIntens);
            calcNormalizationFactors(imp, nc, angles, totalIntens, normFactors);
            ImagePlus colorImp = colorAngles(imp, projImp, nc, nz, normFactors);
            results.addImp("false-colored angles (C, M, Y)", colorImp);
            results.addInfo("False-colored angles",
                    "phases averaged, angles normalized, colored Cyan,"
                    + " Magenta, Yellow for angles 1, 2 & 3 \n"
                    + "  (non-white indicates differences between angles due"
                    + " to drift, floating particles or uneven illumination");
        }
        return results;
    }

    /** Arrange and run average projections of the 5 phases each CZAT. **/
    ImagePlus averagePhase(ImagePlus imp, int nc, int nz, int nt,
            double[][] intens) {
        ImageStack stack = imp.getStack();
        ImageStack Pset = new ImageStack(imp.getWidth(), imp.getHeight());
        ImageStack avStack = new ImageStack(imp.getWidth(), imp.getHeight());

        int sliceIn = 0;
        int sliceOut = 0;
        int PsetSize = phases;
        // loop in desired (P)ACZT output order, projecting each Phase set
        IJ.showStatus("Averaging over phases");
        for (int frame = 1; frame <= nt; frame++) {
            for (int Zplane = 1; Zplane <= nz; Zplane++) {
                IJ.showProgress(Zplane, nz);
                for (int channel = 1; channel <= nc; channel++) {
                    for (int angle = 1; angle <= angles; angle++) {
                        for (int phase = 1; phase <= phases; phase++) {
                            sliceIn = (frame - 1) * (nc * phases * nz * angles);
                            sliceIn += (angle - 1) * (nc * phases * nz);
                            sliceIn += (Zplane - 1) * (nc * phases);
                            sliceIn += (phase - 1) * nc;
                            sliceIn += channel;
                            ImageProcessor ip = stack.getProcessor(sliceIn);
                            Pset.addSlice(null, stack.getProcessor(sliceIn));
                            if (phase == PsetSize) {
                                ip = averageSlices(imp, Pset, PsetSize);
                                for (int slice = PsetSize; slice >= 1; slice--) {
                                    Pset.deleteSlice(slice);
                                }
                                sliceOut++;
                                avStack.addSlice(String.valueOf(sliceOut),ip);
                                FloatStatistics stats = new FloatStatistics(ip);
                                intens[angle - 1][channel - 1] += stats.umean;
                            }
                        }
                    }
                }
            }
        }
        ImagePlus projImp = new ImagePlus(I1l.makeTitle(imp, "PPJ"), avStack);
        projImp.setDimensions(nc * angles, nz, nt);
        return projImp;
    }

    /** Average a set of slices (mean projection). **/
    ImageProcessor averageSlices(ImagePlus imp, ImageStack stack, int nslices) {
        int width = imp.getWidth();
        int height = imp.getHeight();
        int npix = width * height;  // per slice
        float[] avpix;
        FloatProcessor oip = new FloatProcessor(width, height);
        avpix = (float[])oip.getPixels();
        for (int s = 1; s <= nslices; s++) {
            FloatProcessor fp = (FloatProcessor)stack.getProcessor(s).convertToFloat();
            float[] fpixels = (float[])fp.getPixels();
            for (int i = 0; i < npix; i++) {
                avpix[i] += fpixels[i];
            }
        }
        for (int i = 0; i < npix; i++){
            avpix[i] /= (float)nslices;
        }
        oip = new FloatProcessor(width, height, avpix, null);
        return oip;
    }


    /** Calculate factors to normalize to highest intensity in angle group. **/
    void calcNormalizationFactors(ImagePlus imp, int nc, int na,
            double[][] intens, double[][] normFactor) {
        for(int c = 0; c < nc; c++){
            double maxIntensity = 0;
            for (int a = 0; a < na; a++){
                if (intens[a][c] > maxIntensity){
                    maxIntensity = intens[a][c];
                }
            }
            for (int a = 0; a < na; a++){
                normFactor[a][c] = maxIntensity / intens[a][c];
            }
        }
    }


    /** Create RGB stacks colored Cyan, Magenta, Yellow for Angles 1, 2, 3 **/
    ImagePlus colorAngles(ImagePlus imp, ImagePlus projImp, int nc, int nz,
            double[][] normFactor) {
        int nt = projImp.getNFrames();  // after phase removal
        // average phases and make new stack to hold RGB result
        ImageStack projStack = projImp.getStack();
        ImageStack RBGstack = new ImageStack(imp.getWidth(), imp.getHeight());
        int sliceIn = 0;
        int sliceOut = 0;
        int width = imp.getWidth();
        int height = imp.getHeight();
        double half = 0.5;
        IJ.showStatus("False-coloring each angle");
        for (int t = 1; t <= nt; t++) {
            for (int z = 1; z <= nz; z++) {
                IJ.showProgress(z, nz);
                for (int c = 1; c <= nc; c++) {
                    // make new (temporary) stack to hold angle set
                    ImageStack angleSet = new ImageStack(imp.getWidth(),
                            imp.getHeight());
                    // make new (temporary) stack to hold 8-bit RGB sets (i.e. 3 slices)
                    ImageStack RGBset = new ImageStack(imp.getWidth(),
                            imp.getHeight());
                    for (int a = 1; a <= angles; a++) {
                        sliceIn++;
                        // get a slice as float, normalize it (see above) and convert to 8-bit
                        FloatProcessor fp =
                                (FloatProcessor)projStack.getProcessor(sliceIn).convertToFloat();
                        fp.multiply(normFactor[a-1][c-1]);
                        angleSet.addSlice(null, fp);
                        // calculate RGB values now we have all the angles
                        if (a == angles) {
                            sliceOut++;
                            // 1. convert angleSet to RGBset using proportions:-
                            //      0,0.5,0.5 / 0.5,0,0.5 / 0.5,0.5,0 = C / M / Y
                            FloatProcessor ipAngle1 = (FloatProcessor)angleSet.getProcessor(1);
                            FloatProcessor ipAngle2 = (FloatProcessor)angleSet.getProcessor(2);
                            FloatProcessor ipAngle3 = (FloatProcessor)angleSet.getProcessor(3);
                            // halve all values since each angle split between 2 RGB colors
                            ipAngle1.multiply(half);
                            ipAngle2.multiply(half);
                            ipAngle3.multiply(half);
                            // calculate RGB slices using copyBits/Blitter (see ImageCalculator)
                            FloatProcessor ipRed = new FloatProcessor(width, height);
                            ipRed.copyBits(ipAngle2, 0, 0, Blitter.ADD);
                            ipRed.copyBits(ipAngle3, 0, 0, Blitter.ADD);
                            ByteProcessor bpRed = (ByteProcessor)ipRed.convertToByte(true);
                            RGBset.addSlice(String.valueOf(1),bpRed);
                            FloatProcessor ipGreen = new FloatProcessor(width, height);
                            ipGreen.copyBits(ipAngle1, 0, 0, Blitter.ADD);
                            ipGreen.copyBits(ipAngle3, 0, 0, Blitter.ADD);
                            ByteProcessor bpGreen = (ByteProcessor)ipGreen.convertToByte(true);
                            RGBset.addSlice(String.valueOf(2),bpGreen);
                            FloatProcessor ipBlue = new FloatProcessor(width, height);
                            ipBlue.copyBits(ipAngle1, 0, 0, Blitter.ADD);
                            ipBlue.copyBits(ipAngle2, 0, 0, Blitter.ADD);
                            ByteProcessor bpBlue = (ByteProcessor)ipBlue.convertToByte(true);
                            RGBset.addSlice(String.valueOf(3),bpBlue);
                            // 2. convert 3 slices of RGBset to 1 RGB slice
                            ImagePlus tempImp = new ImagePlus("Temp", RGBset);
                            ImageConverter ic = new ImageConverter(tempImp);
                            ic.convertRGBStackToRGB();
                            ImageStack singleRGB = tempImp.getStack();
                            RBGstack.addSlice(String.valueOf(sliceOut),singleRGB.getProcessor(1));
                        }
                    }
                }
            }
        }
        ImagePlus colorImp = new ImagePlus(I1l.makeTitle(imp, "APJ"), RBGstack);
        colorImp.setDimensions(nc, nz, nt);
        int centralZ = nz / 2;
        colorImp.setPosition(1, centralZ, 1);
        colorImp.setOpenAsHyperStack(true);
        return colorImp;
    }
}
