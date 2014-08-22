package SIMcheck;

// adapted from ij/plugin/Slicer.java
// original author: unknown
// license: unknown (public domain?)

import ij.*;
import ij.plugin.PlugIn;
import ij.process.*;
import ij.gui.*;
import ij.measure.*;
import java.awt.*;

/** Custom ortho reslicer from stripped-down version of IJ Reslice command.
 * @see ij.plugin.Slicer
 */
class OrthoReslicer implements PlugIn {

    private double inputZSpacing = 1.0;
    private double outputZSpacing = 1.0;
    private int outputSlices = 1;
    private ImagePlus imp;
    boolean interpolate = true;

    /** OrthoReslicer can be run as an IJ plugin for testing - see exec */
    public void run(String arg) {
        imp = WindowManager.getCurrentImage();
        ImagePlus impOrtho = exec(imp, true);
        impOrtho.show();
    }

    /** Reslice hyperstack to create orthogonal view (from top, with interpln).
     * @param imp gray32 hyperstack/stack, ROI ignored
     * @return hyperstack with orthogonal view
     */
    public ImagePlus exec(ImagePlus imp, boolean interp) {

        interpolate = interp;
        // 1. check input image is valid
        int stackSize = imp.getStackSize();
        if ((imp.getType() != ImagePlus.GRAY32) || (stackSize < 2)) {
            throw new IllegalArgumentException(
                    "Error creating orthogonal view - need 32-bit stack.");
        }
        Roi roi = imp.getRoi();

        // 2. set up calibration etc.
        Calibration cal = imp.getCalibration();
        if (interpolate) {
            inputZSpacing = cal.pixelDepth;
            double outputSpacing = cal.pixelDepth;
            outputZSpacing = outputSpacing / cal.pixelHeight;
        } else {
            inputZSpacing = cal.pixelDepth;
            outputZSpacing = 1;
        }
        ImagePlus imp2 = null;

        // 3. reslice stack / hyperstack & display result
        imp2 = resliceHyperstack(imp);
        ImageProcessor ip = imp.getProcessor();
        double min = ip.getMin();
        double max = ip.getMax();
        imp2.getProcessor().setMinAndMax(min, max);
        imp.setRoi(roi); // restore input imp ROI

        return imp2;
    }

    private ImagePlus resliceHyperstack(ImagePlus imp) {
        int channels = imp.getNChannels();
        int slices = imp.getNSlices();
        int frames = imp.getNFrames();
        int c1 = imp.getChannel();
        int z1 = imp.getSlice();
        int t1 = imp.getFrame();
        int width = imp.getWidth();
        int height = imp.getHeight();
        ImagePlus imp2 = null;
        ImageStack stack2 = null;
        Roi roi = imp.getRoi();
        for (int t = 1; t <= frames; t++) {
            for (int c = 1; c <= channels; c++) {
                ImageStack tmp1Stack = new ImageStack(width, height);
                for (int z = 1; z <= slices; z++) {
                    imp.setPositionWithoutUpdate(c, z, t);
                    tmp1Stack.addSlice(null, imp.getProcessor());
                }
                ImagePlus tmp1 = new ImagePlus("tmp", tmp1Stack);
                tmp1.setCalibration(imp.getCalibration());
                tmp1.setRoi(roi);
                ImagePlus tmp2 = resliceStack(tmp1);
                int slices2 = tmp2.getStackSize();
                if (imp2 == null) {
                    imp2 = tmp2.createHyperStack("Ortho_Z_" + imp.getTitle(),
                            channels, slices2, frames, tmp2.getBitDepth());
                    stack2 = imp2.getStack();
                }
                ImageStack tmp2Stack = tmp2.getStack();
                for (int z = 1; z <= slices2; z++) {
                    imp.setPositionWithoutUpdate(c, z, t);
                    int n2 = imp2.getStackIndex(c, z, t);
                    stack2.setPixels(tmp2Stack.getPixels(z), n2);
                }
            }
        }
        imp.setPosition(c1, z1, t1);
        if (channels > 1 && imp.isComposite()) {
            imp2 = new CompositeImage(imp2, ((CompositeImage) imp).getMode());
            ((CompositeImage) imp2).copyLuts(imp);
        }
        return imp2;
    }

    private ImagePlus resliceStack(ImagePlus imp) {
        ImagePlus imp2;
        Calibration origCal = imp.getCalibration();
        double zSpacing = inputZSpacing / imp.getCalibration().pixelHeight;
        if (!interpolate) {
            Calibration tmpCal = origCal.copy();                            
            tmpCal.pixelWidth = 1.0;                                        
            tmpCal.pixelHeight = 1.0;                                       
            tmpCal.pixelDepth = 1.0;                                        
            imp.setCalibration(tmpCal);
            inputZSpacing = 1.0;
            outputZSpacing = 1.0;
        }
        imp2 = resliceRectOrLine(imp);
        imp.setCalibration(origCal);
        imp2.setCalibration(origCal);
        Calibration cal = imp2.getCalibration();
        cal.pixelWidth = origCal.pixelWidth;
        cal.pixelDepth = origCal.pixelHeight * outputZSpacing;
        if (interpolate) {
            cal.pixelHeight = origCal.pixelDepth / zSpacing;
        } else {
            cal.pixelHeight = origCal.pixelDepth;
        }
        return imp2;
    }

