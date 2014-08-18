/*  
 *  Copyright (c) 2013, Graeme Ball and Micron Oxford,                          
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
import ij.process.ImageStatistics;
import ij.gui.GenericDialog; 
import ij.IJ;

/** This plugin takes raw SI data and splits each angle into a separate stack     
 * to which a 2D FFT is applied to each slice.
 * @author Graeme Ball <graemeball@gmail.com>
 */ 
public class Raw_FourierProjections implements PlugIn, Executable {

    public static final String name = "Raw Data Fourier Projections";
    public static final String TLA = "FPJ";
    private ResultSet results = new ResultSet(name);

    // parameter fields
    public int phases = 5;                                                         
    public int angles = 3;                                                         

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

    /** Execute plugin functionality (old version): split angles into separate
     * stacks and perform 2D FFT on each slice for V2 OMX CPZAT dimension order.
     * @param imps first imp should be input raw SI data ImagePlus
     * @return ResultSet containing FFTs for each angle
     */
    public ResultSet exec(ImagePlus... imps) {
        ImagePlus imp = imps[0];
        ImagePlus montage = null;
        StackCombiner comb = new StackCombiner();
        for (int a = 1; a <= angles; a++) {
          	ImagePlus impCurrentA = SIMcheck_.getImpForAngle(
          	        imp, a, phases, angles);
          	String statusString = "Performing FFT for angle " 
          			+ Integer.toString(a);
          	IJ.showStatus(statusString);
          	impCurrentA = FFT2D.fftImp(impCurrentA);
          	String title = I1l.makeTitle(imp, "FT" + a);
          	impCurrentA.setTitle(title);
          	IJ.run(impCurrentA, "Z Project...", "projection=[Max Intensity]");
          	impCurrentA = ij.WindowManager.getCurrentImage();
          	int nChannels = impCurrentA.getNChannels();
          	ImagePlus[] impChannels = new ImagePlus[nChannels];
          	for (int c = 1; c <= nChannels; c++) {
          	    ImagePlus impC = I1l.copyChannel(impCurrentA, c);
          	    ImageStatistics stats = impC.getStatistics();
          	    double newMin = stats.dmode - stats.stdDev;
          	    I1l.rescale8bitToMinMax(impC, newMin, stats.max);
          	    I1l.drawLabel(impC, "A" + a);
          	    impChannels[c - 1] = impC;
          	}
          	impCurrentA.getWindow().close();
          	impCurrentA = I1l.mergeChannels("FFT_A" + a, impChannels);
          	impCurrentA.setC(1);
          	if (a == 1) {
          	    montage = impCurrentA.duplicate();
          	} else {
          	    ImageStack montageStack = comb.combineHorizontally(
          	            montage.getStack(), impCurrentA.getStack());
          	    montage.setStack(montageStack);
          	}
          	// TODO: clear MAX_<originalTitle>_FT2.tif and "FT3.tif
          	//       from Window list after the plugin exits ()
          	impCurrentA.close();  // only seems to fully close "FT1.tif
        }
        IJ.run(montage, "Grays", "");
        montage.setTitle(I1l.makeTitle(imps[0], TLA));
      	results.addImp("2D FFT max projection montage for angles 1-" + angles,
      	        montage);
        results.addInfo("How to interpret", 
                "look for clean 1st & 2nd order spots, similar across angles.");
        return results;
    }
    
    /** Interactive test method */
    public static void main(String[] args) {
        new ImageJ();
        TestData.raw.show();
        IJ.runPlugIn(Raw_FourierProjections.class.getName(), "");
    }
}


