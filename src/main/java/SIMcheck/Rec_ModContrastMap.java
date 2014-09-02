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
import ij.gui.Overlay;

import java.lang.Math;

/** This plugin summarizes reconstructed data intensity and modulation 
 * contrast in the same image. The two metrics are displayed using color 
 * values of a 3-byte RBG: 1. Modulation Contrast-to-Noise Ratio (MCNR), 
 * according to the LUT used for the Raw_ModContrast plugin output
 * (purple=poor to white=excellent). 2. reconstructed image intensity 
 * scales pixel brightness. The plugin requires an ImagePlus containing MCNR 
 * values, so "Raw_ModulationContrast" output for matching raw data is 
 * required.
 * @author Graeme Ball <graemeball@gmail.com>
 */
public class Rec_ModContrastMap implements PlugIn, Executable {
    
    public static final String name = "Modulation Contrast Map";
    public static final String TLA = "MCM";
    private ResultSet results = new ResultSet(name);
    
    // parameter fields
    public int phases = 5;
    public int angles = 3;
    public int camBitDepth = 16;
    public float mcnrMax = 24.0f;

    @Override
    public void run(String arg) {
        GenericDialog gd = new GenericDialog(name);
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
        gd.addChoice("Reconstructed data stack:", titles, titles[0]);
        
        gd.showDialog();
        if (gd.wasOKed()) {
            String rawStackChoice = gd.getNextChoice();
            camBitDepth = (int)gd.getNextNumber();
            ij.Prefs.set("SIMcheck.camBitDepth", camBitDepth);
            String mcnrStackChoice = gd.getNextChoice();
            String recStackChoice = gd.getNextChoice();
            ImagePlus rawImp = ij.WindowManager.getImage(rawStackChoice);
            ImagePlus impMCNR = null;
            if (gd.getNextBoolean()) {
                Raw_ModContrast plugin = new Raw_ModContrast();
                plugin.phases = phases;
                plugin.angles = angles;
                impMCNR = plugin.exec(rawImp).getImp(0);
            } else {
                impMCNR = ij.WindowManager.getImage(mcnrStackChoice);
            }
            ImagePlus impRec = ij.WindowManager.getImage(recStackChoice);
            results = exec(rawImp, impRec, impMCNR);
            results.report();
        }
    }

