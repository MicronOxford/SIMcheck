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
import ij.measure.*;
import ij.gui.*;
import ij.IJ;

import java.awt.Color;

/** This plugin plots slice average intensity for each channel of raw SI data
 * to evaluate intensity stability as phase, Z, angle and time are incremented.
 * Each channel is plotted in a different (arbitrary) color.
 * @author Graeme Ball <graemeball@gmail.com>
 */
public class Raw_IntensityProfiles implements PlugIn, Executable {

    public static final String name = "Channel Intensity Profiles";
    public static final String TLA = "CIP";
    private ResultSet results = new ResultSet(name);

    // parameter fields
    public int phases = 5;
    public int angles = 3;
    public double zwin = 9;  // Z window that affects reconstruction of a slice

    @Override
    public void run(String arg) {
        ImagePlus imp = IJ.getImage();
        GenericDialog gd = new GenericDialog(name);
        gd.addMessage("Requires raw SI data in OMX (CPZAT) order.");
        gd.addNumericField("Angles", angles, 0);
        gd.addNumericField("Phases", phases, 0);
        gd.showDialog();
        if (gd.wasCanceled()) return;
        if (gd.wasOKed()) {
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

    /** Execute plugin functionality: create a plot of intensity profile per 
     * channel. Assumes OMX CPZAT dimension order.
     * @param imps input raw SI data ImagePlus should be first imp
     * @return ResultSet containing intensity profile plots
     */
    public ResultSet exec(ImagePlus... imps) {
        ImagePlus imp = imps[0];
        IJ.showStatus("Building intensity profile plot...");
        int nc = imp.getNChannels();
        int np = phases;
        int nz = imp.getNSlices();
        int na = angles;
        int nt = imp.getNFrames();
        nz = nz / (np * na);  // take phase & angle out of Z
        int totalPlanes = nc * np * nz * na * nt;

        ImageStack stack = imp.getStack();
        int moptions = Analyzer.getMeasurements();
        Calibration cal = imp.getCalibration();

        float[] avIntensities = new float[totalPlanes / nc];
        float[] pzat_no = new float[totalPlanes / nc];
        Plot plot = new Plot(I1l.makeTitle(imp, TLA), 
        		"Slices in order: Phase, Z, Angle, Time (C1=red,C2=grn,C3=blu)",
        		"Slice Mean Intensity", pzat_no, avIntensities);

        // assess intensities for plot scaling
        double sliceMeanMin = 0;
        double sliceMeanMax = 0;  // max of means for each slice
        for (int slice = 1; slice <= stack.getSize(); slice++) {
            ImageProcessor ip = stack.getProcessor(slice);
            ImageStatistics stats = ImageStatistics.getStatistics(
            		ip, moptions, cal);
            double sliceMean = stats.mean;
            if (sliceMean < sliceMeanMin) {
                sliceMeanMin = sliceMean;
            }
            if (sliceMean > sliceMeanMax) {
                sliceMeanMax = sliceMean;
            }
        }
        sliceMeanMax = sliceMeanMax * (double)1.1;  // show 10% above max slice
        plot.setLimits((double)1, (double)totalPlanes 
        		/ nc, sliceMeanMin, sliceMeanMax);

        // add plot points for each channel, stepping by nc (over P,Z,A,T)
        for (int channel = 1; channel <= nc; channel++) {
            int pzat = 0;  // plot x-axis
            for (int plane = channel; plane <= totalPlanes; plane += nc) {
                pzat++; 
                pzat_no[pzat-1] = (float)pzat; 
                ImageProcessor ip = stack.getProcessor(plane);
                ImageStatistics stats = ImageStatistics.getStatistics(
                		ip, moptions, cal);
                float planeMean = (float)stats.mean;
                avIntensities[pzat-1] = planeMean;
            }
            if (channel == 1) {
                Color color = Color.RED;
                plot.setColor(color);
            } else if (channel == 2) {
                Color color = Color.GREEN;
                plot.setColor(color);
            } else if (channel == 3) {
                Color color = Color.BLUE;
                plot.setColor(color);
            } else {
                Color color = Color.BLACK;  // channels beyond 3rd BLACK 
                plot.setColor(color);
            }
            plot.addPoints(pzat_no, avIntensities, 1);
            
            // TODO: refactor the code below, which has grown very messy!
            
            // (0) overall stat, influenced by (1), (2) and (3)
            // calc max % intensity fluctuation over slices used to reconstruct
            // 1 slice (e.g. 5P,9Z,3A); for central 9Z window & 1st time-point
            int zFirst = nz / 2 - (int)zwin / 2;
//            int zLast = zFirst + (int)zwin + 1;
            int zLast = zFirst + (int)zwin;
            int pzMid = (np * nz / 2) + np / 2; // central phase & Z, 1st angle
            // initialize min and max arbitrarily within window before updating
            double intensMin = avIntensities[pzMid];
            double intensMax = avIntensities[pzMid];
//            double intensMean = 0.0d;
//            int nWinSlices = 0;
            for (int a = 0; a < na; a++) {
                for (int z = 0; z < nz; z++) {
                    if (z >= zFirst && z < zLast)  {
                        // consider intensities inside central 9Z window
                        for (int p = 0; p < np; p++) {
//                            nWinSlices++;
                            int slice = (a * nz * np) + (z * np) + p;
                            double intens = avIntensities[slice];
//                            intensMean += intens;
                            if (intens > intensMax) {
                                intensMax = intens;
                            }
                            if (intens < intensMin) {
                                intensMin = intens;
                            }
                        }
                    }
                }
            }  // TODO: test the above calc
//            intensMean /= nWinSlices;
            double pcDiff = 100.0d * (intensMax - intensMin) / intensMax;
            results.addStat(
                    "C" + Integer.toString(channel) + " max % intensity"
                        + " variation over reconstruction window",
                    (double)Math.round(pcDiff), checkPercentDiff(pcDiff));
            
            /// (1) per-channel intensity decay
            double[] xSlice = J.f2d(pzat_no);
            double[] yIntens = J.f2d(avIntensities);
            //  fitting with y=a*exp(bx)  ; fitter returns a, b, R^2
            //   e.g. x=ln((2/3)/b) for 2/3 original intensity (<33% decay)
            CurveFitter expFitter = new CurveFitter(xSlice, yIntens);
            expFitter.doFit(CurveFitter.EXPONENTIAL);
            double[] fitResults = expFitter.getParams();
            double channelDecay = (double)100 * (1 - Math.exp(fitResults[1]
            		* pzat_no.length * (zwin / nz)));
            results.addStat(
                    "C" + Integer.toString(channel) + " intensity decay"
                        + " per " + (int)zwin + " z-slices (%)",
                    (double)Math.round(channelDecay),
                    ResultSet.StatOK.NA);
            
            /// (2) angle intensity differences
            float[] angleMeans = new float[na];
            for (int angle = 1; angle <= na; angle++) {
                float[] yIntens3 = new float[np*nz];
                System.arraycopy(avIntensities, (angle - 1) * np * nz, 
                		yIntens3, 0, np * nz);
                angleMeans[angle-1] = J.mean(yIntens3);
            }
            double largestDiff = 0;
            for (int angle=1; angle<=na; angle++) {
                for (int angle2=1; angle2 < na; angle2++) {
                    double intensityDiff = Math.abs((double)(
                            angleMeans[angle - 1] - angleMeans[angle2 - 1]));
                    if (intensityDiff > largestDiff) 
                    	largestDiff = intensityDiff;
                }
            }
            // normalize largest av intensity diff using max intensity angle
            float maxAngleIntensity = J.max(angleMeans);
            largestDiff = (double)100 * largestDiff / (double)maxAngleIntensity;
            results.addStat("C" + Integer.toString(channel) 
                    + " max intensity difference between angles (%)",
                    (double)Math.round(largestDiff),
                    ResultSet.StatOK.NA);
            
            // (3) intensity range over central 9Z, averaged over P and A
            // re-use zFirst and zLast for central 9Z window from (0) above
            double[][] zSeries = new double[na * np][(int)zwin];
            IJ.log("zFirst=" + zFirst);
            IJ.log("zLast=" + zLast);
            for (int a = 0; a < na; a++) {
                for (int z = 0; z < nz; z++) {
                    if (z >= zFirst && z < zLast) {
                        // consider intensities inside central 9Z window
                        for (int p = 0; p < np; p++) {
                            int slice = (a * nz * np) + (z * np) + p;
                            zSeries[a * np + p][z - zFirst] = avIntensities[slice];
                        }
                    }
                }
            }
            double avRangeN = 0.0d;  // normalised to max
            for (int ap = 0; ap < na * np; ap++) {
                double max = J.max(J.d2f(zSeries[ap]));
                double min = J.min(J.d2f(zSeries[ap]));
                avRangeN += (max - min) / max;
            }
            avRangeN /= (na * np);
            avRangeN *= 100.0d;
            results.addStat("C" + Integer.toString(channel) 
                    + " intensity range over reconstruction"
                    + " window, averaged over phase and angle (%)", avRangeN,
                    ResultSet.StatOK.NA);
            
        }
        
        ImagePlus impResult = plot.getImagePlus();
        I1l.drawPlotTitle(impResult, "Per Channel Intensity Profiles");
        results.addImp(name, plot.getImagePlus());
        results.addInfo("How to interpret",
                "intensity differences > ~30% over the 9-z-window used to"
                + " reconstruct each z-section may cause artifacts (threshold"
                + " depends on signal-to-noise level).");
        return results;
    }
    
    /** Is this percentage difference stat value acceptable? */
    private ResultSet.StatOK checkPercentDiff(double statValue) {
        if (statValue <= 10) {
            return ResultSet.StatOK.YES;
        } else if (statValue <= 30) {
            return ResultSet.StatOK.MAYBE;
        } else {
            return ResultSet.StatOK.NO;
        }
    }
    
    /** Interactive test method */
    public static void main(String[] args) {
        new ImageJ();
        TestData.raw.show();
        IJ.runPlugIn(Raw_IntensityProfiles.class.getName(), "");
    }
}
