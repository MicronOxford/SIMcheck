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
import ij.plugin.filter.*;
import ij.process.*;
import ij.gui.GenericDialog;
import ij.IJ;
import ij.measure.*;

/** This plugin takes raw SI data (1C, 1T) for a bead lawn and shows
 * an axial side-view of the illumination pattern for the first phase.
 * @author Graeme Ball <graemeball@gmail.com>
 **/
public class Cal_PatternFocus implements PlugIn {

    String name = "Calibration check pattern focus";
    ResultSet results = new ResultSet(name);
	int phases = 5;
	int angles = 3;
	private int width;
	private int height;
	
    // default parameters
    double angle1 = 0.00d;      // 1st illumination pattern angle in degrees

    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        int nz = imp.getNSlices() / (phases * angles);
        GenericDialog gd = new GenericDialog("Caibrate Pattern Focus");
        gd.addMessage("Requires SI raw data in API OMX (CPZAT) order.");
        gd.addNumericField("Angles", angles, 1);
        gd.addNumericField("Phases", phases, 1);
        gd.addNumericField("Angle 1 (deg)", angle1, 1);
        gd.showDialog();
        if (gd.wasCanceled()) return;
        if (gd.wasOKed()) {
            angles = (int)gd.getNextNumber();
            phases = (int)gd.getNextNumber();
            angle1 = (double)gd.getNextNumber();
        }
        if (!I1l.stackDivisibleBy(imp, phases * angles)) {
            IJ.showMessage( "Calibrate Pattern Focus", 
                    "Error: stack size not consistent with phases/angles." );
            return;
        }
        results = exec(imp);
        results.report();
    }

    /** Execute plugin functionality: split out phase1 for 3 angles
     * and show max-intensity-projected axial view.
     * @param imps input raw SI data ImagePlus should be first imp
     * @return ResultSet containing 3 projections
     */
    public ResultSet exec(ImagePlus... imps) {
        ImagePlus imp = imps[0];
        width = imp.getWidth();
        height = imp.getHeight();
        // TODO, check nChannels & nFrames & exit if > 1
        double angleDegrees = 90.0d - angle1;
        ImagePlus[] phase1imps = phase1eachAngle(imp);
        for (int a = 0; a < angles; a++) {
            rotateStripes(phase1imps[a], angleDegrees);
            phase1imps[a] = resliceAndProject(phase1imps[a]);
            String title = I1l.makeTitle(imp, "APF" + (a + 1));
            phase1imps[a].setTitle(title);
            results.addImp("Angle " + String.format("%.1f", angleDegrees) + 
                    " pattern focus", phase1imps[a]);
            angleDegrees += 180.0d / angles;  // angles cover 180 deg
        }
        return results;
    }
    
    /** Return 1 stack per angle, first phase only. */
    private ImagePlus[] phase1eachAngle(ImagePlus imp) {
        ImagePlus[] phase1Imps = new ImagePlus[angles];
        int nz = imp.getNSlices();
        nz /= phases * angles;
        int ix = 0;
        for (int a = 0; a < angles; a++) {
            ImageStack stack = new ImageStack(width, height);
            for (int z = 0; z < nz; z++) {
                for (int p = 0; p < phases; p++) {
                    ImageProcessor ip = imp.getStack().getProcessor(++ix);
                    if (p == 0) {
                        stack.addSlice(ip.duplicate());
                    }
                }
            }
            phase1Imps[a] = new ImagePlus("SI Pattern P1, A" + (a + 1), stack);
            I1l.copyCal(imp, phase1Imps[a]);
        }
        return phase1Imps;
    }
    
    /** Rotate stripes for each angle to vertical. */
    private void rotateStripes(ImagePlus imp2, double angleDeg) {
        // FIXME, rotate by angles: -48 72 11 (in IJ, E is 0, in worx N is 0)
        angleDeg = -angleDeg;
        IJ.log("rotating " + imp2.getTitle() + " by " + angleDeg + " degrees");
        IJ.run(imp2, "Rotate... ", "angle=" + angleDeg +
                " grid=1 interpolation=Bilinear stack");
    }
    
    /** Reslice to XZ view and max-project along y. */
    private ImagePlus resliceAndProject(ImagePlus imp2) {
        // TODO, reslice over range?
        int ymin = (int)(height * 0.33);
        int ymax = (int)(height * 0.67);
        IJ.run(imp2, "Reslice [/]...", "output=" + 
                imp2.getCalibration().pixelDepth + " start=Top avoid");
        ImagePlus impResliced = IJ.getImage();
        IJ.run(impResliced, "Z Project...", "projection=[Max Intensity]");
        ImagePlus impProjected = IJ.getImage();
        imp2.close();
        impResliced.close();
        return impProjected;
    }
    
    public static void main(String[] args) {
        System.out.println("Testing...");
    }
    
}
