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

import ij.IJ;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.gui.Roi;
import ij.plugin.PlugIn;
import ij.plugin.Duplicator;
import ij.process.ImageProcessor;
import ij.ImageJ;

/** This plugin displays a GenericDialog and runs the other SIMcheck plugins.
 * <ul>
 * <li>enables selection of images and checks</li>
 * <li>collects important parameters (data type, number of angles etc.)</li>
 * <li>displays instructions and has help button pointing to website</li>
 * <li>is blocking and has no exec method</li>
 * <li>contains some static utility methods only relevant to SIM data</li>
 * </ul>
 * @author Graeme Ball <graemeball@gmail.com>
 */
public class SIMcheck_ implements PlugIn {
    
    private static final String none = "[None]";  // no image
    private static final String omx = "OMX (CPZAT)";

    // options with default values
    private boolean doCrop = false;
    private ImagePlus impRaw = null;
    private int formatChoice = 0;
    private int phases = 5;
    private int angles = 3;
    private boolean doIntensityProfiles = true;
    private boolean doFourierProjections = true;
    private boolean doMotionCheck = true;
    private boolean doModContrast = true;
    private ImagePlus impMCNR = null;
    private ImagePlus impRecon = null;
    private boolean doHistogram = true;
    private boolean doZvar = true;
    private boolean doReconFourier = true;
    private boolean doMCNRmap = true;
    private Crop crop = new Crop();
    private int camBitDepth = 16;

    /** Crop ROI */
    private class Crop {
        int x = 0;
        int y = 0;
        int w = 0;  // width
        int h = 0;  // height
        int zFirst = 1;
        int zLast = 1;
    }
    
