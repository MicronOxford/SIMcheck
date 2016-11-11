/** TODO: add copyright, license etc. for ParallelFFTJ (apologies for now!) */

package SIMcheck;
import ij.IJ;
import ij.ImageJ;
import ij.measure.Calibration;
import ij.plugin.*;
import ij.ImagePlus;
import edu.emory.mathcs.parallelfftj.*;

/**
 * Perform 3D Fourier Transform on a 3D ImageJ1 stack (ImagePlus input),
 * using Piotr Wendykier's ParallelFFTJ.
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
