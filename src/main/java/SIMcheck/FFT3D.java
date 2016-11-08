/** TODO: add copyright, including ImgLib2 stuff? (apologies for now) */

package SIMcheck;
import ij.IJ;
import ij.ImageJ;
import ij.measure.Calibration;
import ij.plugin.*;
import ij.ImagePlus;
import edu.emory.mathcs.parallelfftj.*;

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
        ImagePlus impf = fftImp(imp);
        impf.show();
    }
    
    
    /**
     * 3D Fourier Transform using parallel FFTJ.
     * @param imp ImagePlus to be Fourier-transformed.
     * @return ImagePlus after 3D Fourier transform.
     */
    public static ImagePlus fftImp(ImagePlus imp)
    {
        Calibration cal = imp.getCalibration();
        // process stack for each channel sequentially
        int nc = imp.getNChannels();
        ImagePlus[] imps = new ImagePlus[nc];
        for (int c = 1; c <= nc; c++) {
            ImagePlus impC = I1l.copyChannel(imp, c);
            Transformer transform = new FloatTransformer(impC.getStack(), null);
            transform.fft();
            imps[c - 1] = transform.toImagePlus(
                    SpectrumType.POWER_SPECTRUM_LOG,
                    FourierDomainOriginType.AT_CENTER);
        }
        ImagePlus impf = I1l.mergeChannels(imp.getTitle() + "FFT3D", imps);
        impf.setCalibration(cal);
        return impf;
    }
    
    
    /** Method intended for interactive testing, e.g. from an IDE. */
    public static void main(String[] args) {
        new ImageJ();
        TestData.raw.show();
        IJ.runPlugIn(FFT3D.class.getName(), "");
    }
}
