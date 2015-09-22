/*  
 *  Copyright (c) 2015, Graeme Ball and Micron Oxford,                          
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
import ij.gui.GenericDialog; 
import ij.gui.OvalRoi;
import ij.IJ;

/** This plugin takes raw SIM data and splits each angle into a separate stack     
 * to which a 2D FFT is applied to each slice. The result shows, for each
 * channel, a maximum intensity projection over all phases, angles and Z.
 * @author Graeme Ball <graemeball@gmail.com>
 */ 
public class Raw_FourierProjections implements PlugIn, Executable {

    public static final String name = "Raw Data Fourier Projections";
    public static final String TLA = "FPJ";
    private ResultSet results = new ResultSet(name, TLA);

    // parameter fields
    public int phases = 5;                                                         
    public int angles = 3;                    
    public float offsetF = 75;
    public double maskDiameter = 0.125; // (as fraction of image width)

    @Override
    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        GenericDialog gd = new GenericDialog(name);                   
        gd.addMessage("Requires SI raw data in OMX (CPZAT) order.");        
        gd.addNumericField("angles", angles, 0);                               
        gd.addNumericField("phases", phases, 0);                               
        gd.showDialog();                                                        
        if (gd.wasCanceled()) return;
        if( gd.wasOKed() ){                                                     
            angles = (int)gd.getNextNumber();                                   
            phases = (int)gd.getNextNumber();                                   
        }                                                                       
        if (!I1l.stackDivisibleBy(imp, phases * angles)) {    
            IJ.showMessage(name,
            		"Error: stack size not consistent with phases/angles.");
            return;                                                             
        }
        results = exec(imp);
        results.report();
    }
    
    /**
     * Execute plugin functionality: stack FFT with window function,
     * max projection over all slices (phase, Z angle), blank out central
     * 1/8 circle (set to min value), display min-max.
     */
    public ResultSet exec(ImagePlus... imps) {
        ImagePlus imp = imps[0];
        Util_StackFFT2D stackFFT2D = new Util_StackFFT2D();
        stackFFT2D.resultTypeChoice = Util_StackFFT2D.resultType[1];
        ImagePlus impF = stackFFT2D.exec(imp);
        IJ.run(impF, "Z Project...", "projection=[Max Intensity]");
        ImagePlus impProjF = ij.WindowManager.getCurrentImage();
        maskCentralRegion(impProjF);
        if (impProjF.isComposite()) {
            // display grayscale, not colored composite
            CompositeImage ci = (CompositeImage)impProjF;
            ci.setMode(IJ.GRAYSCALE);
            impProjF.updateAndDraw();
        }
        displayMinToMax(impProjF);
        impProjF.setTitle(I1l.makeTitle(imps[0], TLA));
        String shortInfo = "Maximum intensity projection of log"
                + " (amplitude^2) 2D FFT stack, central region masked,"
                + " rescaled (min-max) to improve contrast of the relevant"
                + " frequency range.";
        results.addImp(shortInfo, impProjF);
        results.addInfo("How to interpret", "look for clean 1st & 2nd"
                + " order spots, similar across angles. Note that spot"
                + " intensity depends on image content.");
        return results;
    }
    
    /** Mask central offset / low freq spike to better use display range. */
    private void maskCentralRegion(ImagePlus imp) {
        int w = imp.getWidth();
        double d = this.maskDiameter * w;
        if (w % 2 == 0) {
            d += 1;  // tweak to centre the mask about zero freq stripes
        }
        // N.B. we assume the image is square! (FFT result)
        OvalRoi mask = new OvalRoi(w/2 - d/2 + 1, w/2 - d/2 + 1, d, d);
        imp.setRoi(mask);
            for (int c = 1; c <= imp.getNChannels(); c++) {
                imp.setC(c);
                double min = I1l.getStatsForChannel(imp, c).min;
                IJ.run(imp, "Set...", "value=" + min + " slice");
            }
            imp.setC(1);
        imp.deleteRoi();
    }

    /** Set display range for each channel to min-max. */
    private static void displayMinToMax(ImagePlus imp) {
        if (imp.isComposite()) {
            for (int c = 1; c <= imp.getNChannels(); c++) {
                double min = I1l.getStatsForChannel(imp, c).min;
                double max = I1l.getStatsForChannel(imp, c).max;
                imp.setC(c);
                IJ.setMinAndMax(imp, min, max);
            }
            imp.setC(1);
        } else {
            double min = imp.getStatistics().min;
            double max = imp.getStatistics().max;
            IJ.setMinAndMax(imp, min, max);
        }
    }
    
    /** Interactive test method */
    public static void main(String[] args) {
        new ImageJ();
        TestData.raw.show();
        IJ.runPlugIn(Raw_FourierProjections.class.getName(), "");
    }
}


