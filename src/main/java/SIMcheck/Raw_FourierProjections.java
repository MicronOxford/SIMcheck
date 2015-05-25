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
import ij.process.*;
import ij.gui.GenericDialog; 
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
     * auto-scaling and projection over all slices (phase, Z angle).
     */
    public ResultSet exec(ImagePlus... imps) {
        ImagePlus imp = imps[0];
        Util_StackFFT2D stackFFT2D = new Util_StackFFT2D();
        ImagePlus impF = stackFFT2D.exec(imp);
        autoscaleSlices(impF);
        IJ.run(impF, "Z Project...", "projection=[Max Intensity]");
        ImagePlus impProjF = ij.WindowManager.getCurrentImage();
        if (impProjF.isComposite()) {
            // display grayscale, not colored composite
            CompositeImage ci = (CompositeImage)impProjF;
            ci.setMode(IJ.GRAYSCALE);
            impProjF.updateAndDraw();
        }
        impProjF.setTitle(I1l.makeTitle(imps[0], TLA));
        results.addImp("2D FFT max-intensity projection", impProjF);
        results.addInfo("How to interpret", "look for clean 1st & 2nd"
                + " order spots, similar across angles. Note that Spot"
                + " intensity depends on image content.");
        return results;
    }
    
    /** Rescale 8-bit imp 0-255 for input mode to max. */
    private void autoscaleSlices(ImagePlus imp) {
        // FIXME, duplicated in Rec_fourier
        int ns = imp.getStackSize();
        for (int s = 1; s <= ns; s++) {
            imp.setSlice(s);
            ImageProcessor ip = imp.getProcessor();
            ImageStatistics stats = imp.getProcessor().getStatistics();
            int min = (int)stats.mode;
            int max = (int)stats.max;
            ByteProcessor bp = (ByteProcessor)imp.getProcessor();
            ip = (ImageProcessor)I1l.setBPminMax(bp, min, max, 255);
            imp.setProcessor(ip);
        }
    }
    
    /** Execute old plugin functionality: apply stack FFT with win func,
     * bleach correction "simple ratio", subtract 50, max-intensity
     * project, auto-contrast mode-max.
     */
    public ResultSet exec2(ImagePlus... imps) {
        ImagePlus imp = imps[0];
        imp = Util_RescaleTo16bit.exec(imp);
        Util_StackFFT2D stackFFT2D = new Util_StackFFT2D();
        ImagePlus impF = stackFFT2D.exec(imp);
        IJ.run(impF, "Subtract...", "value=" + offsetF + " stack");
        ImagePlus impNormF = impF.duplicate();
        impNormF.setStack(I1l.normalizeStack(impF.getStack()));
        IJ.run(impNormF, "Z Project...", "projection=[Max Intensity]");
        ImagePlus impProjF = ij.WindowManager.getCurrentImage();
        for (int c = 0; c < impProjF.getNChannels(); c++) {
            impProjF.setC(c+1);
//            int projMode = impProjF.getStatistics().mode;
//            int projMax = (int)impProjF.getStatistics().max;
//            IJ.setMinAndMax(imp, projMode, projMax);
//            IJ.run(imp, "Apply LUT", "");
//        impProjF.setProcessor((ImageProcessor)I1l.setBPminMax(
//                (ByteProcessor)impProjF.getProcessor(),
//                projMode, projMax, 255));  // rescale input mode-max to 0-255
        }
        impProjF.setTitle(I1l.makeTitle(imps[0], TLA));
        results.addImp("2D FFT max-intensity projection", impProjF);
        results.addInfo("How to interpret", "look for clean 1st & 2nd" +
                " order spots, similar across angles. N.B. Spot intensity" +
                " depends on image content.");
        return results;
    }        

    /** Execute even older plugin functionality: split angles into separate
     * stacks and perform 2D FFT on each slice for V2 OMX CPZAT dimension order.
     * @param imps first imp should be input raw SI data ImagePlus
     * @return ResultSet containing FFTs for each angle
     */
    public ResultSet exec3(ImagePlus... imps) {
        ImagePlus imp = imps[0];
        ImagePlus montage = null;
        StackCombiner comb = new StackCombiner();
        for (int a = 1; a <= angles; a++) {
          	ImagePlus impCurrentA = SIMcheck_.getImpForAngle(
          	        imp, a, phases, angles);
//          	impCurrentA = Util_RescaleTo16bit.exec(impCurrentA);
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
      	results.addImp("2D FFT montage for each angle (max projection)",
      	        montage);
        results.addInfo("How to interpret", "look for clean 1st & 2nd" +
      	        " order spots, similar across angles. N.B. Spot intensity" +
                " depends on image content.");
        return results;
    }
    
    /** Interactive test method */
    public static void main(String[] args) {
        new ImageJ();
        TestData.raw.show();
        IJ.runPlugIn(Raw_FourierProjections.class.getName(), "");
    }
}


