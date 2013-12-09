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
import ij.gui.Overlay;
import ij.gui.Plot;
import ij.IJ;
import ij.measure.*;

import java.awt.Color;
import java.awt.Polygon;
import ij.gui.*;

/** This plugin takes raw SI data for an even field of fluorescence and
 * calculates illumination pattern phase-step and offset stability.
 * @author Graeme Ball <graemeball@gmail.com>
 **/
public class Cal_Phases implements PlugIn {

    String name = "Calibration check phases";
    ResultSet results = new ResultSet(name);
	int phases = 5;
	int angles = 3;
	ImagePlus imp;
	Calibration cal;
	
    // default parameters
    double gaussRad = 0.08;      // gaussian radius in units of image width
    double stripeWidth = 0.005;  // width of stripes in units of image width
    double peakStdev = 6;        // stdevs above background for peaks
    int peakPosTolerance = 6;    // allowed deviation from ideal position in px
    private int zFirst = 1;
    private int zLast = 1;
    private int width = 0;
    private int height = 0;
    private boolean doPadding = false;
    private int pltW = 528;
    private int pltH = 255;

    public void run(String arg) {
        imp = IJ.getImage();
        int nz = imp.getNSlices() / (phases * angles);
        // NB. zFirst/Last estimate screwed up if phases != 5 or angles != 3
        zFirst = nz / 2 - 2;
        if (zFirst < 1) {
            zFirst = 1;
        }
        zLast = nz / 2 + 2;
        if (zFirst > nz) {
            zLast = nz;
        }
        GenericDialog gd = new GenericDialog("Caibrate Phases");
        gd.addMessage("Requires SI raw data in API OMX (CPZAT) order.");
        gd.addNumericField("Angles", angles, 1);
        gd.addNumericField("Phases", phases, 1);
        gd.addNumericField("first Z slice to analyze", zFirst, 1);
        gd.addNumericField("last Z slice to analyze", zLast, 1);
        gd.showDialog();
        if (gd.wasCanceled()) return;
        if (gd.wasOKed()) {
            angles = (int)gd.getNextNumber();
            phases = (int)gd.getNextNumber();
            zFirst = (int)gd.getNextNumber();
            zLast = (int)gd.getNextNumber();
        }
        if (!I1l.stackDivisibleBy(imp, phases * angles)) {
            IJ.showMessage( "Calibrate phases", 
                    "Error: stack size not consistent with phases/angles." );
            return;
        }
        results = exec(imp);
        results.report();
    }

