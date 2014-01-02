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
import ij.process.*;
import ij.gui.GenericDialog; 
import ij.IJ;

/** This plugin takes raw SI data and splits each angle into a separate stack     
 * to which a 2D FFT is applied to each slice.
 * @author Graeme Ball <graemeball@gmail.com>
 */ 
public class Raw_Fourier implements PlugIn, EProcessor {

    String name = "Raw Data Fourier plots";
    ResultSet results = new ResultSet(name);

    // parameter fields
    public int phases = 5;                                                         
    public int angles = 3;                                                         

    @Override
    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        GenericDialog gd = new GenericDialog(name);                   
        gd.addMessage("Requires SI raw data in API OMX (CPZAT) order.");        
        gd.addNumericField("angles", angles, 1 );                               
        gd.addNumericField("phases", phases, 1 );                               
        gd.showDialog();                                                        
        if (gd.wasCanceled()) return;
        if( gd.wasOKed() ){                                                     
            angles = (int)gd.getNextNumber();                                   
            phases = (int)gd.getNextNumber();                                   
        }                                                                       
        if (!I1l.stackDivisibleBy(imp, phases * angles)) {    
            IJ.showMessage("Raw Data Fourier Plots",
            		"Error: stack size not consistent with phases/angles.");
            return;                                                             
        }
        results = exec(imp);
        results.report();
    }

    /** Execute plugin functionality: split angles into separate stacks and
     * perform 2D FFT on each slice. Assumes V2 OMX CPZAT dimension order.
     * @param imps first imp should be input raw SI data ImagePlus
     * @return ResultSet containing FFTs for each angle
     */
    public ResultSet exec(ImagePlus... imps) {
        ImagePlus imp = imps[0];
        for (int a = 1; a <= angles; a++) {
          	ImagePlus impCurrentA = getImpForAngle(imp, a);
          	String statusString = "Performing FFT for angle " 
          			+ Integer.toString(a);
          	IJ.showStatus(statusString);
          	impCurrentA = FFT2D.fftImp(impCurrentA);
          	String title = I1l.makeTitle(imp, "FT" + a);
          	impCurrentA.setTitle(title);
          	results.addImp("2D FFT for angle " + a, impCurrentA);
        }
        results.addInfo("Fourier-transformed raw data", 
                "check for clean 1st & 2nd order spots");
        return results;
    }
    
    /** Split hyperstack, returning new ImagePlus for angle requested.
     * Assumes API V2 OMX CPZAT channel order. 
     */
    ImagePlus getImpForAngle(ImagePlus impAll, int a) {
    	int nc = impAll.getNChannels();
      	int nz = impAll.getNSlices();
      	int nt = impAll.getNFrames();
      	nz = nz / (phases * angles);  // take phase & angle out of Z
      	int sliceIn = 0;
      	int sliceOut = 0;
      	int width = impAll.getWidth();
        int height = impAll.getHeight();
      	ImageStack stackAll = impAll.getStack();
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
    	String title = I1l.makeTitle(impAll, "A" + a);
        ImagePlus impOut = new ImagePlus(title, stackOut);
        impOut.setDimensions(nc, nz * phases, nt);
        I1l.copyCal(impAll, impOut);
        int centralZ = ((nz / 2) * phases) - phases + 1;  // 1st phase
        impOut.setPosition(1, centralZ, 1);
        impOut.setOpenAsHyperStack(true);
        return impOut;
    }
}