    @Override
    public void run(String arg) {
        String[] titles = JM.cat(new String[] {none}, I1l.collectTitles());
        String[] formats = JM.cat(new String[] {omx}, Util_FormatConverter.formats);
        camBitDepth = (int)ij.Prefs.get("SIMcheck.camBitDepth", camBitDepth);
        if (titles.length < 2) {
            IJ.noImage();
            return;
        }
        GenericDialog gd = new GenericDialog("SIMcheck (v0.9.3)");
        gd.addMessage(
                "--------------- INSTRUCTIONS ---------------");
        gd.addMessage(
                "  1. Choose a raw and/or reconstructed SIM data stacks.");
        gd.addMessage(
                "  2. Tick boxes for checks you would like to run & click OK.");
        String helpMessage =
                "    \"Help\" button below navigates to the SIMcheck web page.";
        gd.addMessage(helpMessage);
        
        // present options
        gd.addMessage("---------------- Raw data -----------------");
        gd.addChoice("Raw_Data:", titles, titles[1]);
        gd.addChoice("Data format:", formats, omx);
        gd.addNumericField("angles", angles, 0);
        gd.addNumericField("phases", phases, 0);
        gd.addCheckbox(Raw_IntensityProfiles.name, doIntensityProfiles);
        gd.addCheckbox(Raw_FourierProjections.name, doFourierProjections);
        gd.addCheckbox(Raw_MotionCheck.name, doMotionCheck);
        gd.addCheckbox(Raw_ModContrast.name, doModContrast);
        gd.addNumericField("    Camera Bit Depth", camBitDepth, 0);
        gd.addMessage("------------ Reconstructed data ------------");
        gd.addChoice("Reconstructed_Data:", titles, titles[0]);
        gd.addCheckbox("Intensity Histograms", doHistogram);
        gd.addCheckbox("Spherical Aberration Mismatch", doZvar);
        gd.addCheckbox("Fourier Plots", doReconFourier);
        gd.addCheckbox(
                "Mod_Contrast_Map (requires Raw Mod Contrast)", doMCNRmap);
        gd.addCheckbox("Use reconstructed data ROI to crop images?", doCrop);
        gd.addNumericField("* first Z (crop)", crop.zFirst, 0);
        gd.addNumericField("* last Z (crop)", crop.zLast, 0);
        gd.addHelp(
                "http://www.micron.ox.ac.uk/microngroup/software/SIMcheck.html");
        gd.showDialog();

        // collect options
        if (gd.wasOKed()) {
            String rawTitle = titles[gd.getNextChoiceIndex()];
            if (!rawTitle.equals(none)) {
                impRaw = WindowManager.getImage(rawTitle);
            }
            formatChoice = gd.getNextChoiceIndex();
            angles = (int)gd.getNextNumber();
            phases = (int)gd.getNextNumber();
            doIntensityProfiles = gd.getNextBoolean();
            doFourierProjections = gd.getNextBoolean();
            doMotionCheck = gd.getNextBoolean();
            doModContrast = gd.getNextBoolean();
            camBitDepth = (int)gd.getNextNumber();
            ij.Prefs.set("SIMcheck.camBitDepth", camBitDepth);
            String reconTitle = titles[gd.getNextChoiceIndex()];
            if (!reconTitle.equals(none)) {
                impRecon = WindowManager.getImage(reconTitle);
            }
            doHistogram = gd.getNextBoolean();
            doZvar = gd.getNextBoolean();
            doReconFourier = gd.getNextBoolean();
            doMCNRmap = gd.getNextBoolean();
            doCrop = gd.getNextBoolean();
            crop.zFirst = (int)gd.getNextNumber();
            crop.zLast = (int)gd.getNextNumber();
            IJ.log(   "\n   =====================      "
                    + "\n                      SIMcheck        "
                    + "\n   =====================      ");
        } else {
            return;  // bail out upon cancel
        }

        // format conversion, check, crop, open tools to work with ouput
        if (formatChoice != 0) {
            Util_FormatConverter formatConverter = new Util_FormatConverter();
            IJ.log("      converting " + formats[formatChoice] +
                    " to OMX format");
            impRaw = formatConverter.exec(
                    impRaw, phases, angles, formatChoice - 1);
        }
        if (impRaw != null && !I1l.stackDivisibleBy(impRaw, phases * angles)) {
            IJ.log("  ! invalid raw SI data - raw data checks aborted");
            impRaw = null;
        }
        if (doCrop && impRecon != null) {
            Roi roi = impRecon.getRoi();
            if (impRecon.getRoi() == null) {
                crop.x = 0;
                crop.y = 0;
                crop.w = impRecon.getWidth();
                crop.h = impRecon.getHeight();
            } else {
                crop.x = roi.getBounds().x;
                crop.y = roi.getBounds().y;
                crop.w = roi.getBounds().width;
                crop.h = roi.getBounds().height;
            }
            // Do recon image crop
            IJ.log("\n      Cropping to Reconstructed image ROI:");
            IJ.log("        x, y, width, height = " + crop.x + ", " + crop.y +
                    ", " + crop.w + ", " + crop.h);
            IJ.log("        slices Z = " + crop.zFirst + "-" + crop.zLast);
            int[] d = impRecon.getDimensions();
            String impReconTitle = I1l.makeTitle(impRecon, "CRP");
            ImagePlus impRecon2 = new Duplicator().run(impRecon, 
                    1, d[2], crop.zFirst, crop.zLast, 1, d[4]);
            impRecon2.setTitle(impReconTitle);
            impRecon.close();
            impRecon = impRecon2;
            impRecon.show();
            if (impRaw != null) {
                // Do raw image crop
                // 1) crop to Z range: tricky since Zobs = phase, Ztrue, angle
                ImageStack cropStack = null;
                ImagePlus imp2 = null;
                for (int a = 1; a <= angles; a++) {
                    imp2 = getImpForAngle(impRaw, a, phases, angles);
                    int zFirst = 1 + (crop.zFirst - 1) * phases;
                    imp2 = new Duplicator().run(imp2, 1, d[2], zFirst,
                            crop.zLast * phases, 1, d[4]);
                    if (a == 1) {
                        cropStack = imp2.getStack();
                    } else {
                        cropStack = I1l.cat(cropStack, imp2.getStack());
                    }
                }
                int nz = (crop.zLast - crop.zFirst + 1) * phases * angles;
                impRaw.setStack(cropStack);
                impRaw.setDimensions(d[2], nz , d[4]);
                impRaw.setOpenAsHyperStack(true);
                impRaw.setTitle(I1l.makeTitle(impRaw, "CRP"));
                // 2) crop to ROI derived from recon ROI (raw dims = 1/2 recon)
                impRaw.setRoi(new Roi(crop.x / 2, crop.y / 2,
                        crop.w / 2, crop.h / 2));
                IJ.run(impRaw, "Crop", "");
            }
        } else if (doCrop && impRecon != null) {
            IJ.log("      ! cannot crop: reconstructed image requires ROI");
        }
        IJ.run("Brightness/Contrast...");
        IJ.run("Channels Tool... ", "");
        
        // run checks, report results
        if (impRaw != null) {
            IJ.log("\n ==== Raw data checks ====");
            IJ.log("  Using SI stack: " + impRaw.getTitle());
            // do checks on raw SI data
            if (doIntensityProfiles) {
                Raw_IntensityProfiles ipf = new Raw_IntensityProfiles();
                ipf.phases = phases;
                ipf.angles = angles;
                ResultSet results = ipf.exec(impRaw);
                results.report();
            }
            if (doFourierProjections) {
                Raw_FourierProjections fpj = new Raw_FourierProjections();
                fpj.phases = phases;
                fpj.angles = angles;
                ResultSet results = fpj.exec(impRaw);
                results.report();
            }
            if (doMotionCheck) {
                Raw_MotionCheck mot = new Raw_MotionCheck();
                mot.phases = phases;
                mot.angles = angles;
                ResultSet results = mot.exec(impRaw);
                results.report();
            }
            if (doModContrast) {
                Raw_ModContrast mcn = new Raw_ModContrast();
                mcn.phases = phases;
                mcn.angles = angles;
                ResultSet results = mcn.exec(impRaw);
                impMCNR = results.getImp(0);
                results.report();
            }
        }
        if (impRecon != null) {
            IJ.log("\n ==== Reconstructed data checks ====");
            IJ.log("  using reconstructed stack: " + impRecon.getTitle());
            if (doHistogram) {
                Rec_Histograms histPlugin = new Rec_Histograms();
                ResultSet results = histPlugin.exec(impRecon);
                results.report();
            }
            if (doZvar) {
                Rec_SAMismatch mismatchPlugin = new Rec_SAMismatch();
                ResultSet results = mismatchPlugin.exec(impRecon);
                results.report();
            }
            if (doReconFourier) {
                Rec_FourierPlots fourierResPlugin = new Rec_FourierPlots();
                ResultSet results = fourierResPlugin.exec(impRecon);
                results.report();
            }
            if (doMCNRmap) {
                Rec_ModContrastMap modConMapPlugin = new Rec_ModContrastMap();
                modConMapPlugin.phases = phases;
                modConMapPlugin.angles = angles;
                modConMapPlugin.camBitDepth = camBitDepth;
                ResultSet results = modConMapPlugin.exec(
                        impRaw, impRecon, impMCNR);
                results.report();
            }
        }
        IJ.log("\n\n\n");
    }