    /** Execute plugin functionality: split the angles into separate stacks
     * and perform a 2D FFT on each slice.
     * @param imps input raw SI data ImagePlus should be first imp
     * @return ResultSet containing FFT with detected peaks
     */
    public ResultSet exec(ImagePlus... imps) {
        // TODO, check we have micron calibration
//        ImagePlus imp = imps[0];
        this.imp = imps[0];
        this.cal = imp.getCalibration();
        int nc = imp.getNChannels();
        int nz = imp.getNSlices();
        nz = nz / (phases * angles);  // take phase & angle out of Z
        width = imp.getWidth();
        height = imp.getHeight();
        setResultSize(imp);  // work out FFT padding, update width/height fields 
        ImageStack stack = imp.getStack();
        ImagePlus[] ampImps = new ImagePlus[angles];    // FFT amplitude 
        ImagePlus[] phaseImps = new ImagePlus[angles];  // FFT phase
        ImagePlus[] plotImps = new ImagePlus[angles];   // phase fit plots
        // TODO 1, measure k0 angle and line spacing from 1st order peaks 
        // TODO 2, add phase amplitude stat, or plot??
        ResultsTable rt = new ResultsTable();
	//IMD constant of expected phase steps	    
	double expectedStep = 2.0*Math.PI/phases; //
        for (int a = 1; a <= angles; a++) {
            IJ.showStatus("FFT & peak-finding for angle " + a);
            ImageStack stackAmp = new ImageStack(width, height);
            ImageStack stackPhase = new ImageStack(width, height);
            float[][] phaseSets = new float[nc][phases * (zLast - zFirst + 1)];
            float[][] phaseShifts = new float[nc][phases * (zLast - zFirst + 1)];
            Polygon[][] peakSets = new Polygon[nc][phases * (zLast - zFirst + 1)];
            double[][] lineSpacings = new double[nc][phases * (zLast - zFirst + 1)];
            double[][] kAngles = new double[nc][phases * (zLast - zFirst + 1)];
            Overlay peakOverlay = new Overlay();
            int sliceOut = 0;
	    int index = 0;
            for (int z = zFirst ; z <= zLast; z++) {
                for (int p = 1; p <= phases; p++) {
                    for (int c = 1; c <= nc; c++) {
                        int sliceIn = I1l.stackSliceNo(
                        		c, nc, p, phases, z, nz, a, angles);
                        sliceOut++;
                        ImageProcessor ip = stack.getProcessor(sliceIn);
                        FloatProcessor[] complexPair = complexFFT(ip);
                        FloatProcessor fpAmp = complexPair[0];
                        FloatProcessor fpPhase = complexPair[1];
                        Polygon peaks1stOrder = findSIpeakPair(fpAmp, 1);
                        Polygon peakPlus = selectPeakPlus(peaks1stOrder, width);
                        double lineSpacing = calcLineSpacing(
                                peaks1stOrder, width, height, cal, 1);
                        lineSpacings[c - 1][(p - 1) + (z - zFirst) * phases] = lineSpacing;
                        double kAngle = calcKangle(peakPlus, width, height);
                        kAngles[c - 1][(p - 1) + (z - zFirst) * phases] = kAngle;
                        if (peakPlus != null) {
                            peakSets[c - 1][(p - 1) + (z - zFirst) * phases] = peakPlus;
                            int x = peakPlus.xpoints[0];
                            int y = peakPlus.ypoints[0];
                            float rawPhase = fpPhase.getPixelValue(x, y);
                            phaseSets[c - 1][(p - 1) + (z - zFirst) * phases] = rawPhase;
                            PointRoi pointRoi = new PointRoi(peakPlus);
                            pointRoi.setStrokeColor(Color.YELLOW);
                            pointRoi.setStrokeWidth(1);
                            pointRoi.setName("Z" + z + "/P" + p + "/C" + c);
                            fpAmp.setRoi((Roi)pointRoi);
                            peakOverlay.add((Roi)pointRoi);
                        } else {
                            phaseSets[c - 1][(p - 1) + (z - zFirst) * phases] = Float.NaN;
                        }
                        String sliceName = "Z" + z + "/P" + p; 
                        stackAmp.addSlice(String.valueOf(sliceName), fpAmp);
                        stackPhase.addSlice(String.valueOf(sliceName), fpPhase);
			//IMD 20131207 Attemp to unwrap phases adnncalucate phase steps. 
			if (p>1){ //cant do phase sift from  single data point
			    index=(p-1) + (z - zFirst) * phases;
			    phaseShifts[c-1][index]= 
				phaseSets[c-1][index]
				-phaseSets[c-1][index-1];
			    
			    //slack of .5 X expectedStep for noise, error etc... 
			    if ((Math.abs(phaseShifts[c-1][index]) > 1.5*expectedStep) | 
				(Math.abs(phaseShifts[c-1][index]) < 0.5*expectedStep)){
				if (phaseShifts[c-1][index]<0) {
				    phaseShifts[c-1][index] += 2.0*Math.PI;
				    phaseSets[c-1][index]+=2.0*Math.PI;
				}
				else {
				    phaseShifts[c-1][index] -= 2.0*Math.PI;
				    phaseSets[c-1][index]-=2.0*Math.PI;
				}
			    }
			    if ((Math.abs(phaseShifts[c-1][index]) > 1.5*expectedStep) | 
				(Math.abs(phaseShifts[c-1][index]) < 0.5*expectedStep)){
				IJ.log("C"+c+"-A"+a+"-Z"+z+"-p"+p+" : Phase step > 1.5 or  <0.5 times expected step size");
			    }
			    // need some way to decide on the initial direction to triger this warning.
			    //if(phaseShift*phaseDirection < 1){
			    //	IJ.log("Phase step changing direction.");
			    //}
			}
		    }
                }
                IJ.showProgress(z - zFirst + 1, zLast - zFirst + 1);
            }
            // suppress local variable not used warning :-||
            IJ.log("=== Cal_Phases ===\n  considering " + sliceOut-- + " slices");
            // add transforms & peak overlays to results
            String title = I1l.makeTitle(imp, "A" + a + "_FTA");
            ampImps[a - 1] = new ImagePlus(title, stackAmp);
            I1l.copyCal(imp, ampImps[a - 1]);
            ampImps[a - 1].setProperty("FHT", "F");
            ampImps[a - 1].setOverlay(peakOverlay);
            ampImps[a - 1].setDimensions(nc, phases * (zLast - zFirst + 1), 1);
            ampImps[a - 1].setOpenAsHyperStack(true);
            results.addImp("Angle " + a + " FFT amplitude", ampImps[a - 1]);
            title = I1l.makeTitle(imp, "A" + a + "_FTP");
            phaseImps[a - 1] = new ImagePlus(title, stackPhase);
            phaseImps[a - 1].setDimensions(nc, phases * (zLast - zFirst + 1), 1);
            phaseImps[a - 1].setOpenAsHyperStack(true);
            results.addImp("Angle " + a + " FFT phase", phaseImps[a - 1]);
            // do phase plotting & statistics, and add to results
            ImageStack stackPlots = new ImageStack(pltW, pltH); 

            for (int c = 1; c <= nc; c++) {
                String colName = "A" + a + "/C" + c;
                IJ.log("\n= Channel " + c + " =");
                double[] positionStdevs = peakPositionStdevs(peakSets[c - 1]);
                double[] phaseStats = plotPhases(
                        phaseSets[c - 1], positionStdevs, stackPlots);
                double avPosStdev = I1l.mean(positionStdevs);
                results.addStat("a" + a + " c" + c + " peak postion stdev", 
                        avPosStdev);

		//IMD dont quite understand how to add my phaseShifts variable to the stats. 

                results.addStat("a" + a + " c" + c + " phase step stdev", 
                        phaseStats[0]);
                results.addStat("a" + a + " c" + c + " phase offset stdev", 
                        phaseStats[1]);
                IJ.log("  line spacing = " + I1l.mean(lineSpacings[c - 1]));
                IJ.log("  k angle = " + I1l.mean(kAngles[c - 1]));
            }
            title = I1l.makeTitle(imp, "A" + a + "_PPL");
            plotImps[a - 1] = new ImagePlus(title, stackPlots);
            plotImps[a - 1].setDimensions(nc, 1, 1);
            plotImps[a - 1].setOpenAsHyperStack(true);
            results.addImp("Angle " + a + " phase plot", plotImps[a - 1]);
        }
        // TODO, add ResultTable or equivalent to ResultSet class
        rt.show("Phase measurements");
        return results;
    }
    
