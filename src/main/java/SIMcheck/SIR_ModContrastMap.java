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
import ij.process.*;
import ij.plugin.*;
import ij.gui.GenericDialog;

import java.lang.Math;

/** This plugin summarizes reconstructed data intensity and modulation 
 * contrast in the same image. The two metrics are displayed using color 
 * values of a 3-byte RBG: 1. Modulation Contrast-to-Noise Ratio (MCNR), 
 * according to the LUT used for the Raw_ModContrast plugin output
 * (purple=poor to white=excellent). 2. SIR reconstructed image intensity 
 * scales pixel brightness. The plugin requires an ImagePlus containing MCNR 
 * values, so "Raw_Modulation_Contrast" output for matching raw data is 
 * required.
 * @author Graeme Ball <graemeball@gmail.com>
 */
public class SIR_ModContrastMap implements PlugIn, Executable {
    
    String name = "Reconstructed Data Mod Contrast Map (MCM)";
    ResultSet results = new ResultSet(name);
    
    // parameter fields
    public int phases = 5;
    public int angles = 3;
    public int camBitDepth = 16;
    public float mcnrMax = 24.0f;

    @Override
    public void run(String arg) {
        GenericDialog gd = new GenericDialog("SIR_Mod_Contrast_Map");
        String[] titles = I1l.collectTitles();
        if (titles.length < 2) {
            IJ.showMessage("Error", "Did not find at least 2 images" +
                    " (raw and recon)");
            return;
        }
        camBitDepth = (int)ij.Prefs.get("SIMcheck.camBitDepth", camBitDepth);
        gd.addMessage(" --- Raw data stack --- ");
        gd.addChoice("Raw data stack:", titles, titles[0]);
        gd.addNumericField("       Camera Bit Depth", camBitDepth, 0);
        gd.addMessage(" --- Modulation-Contrast-to-Noise Ratio stack --- ");
        gd.addCheckbox("Calculate MCNR stack from raw data?", true);
        gd.addChoice("OR, specify MCNR stack:", titles, titles[0]);
        gd.addMessage(" ------------- Reconstructed SI stack ----------- ");
        gd.addChoice("SIR stack:", titles, titles[0]);
        
        gd.showDialog();
        if (gd.wasOKed()) {
            String rawStackChoice = gd.getNextChoice();
            camBitDepth = (int)gd.getNextNumber();
            ij.Prefs.set("SIMcheck.camBitDepth", camBitDepth);
            String MCNRstackChoice = gd.getNextChoice();
            String SIRstackChoice = gd.getNextChoice();
            ImagePlus rawImp = ij.WindowManager.getImage(rawStackChoice);
            ImagePlus MCNRimp = null;
            if (gd.getNextBoolean()) {
                Raw_ModContrast raw_MCNR_plugin = new Raw_ModContrast();
                raw_MCNR_plugin.phases = phases;
                raw_MCNR_plugin.angles = angles;
                MCNRimp = raw_MCNR_plugin.exec(rawImp).getImp(0);
            } else {
                MCNRimp = ij.WindowManager.getImage(MCNRstackChoice);
            }
            ImagePlus SIRimp = ij.WindowManager.getImage(SIRstackChoice);
            results = exec(rawImp, SIRimp, MCNRimp);
            results.report();
        }
    }

