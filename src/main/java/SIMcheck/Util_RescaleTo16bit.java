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
import ij.gui.GenericDialog;
import ij.plugin.ChannelSplitter;
import ij.plugin.PlugIn;
import ij.process.StackStatistics;

/**
 * This plugin discards values below zero-point and converts to 16-bit.
 * @author Graeme Ball <graemeball@gmail.com>
 */
public class Util_RescaleTo16bit implements PlugIn {

    public static final String name = "Threshold and 16-bit Conversion";
    public static final String TLA = "THR";
    private static final int MAX_16_BIT = 65535;
    
    @Override
    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        ImagePlus imp2 = null;
        double[] channelMinima = new double[imp.getNChannels()];
        GenericDialog gd = new GenericDialog(name);
        gd.addCheckbox("Auto-scale input slices mode-max", true);
        gd.showDialog();
        if (gd.wasOKed()) {
            if (gd.getNextBoolean()) {
                imp2 = exec(imp);
            } else {
                SIMcheck_.specifyBackgrounds(
                        channelMinima, "Discard intensities up to:");
                imp2 = exec(imp, channelMinima);
            }
            IJ.run("Brightness/Contrast...");
            imp2.show();
        }
    }
    
    /** Execute plugin functionality: discard <mode and convert to 16-bit.
     * For each channel, old mode is new zero, and values are rescaled to
     * fit 0-65535 if channel max exceeds 65535.
     * @param imp input ImagePlus, i.e. 32-bit reconstructed SIM data
     * @return ImagePlus (16-bit) after discarding below channel mode
     */
    public static ImagePlus exec(ImagePlus imp) {
        String title = I1l.makeTitle(imp, TLA);
        ImagePlus imp2 = imp.duplicate();
        I1l.subtractMode(imp2);
        IJ.log(name + ", auto-scale using per channel mode.");
        if (exceeds16bit(imp2)) {
            IJ.run("Conversions...", "scale");
            IJ.log("Data exceeds 16-bit range and has been rescaled!");
        } else {
            IJ.run("Conversions...", " ");
        }
        IJ.run(imp2, "16-bit", "");
        IJ.run("Conversions...", " ");  // assume default no rescaling
        imp2.setTitle(title);
        return imp2;
    }

    /** Alternative exec(): discard below specified channel minima, to 16-bit.
     * @param imp input ImagePlus, i.e. 32-bit reconstructed SIM data
     * @param channelMinima array of minimum cut-offs for each channel
     * @return ImagePlus (16-bit) after discarding below channel minimum
     */
    public static ImagePlus exec(ImagePlus imp, double[] channelMinima) {
        String title = I1l.makeTitle(imp, TLA);
        ImagePlus imp2 = imp.duplicate();
        IJ.log(name + ", using specified minima:");
        for (int c = 1; c <= channelMinima.length; c++) {
            IJ.log("  Channel " + c + " minimum = " + channelMinima[c - 1]);
        }
        subtractChannelMinima(imp2, channelMinima);
        if (exceeds16bit(imp2)) {
            IJ.run("Conversions...", "scale");
            IJ.log("Data exceeds 16-bit range and has been rescaled!");
        } else {
            IJ.run("Conversions...", " ");
        }
        IJ.run(imp2, "16-bit", "");
        IJ.run("Conversions...", " ");  // assume default no rescaling
        imp2.setTitle(title);
        return imp2;
    }
    
    /** Subtract specified minimum from each channel. */
    static void subtractChannelMinima(ImagePlus imp, double[] minima) {
        ImagePlus[] imps = ChannelSplitter.split(imp);
        for (int c = 0; c < imps.length; c++) {
            double minimum = minima[c];
            IJ.run(imps[c], "Subtract...", "value=" + minimum + " stack");
        }
        imp.setStack(I1l.mergeChannels(imp.getTitle(), imps).getStack());
    }
    
    /** Return true if StackStatistics report a max outside 16-bit range. */
    private static boolean exceeds16bit(ImagePlus imp) {
        StackStatistics sstats = new StackStatistics(imp);
        return sstats.histMax > (double)MAX_16_BIT;
    }
    
    /** Interactive test method. */
    public static void main(String[] args) {
        new ImageJ();
        TestData.recon.show();
        IJ.runPlugIn(Util_RescaleTo16bit.class.getName(), "");
    }
}