    /** Determine if padding necessary for FFT, update result width/height */
    void setResultSize(ImagePlus imp) {
        int paddedSize = FFT2D.calcPadSize(imp);
        if (paddedSize != width || paddedSize != height) {
            doPadding = true;
            this.width = paddedSize;
            this.height = paddedSize;
        }
    }
    
    /** Complex FFT an ImageProcessor, returining Amp, Phase FloatProcessors */
    FloatProcessor[] complexFFT(ImageProcessor ip) {
        ip = FFT2D.gaussWindow(ip, 0.08d);
        if (doPadding) {
            ip = FFT2D.pad(ip, width);
        }
        FFT2D ifft = new FFT2D(ip);
        ifft.transform();
        FloatProcessor amp = (FloatProcessor)ifft.getComplexAbs();
        FloatProcessor phase = (FloatProcessor)ifft.getComplexPhase();
        amp = (FloatProcessor)FFT2D.filterLow(amp, gaussRad, stripeWidth);
        FloatProcessor[] complexPair = new FloatProcessor[2];
        complexPair[0] = amp;
        complexPair[1] = phase;
        return complexPair;
    }

    /** Find structured illumination pattern peak pair for specified order. */
    Polygon findSIpeakPair(FloatProcessor fp, int order) {
        double tolerance = fp.getStatistics().stdDev * peakStdev;
        MaximumFinder maxfind = new MaximumFinder();
        Polygon maxima = maxfind.getMaxima(fp, tolerance, true);
//        IJ.log("raw maxima");
//        logPosMaxima(maxima);
        // TODO, magic numbers
        double minR = 0.14d * 2.0d / order;
        double maxR = 0.29d * 2.0d / order;
        maxima = I1l.filterPeaksRadial(maxima, cal, width, height, minR, maxR);  
//        IJ.log("maxima in band order " + order);
//        logPosMaxima(maxima);
//        IJ.log("brightest 2 maxima in 1st order band");
        maxima = Cal_Phases.filterPeakPair(maxima, fp, peakPosTolerance);  
//        logPosMaxima(maxima);
        return maxima;
    }
    