    private ImagePlus resliceRectOrLine(ImagePlus imp) {
        double x1 = 0.0;
        double y1 = 0.0;
        double x2 = 0.0;
        double y2 = 0.0;
        double xInc = 0.0;
        double yInc = 0.0;
        imp.setRoi(0, 0, imp.getWidth(), imp.getHeight());
        Roi roi = imp.getRoi();

        Rectangle r = roi.getBounds();
        x1 = r.x;
        y1 = r.y;
        x2 = r.x + r.width;
        y2 = r.y;
        
        xInc = 0.0;
        yInc = outputZSpacing;
        outputSlices = (int) (r.height / outputZSpacing);
        ImageStack stack2 = null;
        for (int i = 0; i < outputSlices; i++) {
            ImageProcessor ip = getSlice(imp, x1, y1, x2, y2);
            if (stack2 == null) {
                stack2 = createOutputStack(imp, ip);
            }
            stack2.setPixels(ip.getPixels(), i + 1);
            x1 += xInc;
            x2 += xInc;
            y1 += yInc;
            y2 += yInc;
        }
        return new ImagePlus("Reslice of " + imp.getShortTitle(), stack2);
    }

    private ImageStack createOutputStack(ImagePlus imp, ImageProcessor ip) {
        int bitDepth = imp.getBitDepth();
        int w2 = ip.getWidth(), h2 = ip.getHeight(), d2 = outputSlices;
        int flags = NewImage.FILL_BLACK + NewImage.CHECK_AVAILABLE_MEMORY;
        ImagePlus imp2 = NewImage.createImage("temp", w2, h2, d2, bitDepth,
                flags);
        ImageStack stack2 = imp2.getStack();
        stack2.setColorModel(ip.getColorModel());
        return stack2;
    }

    private ImageProcessor getSlice(ImagePlus imp, double x1, double y1,
            double x2, double y2) {
        ImageStack stack = imp.getStack();
        int stackSize = stack.getSize();
        ImageProcessor ip, ip2 = null;
        float[] line = null;
        for (int i = 0; i < stackSize; i++) {
            ip = stack.getProcessor(i + 1);
            line = getOrthoLine(ip, (int) x1, (int) y1, (int) x2, (int) y2,
                    line);
            if (i == 0)
                ip2 = ip.createProcessor(line.length, stackSize);
            putRow(ip2, 0, i, line, line.length);
        }
        Calibration cal = imp.getCalibration();
        double zSpacing = inputZSpacing / cal.pixelWidth;
        if (zSpacing != 1.0) {
            ip2.setInterpolate(true);
            ip2 = ip2.resize(line.length, (int) (stackSize * zSpacing));
        }
        return ip2;
    }

    private void putRow(ImageProcessor ip, int x, int y, float[] data,
            int length) {
        for (int i = 0; i < length; i++)
            ip.putPixelValue(x++, y, data[i]);
    }

    private float[] getOrthoLine(ImageProcessor ip, int x1, int y1, int x2,
            int y2, float[] data) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        int n = Math.max(Math.abs(dx), Math.abs(dy));
        if (data == null)
            data = new float[n];
        int xinc = dx / n;
        int yinc = dy / n;
        int rx = x1;
        int ry = y1;
        for (int i = 0; i < n; i++) {
            data[i] = (float) ip.getPixelValue(rx, ry);
            rx += xinc;
            ry += yinc;
        }
        return data;
    }

    /** main() method for testing. */
    public static void main(String[] args) {
        System.out.println("Testing OrthoReslicer.java");
        ImagePlus impTest = TestData.recon;
        impTest.show();
        OrthoReslicer orthoReslicer = new OrthoReslicer();
        ImagePlus impOrthoI = orthoReslicer.exec(impTest, true);
        impOrthoI.setTitle("orthoResliced_interpolated");
        impOrthoI.show();
        IJ.log("impOrthoI height/width after reslice: " + impOrthoI.getHeight()
                + "/" + impOrthoI.getWidth());
        ImagePlus impOrthoNI = orthoReslicer.exec(impTest, false);
        impOrthoNI.setTitle("orthoResliced_non_interpolated");
        impOrthoNI.show();
        IJ.log("impOrthoNI height/width after reslice: "
                + impOrthoNI.getHeight() + "/" + impOrthoNI.getWidth());
    }

}
