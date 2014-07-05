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

/** This plugin displays a GenericDialog and runs the other SIMcheck plugins.
 * <ul>
 * <li>enables selection of images and checks</li>
 * <li>collects important parameters (data type, number of angles etc.)</li>
 * <li>displays instructions and has help button pointing to website</li>
 * <li>is blocking and has no exec method</li>
 * </ul>
 * @author Graeme Ball <graemeball@gmail.com>
 */
public class SIMcheck_ implements PlugIn {
    
    private static final String none = "[None]";  // no image
    private static final String omx = "API OMX (CPZAT)";

    // options with default values
    private boolean doCrop = false;
    private int zFirst = 1;
    private int zLast = 1;
    private ImagePlus impRaw = null;
    private int formatChoice = 0;
    private int phases = 5;
    private int angles = 3;
    private boolean doIntensity = true;
    private boolean doRawFourier = true;
    private boolean doAngleDifference = true;
    private boolean doMCNR = true;
    private ImagePlus impMCNR = null;
    private ImagePlus impRecon = null;
    private boolean doHistogram = true;
    private boolean doZvar = true;
    private boolean doReconFourier = true;
    private boolean doMCNRmap = true;
    private Crop crop = new Crop();

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
        String[] titles = I1l.cat(new String[] {none}, I1l.collectTitles());
        String[] formats = I1l.cat(new String[] {omx}, Util_formats.formats);
        if (titles.length < 2) {
            IJ.noImage();
            return;
        }
        GenericDialog gd = new GenericDialog("SIMcheck (v0.9)");
        gd.addMessage(
                "--------------- INSTRUCTIONS ---------------");
        gd.addMessage(
                "  1. Choose a raw and/or reconstructed SI data stacks.");
        gd.addMessage(
                "  2. Tick boxes for checks you would like to run & click OK.");
        String helpMessage =
                "    \"Help\" below navigates to Micron web page about SIMcheck\n";
        helpMessage +=
                "      or extract SIMcheck.html from SIMcheck_.jar using unzip";
        gd.addMessage(helpMessage);
        
        // present options
        gd.addCheckbox("Use reconstructed data ROI to crop images?", doCrop);
        gd.addNumericField("* first Z (crop)", crop.zFirst, 0);
        gd.addNumericField("* last Z (crop)", crop.zLast, 0);
        gd.addMessage("---------------- Raw data -----------------");
        gd.addChoice("Raw_Data:", titles, titles[1]);
        gd.addChoice("Data format:", formats, omx);
        gd.addNumericField("angles", angles, 0);
        gd.addNumericField("phases", phases, 0);
        gd.addCheckbox("Raw_Intensity_Profile", doIntensity);
        gd.addCheckbox("Raw_Fourier_Plots", doRawFourier);
        gd.addCheckbox("Raw_Angle_Difference", doAngleDifference);
        gd.addCheckbox("Raw_Modulation_Contrast", doMCNR);
        gd.addMessage("------------ Reconstructed data ------------");
        gd.addChoice("Reconstructed_Data:", titles, titles[0]);
        gd.addCheckbox("SIR_Histogram", doHistogram);
        gd.addCheckbox("SIR_Z_Variation", doZvar);
        gd.addCheckbox("SIR_Fourier Plot", doReconFourier);
        gd.addCheckbox(
                "SIR_Mod_Contrast_Map (requires Raw Mod Contrast)", doMCNRmap);
        gd.addHelp(
                "http://www.micron.ox.ac.uk/microngroup/software/SIMcheck.html");
        gd.showDialog();

        // collect options
        if (gd.wasOKed()) {
            doCrop = gd.getNextBoolean();
            crop.zFirst = (int)gd.getNextNumber();
            crop.zLast = (int)gd.getNextNumber();
            String rawTitle = titles[gd.getNextChoiceIndex()];
            if (!rawTitle.equals(none)) {
                impRaw = WindowManager.getImage(rawTitle);
            }
            formatChoice = gd.getNextChoiceIndex();
            angles = (int)gd.getNextNumber();
            phases = (int)gd.getNextNumber();
            doIntensity = gd.getNextBoolean();
            doRawFourier = gd.getNextBoolean();
            doAngleDifference = gd.getNextBoolean();
            doMCNR = gd.getNextBoolean();
            String reconTitle = titles[gd.getNextChoiceIndex()];
            if (!reconTitle.equals(none)) {
                impRecon = WindowManager.getImage(reconTitle);
            }
            doHistogram = gd.getNextBoolean();
            doZvar = gd.getNextBoolean();
            doReconFourier = gd.getNextBoolean();
            doMCNRmap = gd.getNextBoolean();
            IJ.log(   "\n   =====================      "
                    + "\n                      SIMcheck        "
                    + "\n   =====================      ");
        } else {
            return;  // bail out upon cancel
        }