    // TODO, temporary
    void logPosMaxima(Polygon maxima) {
        if (maxima != null) {
            for (int i = 0; i < maxima.npoints; i++) {
                int x = maxima.xpoints[i];
                int y = maxima.ypoints[i];
                double r = I1l.calcFourierR(x, y, width, height, cal);
                IJ.log("x,y,r:" + x + "," + y + "," + r + cal.getUnit() + "/c");
            }
        }
    }
    
    /** Peak pair? i.e. on line through center at equal distance? */
    static boolean isPeakPair(Polygon peaks, int w, int h, int tol) {
        boolean ispeakPair = true;
        int[] x = {peaks.xpoints[0], w / 2, peaks.xpoints[1]};
        int[] y = {peaks.ypoints[0], h / 2, peaks.ypoints[1]};
        // line between peaks, y = mx + c, passes through center?
        double m = (double)(y[2] - y[0]) / (x[2] - x[0]);
        double yCenEstim = y[0] + m * (w / 2 - x[0]);
        if (Math.abs(yCenEstim - h / 2) > tol) {
            ispeakPair = false;
        }
        // peaks equal distance from center?
        double dist0 = I1l.dist(x[0], y[0], x[1], y[1]);
        double dist1 = I1l.dist(x[2], y[2], x[1], y[1]);
        if (Math.abs(dist1 - dist0) > tol) {
            ispeakPair = false;
        }
        return ispeakPair;
    }

    /** Calculate angle between peak pair and x-axis (radians) */
    // TODO, check
    public static double calcKangle(Polygon peakPlus, int w, int h) {
        if (peakPlus == null) {
            return Double.NaN;
        }
        // xc and yc are centered coords where w / 2 and h / 2 are 0
        int xc = peakPlus.xpoints[0] - w / 2;
        int yc = peakPlus.ypoints[0] - h / 2;
        return I1l.calcAngle(xc, yc);
    }
    
    /** Calculate SI line spacing based on 1st or 2nd order peak positions */
    public static double calcLineSpacing(Polygon peakPair, int w, int h, 
            Calibration cal, int order) {
        if (peakPair == null) {
            return Double.NaN;
        }
        double spacing = 0.0d;
        int x1 = peakPair.xpoints[0];
        int y1 = peakPair.ypoints[0];
        int x2 = peakPair.xpoints[1];
        int y2 = peakPair.ypoints[1];
        spacing = I1l.calcFourierR(x1, y1, w, h, cal);
        spacing += I1l.calcFourierR(x2, y2, w, h, cal);
        spacing /= 2.0d;
        if (order == 2) {
            return spacing;
        } else if (order == 1) {
            return spacing / 2.0d;
        } else {
            return Double.NaN;
        }
    }
    
    /** Select k+, i.e. x > 0, from a pair of peaks and return as new Polygon */
    public static Polygon selectPeakPlus(Polygon peakPair, int w) {
        if (peakPair == null) return null;
        Polygon peakPlus;
        int[] xpt = new int[1];
        int[] ypt = new int[1];
        if (peakPair.xpoints[0] - w / 2 > 0) {
            xpt[0] = peakPair.xpoints[0];
            ypt[0] = peakPair.ypoints[0];
        } else {
            xpt[0] = peakPair.xpoints[1];
            ypt[0] = peakPair.ypoints[1];
        }
        peakPlus = new Polygon(xpt, ypt, 1);
        return peakPlus;
    }
    