    /** Execute plugin functionality: create a modulation contrast color map.
     * @param imps ImagePluses should be SIRimp reconstructed data
     *        and MCNRimp MCNR result for raw data used to produce SIRimp
     * @return ResultSet containing map of SIR intesnsity colored by MCNR
     */
    public ResultSet exec(ImagePlus... imps) {
        ImagePlus rawImp = imps[0];  
        ImagePlus SIRimp = imps[1];
        ImagePlus MCNRimp = imps[2];
        if (rawImp == null) {
            IJ.showMessage("Error", "Specify raw data image");
            return results;
        } else if (SIRimp == null) {
            IJ.showMessage("Error", "Specify reconstructed image");
            return results;
        } else if (MCNRimp == null) {
            IJ.showMessage("Error", "Specify modulation contrast image");
            return results;
        }
        if (!SIandSIRimpMatch(MCNRimp, SIRimp)) {
            IJ.log("  ! SIR_ModContrastMap: MCNR and SIR stack size mismatch");
            return results;
        }
        // convert raw data imp into pseudo-widefield
        Util_SI2WF si2wf = new Util_SI2WF();
        ImagePlus wfImp = si2wf.exec(rawImp, phases, angles);
        IJ.showStatus("Reconstructed data Mod Contrast Map...");
        ImagePlus SIRimp2 = (ImagePlus) SIRimp.duplicate();
        IJ.run(SIRimp2, "32-bit", "");
        ImagePlus MCNRimp2 = (ImagePlus)MCNRimp.duplicate();
        IJ.run(SIRimp2, "Enhance Contrast", "saturated=0.35 process_all use");
        IJ.run(MCNRimp2, "Gaussian Blur 3D...", "x=1 y=1 z=1"); 
        int width = SIRimp2.getWidth();
        int height = SIRimp2.getHeight();
        int nc = SIRimp2.getNChannels();
        int nz = SIRimp2.getNSlices();
        int nt = SIRimp2.getNFrames();
        ImagePlus[] MCMimpsC = new ImagePlus[nc];
        // each channel must be scaled separately: merge 1-channel images later
        for (int c = 1; c <= nc; c++) {
            ImagePlus SIRimpC = I1l.copyChannel(SIRimp2, c); 
            ImagePlus MCNimpC = I1l.copyChannel(MCNRimp2, c); 
            ImagePlus wfImpC = I1l.copyChannel(wfImp, c); 
            StackStatistics stats = new ij.process.StackStatistics(SIRimpC);
            float chMax = (float)stats.max; 
            int slices = SIRimpC.getStack().getSize();
            ImageStack MCMstackC = new ImageStack(width, height);
            for (int slice = 1; slice <= slices; slice++) {
                // 1. new processors for values to be converted to 8-bit color
                FloatProcessor SIRfp = 
                        (FloatProcessor)SIRimpC.getStack().getProcessor(slice);
                FloatProcessor MCNfp = 
                        (FloatProcessor)MCNimpC.getStack().getProcessor(slice);
                FloatProcessor MCNRfpRsz = fpResize(MCNfp, width, height);
                FloatProcessor fpRed = new FloatProcessor(width, height);
                FloatProcessor fpGrn = new FloatProcessor(width, height);
                FloatProcessor fpBlu = new FloatProcessor(width, height);
                ImageStack RGBset = new ImageStack(width, height);  // 1 set
                // 2. copy scaled values from input Processors to new Processors
                FloatProcessor wfFp = 
                        (FloatProcessor)wfImpC.getStack().getProcessor(slice);
                scaledRGBintensities(SIRfp, MCNRfpRsz, wfFp, chMax,
                        fpRed, fpGrn, fpBlu);
                // 3. assemble 1 RGBset (3 slices)
                ByteProcessor bpRed = (ByteProcessor)fpRed.convertToByte(false);
                ByteProcessor bpGrn = (ByteProcessor)fpGrn.convertToByte(false);
                ByteProcessor bpBlu = (ByteProcessor)fpBlu.convertToByte(false);
                RGBset.addSlice(String.valueOf(1), bpRed);
                RGBset.addSlice(String.valueOf(2), bpGrn);
                RGBset.addSlice(String.valueOf(3), bpBlu);
                ImagePlus tempImp = new ImagePlus("Temp", RGBset);
                ImageConverter ic = new ImageConverter(tempImp);
                // 4. convert RGBset to RGB image slice and add to output
                ic.convertRGBStackToRGB();
                ImageStack singleRGB = tempImp.getStack();
                MCMstackC.addSlice(String.valueOf(slice),
                        singleRGB.getProcessor(1));
            }
            MCMimpsC[c - 1] = new ImagePlus("MCM" + c, MCMstackC);
            IJ.run(MCMimpsC[c - 1], "Enhance Contrast", 
                    "saturated=0.08 process_all use");
        }
        // try to set result ImagePlus with reasonable display settings
        ImagePlus outImp = I1l.mergeChannels(I1l.makeTitle(SIRimp, "MCM"), 
                MCMimpsC);
        outImp.setDimensions(nc, nz, nt);
        outImp.setOpenAsHyperStack(true);
        int Cmid = nc / 2;
        int Zmid = nz / 2;
        int Tmid = nt / 2;
        outImp.setPosition(Cmid, Zmid, Tmid);
        I1l.copyCal(SIRimp2, outImp);
        results.addImp("raw data mod contrast with reconstructed intensities", 
                outImp);
        results.addInfo("Modulation contrast", 
                "0-3 purple (inadequate), to 6 red (acceptable),\n"
                + "    to 12 orange (good), to 18 yellow (very good), " 
                + "to 24 white (excellent).");
        return results;
    }

