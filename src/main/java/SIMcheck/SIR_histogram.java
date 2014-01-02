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
import ij.plugin.PlugIn;
import ij.gui.HistogramWindow;
import ij.process.*;

/** This plugin takes reconstructed data and produces produces 
 * linear+logarithmic histogram showing relative contribution of 
 * negative values to the reconstructed result.
 * @author Graeme Ball <graemeball@gmail.com>
 */
public class SIR_histogram implements PlugIn, EProcessor {
    
    String name = "Reconstructed Data Histograms";
    ResultSet results = new ResultSet(name);
    double percentile = 0.5;  // 0-100
    double min_ratio = 6.0;
    double mode_tol = 0.25;

    @Override
    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        results = exec(imp);
        results.report();
    }

    /** Execute plugin functionality: plot histogram and calculate +ve/-ve
     * ratio. 
     * @param imps reconstructed SIR data ImagePlus should be first imp
     * @return ResultSet containing histogram plots                                  
     */
    public ResultSet exec(ImagePlus... imps) { 
        IJ.showStatus("Reconstructed data histograms...");
        int nc = imps[0].getNChannels();
        ImagePlus[] plots = new ImagePlus[nc];
        for (int c = 1; c <= nc; c++){
            ImagePlus imp2 = I1l.copyChannel(imps[0], c);
            StackStatistics stats = new StackStatistics(imp2);
            if (Math.abs(stats.dmode) > (stats.stdDev*mode_tol)) {
                IJ.log("  ! warning, ch" + c + " histogram mode=" 
                        + Math.round(stats.dmode) 
                        + " not within " + mode_tol + " stdev of 0\n");
            }
            if (stats.histMin < stats.dmode) {
                // caluclate +ve / -ve ratio if histogram has negatives
                double posNegRatio = calcPosNegRatio(
                		stats, (double)percentile / 100);
                String statDescription = "Ratio of extreme " 
                        + Double.toString(percentile) + "% positive/negative"
                        + " intensities for channel " + c;
                results.addStat(statDescription, 
                        (double)((int)(posNegRatio * 10)) / 10);
                
            } else {
                results.addInfo("  ! histogram minimum above mode for channel "
                        + Integer.toString(c), 
                        "unable to calculate +ve/-ve intensity ratio");
            }
            String newTitle = "Reconstructed Data Histogram Channel " + c;
            EhistWindow histW = new EhistWindow(newTitle, imp2, stats);
            histW.setVisible(false);
            plots[c - 1] = histW.getImagePlus();
        }
        String title = "log-scaled intensity counts in gray";
        ImagePlus impAllPlots = I1l.mergeChannels(title, plots);
        impAllPlots.setDimensions(nc, 1, 1);
        impAllPlots.setOpenAsHyperStack(true);
        results.addImp(title, impAllPlots);
        results.addInfo("Intensity ratio above / below mode", 
                "<3 inadequate, 3-6 low, 6-12 good, >12 excellent");
        return results;
    }

    /** Calulate the ratio of extreme positive versus negative values in the 
     * image histogram. 
     * @param stats for the ImagePlus
     * @param pc (0-1) fraction of histogram to use at lower AND upper ends
     * @return (Imax - Imode) / (Imode - Imin), i.e. positive / negative ratio 
     */
    static double calcPosNegRatio(ImageStatistics stats, double pc) {
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
        while (negPc < pc && binValue < stats.dmode && bin < hist.length) {
            negPc += (double)hist[bin] / nPixels;
            negTotal += (binValue - stats.dmode) * hist[bin];  // make mode 0
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
            posTotal += (binValue - stats.dmode) * hist[bin];  // make mode 0
            bin -= 1;
            binValue -= histStep;
        }
        // negTotal may or may not be negative
        double posNegRatio = Math.abs(posTotal / negTotal);  
        return posNegRatio;
    }

    /** test private methods, return true if all OK */
    static boolean selfTest(boolean verbose) {
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
        double pnRatio = calcPosNegRatio(stats, 0.005);
        if (verbose) {
            System.out.println("hist: " + I1l.prn(stats.histogram));
            System.out.println("min; mode; max = " +
                    stats.histMin + "; " + stats.dmode + "; " + stats.histMax);
            System.out.println("pnRatio = " + pnRatio);
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
        System.out.println("selfTest successful? " + selfTest(true));
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