    /** Return standard deviation of array of single point Polygon coords */
    double[] peakPositionStdevs(Polygon[] peakSets) {
        // TODO, test me
        String debug = "  filtered peak+ position ";
        Polygon medCoord = medianCoords(peakSets);
        debug += "median coords: " + medCoord.xpoints[0] + "," + medCoord.ypoints[0];
        int ncyc = peakSets.length / phases;
        double[] stdevs = new double[ncyc];

        for (int cyc = 0; cyc < ncyc; cyc++) {
            Polygon[] peakSet = new Polygon[phases];
            for (int p = 0; p < phases; p++) {
                int n = p + phases * cyc;
                peakSet[p] = new Polygon();
                if (peakSets[n] != null) {
                    peakSet[p].addPoint(peakSets[n].xpoints[0], peakSets[n].ypoints[0]);
                }
            }
            stdevs[cyc] = stdev2D(peakSet) + 0.0001f;
            Polygon setMedCoord = medianCoords(peakSet);
            if (I1l.dist(medCoord.xpoints[0], medCoord.ypoints[0], 
                    setMedCoord.xpoints[0], setMedCoord.ypoints[0]) 
                    > (double)peakPosTolerance) {
                stdevs[cyc] = -stdevs[cyc];  // FIXME, -ve indicates outside tolerance
            }
        }
        IJ.log(debug);
        return stdevs;
    }
    
    /** Return median x,y coordinates of a set */ 
    public static Polygon medianCoords(Polygon[] coordSetRaw) {
        Polygon[] coordSet = filterNull(coordSetRaw);
        int len = coordSet.length;
        int[] x = new int[len];
        int[] y = new int[len];
        for (int i = 0; i < len; i++) {
            x[i] = coordSet[i].xpoints[0];
            y[i] = coordSet[i].ypoints[0];
        }
        int[] xm = {I1l.median(x)};
        int[] ym = {I1l.median(y)};
        return new Polygon(xm, ym, 1);
    }
    
    /** Filter out null Polygons from an array of Polygons */
    static Polygon[] filterNull(Polygon[] coordSetRaw) {
        int rawLen = coordSetRaw.length;
        int flen = 0;
        for (int i = 0; i < rawLen; i++) {
            if (coordSetRaw[i] != null) {
                flen++;
            }
        }
        Polygon[] coordSetFiltered = new Polygon[flen];
        int i2 = 0;
        for (int i = 0; i < rawLen; i++) {
            if (coordSetRaw[i] != null) {
                coordSetFiltered[i2] = coordSetRaw[i];
                i2++;
            }
        }
        return coordSetFiltered;
    }
    
    /** 
     * Positional standard deviation for 2D x, y coordinates in Polygon[] 
     * where each Polygon contains a single coordiate pair only.
     */
    public static double stdev2D(Polygon[] pos) {
        double stdev = 0;
        double xm = 0, ym = 0;
        for (int i = 0; i < pos.length; i++) {
            xm += pos[i].xpoints[0];
            ym += pos[i].ypoints[0];
        }
        xm /= pos.length;
        ym /= pos.length;
        for (int i = 0; i < pos.length; i++) {
            stdev += Math.pow((double)pos[i].xpoints[0] - xm, 2);
            stdev += Math.pow((double)pos[i].ypoints[0] - ym, 2);
        }
        stdev /= pos.length * 2;  // 2 since x & y coords
        return Math.sqrt(stdev);
    }
    
    /** Write a series of phase measurements as a column to a ResultsTable */
    void writeResults(ResultsTable rt, String colName, float[] phaseSet) {
        //  To enable writing in columns, both addValue and setValue are
        //  called - addValue prevents prior columns disappearing,
        //  setValue actually writes values to correct row.
        //  It's a hack: I found ResultsTable awkward to use.
        int p = 0;  // phase
        for (int s = 0; s < phaseSet.length; s++) {
            if (s + 1 > rt.getCounter()) {
                rt.incrementCounter();
            }
            if (s % phases == 0) {
                p = 0;
            }
            rt.addValue(colName, phaseSet[s]);
            rt.setValue(colName, s, phaseSet[s]);
            String rowLabel = "Z" + s / phases + "/P" + p;
            rt.setLabel(rowLabel, s);
            p++;
        }
        
    }
    
