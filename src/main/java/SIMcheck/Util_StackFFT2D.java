/*  Copyright (c) 2015, Graeme Ball and Micron Oxford,
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
import ij.plugin.PlugIn;

/**
 * This plugin carries out ImageJ's 2D FFT on each slice of a stack.
 * Allows choice of result scaling / format.
 * @author Graeme Ball <graemeball@gmail.com>
 */
public class Util_StackFFT2D implements PlugIn {

    public static final String name = "Stack FFT (2D)";
    public static final String TLA = "FFT";
    public static String[] resultType = {
        "8-bit log(Amplitude^2)",
        "32-bit log(Amplitude^2)",
        "32-bit gamma-scaled Amplitude"};
    private static double NO_GAMMA = 0.0d;

    // parameter fields
    public String resultTypeChoice = resultType[0];
    public double winFraction = 0.06d;
    public double gamma = 0.3;

    @Override
    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        GenericDialog gd = new GenericDialog(name);
        imp.getWidth();
        gd.addNumericField("gaussian window %", winFraction, 3);
        gd.addRadioButtonGroup("Result type", resultType, 3, 1, resultType[0]);
        gd.addNumericField("gamma", gamma, 2);
        gd.showDialog();
        if (gd.wasOKed()) {
            this.resultTypeChoice = gd.getNextRadioButton();
            this.winFraction = gd.getNextNumber();
            this.gamma = gd.getNextNumber();
            IJ.showStatus("FFT stack...");
            ImagePlus impF = exec(imp);
            impF.show();
        }
    }
    /** Execute plugin functionality: 2D FFT each slice.
     * @param imp input format ImagePlus
     * @return ImagePlus after 2D FFT of each slice
     */
    public ImagePlus exec(ImagePlus imp) {
        ImagePlus impF = null;
        if (resultTypeChoice.equals(resultType[0])) {
            // default, log-scaled amplitude^2, converted to 8-bit
            impF = FFT2D.fftImp(imp, false, winFraction, NO_GAMMA);
            IJ.log(resultTypeChoice + ", gaussian window " + winFraction + "%");
        } else if(resultTypeChoice.equals(resultType[1])) {
            // log-scaled amplitude^2, as 32-bit float
            impF = FFT2D.fftImp(imp, true, winFraction, NO_GAMMA);
            IJ.log(resultTypeChoice + ", gaussian window " + winFraction + "%");
        } else {
            // gamma-scaled amplitude (as 32-bit float)
            impF = FFT2D.fftImp(imp, false, winFraction, gamma);
            IJ.log(resultTypeChoice + ", gaussian window "
                    + winFraction + "%, gamma=" + gamma);
        }
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
