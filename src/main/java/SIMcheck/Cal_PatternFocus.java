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
import ij.gui.NonBlockingGenericDialog;
import ij.IJ;
import ij.gui.*;

import java.awt.Color;

import ij.plugin.StackCombiner;

/** This plugin takes raw SIM data (1C, 1T) for a bead lawn and shows
 * an axial side-view of the illumination pattern for the first phase.
 * @author Graeme Ball <graemeball@gmail.com>
 **/
public class Cal_PatternFocus implements PlugIn, Executable {

    public static final String name = "Illumination Pattern Focus";
    public static final String TLA = "IPF";
    ResultSet results = new ResultSet(name, TLA);
	
	private int width;
	private int height;
	private static final String[] angleMethods = {
	    "degrees (IJ)", "IJ line selection**", "radians (OMX)"
	};
	
	// parameter fields
	public int phases = 5;
	public int angles = 3;
	public double angle1 = 0.00d;  // 1st pattern angle (IJ: deg CCW from E)
	public String angleMethod = angleMethods[0];
	public boolean showRotated = false;
	
    @Override
    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        NonBlockingGenericDialog gd = new NonBlockingGenericDialog(name);
        gd.addMessage("Requires SI raw data in OMX (CPZAT) order.");
        gd.addNumericField("Angles", angles, 0);
        gd.addNumericField("Phases", phases, 0);
        // NB. in IJ, East is 0, in worx North is 0 (CCW is +ve in both cases)
        angle1 = ij.Prefs.get("SIMcheck.angle1", angle1);
        gd.addNumericField("Angle 1 (deg, IJ)", angle1, 1);
        gd.addNumericField("Angle 1 (rad, OMX)", ij2omx(angle1), 3);
        gd.addRadioButtonGroup("Method to specify angle", angleMethods,
                1, angleMethods.length, angleMethods[0]);
        gd.addCheckbox("Show rotated illumination patterns?", showRotated);
        gd.addMessage("** Select focal plane in angle 1 & draw line from" +
                      " bottom to top end of an arbitrary stripe.");
        gd.showDialog();
        if (gd.wasCanceled()) return;
        if (gd.wasOKed()) {
            angleMethod = gd.getNextRadioButton();
            angles = (int)gd.getNextNumber();
            phases = (int)gd.getNextNumber();
            if (angleMethod.equals(angleMethods[0])) {
                // IJ degrees
                angle1 = (double)gd.getNextNumber();
            } else if (angleMethod.equals(angleMethods[1])) {
                // line selection
                try {
                    angle1 = imp.getRoi().getAngle();
                } catch (NullPointerException e) {
                    IJ.error("Error",
                            "You forgot to draw a line selection!");
                    return;
                }
            } else {
                // OMX radians
                gd.getNextNumber();  // angle in degrees: discard!
                angle1 = omx2ij((double)gd.getNextNumber());
            }
            ij.Prefs.set("SIMcheck.angle1", angle1);
            showRotated = gd.getNextBoolean();
        }
        if (!I1l.stackDivisibleBy(imp, phases * angles)) {
            IJ.error( "Calibrate Pattern Focus", 
                    "Error: stack size not consistent with phases/angles." );
            return;
        }
        results = exec(imp);
        results.report();
    }

    /** Execute plugin functionality: split out phase1 for 3 angles
     * and show max-intensity-projected axial view.
     * @param imps input raw SIM data ImagePlus should be first imp
     * @return ResultSet containing montage of 3 projections
     */
    public ResultSet exec(ImagePlus... imps) {
        ImagePlus imp = imps[0];
        width = imp.getWidth();
        height = imp.getHeight();
        if (imp.getNChannels() > 1 || imp.getNFrames() > 1) {
            IJ.showMessage("Error", name + " only works for 1 channel/frame");
            return results;
        }
        imp.setStack(I1l.normalizeStack(imp.getStack()));
        double angleDegrees = 90.0d - angle1;
        ImagePlus[] phase1imps = phase1eachAngle(imp);
        ImagePlus montage = null;
        StackCombiner comb = new StackCombiner();
        for (int a = 0; a < angles; a++) {
            rotateStripes(phase1imps[a], angleDegrees);
            if (showRotated) {
                results.addImp("Angle " + (a + 1) + " (phase 1) SI image"
                        + " rotated " + String.format("%.1f", angleDegrees) +
                        "º CCW from East", phase1imps[a]);
            }
            phase1imps[a] = resliceAndProject(phase1imps[a].duplicate());
            IJ.run(phase1imps[a], "Enhance Contrast", "saturated=0.35");
            String label = "A" + (a + 1);
            String title = I1l.makeTitle(imp, "APF" + (a + 1));
            phase1imps[a].setTitle(title);
            I1l.drawLabel(phase1imps[a], label);
            if (a == 0) {
                montage = phase1imps[a].duplicate();
            } else {
                ImageStack montageStack = comb.combineVertically(
                        montage.getStack(), phase1imps[a].getStack());
                montage.setStack(montageStack);
            }
            angleDegrees += 180.0d / angles;  // angles cover 180 deg
        }
        for (int a = 0; a < angles; a++) {
            phase1imps[a].close();
        }
        montage.setTitle(I1l.makeTitle(imp, TLA));
        String description = "Projected side view along the illumination"
                + " stripes (phase 1 only) for each angle to illustrate"
                + " alignment of the z-modulation with the focal plane."
                + " Slice intensities are normalized to correct for"
                + " intensity variations.";
        results.addImp(description, montage);
        results.addInfo("\nHow to interpret",
                "100 nm bead layer in the focal plane should display distinct"
                + " intensity modulation (side view of first order stripes."
                + " Intensity dips above and below the intensity peaks should"
                + " be balanced. A \"zipper-like\" appearance (i.e. two"
                + " staggered layers of modulated intensities) indicate"
                + " defocussing of the z-modulation against the focal plane."
                + " All angles should have the same characteristics.");
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
            phase1Imps[a] = new ImagePlus("Angle " + (a + 1) + " (Phase 1)",
                    stack);
            I1l.copyCal(imp, phase1Imps[a]);
        }
        return phase1Imps;
    }
    
    /** Rotate stripes for each angle to vertical. */
    private void rotateStripes(ImagePlus imp2, double angleDeg) {
        angleDeg = -angleDeg;
        results.addInfo(imp2.getTitle(), "rotated " + J.d2s(angleDeg) + "° CCW");
        IJ.run(imp2, "Rotate... ", "angle=" + angleDeg +
                " grid=1 interpolation=Bilinear stack");
        // draw central vertical line to visually check angle after rotation 
        imp2.setOverlay((Roi)(new Line(width / 2, 0, width / 2, height)),
                Color.YELLOW, 1, Color.YELLOW);
        imp2.setSlice(imp2.getNSlices() / 2);
        imp2.updateAndDraw();
    }
    
    /** Reslice to XZ view and max-project along y. */
    private ImagePlus resliceAndProject(ImagePlus imp2) {
        imp2.show();
        // reslice over central half of rotated image
        int ymin = (int)(height * 0.25);
        int ymax = (int)(height * 0.75);
        imp2.setRoi(0, ymin, width, ymax - ymin);
        IJ.run(imp2, "Reslice [/]...", "output=" + 
                imp2.getCalibration().pixelDepth + " start=Top");
        ImagePlus impResliced = IJ.getImage();
        IJ.run(impResliced, "Z Project...", "projection=[Max Intensity]");
        ImagePlus impProjected = IJ.getImage();
        imp2.close();
        impResliced.close();
        return impProjected;
    }

    /** Convert ImageJ angle (degrees CCW from E) to OMX (rad CCW from N). */
    static double ij2omx(double angle) {
        return Math.toRadians(angle - 90.0d);
    }
    
    /** Convert OMX angle (radians CCW from N) to IJ (deg CCW from E). */
    static double omx2ij(double angle) {
        double angleDeg = Math.toDegrees(angle) + 90.0d;
        if (angleDeg < 0.0d) {
            return 180.0d + angleDeg;
        } else {
            return angleDeg;
        }
    }
    
    /** Unit test runner for private methods, returns true if all OK. */
    boolean test(boolean verbose) {
        boolean passAll = true;
        double[] omxAngles = {-0.84d, -1.88d, 0.20d};
        double[] ijAngles = {42.0d, 162.0d, 102.0d};
        if (verbose) { IJ.log("* testing ij2omx() and omx2ij() conversions:"); }
        for (int i = 0; i < ijAngles.length; i++) {
            boolean pass = J.approxEq(ijAngles[i], omx2ij(omxAngles[i]));
            // ij2omx is complicated to test: may not give angle with same sign
            if (verbose) {
                IJ.log(ijAngles[i] + " -> ij2omx -> " + ij2omx(ijAngles[i]));
                IJ.log(omxAngles[i] + " -> omx2ij -> " + omx2ij(omxAngles[i]));
                IJ.log(ijAngles[i] + "deg ~= " + omxAngles[i] + "rad? " + pass);
            }
            passAll = passAll && pass;
        }
        return passAll;
    }
    
    /** Interactive test method. */
    public static void main(String[] args) {
        Cal_PatternFocus plugin = new Cal_PatternFocus();
        System.out.println("private methods test OK? " + plugin.test(true));
        new ImageJ();
        TestData.lawn.show();
        IJ.runPlugIn(Cal_PatternFocus.class.getName(), "");
    }
    
}