    /** Split hyperstack, returning new ImagePlus for angle requested.
     * Assumes V2 OMX CPZAT channel order.
     */
    static ImagePlus getImpForAngle(
            ImagePlus imp, int a, int phases, int angles) {
    	int nc = imp.getNChannels();
      	int nz = imp.getNSlices();
      	int nt = imp.getNFrames();
      	nz = nz / (phases * angles);  // take phase & angle out of Z
      	int sliceIn = 0;
      	int sliceOut = 0;
      	int width = imp.getWidth();
        int height = imp.getHeight();
      	ImageStack stackAll = imp.getStack();
      	ImageStack stackOut = new ImageStack(width, height);
    	for (int t = 1; t <= nt; t++) {
            for (int z = 1; z <= nz; z++) {
                for (int p = 1; p <= phases; p++) {
                    for (int c = 1; c <= nc; c++) {
                        sliceIn = (t - 1) * (nc * phases * nz * angles);
                        sliceIn += (a - 1) * (nc * phases * nz);
                        sliceIn += (z - 1) * (nc * phases);
                        sliceIn += (p - 1) * nc;
                        sliceIn += c;
                        sliceOut++;  
                        ImageProcessor ip = stackAll.getProcessor(sliceIn);
                        stackOut.addSlice(String.valueOf(sliceOut), ip);
                    }
                }
            }
        }
    	String title = I1l.makeTitle(imp, "A" + a);
        ImagePlus impOut = new ImagePlus(title, stackOut);
        impOut.setDimensions(nc, nz * phases, nt);
        I1l.copyCal(imp, impOut);
        int centralZ = ((nz / 2) * phases) - phases + 1;  // 1st phase
        impOut.setPosition(1, centralZ, 1);
        impOut.setOpenAsHyperStack(true);
        return impOut;
    }
    
    /** Interactive test. */
    public static void main(String args[]) {
        new ImageJ();
        TestData.raw.show();
        TestData.recon.show();
        IJ.runPlugIn(SIMcheck_.class.getName(), "");
    }
}
