/** TODO: add copyright, license etc. for ParallelFFTJ (apologies for now!) */

package SIMcheck;
import ij.IJ;
import ij.ImageJ;
import ij.measure.Calibration;
import ij.plugin.*;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.ImageStack;
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
    
    private static final double WIN_FRACTION_DEFAULT = 0.06d;
    
    /** Carry out 3D FFT on stack of current ImagePlus. */
    @Override
    public void run(String arg) {
        // process ImagePlus from current active window
        ImagePlus imp = IJ.getImage();
        ImagePlus impf = fftImp(imp, WIN_FRACTION_DEFAULT);
        impf.show();
    }
    
    
    /**
     * 3D Fourier Transform using parallel FFTJ.
     * @param imp ImagePlus to be Fourier-transformed.
     * @return ImagePlus after 3D Fourier transform.
     */
    public static ImagePlus fftImp(ImagePlus imp, double winFraction)
    {
        Calibration cal = imp.getCalibration();
        // apply XY window function to each slice (TODO: Z winfunc!)
        ImagePlus imp2 = imp.duplicate();
        ImageStack stk = imp2.getStack();
        for (int s = 1; s <= imp.getStackSize(); s++) {
            ImageProcessor ip = stk.getProcessor(s);
            stk.setProcessor(FFT2D.gaussWindow(ip, winFraction), s);
        }
        imp2.setStack(stk);
        // process stack for each channel sequentially
        int nc = imp2.getNChannels();
        ImagePlus[] imps = new ImagePlus[nc];
        for (int c = 1; c <= nc; c++) {
            ImagePlus impC = I1l.copyChannel(imp2, c);
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
