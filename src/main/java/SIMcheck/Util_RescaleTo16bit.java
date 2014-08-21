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
 * This plugin discards values below channel mode and converts to 16-bit.
 * @author Graeme Ball <graemeball@gmail.com>
 */
public class Util_RescaleTo16bit implements PlugIn {

    public static final String name = "Threshold and 16-bit conversion";
    public static final String TLA = "THR";
    
    @Override
    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        ImagePlus imp2 = exec(imp);
        IJ.run("Brightness/Contrast...");
        imp2.show();
    }

    /** Execute plugin functionality: discard -ve and convert to 16-bit.
     * For each channel, old mode is new zero, and values are rescaled to
     * fit 0-65535 if channel max exceeds 65535.
     * @param imp input ImagePlus, i.e. 32-bit reconstructed SIM data
     * @return ImagePlus (16-bit) after discarding below channel mode
     */
    public static ImagePlus exec(ImagePlus imp) {
        String title = I1l.makeTitle(imp, TLA);
        ImagePlus imp2 = imp.duplicate();
        I1l.subtractPerSliceMode(imp2);
        // TODO: get conversion opts, rescale only if necess, reset conv opts
        // TODO: option to "discard negatives" where not much background
        IJ.run("Conversions...", " ");
//        IJ.run("Conversions...", "scale");
        IJ.run(imp2, "16-bit", "");
        imp2.setTitle(title);
        return imp2;
    }
    
    /** Interactive test method. */
    public static void main(String[] args) {
        new ImageJ();
        TestData.recon.show();
        IJ.runPlugIn(Util_RescaleTo16bit.class.getName(), "");
    }
}
