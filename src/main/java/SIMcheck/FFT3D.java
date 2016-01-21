/** TODO: add copyright, including ImgLib2 stuff? (apologies for now) */

package SIMcheck;
import ij.IJ;
import ij.ImageJ;
import ij.plugin.*;
import ij.process.ImageProcessor;
import ij.ImagePlus;
import net.imglib2.img.Img;
import net.imglib2.exception.IncompatibleTypeException;
import net.imglib2.img.ImagePlusAdapter;
import net.imglib2.img.display.imagej.ImageJFunctions;
import net.imglib2.type.numeric.complex.ComplexFloatType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.converter.ComplexPowerGLogFloatConverter;

/**
 * Perform 3D Fourier Transform on a 3D ImageJ1 stack (ImagePlus input),
 * using ImgLib2 to carry out the FFT. Each channel and/or time-point is
 * processed sequentially, and the result is a log-scaled power spectrum.
 * 
 * This class is an ImageJ1 plugin, but requires ImgLib2 to run. It does
 * not depend on ImageJ2/SCIFIO. Call the exec() method to use programatically.
 * 
 * @author graemeball@gmail.com
 *
 */
public class FFT3D implements PlugIn
{
    // Note: exec() calls processVols(), which applies a VolProcessor
    // to each 3D volume & builds an ImagePlus hyperstack result.
    
    /** Process ImagePlus containing a 3D volume, return a new ImagePlus. */
    public interface VolProcessor {
        public ImagePlus process(ImagePlus imp);
    }
    
    /**
     * Implements 3D FFT using ImgLib2, converting to FloatType
     * and returning float result (log-scaled power spectrum). 
     */
    private class FFT3Dprocessor implements VolProcessor {
        
        public ImagePlus process(ImagePlus imp) {
            Img<FloatType> floatImg = ImagePlusAdapter.convertFloat(imp);
            Img<ComplexFloatType> imgFC = fft3d(floatImg);
            String resultTitle = imp.getTitle() + "_FFT";
            ImagePlus impFT = ImageJFunctions.wrapFloat(imgFC,
                    new ComplexPowerGLogFloatConverter<ComplexFloatType>(),
                    resultTitle);
            impFT.setTitle(resultTitle);
            impFT.setDimensions(1, imp.getNSlices(), 1);
            ImagePlus fullImpFT = mirrorStackXabout0(impFT);
            return fullImpFT;
        }
        
        /**
         * Apply 3D FFT to an Img using ImgLib2, return complex Img.
         * Throw RuntimeException & return null if something goes wrong.
         */
        @SuppressWarnings("deprecation")  // Ugly. TODO: use the new fft2
        private Img<ComplexFloatType> fft3d(Img<FloatType> image) {
            Img<ComplexFloatType> imageFC = null;
            try {
                net.imglib2.algorithm.fft.FourierTransform
                <FloatType, ComplexFloatType> fft =
                new net.imglib2.algorithm.fft.FourierTransform
                <FloatType, ComplexFloatType>(
                        image, new ComplexFloatType());
                fft.process();
                imageFC = fft.getResult();
            } catch (IncompatibleTypeException e) {
                throw new RuntimeException(e.toString());
            }
            return imageFC;
        }
        
        /** 
         * Mirror an IJ1 ImagePlus's stack in X "about 0", doubling X size.
         * Here the 0-frequency is to the right of the input stack, mid-Y.
         */
        private ImagePlus mirrorStackXabout0(ImagePlus imp) {
            ImagePlus imp1 = new Duplicator().run(imp);
            ImagePlus imp2 = new Duplicator().run(imp1);
            // TODO: use ImgLib2 instead. "Combine" requires show() for inputs
            imp1.show();
            imp2.show();
            for (int s = 1; s <= imp2.getStackSize(); s++) {
                imp2.setSlice(s);
                ImageProcessor ip = imp2.getProcessor();
                ip.flipHorizontal();
                ip.flipVertical();
                imp2.setProcessor(ip);
            }
            String t1 = imp1.getTitle();
            String t2 = imp2.getTitle();
            IJ.run(imp2, "Combine...", "stack1=" + t1 + " stack2=" + t2);
            return imp2;
        }
        
    }  // end FFT3Dprocessor class definition
    
    
    /** Carry out 3D FFT on stack of current ImagePlus, if possible. */
    @Override
    public void run(String arg) {
        // process ImagePlus from current active window
        ImagePlus imp = IJ.getImage();
        ImagePlus impf = exec(imp);
        if (impf != null) {
            impf.show();
        } else {
            IJ.error("Invalid Input", "FFT3D requires 3D input with >1 slice");
        }
    }
    
    /** Carry out 3D FFT. Return null if image has only 1 Z slice. */
    public ImagePlus exec(ImagePlus imp) {
        if (imp.getNSlices() < 2) {
            return null;
        }
        ImagePlus impf = processVols(new FFT3Dprocessor(), imp);
        return impf;
    }
    
    /**
     * Process each 3D volume and recombine into hyperstack.
     * This is an example of the command pattern (TODO: convert to lambda)
     */
    private static ImagePlus processVols(VolProcessor p, ImagePlus imp) {
        // TODO, loop over C, T & merge into  hyperstack
        ImagePlus imp2 = p.process(imp);
        return imp2;
    }
    
    /** Method intended for interactive testing, e.g. from an IDE. */
    public static void main(String[] args) {
        new ImageJ();
        String headURL = "http://imagej.nih.gov/ij/images/t1-head.zip";
        ImagePlus imp = IJ.openImage(headURL);
        imp.show();
        imp.setRoi(40,40,128,128);
        ImagePlus impCrop = new Duplicator().run(imp, 33, 96);
        imp.close();
        impCrop.show();
        IJ.runPlugIn(FFT3D.class.getName(), "");
    }
}
