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
import java.awt.Color;

import ij.*;
import ij.plugin.PlugIn;
import ij.gui.Plot;
import ij.process.*;

/** This plugin takes SIR reconstructed data and produces produces
 * plots of slice minima and summarizes their standard deviation, which 
 * diagnoses refractive index mismatch.
 * @author Graeme Ball <graemeball@gmail.com>
 */
public class SIR_Z_variation implements PlugIn, Executable {
    
    String name = "Reconstructed data Z minimum variance";
    ResultSet results = new ResultSet(name);
    
    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        exec(imp);
        results.report();
    }

    /** Execute plugin functionality: plot slice minimum and feature mean
     * scaled to stack mode as zero point, and report variance of minima. 
     * @param imps reconstructed SIR data ImagePlus should be first imp
     * @return ResultSet containing plots of mnimum and mean variation 
     */
     public ResultSet exec(ImagePlus... imps) {  
        IJ.showStatus("Reconstructed data minimum plot...");
        int nc = imps[0].getNChannels();
        ImagePlus[] plots = new ImagePlus[nc];
        for (int c = 1; c <= nc; c++) {
            ImagePlus imp2 = singleChannel(imps[0], c);
            StackStatistics stackStats = new StackStatistics(imp2);
            int nz = imp2.getNSlices();
            // build arrays of stats per Z plane
            double[] sliceMinima = new double[nz];
            double[] sliceMeans = new double[nz];
            double[] zPlanes = new double[nz];
            double plotMax = stackStats.min;
            double plotMin = stackStats.max;
            double stackMode = stackStats.dmode;
            for (int z = 0; z < nz; z++) {
                zPlanes[z] = z + 1;  // Z value for the plot (x-axis)
                imp2.setSlice(z + 1);
                ImageProcessor ip = imp2.getProcessor();
                ImageStatistics stats = ip.getStatistics();
                ImageStatistics featStats = I1l.featStats(ip);
                // normalize all statistics to stack mode as 0 point
                double nmin = stats.min - stackMode;
                double nmean = featStats.mean - stackMode;
                sliceMinima[z] = nmin;
                sliceMeans[z] = nmean; 
                if (nmin < plotMin) {
                    plotMin = nmin;
                }
                if (nmean > plotMax) {
                    plotMax = nmean;
                }
                if (nmean < plotMin) {
                    plotMin = nmean;
                }
            }
            Plot plot = new Plot(
                    "SIR normalized min variation, C" + c,
                    "Z plane", "slice feature means (gray) & minima (black)");
            // display 20% beyond min and max
            plotMin -= 0.2 * Math.abs(plotMin);
            plotMax += 0.2 * Math.abs(plotMax);
            plot.setLimits(1.0d, (double)nz, plotMin, plotMax);
            plot.setLineWidth(2);
            plot.setColor(Color.BLACK);
            plot.addPoints(zPlanes, sliceMinima, Plot.LINE);
            plot.setColor(Color.LIGHT_GRAY);
            plot.addPoints(zPlanes, sliceMeans, Plot.LINE);
            plot.setLineWidth(1);
            plots[c - 1] = plot.getImagePlus();
            double nstdev = Math.sqrt(J.variance(sliceMinima)) / 
                    J.mean(sliceMeans);
            results.addStat("  channel " + c + " normalized stdev", nstdev);
            
        }
        String title = "slice feature means (gray) & minima (black)";
        ImagePlus impAllPlots = I1l.mergeChannels(title, plots);
        impAllPlots.setDimensions(nc, 1, 1);
        impAllPlots.setOpenAsHyperStack(true);
        results.addImp(title, impAllPlots);
        results.addInfo("Standard deviation of minimum", "high value with"
                + " respect to mean feature intensity indicates\n"
                + "    sample / PSF Refractive Index mismatch.");
        return results;
    }

    /**  Return a new ImagePlus containing a single channel of the input. */
    private ImagePlus singleChannel(ImagePlus imp, int channel) {
        int stackSize = imp.getStackSize();
        int channels = imp.getNChannels();
        ImageStack nuStack = new ImageStack(imp.getWidth(),imp.getHeight());
        for (int s = 1; s <= stackSize; s++) {
            if (((s % channels) == channel) || 
                    ((channel == channels) && ((s % channels) == 0))) {
                nuStack.addSlice(imp.getStack().getProcessor(s));
            }
        }
        String nuTitle = imp.getShortTitle() + "_Ch" + Integer.toString(channel);
        ImagePlus imp2 = new ImagePlus(nuTitle, nuStack);
        imp2.setDimensions(1, imp.getNSlices(), imp.getNFrames());  // 1 channel
        return imp2;
    }
}