        // format conversion, check, crop, open tools to work with ouput
        if (formatChoice != 0) {
            Util_formats formatConverter = new Util_formats();
            IJ.log("      converting " + formats[formatChoice] +
                    " to OMX format");
            impRaw = formatConverter.exec(
                    impRaw, phases, angles, formatChoice - 1);
        }
        if (!I1l.stackDivisibleBy(impRaw, phases * angles)) {
            IJ.log("  ! invalid raw SI data - raw data checks aborted");
            impRaw = null;
        }
        if (doCrop && impRecon != null) {
            Roi roi = impRecon.getRoi();
            crop.x = roi.getBounds().x;
            crop.y = roi.getBounds().y;
            crop.w = roi.getBounds().width;
            crop.h = roi.getBounds().height;
            IJ.log("\n      Cropping to Reconstructed image ROI:");
            IJ.log("        x, y, width, height = " + crop.x + ", " + crop.y +
                    ", " + crop.w + ", " + crop.h);
            IJ.log("        slices Z = " + crop.zFirst + "-" + crop.zLast);
            // TODO, log crop parameters
            int[] d = impRecon.getDimensions();
            String impReconTitle = I1l.makeTitle(impRecon, "CRP");
            impRecon = new Duplicator().run(impRecon, 
                    1, d[2], crop.zFirst, crop.zLast, 1, d[4]);
            impRecon.setTitle(impReconTitle);
            if (impRaw != null) {
                IJ.log("*** TODO: crop raw! ***");
            }
        }
        IJ.run("Brightness/Contrast...");
        IJ.run("Channels Tool... ", "");
        
        // run checks, report results
        if (impRaw != null) {
            IJ.log("\n ==== Raw data checks ====");
            IJ.log("  Using SI stack: " + impRaw.getTitle());
            // do checks on raw SI data
            if (doIntensity) {
                Raw_intensity raw_int_plugin = new Raw_intensity();
                raw_int_plugin.phases = phases;
                raw_int_plugin.angles = angles;
                ResultSet results = raw_int_plugin.exec(impRaw);
                results.report();
            }
            if (doRawFourier) {
                Raw_Fourier raw_fourier_plugin = new Raw_Fourier();
                raw_fourier_plugin.phases = phases;
                raw_fourier_plugin.angles = angles;
                ResultSet results = raw_fourier_plugin.exec(impRaw);
                results.report();
            }
            if (doAngleDifference) {
                Raw_Angle_Difference raw_a_diff_plugin = new Raw_Angle_Difference();
                raw_a_diff_plugin.phases = phases;
                raw_a_diff_plugin.angles = angles;
                ResultSet results = raw_a_diff_plugin.exec(impRaw);
                results.report();
            }
            if (doMCNR) {
                Raw_ModContrast raw_MCNR_plugin = new Raw_ModContrast();
                raw_MCNR_plugin.phases = phases;
                raw_MCNR_plugin.angles = angles;
                ResultSet results = raw_MCNR_plugin.exec(impRaw);
                impMCNR = results.getImp(0);
                results.report();
            }
        }
        if (impRecon != null) {
            IJ.log("\n ==== Reconstructed data checks ====");
            IJ.log("  using SIR stack: " + impRecon.getTitle());
            if (doHistogram) {
                SIR_histogram sir_hist_plugin = new SIR_histogram();
                ResultSet results = sir_hist_plugin.exec(impRecon);
                results.report();
            }
            if (doZvar) {
                SIR_Z_variation sir_z_var_plugin = new SIR_Z_variation();
                ResultSet results = sir_z_var_plugin.exec(impRecon);
                results.report();
            }
            if (doReconFourier) {
                SIR_Fourier sir_fourier_plugin = new SIR_Fourier();
                ResultSet results = sir_fourier_plugin.exec(impRecon);
                results.report();
            }
            if (doMCNRmap) {
                SIR_ModContrastMap sir_mcnr_plugin = 
                    new SIR_ModContrastMap();
                sir_mcnr_plugin.phases = phases;
                sir_mcnr_plugin.angles = angles;
                ResultSet results = sir_mcnr_plugin.exec(impRaw, impRecon, impMCNR);
                results.report();
            }
        }
        IJ.log("\n\n\n");
    }

    /** Split hyperstack, returning new ImagePlus for angle requested.
     * Assumes API V2 OMX CPZAT channel order. 
     */
    public static ImagePlus getImpForAngle(ImagePlus imp, int a,
            int phases, int angles) {
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
}
