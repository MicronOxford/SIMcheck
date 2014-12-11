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
import ij.ImageJ;
import ij.ImagePlus;
import ij.plugin.PlugIn;

/** 
 * This plugin carries out ImageJ's 2D FFT on each slice of a stack. 
 * @author Graeme Ball <graemeball@gmail.com>
 */ 
public class Util_StackFFT2D implements PlugIn {
    
    public static final String name = "Stack FFT (2D)";
    public static final String TLA = "FFT";
    
    @Override 
    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        IJ.showStatus("FFT stack...");
        ImagePlus impF = exec(imp);
        impF.show();
    }
    
    /** Execute plugin functionality: 2D FFT each slice.
     * @param imp input format ImagePlus
     * @return ImagePlus after 2D FFT of each slice
     */ 
    public ImagePlus exec(ImagePlus imp) {
        ImagePlus impF = FFT2D.fftImp(imp);
        impF.setTitle(I1l.makeTitle(imp, TLA));
        return impF;
    }
    
    /** Interactive test method. */
    public static void main(String[] args) {
        new ImageJ();
        TestData.raw.show();
        IJ.runPlugIn(Util_StackFFT2D.class.getName(), "");
    }
}
