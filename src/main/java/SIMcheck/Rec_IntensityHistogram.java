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
import java.util.Arrays;

import ij.*;
import ij.plugin.PlugIn;
import ij.gui.GenericDialog;
import ij.gui.HistogramWindow;
import ij.process.*;

/** This plugin takes reconstructed data and produces produces 
 * linear+logarithmic histogram showing relative contribution of 
 * negative values to the reconstructed result.
 * @author Graeme Ball <graemeball@gmail.com>
 */
public class Rec_IntensityHistogram implements PlugIn, Executable {
    
    public static final String name = "Reconstructed Intensity Histogram";
    public static final String TLA = "RIH";
    private ResultSet results = new ResultSet(name, TLA);
    
    // parameter fields
    public double percentile = 0.0001;  // use 0-100% of histogram extrema
    public long minPixels = 100; // minimum pixels at histogram extrema to use
    public double modeTol = 0.25;  // mode should be within modeTol*stdev of 0
    
    // noise cut-off
    private boolean manualCutoff = false;
    private double[] backgrounds;  // for manual
    
    private int nNegPixels = 0;
    private int nPosPixels = 0;

    @Override
    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        GenericDialog gd = new GenericDialog(name);
        gd.addCheckbox("Specify_manual_noise_cut-off...", manualCutoff);
        gd.showDialog();
        if (gd.wasOKed()) {
            this.manualCutoff = gd.getNextBoolean();
            if (manualCutoff) {
                this.backgrounds = new double[imp.getNChannels()];
                SIMcheck_.specifyBackgrounds(
                        backgrounds, "Background / noise mid-points:");
            }
            results = exec(imp);
            results.report();
        }
    }

    /** Execute plugin functionality: plot histogram and calculate
     * 'max-to-min' intensity ratio. 
     * @param imps reconstructed data ImagePlus should be first imp
     * @return ResultSet containing histogram plots                                  
     */
    public ResultSet exec(ImagePlus... imps) {
        IJ.showStatus(name + "...");
        int nc = imps[0].getNChannels();
        ImagePlus[] plots = new ImagePlus[nc];
        for (int c = 1; c <= nc; c++){
            ImagePlus imp2 = I1l.copyChannel(imps[0], c);
            StackStatistics stats = new StackStatistics(imp2);
            double background = stats.dmode;
            if (manualCutoff) {
                background = backgrounds[c - 1];
            }
//            if (Math.abs(background) > (stats.stdDev*modeTol)) {
//                IJ.log("! warning, C" + c + " histogram mode=" 
//                        + Math.round(background) 
//                        + " not within " + modeTol + " stdDev of 0\n");
//            }
            if (stats.histMin <= background) {
                // ensure we consider a bare minimum of pixels
                long totalPixels = stats.longPixelCount;
                if (totalPixels * percentile / 100 < minPixels) {
                    percentile = (double)minPixels * 100 / totalPixels;
                }
                // caluclate +ve / -ve ratio if histogram has negatives
                double posNegRatio = calcPosNegRatio(
                		stats, percentile / 100, background);
                String statDescription = "C" + c +
//                        " max-to-min intensity ratio MMR";
                        " max-to-min intensity ratio";
                double roundedRatio = (double)((int)(posNegRatio * 10)) / 10;
                results.addStat(statDescription, 
                        roundedRatio, checkRatio(roundedRatio));
                results.addInfo("C" + c + " number of max / min" +
                        " pixels averaged", nNegPixels + "/" + nPosPixels);
                
            } else {
                results.addInfo("! histogram minimum above background for"
                        + " for channel " + Integer.toString(c), 
                        "unable to calculate +ve/-ve intensity ratio");
            }
            String plotTitle = "Intensity histogram (log-scale=gray)";
            EhistWindow histW = new EhistWindow(plotTitle, imp2, stats);
            histW.setVisible(false);
            plots[c - 1] = histW.getImagePlus();
            I1l.drawPlotTitle(plots[c - 1], plotTitle);
        }
        String title = I1l.makeTitle(imps[0], TLA);
        ImagePlus impAllPlots = I1l.mergeChannels(title, plots);
        // TODO: clear Reconstructed Data Histogram Channel N windows
        //       from Window list after the plugin exits ()
        for (int c = 1; c <= nc; c++) {
            plots[c - 1].close();  // only seems to fully close 1st channel
        }
        impAllPlots.setDimensions(nc, 1, 1);
        impAllPlots.setOpenAsHyperStack(true);
        results.addImp("Intensity counts in black (linear) & gray (log-scale)",
                impAllPlots);
        results.addInfo("About", "Max-to-Min intensity Ratio (MMR) is"
                + " calculated as the ratio of the averaged 0.001%"
                + " highest (Max*) and lowest (Min*) intensity pixels in a"
                + " 32-bit stack, centered at the stack mode (assumed to be"
                + " the center of the noise distribution),"
                + " that is,\n"
                + "  Max* - Mode / |Min* - Mode|");
        results.addInfo("How to interpret", "max-to-min intensity ratio, MMR"
                + " <3 is inadequate, 3-6 is low, 6-12 is good, >12 excellent."
                + " For valid results, the data set must contain sufficient"
                + " background areas (so that the mode reflects background)"
                + " and should be constrained to z-slices containing features."
                + " N.B. MMR statistic is only valid for unclipped data"
                + " (reconstruction option 'discard negatives' or 'baseline"
                + " cut mode' deactivated).");
        return results;
    }

    /** Calulate the ratio of extreme positive versus negative values in the 
     * image histogram. 
     * @param stats for the ImagePlus
     * @param pc (0-1) fraction of histogram to use at lower AND upper ends
     * @param bg background / noise mid-point of histogram
     * @return (Imax - Imode) / (Imode - Imin), i.e. positive / negative ratio 
     */
    double calcPosNegRatio(ImageStatistics stats, double pc, double bg) {
        int[] hist = stats.histogram;
        // find hist step (bin size), and number of pixels in image 
        double histStep = (stats.histMax - stats.histMin)
                / (double)hist.length;
        long nPixels = 0;
        for (int i = 0; i < hist.length; i++) {
            nPixels += hist[i];
        }
        // for negative histogram extreme, add until percentile reached
        double negPc = 0;
        double negTotal = 0;
        int bin = 0;
        double binValue = stats.histMin;
        while (negPc < pc && binValue <= bg && bin < hist.length) {
            negPc += (double)hist[bin] / nPixels;
            negTotal += (binValue - bg) * hist[bin];  // make mode 0
            bin += 1;
            binValue += histStep;
        }
        pc = negPc;  // consider same-sized positive & negative extrema
        // for positive histogram extreme, add until percentile reached
        double posPc = 0;
        double posTotal = 0;
        bin = hist.length - 1;
        binValue = stats.histMax;
        while ((posPc < pc) && (bin >= 0)) {
            posPc += (double)hist[bin] / nPixels;
            posTotal += (binValue - bg) * hist[bin];  // make mode 0
            bin -= 1;
            binValue -= histStep;
        }
        // uncomment to check actual histogram bins used (pc and pixels)
        nNegPixels = (int)(negPc * nPixels);
        nPosPixels = (int)(posPc * nPixels);
        // since negTotal may or may not be negative...
        double posNegRatio = Math.abs(posTotal / negTotal);  
        return posNegRatio;
    }
    
    /** Is max/min ratio value acceptable? */
    private static ResultSet.StatOK checkRatio(Double statValue) {
        if (statValue >= 6.0) {
            return ResultSet.StatOK.YES;
        } else if (statValue >= 3.0) {
            return ResultSet.StatOK.MAYBE;
        } else {
            return ResultSet.StatOK.NO;
        }
    }

    /** test private methods, return true if all OK */
    static boolean test(boolean verbose) {
        // test calcPosNegRatio() using image with a few high and low pixels
        ImagePlus imp = IJ.createImage("stripe", "8-bit black", 64, 64, 1);
        ImageProcessor ip = imp.getProcessor();
        IJ.run(imp, "Select All", "");
        ip.setColor(100);  // i.e. mode = 100
        ip.fill();
        ip.setColor(140);
        ip.fill(new ij.gui.Roi(0, 0, 48, 1));  // 48 pixels at 140 (mode + 40)
        ip.setColor(80);
        ip.fill(new ij.gui.Roi(0, 1, 48, 1));  // 48 pixels at 80 (mode -20)
        imp.setProcessor(ip);
        IJ.run(imp, "Select All", "");
        ImageStatistics stats = new StackStatistics(imp);
        Rec_IntensityHistogram plugin = new Rec_IntensityHistogram();
        double pnRatio = plugin.calcPosNegRatio(stats, 0.005, stats.dmode);
        if (verbose) {
            System.out.println("hist: " + Arrays.toString(stats.histogram));
            System.out.println("min; background (mode); max = " +
                    stats.histMin + "; " + stats.dmode + "; " + stats.histMax);
            System.out.println("pnRatio = " + pnRatio);
            System.out.println("nNegPixels = " + plugin.nNegPixels);
            System.out.println("nPosPixels = " + plugin.nPosPixels);
            imp.show();
        } else {
            imp.close();
        }
        // expect 48*40 / 48*20 = 2.0
        boolean testResult = pnRatio > 1.95 && pnRatio < 2.05;
        return testResult;
    }

    /** Call selfTest */
    public static void main(String[] args) {
        System.out.println("selfTest successful? " + test(true));
        new ImageJ();
        TestData.recon.show();
        IJ.runPlugIn(Rec_IntensityHistogram.class.getName(), "");
    }
    
    /** Extend ImageJ HistogramWindow for auto log scaling and stack stats. */
    class EhistWindow extends HistogramWindow {
        private static final long serialVersionUID = 1L;
        /* customize the HistogramWindow during construction */
        public EhistWindow(String title, ImagePlus imp, ImageStatistics stats){
            super(title, imp, stats);
            super.logScale = true;
            this.showHistogram(imp, stats);
        }
        
    }
}