    /** Check SIR imp width and height are 2x raw SI data width and height. */
    private static boolean SIandSIRimpMatch(
            ImagePlus rawImp, ImagePlus sirImp) {
        boolean match = false;
        int slicesMCNR = rawImp.getStackSize();
        int widthMCNR = rawImp.getStack().getWidth();
        int heightMCNR = rawImp.getStack().getHeight();
        int slicesSIR = sirImp.getStackSize();
        int widthSIR = sirImp.getStack().getWidth();
        int heightSIR = sirImp.getStack().getHeight();
        if ((sirImp != null) && (rawImp != null)){
            boolean stackMatch = slicesMCNR == slicesSIR;
            boolean widthMatch = (int)(widthMCNR * 2) == widthSIR;
            boolean heightMatch = (int)(heightMCNR * 2) == heightSIR;
            if (stackMatch && widthMatch && heightMatch) { 
                match = true;
            }
        }else{
            IJ.log("  ! warning: raw or SIR stack was null");
        }
        return match;
    }

    /** Method to resize a FloatProcessor - IJ's doesn't work or doesn't update
     * size info :-(
     */
    private FloatProcessor fpResize(
            FloatProcessor fpIn, int newWidth, int newHeight) {
        int oldWidth = fpIn.getWidth();
        int oldHeight = fpIn.getHeight();
        float[] oldPixels = (float[]) fpIn.getPixels();
        float[] newPixels = new float[newWidth * newHeight];
        if ((newWidth / oldWidth == 0) || (newWidth % oldWidth != 0)
                || ((newWidth / oldWidth) != (newHeight / oldHeight))) {
            throw new IllegalArgumentException(
                    "same aspect, integer scaling only: arguments asked for"
                    + " width " + oldWidth + "->" + newWidth 
                    + ", height" + oldHeight + "->" + newHeight);
        } else {
            int scaleF = 1;
            scaleF = newWidth / oldWidth;
            int xpos = 0;
            int ypos = 0;
            for (int i = 0; i < newWidth * newHeight; i++) {
                // TODO: interpolate etc. - for now we'll just blow it up
                int oldIndex = (xpos / scaleF) + ((ypos / scaleF) * oldWidth);
                newPixels[i] = oldPixels[oldIndex];
                xpos++;
                if (xpos == newWidth) {
                    xpos = 0;
                    ypos++;
                }
            }
            FloatProcessor fpOut = new FloatProcessor(newWidth, newHeight,
                    newPixels);
            return fpOut;
        }
    }

