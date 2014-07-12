package SIMcheck;

// adpated from Radial Profile Plot plugin: 
//    http://rsbweb.nih.gov/ij/plugins/radial-profile.html
// original author: Paul Baggethun, an engineer in Pittsburgh, PA
// license: unknown (public domain?)

import ij.*;
import ij.plugin.PlugIn;
import ij.process.*;
import ij.gui.*;
import ij.measure.Calibration;
import java.util.Properties;

class Radial_Profile implements PlugIn {

    // parameter fields
    public int nBins=100;

    private ImagePlus imp;
    private double X0;
    private double Y0;
    private double mR;
    private boolean useCalibration = true;

    public void run(String arg) {
        ImagePlus plotImp = exec(IJ.getImage());
        plotImp.show();
    }
    
    public ImagePlus exec(ImagePlus imp) {
        this.imp = imp;
        setXYcenter();
        ImageProcessor ip = imp.getProcessor();
        imp.startTiming();
        ImagePlus result = doRadialDistribution(ip);
        return result;
    }
    
    private void setXYcenter() {
        int width = imp.getWidth();
        int height = imp.getHeight();
        X0 = width / 2;
        Y0 = height / 2;
        mR = (width + height) / 4.0;
    }

    private ImagePlus doRadialDistribution(ImageProcessor ip) {
        nBins = (int) (3*mR/4);
        int thisBin;
        float[][] Accumulator = new float[2][nBins];
        double R;
        double xmin=X0-mR, xmax=X0+mR, ymin=Y0-mR, ymax=Y0+mR;
        for (double i=xmin; i<xmax; i++) {
            for (double j=ymin; j<ymax; j++) {
                R = Math.sqrt((i-X0)*(i-X0)+(j-Y0)*(j-Y0));
                thisBin = (int) Math.floor((R/mR)*(double)nBins);
                if (thisBin==0) thisBin=1;
                thisBin=thisBin-1;
                if (thisBin>nBins-1) thisBin=nBins-1;
                Accumulator[0][thisBin]=Accumulator[0][thisBin]+1;
                Accumulator[1][thisBin]=
                        Accumulator[1][thisBin]+ip.getPixelValue((int)i,(int)j);
            }
        }
        Calibration cal = imp.getCalibration();
        Properties props = imp.getProperties();
        boolean isFourier = false;
        if (props != null) {
            isFourier = props.containsKey("FHT");
        }
        if (cal.getUnit() == "pixel") useCalibration=false;
        Plot plot = null;
        if (useCalibration) {
            for (int i = 0; i < nBins; i++) {
                Accumulator[1][i] =  Accumulator[1][i] / Accumulator[0][i];
                Accumulator[0][i] =
                        (float)(cal.pixelWidth*mR*((double)(i+1)/nBins));
            }
            String units = cal.getUnits();
            if (isFourier) {
                units = "1/x " + units;
            }
            plot = new Plot("Radial Profile Plot", "Radius ["+ units +"]", 
                    "Normalized Integrated Intensity",
                    Accumulator[0], Accumulator[1]);
        } else {
            for (int i = 0; i < nBins; i++) {
                Accumulator[1][i] = Accumulator[1][i] / Accumulator[0][i];
                Accumulator[0][i] = (float)(mR*((double)(i+1)/nBins));
            }
            plot = new Plot("Radial Profile Plot", "Radius [pixels]", 
                    "Normalized Integrated Intensity",
                    Accumulator[0], Accumulator[1]);
        }
        return plot.getImagePlus();
    }

    /** main() method for testing. */
    public static void main(String[] args) {
        System.out.println("Testing OrthoReslicer.java");
        ImagePlus impTest = IJ.openImage(
                    "/Users/graemeb/Documents/InTray/SIMcheck/Test/V3_good_DAPI_SIR_Z24.tif");
        impTest = FFT2D.fftImp(impTest);
        impTest.show();
        Radial_Profile radialProfiler = new Radial_Profile();
        ImagePlus radialProfile = radialProfiler.exec(impTest);
        radialProfile.show();
    }
    
}


