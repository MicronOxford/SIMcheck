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
import ij.plugin.*;
import ij.process.*;
import ij.gui.GenericDialog; 

/** This plugin converts a SIM image from any supported format into the 
 * OMX V2 format. Output hyperstacks arranged in CPZAT order. 
 * Supported formats are: Zeiss ELYRA &amp; Nikon N-SIM (TODO).
 * @author Graeme Ball <graemeball@gmail.com>
 */ 
public class Util_FormatConverter implements PlugIn {

    // supported formats
    public static String[] formats = {
        "Zeiss ELYRA (CZAPT)",
        "Nikon N-SIM (tiled)"};

    // parameter fields
    public int phases = 5;                                                         
    public int angles = 3;                                                         
    
    private int width, height, nc, nz, nt;
    private ImageStack inStack, outStack;
    
    @Override 
    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        GenericDialog gd = new GenericDialog("SIM Formats");
        gd.addMessage("Conversion to OMX (CPZAT) order.");        
        int formatChoice = 0;  // default choice (=ELYRA)
        gd.addNumericField("Angles", angles, 0);
        gd.addNumericField("Phases", phases, 0);
        gd.addChoice("Data format:", formats, formats[formatChoice]);
        gd.showDialog();
        if (gd.wasCanceled()) return;
        if(gd.wasOKed()){                                                     
            angles = (int)gd.getNextNumber();                                   
            phases = (int)gd.getNextNumber();                                   
            formatChoice = gd.getNextChoiceIndex();
        } 
        try {
            ImagePlus convertedImp = exec(imp, phases, angles, formatChoice);
            convertedImp.show();
        } catch (IllegalArgumentException e) {
            IJ.showMessage("Bad input", e.getMessage());
        }
    }

    /** Execute plugin functionality:
     * @param imp input format ImagePlus (single stack)
     * @param phases number of phases                                   
     * @param angles number of angles                                   
     * @param format number: 0=ELYRA, 1=NSIM
     * @return ImagePlus re-ordered into OMX V2 (CPZAT) order
     */ 
    public ImagePlus exec(ImagePlus imp, int phases, int angles, int format) {
        this.phases = phases;
        this.angles = angles;
        this.width = imp.getWidth();
        this.height = imp.getHeight();
        this.nc = imp.getNChannels();
        this.nz = imp.getNSlices();
        this.nt = imp.getNFrames();
        this.inStack = imp.getStack();
        this.outStack = null;
        IJ.log("   converting format " + formats[format]);
        if (format == 0) {
            convertELYRA();
        } else if (format == 1) {
            convertNSIM();
        } else {
            throw new IllegalArgumentException("Unknown format " + format);
        }
        ImagePlus convertedImp = 
                new ImagePlus(I1l.makeTitle(imp, "OMX"), outStack);
        convertedImp.copyScale(imp);
        convertedImp.setDimensions(nc, (phases * nz * angles), nt);
        convertedImp.setOpenAsHyperStack(true);
        return convertedImp;
    }
    
    /** Convert ELYRA data (CZTAP order) to OMX order (CPZAT) */
    private void convertELYRA() {
        // ELYRA data: angles encoded in Z, phases in time dimension
        if (nt % phases != 0 || nz % angles != 0) {
            String issue = "For raw ELYRA data expect angles in Z, phases in T";
            IJ.log(issue);
            throw new IllegalArgumentException(issue);
        }
        // remove phases & angles for true number of frames & Z
        nt /= phases;
        nz /= angles;
        this.outStack = new ImageStack(width, height);
        // loop through in desired output order: OMX (CPZAT)
        for (int t = 1; t <= nt; t++) {
            for (int a = 1; a <= angles; a++) {
                for (int z = 1; z <= nz; z++) {
                    for (int p = 1; p <= phases; p++) {
                        for (int c = 1; c <= nc; c++) {
                            // Zeiss ELYRA (CZAPT)
                            int inSlice = I1l.stackSliceNo( 
                                    c, nc, z, nz, a, angles, p, phases, t, nt);
                            ImageProcessor ip = inStack.getProcessor(inSlice);
                            outStack.addSlice(ip);
                        }
                    }
                }
            }
        }
    }

    /** Convert NSIM data (P, A tiled in X, Y) to OMX order (CPZAT). */
    private void convertNSIM() {
        // Nikon N-SIM: phases tiled in X, angles tiled in Y
        // (assuming NISM data are CZT dimension order)
        if ((width % phases != 0) || (height % angles != 0)) {
            String problem = "Expected NSIM .nd2 with phase/angle tiled in x/y";
            IJ.log(problem);
            throw new IllegalArgumentException(problem);
        }
        int realWidth = width / phases;
        int realHeight = height / angles;
        this.outStack = new ImageStack(realWidth, realHeight);
        Duplicator dup = new Duplicator();
        ImagePlus imp = new ImagePlus("raw", inStack);
        imp.setDimensions(nc, nz, nt);
        imp.show();  // otherwise imp.setRoi() is ignored :-/
        ImagePlus impCrop = null;  // for a single output slice
        int slice = 1;
        for (int t = 1; t <= nt; t++) {
            for (int a = 1; a <= angles; a++) {
                for (int z = 1; z <= nz; z++) {
                    for (int p = 1; p <= phases; p++) {
                        for (int c = 1; c <= nc; c++) {
                            // use ROIs to crop out tiles
                            int roiX = realWidth * (p - 1);
                            int roiY = realHeight * (a - 1);
                            imp.setPosition(c, z, t);
                            imp.setRoi(roiX, roiY, realWidth, realHeight);
                            String label =
                                    "C" + c + "/P" + p + "/Z" + z + "/A" + a;
                            impCrop = dup.run(imp, c, c, z, z, t, t);
                            outStack.addSlice(impCrop.getProcessor());
                            outStack.setSliceLabel(label, slice++);
                        }
                    }
                }
            }
        }
        imp.close();
    }
    
    /** Interactive test method */
    public static void main(String[] args) {
        new ImageJ();
        TestData.elyra.show();
        IJ.runPlugIn(Util_FormatConverter.class.getName(), "");
    }
}