    /** Use input SIR and MCNR values to make output RGB color-coded processors.
     * SIR image intensity combined with MCNR encoded in LUT color map. Also
     * colors saturated pixels green.
     */
    private void scaledRGBintensities(
            FloatProcessor SIRfp, FloatProcessor MCNRfp2,
            FloatProcessor wfFp, float chMax,
            FloatProcessor fpRed, FloatProcessor fpGrn, FloatProcessor fpBlu) {
        float[] wfPix = (float[])wfFp.getPixels();
        float[] fpixSIR = (float[])SIRfp.getPixels();
        float[] fpixMCNR = (float[])MCNRfp2.getPixels();
        float[] fpixRed = (float[])fpRed.getPixels();
        float[] fpixGrn = (float[])fpGrn.getPixels();
        float[] fpixBlu = (float[])fpBlu.getPixels();
        int widthSIR = SIRfp.getWidth();
        final float[] redLUT = 
                { 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80,
                80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80,
                80, 80, 80, 80, 84, 89, 93, 98, 102, 107, 111, 116, 120, 125,
                129, 134, 138, 143, 147, 152, 156, 161, 165, 170, 174, 179,
                183, 188, 192, 197, 201, 206, 210, 215, 219, 224, 224, 224,
                225, 225, 226, 226, 227, 227, 228, 228, 229, 229, 230, 230,
                231, 231, 232, 232, 233, 233, 234, 234, 235, 235, 236, 236,
                237, 237, 238, 238, 239, 239, 239, 240, 240, 241, 241, 242,
                242, 243, 243, 244, 244, 245, 245, 246, 246, 247, 247, 248,
                248, 249, 249, 250, 250, 251, 251, 252, 252, 253, 253, 254,
                254, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255 };
        final float[] grnLUT = 
                { 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
                32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
                32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
                32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32, 32,
                32, 32, 32, 32, 33, 35, 36, 38, 39, 41, 42, 44, 45, 47, 48, 50,
                51, 53, 54, 56, 57, 59, 60, 62, 63, 65, 66, 68, 69, 71, 72, 74,
                75, 77, 78, 80, 81, 83, 84, 86, 87, 89, 90, 92, 93, 95, 96, 98,
                99, 101, 102, 104, 105, 107, 108, 110, 111, 113, 114, 116, 117,
                119, 120, 122, 123, 125, 126, 128, 129, 131, 133, 135, 137,
                139, 141, 143, 145, 147, 149, 151, 153, 155, 157, 159, 161,
                163, 165, 167, 169, 171, 173, 175, 177, 179, 181, 183, 185,
                187, 189, 191, 193, 195, 197, 199, 201, 203, 205, 207, 209,
                211, 213, 215, 217, 219, 221, 223, 225, 227, 229, 231, 233,
                235, 237, 239, 241, 243, 245, 247, 249, 251, 253, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255,
                255, 255 };
        final float[] bluLUT = 
                { 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80,
                80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80, 80,
                80, 80, 80, 80, 77, 75, 72, 70, 67, 65, 62, 60, 57, 55, 52, 50,
                47, 45, 42, 40, 37, 35, 32, 30, 27, 25, 22, 20, 17, 15, 12, 10,
                7, 5, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 3, 7, 11, 15, 19, 23, 27, 31, 35, 39, 43, 47,
                51, 55, 59, 63, 67, 71, 75, 79, 83, 87, 91, 95, 99, 103, 107,
                111, 115, 119, 123, 127, 131, 135, 139, 143, 147, 151, 155,
                159, 163, 167, 171, 175, 179, 183, 187, 191, 195, 199, 203,
                207, 211, 215, 219, 223, 227, 231, 235, 239, 243, 247, 251 };
        for (int i = 1; i < fpixSIR.length; i++) {
            int j = i / widthSIR;
            int rawI = (i / 2) % (widthSIR / 2) + ((j / 2) * widthSIR / 2);
            if (wfPix[rawI] > Math.pow(2, camBitDepth) - 2) {
                fpixRed[i] = 0.0f;
                fpixGrn[i] = 255.0f;
                fpixBlu[i] = 0.0f;
            } else {
                float intensScale = (float) Math.max(
                        Math.min((fpixSIR[i] / chMax), 1.0f), 0.0f);
                int lutIndex = (int) (255.0f * Math.min(
                                (fpixMCNR[i] / mcnrMax), 1.0f));
                fpixRed[i] = intensScale * redLUT[lutIndex];
                fpixGrn[i] = intensScale * grnLUT[lutIndex];
                fpixBlu[i] = intensScale * bluLUT[lutIndex];
            }
        }
    }
    
    /** Interactive test method */
    public static void main(String[] args) {
        new ImageJ();
        TestData.raw.show();
        TestData.recon.show();
        IJ.runPlugIn(SIR_ModContrastMap.class.getName(), "");
    }
}
