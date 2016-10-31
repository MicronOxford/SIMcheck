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
    /** Carry out 3D FFT on stack of current ImagePlus. */
    @Override
    public void run(String arg) {
        // process ImagePlus from current active window
        ImagePlus imp = IJ.getImage();
        ImagePlus impf = exec(imp);
        impf.show();
    }
    
    /** Carry out 3D FFT using parallel FFTJ. */
    public ImagePlus exec(ImagePlus imp) {
        ImagePlus impf = imp.duplicate();
        impf.setTitle(imp.getTitle() + "FFT3D");
        return impf;
    }
    
    
    /** Method intended for interactive testing, e.g. from an IDE. */
    public static void main(String[] args) {
        new ImageJ();
        TestData.raw.show();
        IJ.runPlugIn(FFT3D.class.getName(), "");
    }
}