    /** Execute plugin functionality: create a modulation contrast color map.
     * @param imps ImagePluses should be impRec reconstructed data
     *        and impMCNR MCNR result for raw data used to produce impRec
     * @return ResultSet with map of reconstructed intensity colored by MCNR
     */
    public ResultSet exec(ImagePlus... imps) {
        ImagePlus impRaw = imps[0];  
        ImagePlus impRec = imps[1];
        ImagePlus impMCNR = imps[2];
        if (impRaw == null) {
            IJ.showMessage("Error", "Mod contrast map requires raw data image");
            return results;
        } else if (impRec == null) {
            IJ.showMessage("Error", "Specify reconstructed image");
            return results;
        } else if (impMCNR == null) {
            IJ.showMessage("Error", "Specify modulation contrast image");
            return results;
        }
        if (!rawAndRecImpMatch(impMCNR, impRec)) {
            IJ.log("! Rec_ModContrastMap: input stack size mismatch");
            IJ.log("  - impRaw nx,ny,nz = " + impRaw.getWidth() + "," +
                    impRaw.getHeight() + "," + impRaw.getNSlices() + "\n");
            IJ.log("  - impRec nx,ny,nz = " + impRec.getWidth() + "," +
                    impRec.getHeight() + "," + impRec.getNSlices() + "\n");
            return results;
        }
        // convert raw data imp into pseudo-widefield
        Util_SItoPseudoWidefield si2wf = new Util_SItoPseudoWidefield();
        ImagePlus wfImp = si2wf.exec(impRaw, phases, angles);
        IJ.showStatus(name + "...");
        ImagePlus impRec2 = (ImagePlus)impRec.duplicate();
        IJ.run(impRec2, "32-bit", "");
        ImagePlus impMCNR2 = (ImagePlus)impMCNR.duplicate();
        IJ.run(impRec2, "Enhance Contrast", "saturated=0.35 process_all use");
        IJ.run(impMCNR2, "Gaussian Blur 3D...", "x=1 y=1 z=1"); 
        int width = impRec2.getWidth();
        int height = impRec2.getHeight();
        int nc = impRec2.getNChannels();
        int nz = impRec2.getNSlices();
        int nt = impRec2.getNFrames();
        ImagePlus[] MCMimpsC = new ImagePlus[nc];
        // each channel must be scaled separately: merge 1-channel images later
        for (int c = 1; c <= nc; c++) {
            ImagePlus impRecC = I1l.copyChannel(impRec2, c); 
            ImagePlus MCNimpC = I1l.copyChannel(impMCNR2, c); 
            ImagePlus wfImpC = I1l.copyChannel(wfImp, c); 
            StackStatistics stats = new ij.process.StackStatistics(impRecC);
            float chMax = (float)stats.max; 
            int slices = impRecC.getStack().getSize();
            ImageStack MCMstackC = new ImageStack(width, height);
            for (int slice = 1; slice <= slices; slice++) {
                // 1. new processors for values to be converted to 8-bit color
                FloatProcessor fpRec = 
                        (FloatProcessor)impRecC.getStack().getProcessor(slice);
                FloatProcessor fpMCN = 
                        (FloatProcessor)MCNimpC.getStack().getProcessor(slice);
                FloatProcessor MCNRfpRsz = fpResize(fpMCN, width, height);
                FloatProcessor fpRed = new FloatProcessor(width, height);
                FloatProcessor fpGrn = new FloatProcessor(width, height);
                FloatProcessor fpBlu = new FloatProcessor(width, height);
                ImageStack RGBset = new ImageStack(width, height);  // 1 set
                // 2. copy scaled values from input Processors to new Processors
                FloatProcessor wfFp = 
                        (FloatProcessor)wfImpC.getStack().getProcessor(slice);
                scaledRGBintensities(fpRec, MCNRfpRsz, wfFp, chMax,
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
        ImagePlus outImp = I1l.mergeChannels(I1l.makeTitle(impRec, "MCM"), 
                MCMimpsC);
        outImp.setDimensions(nc, nz, nt);
        outImp.setOpenAsHyperStack(true);
        int Cmid = nc / 2;
        int Zmid = nz / 2;
        int Tmid = nt / 2;
        outImp.setPosition(Cmid, Zmid, Tmid);
        Overlay legend = impMCNR.getOverlay();
        outImp.setOverlay(legend);
        if (outImp.getOverlay() !=null) {
            // MCNR / raw data overlay is 1/2 size and defaults to center
            outImp.getOverlay().translate(height / 2, width / 2);
        }
        I1l.copyCal(impRec2, outImp);
        results.addImp("Reconstructed data color-coded by the underlying" +
                " modulation contrast (MCNR) in the raw data",
                outImp);
        results.addInfo("How to interpret", "Mod contrast color LUT" +
                " indicates local variations in reconstruction quality," +
                " e.g. due to blurring, dense features, or uneven SI" + 
                " illumination.");
        results.addInfo("MCNR values", " 0-3 purple (inadequate)," +
                " to 6 red (acceptable), to 12 orange (good)," +
                " to 18 yellow (very good), to 24 white (excellent).");
        return results;
    }

    /** Check rec imp width and height are 2x raw data width and height. */
    private static boolean rawAndRecImpMatch(
            ImagePlus impRaw, ImagePlus impRec) {
        boolean match = false;
        int slicesMCNR = impRaw.getStackSize();
        int widthMCNR = impRaw.getStack().getWidth();
        int heightMCNR = impRaw.getStack().getHeight();
        int slicesRec = impRec.getStackSize();
        int widthRec = impRec.getStack().getWidth();
        int heightRec = impRec.getStack().getHeight();
        if ((impRec != null) && (impRaw != null)){
            boolean stackMatch = slicesMCNR == slicesRec;
            boolean widthMatch = (int)(widthMCNR * 2) == widthRec;
            boolean heightMatch = (int)(heightMCNR * 2) == heightRec;
            if (stackMatch && widthMatch && heightMatch) { 
                match = true;
            }
        }else{
            IJ.log("  ! warning: raw or reconstructed stack was null");
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
                    "same aspect, integer scaling only: arguments asked for" +
                    " width " + oldWidth + "->" + newWidth +
                    ", height" + oldHeight + "->" + newHeight);
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

    /** Combine reconstructed image intensity with mod contrast encoded in
     * LUT color map. Also color saturated pixels in raw data green.
     */
    private void scaledRGBintensities(
            FloatProcessor fpRec, FloatProcessor MCNRfp2,
            FloatProcessor wfFp, float chMax,
            FloatProcessor fpRed, FloatProcessor fpGrn, FloatProcessor fpBlu) {
        float[] wfPix = (float[])wfFp.getPixels();
        float[] fpixRec = (float[])fpRec.getPixels();
        float[] fpixMCNR = (float[])MCNRfp2.getPixels();
        float[] fpixRed = (float[])fpRed.getPixels();
        float[] fpixGrn = (float[])fpGrn.getPixels();
        float[] fpixBlu = (float[])fpBlu.getPixels();
        int widthRec = fpRec.getWidth();
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
        for (int i = 1; i < fpixRec.length; i++) {
            int j = i / widthRec;
            int rawI = (i / 2) % (widthRec / 2) + ((j / 2) * widthRec / 2);
            if (wfPix[rawI] > Math.pow(2, camBitDepth) - 2) {
                fpixRed[i] = 0.0f;
                fpixGrn[i] = 255.0f;
                fpixBlu[i] = 0.0f;
            } else {
                float intensScale = (float) Math.max(
                        Math.min((fpixRec[i] / chMax), 1.0f), 0.0f);
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
        IJ.runPlugIn(Rec_ModContrastMap.class.getName(), "");
    }
}