    /** Plot phase step series and return step, offset stdevs */
    double[] plotPhases(float[] phaseSet, double[] positionStdevs, ImageStack stackPlots) {
        phaseSet = unwrapPhaseCycles(phaseSet);
        float plotMin = I1l.min(phaseSet);
        float plotMax = I1l.max(phaseSet);
        double[] phaseStats = analyzePhases(phaseSet);
        Plot plot = new Plot("Phase plot", "Z,P", "phase (radians)");
        plot.setLineWidth(1);
        plot.setColor(Color.BLACK);
        plot.setLimits(1.0d, (double)phaseSet.length, plotMin, plotMax);
        float[] slices = new float[phaseSet.length];
        for (int s = 0; s < phaseSet.length; s++) {
            slices[s] = (float)(s + 1);
        }
        plot.addPoints(slices, phaseSet, Plot.LINE);
        slices = new float[phases];
        float[] phasesSingleCycle = new float[phases];
        int slice = 0;
        int ncyc = phaseSet.length / phases;
        for (int cyc = 0; cyc < ncyc; cyc++) {
            for (int p = 0; p < phases; p++) {
                int n = p + cyc * phases;
                slice++;
                slices[p] = slice;
                phasesSingleCycle[p] = phaseSet[n];
            }
            if (positionStdevs[cyc] > 0) {
                plot.setColor(Color.BLACK);
                plot.addPoints(slices, phasesSingleCycle, Plot.X);
            } else {
                plot.setColor(Color.RED);
                plot.addPoints(slices, phasesSingleCycle, Plot.X);
            }
        }
        ImageProcessor ipPlot = plot.getImagePlus().getProcessor();
        stackPlots.addSlice(String.valueOf("m +1"), ipPlot);
        return phaseStats;
    }
    
    /** Unwrap phase discontinuities in a set of cycles */
    float[] unwrapPhaseCycles(float[] rawPhaseSet) {
        // unwrap discontinuities over first phase of each cycle
        return unwrapPhaseCycle(rawPhaseSet);
        // unwrap discontinuities over phases within each cycle
    }
    
    /** Unwrap phase discontinuities in a single cycle, relative to first */
    float[] unwrapPhaseCycle(float[] rawPhaseSet) {
        float[] phaseSet = rawPhaseSet.clone();
        int len = phaseSet.length;
//        int sign = -1;  // i.e. the slope
        float initialStep = phaseSet[1] - phaseSet[0];
        IJ.log("initial step = " + initialStep);
//        if (phaseSet[1] - phaseSet[0] > 0) {                                        
//            sign = 1;
//        }
        float[] uPhaseSet = new float[len];
//        String debug = "  discontinuities: ";
        for (int i = 0; i < len; i++) {                                                 
            if (i % phases == 0) {                                                    
                uPhaseSet[i] = phaseSet[i];                                              
            } else {        
                uPhaseSet[i] = phaseSet[i];                                              
                // handle sign changes in phase step
////                if (sign == -1 && phaseSet[i] > phaseSet[i - 1]) {  
////                    debug +=  i + ",";
////                    uPhaseSet[i] = (-2.0f * (float)Math.PI + phaseSet[i]);             
////                } else if (sign == 1 && phaseSet[i] < phaseSet[i - 1]) {          
////                    uPhaseSet[i] = (2.0f * (float)Math.PI + phaseSet[i]);             
////                    debug +=  i + ",";
//                if (sign == -1 && phaseSet[i] > phaseSet[i - 1]) {  
//                    debug +=  i + ",";
//                    uPhaseSet[i] = (-2.0f * (float)Math.PI + phaseSet[i]);             
//                } else if (sign == 1 && phaseSet[i] < phaseSet[i - 1]) {          
//                    uPhaseSet[i] = (2.0f * (float)Math.PI + phaseSet[i]);             
//                    debug +=  i + ",";
//                } else {                                                                
//                    uPhaseSet[i] = uPhaseSet[i-1] + (phaseSet[i] - phaseSet[i-1]);       
//                }      
            }                                                                           
        }
//        IJ.log(debug);
        return uPhaseSet;
    }
    
