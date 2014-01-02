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
import ij.WindowManager;
import ij.gui.GenericDialog;
import ij.plugin.PlugIn;

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

    private static final int RAW_CHECKS = 4;  // number of raw data checks
    
    // default choices for angles & phases; and known SIM data formats
    private int angles = 3;
    private int phases = 5;
    private String[] formats = {"API OMX (CPZAT)", "Zeiss ELYRA (CZTAP)",
            "Nikon N-SIM (?????)"};

    @Override
    public void run(String arg) {
        /// get existing images so user can choose raw and reconstructed data
        int[] wList = WindowManager.getIDList();
        if (wList == null) {
            IJ.noImage();
            return;
        }
        String[] titles = new String[wList.length + 1];
        int nullTitleIndex = wList.length;
        for (int i = 0; i <= wList.length; i++) {
            if (i != wList.length) {
                titles[i] = WindowManager.getImage(wList[i]).getTitle();
            } else {
                titles[i] = "[None]";  // extra title for "no image" appended
            }
        }
        GenericDialog SIMcheckDialog = new GenericDialog("SIMcheck (v0.9)");
        SIMcheckDialog
                .addMessage( "--------------- INSTRUCTIONS ---------------");
        SIMcheckDialog
                .addMessage( "  1. Choose a raw and/or reconstructed SI data stacks.");
        SIMcheckDialog
                .addMessage( "  2. Tick boxes for checks you would like to run & click OK.");
        String helpMessage = "    \"Help\" below navigates to Micron web page about SIMcheck\n";
        helpMessage +=       "      or extract SIMcheck.html from SIMcheck_.jar using unzip";
        SIMcheckDialog.addMessage(helpMessage);
        SIMcheckDialog.addMessage("---------------- Raw data -----------------");
        SIMcheckDialog.addChoice("Raw_Data:", titles, titles[0]);
        SIMcheckDialog.addChoice("Data format:", formats, "API OMX (CPZAT)");
        SIMcheckDialog.addNumericField("angles", angles, 1);
        SIMcheckDialog.addNumericField("phases", phases, 1);
        SIMcheckDialog.addCheckbox("Raw_Intensity_Profile", true);
        SIMcheckDialog.addCheckbox("Raw_Fourier_Plots", true);
        SIMcheckDialog.addCheckbox("Raw_Angle_Difference", true);
        SIMcheckDialog.addCheckbox("Raw_Modulation_Contrast", true);
        SIMcheckDialog.addMessage("------------ Reconstructed data ------------");
        SIMcheckDialog.addChoice("Reconstructed_Data:", titles, titles[nullTitleIndex]);
        SIMcheckDialog.addCheckbox("SIR_Histogram", true);
        SIMcheckDialog.addCheckbox("SIR_Z_Variation", true);
        SIMcheckDialog.addCheckbox("SIR_Fourier Plot", true);
        SIMcheckDialog.addCheckbox(
                "SIR_Mod_Contrast_Map (requires Raw Mod Contrast)", true);
        SIMcheckDialog.addHelp(
                "http://www.micron.ox.ac.uk/microngroup/software/SIMcheck.html");
        SIMcheckDialog.showDialog();

        if (SIMcheckDialog.wasOKed()) {
            IJ.log(   "\n   =====================      "
                    + "\n                      SIMcheck        "
                    + "\n   =====================      ");
            angles = (int)SIMcheckDialog.getNextNumber();
            phases = (int)SIMcheckDialog.getNextNumber();
            ImagePlus modConImp = null; // to hold modulation contrast map
                                           // for re-use in SIR quality map
            // open B&C and channels tools
            IJ.run("Brightness/Contrast...");
            IJ.run("Channels Tool... ", "");

            IJ.log("\n ==== Raw data checks ====");
            int SIstackChoice = SIMcheckDialog.getNextChoiceIndex();
            int formatChoice = SIMcheckDialog.getNextChoiceIndex();
            if (SIstackChoice == nullTitleIndex) {
                IJ.log("  ! no raw SI data - cannot perform raw data checks");
                // get all raw data choices so SIR booleans make sense
                for (int nb = 0; nb < RAW_CHECKS; nb++) {
                    SIMcheckDialog.getNextBoolean();
                }
            } else {
                int SIstackID = wList[SIstackChoice];
                ImagePlus SIstackImp = ij.WindowManager.getImage(SIstackID);
                if (!I1l.stackDivisibleBy(SIstackImp, phases * angles)) {
                    IJ.log("  ! invalid raw SI data - raw data checks aborted");
                } else {
                    String SIstackName = SIstackImp.getTitle();
                    IJ.log("  Using SI stack: " + SIstackName + " (ID "
                            + SIstackID + ")");
                    IJ.log("    format: " + formats[formatChoice]);
                    Util_formats formatConverter = new Util_formats();
                    if (formatChoice != 0) {
                        IJ.log("      converting " + formats[formatChoice] 
                                + " to OMX format");
                        SIstackImp = formatConverter.exec(
                                SIstackImp, phases, angles, formatChoice - 1);
                    }
                    // do checks on raw SI data
                    if (SIMcheckDialog.getNextBoolean()) {
                        Raw_intensity raw_int_plugin = new Raw_intensity();
                        raw_int_plugin.phases = phases;
                        raw_int_plugin.angles = angles;
                        ResultSet results = raw_int_plugin.exec(SIstackImp);
                        results.report();
                    }
                    if (SIMcheckDialog.getNextBoolean()) {
                        Raw_Fourier raw_fourier_plugin = new Raw_Fourier();
                        raw_fourier_plugin.phases = phases;
                        raw_fourier_plugin.angles = angles;
                        ResultSet results = raw_fourier_plugin.exec(SIstackImp);
                        results.report();
                    }
                    if (SIMcheckDialog.getNextBoolean()) {
                        Raw_Angle_Difference raw_a_diff_plugin = new Raw_Angle_Difference();
                        raw_a_diff_plugin.phases = phases;
                        raw_a_diff_plugin.angles = angles;
                        ResultSet results = raw_a_diff_plugin.exec(SIstackImp);
                        results.report();
                    }
                    if (SIMcheckDialog.getNextBoolean()) {
                        Raw_ModContrast raw_MCNR_plugin = new Raw_ModContrast();
                        raw_MCNR_plugin.phases = phases;
                        raw_MCNR_plugin.angles = angles;
                        ResultSet results = raw_MCNR_plugin.exec(SIstackImp);
                        modConImp = results.getImp(0);
                        results.report();
                    }
                }
            }
            
            IJ.log("\n ==== Reconstructed data checks ====");
            int SIRstackChoice = SIMcheckDialog.getNextChoiceIndex();
            if (SIRstackChoice >= wList.length) {
                IJ.log("  ! no SIR data - cannot perform SIR data checks");
            } else {
                int SIRstackID = wList[SIRstackChoice];
                ImagePlus SIRstackImp = ij.WindowManager.getImage(SIRstackID);
                if (!(SIRstackImp.getStackSize() > 1)) {
                    IJ.log("  ! invalid SIR data - SIR checks aborted");
                    return;
                }
                String SIRstackName = SIRstackImp.getTitle();
                IJ.log("  using SIR stack: " + SIRstackName + " (ID "
                        + SIRstackID + ")");
                // do checks on SIR reconstructed data
                if (SIMcheckDialog.getNextBoolean()) {
                    SIR_histogram sir_hist_plugin = new SIR_histogram();
                    ResultSet results = sir_hist_plugin.exec(SIRstackImp);
                    results.report();
                }
                if (SIMcheckDialog.getNextBoolean()) {
                    SIR_Z_variation sir_z_var_plugin = new SIR_Z_variation();
                    ResultSet results = sir_z_var_plugin.exec(SIRstackImp);
                    results.report();
                }
                if (SIMcheckDialog.getNextBoolean()) {
                    SIR_Fourier sir_fourier_plugin = new SIR_Fourier();
                    ResultSet results = sir_fourier_plugin.exec(SIRstackImp);
                    results.report();
                }
                if (SIMcheckDialog.getNextBoolean()) {
                    SIR_ModContrastMap sir_mcnr_plugin = 
                        new SIR_ModContrastMap();
                    ResultSet results = 
                            sir_mcnr_plugin.exec(SIRstackImp, modConImp);
                    results.report();
                }
            }
            IJ.log("\n\n\n");
        }
    }
}