    /** Return phase step and offset stdev */
    double[] analyzePhases(float[] phaseSet) {
        double[] phaseStats = {0.0d, 0.0d};
        if (phaseMeasurementSeriesUnbrokenForLongEnough(phaseSet)) {
            // 2. TODO, step stdev
            int nsteps = 0;
            for (int i = 0; i < phaseSet.length; i++) {
                if (i % phases != 0) {
                    if (!Float.isNaN(phaseSet[i]) && !Float.isNaN(phaseSet[i - 1])) {
                        nsteps++;
                    }
                }
            }
            double steps[] = new double[nsteps];
            int i2 = 0;
            for (int i = 0; i < phaseSet.length; i++) {
                if (i % phases != 0) {
                    if (!Float.isNaN(phaseSet[i]) && !Float.isNaN(phaseSet[i - 1])) {
                        steps[i2] = phaseSet[i] - phaseSet[i - 1];
                        i2++;
                    }
                }
            }
            phaseStats[0] = Math.sqrt(I1l.variance(steps));
            // 3. TODO, offset stdev
        } else {
            phaseStats[0] = phaseStats[1] = Double.NaN;
        }
        return phaseStats;
    }
    
    
    
    /** Require at least (2 * phases) measurements without gap */
    boolean phaseMeasurementSeriesUnbrokenForLongEnough(float[] phaseSet) {
        int thisRun = 0;
        int longestRun = 0;
        for (int i = 0; i < phaseSet.length; i++) {
            if (!Float.isNaN(phaseSet[i])) {
                thisRun++;
            } else {
                if (thisRun > longestRun) {
                    longestRun = thisRun;
                }
                thisRun = 0;
            }
            if (thisRun > longestRun) {
                longestRun = thisRun;
            }
        }
        if (longestRun >= phases * 2) {
            return true;
        } else {
            return false;
        }
    }
    
    /** Filter for pair of peaks with highest intensity, check symmetric 
     * about image center within tol.
     * Returns Polygon containing peak pair x,y coords or null if failed.
     */
    static Polygon filterPeakPair(Polygon peaks, FloatProcessor fp,
            int tol) {
        int npeaks = peaks.npoints;
        if (npeaks < 2) {
            return null;  // RETURN EARLY IF INSUFFICIENT PEAKS
        }
        float[] peakIntens = new float[npeaks];
        for (int pk = 0; pk < npeaks; pk++) {
            peakIntens[pk] = 
                    fp.getPixelValue(peaks.xpoints[pk], peaks.ypoints[pk]);
        }
        int indexMax1 = I1l.maxIndex(peakIntens);
        peakIntens[indexMax1] = 0;  // so we don't find 1st max again...
        int indexMax2 = I1l.maxIndex(peakIntens);
        int[] filtPkX = {peaks.xpoints[indexMax1], peaks.xpoints[indexMax2]};
        int[] filtPkY = {peaks.ypoints[indexMax1], peaks.ypoints[indexMax2]};
        Polygon filteredPeaks = new Polygon(filtPkX, filtPkY, 2);
        if (isPeakPair(filteredPeaks, fp.getWidth(), fp.getHeight(), tol)) {
            return filteredPeaks;
        } else {
            return null;  
        }
    }

    public static void main(String[] args) {
        System.out.println("Testing...");
        Calibration cal = new Calibration();
        cal.pixelWidth = 0.082;
        cal.pixelHeight = 0.082;
        double r1 = I1l.calcFourierR(144, 77, 256, 256, cal);
        System.out.println("expect r = 0.40, r1 = " + r1);
        double r2 = I1l.calcFourierR(95, 231, 256, 256, cal);
        System.out.println("expect r = 0.19, r2 = " + r2);
    }
    
}
