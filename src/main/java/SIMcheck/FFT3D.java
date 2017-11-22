/*  
 *  Copyright (c) 2015, Graeme Ball and Micron Oxford,                          
 *  Copyright (c) 2017, Marcel Mueller and Micron Oxford,                          
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
 *  along with this program. If not, see http://www.gnu.org/licenses/ .
 */

package SIMcheck;
import ij.IJ;
import ij.ImageJ;
import ij.measure.Calibration;
import ij.plugin.PlugIn;
import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.FloatProcessor;
import ij.ImageStack;
import org.jtransforms.fft.FloatFFT_3D;

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
     * 3D Fourier Transform using JTransforms.
     * @param imp ImagePlus to be Fourier-transformed.
     * @return ImagePlus after 3D Fourier transform.
     */
    public static ImagePlus fftImp(ImagePlus imp, double winFraction)
    {
	// check if the FFT libraries can be found
	if (!ensureFftLibInstalled(true)) {
	    return null;
	}
	
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
    
	    final int w = impC.getStack().getWidth();
	    final int h = impC.getStack().getHeight();
	    final int d = impC.getStack().size();

	    float [] fftData = stackToCplxFloat( impC.getStack() );
	   
	    FloatFFT_3D transform = new FloatFFT_3D(d,h,w); 
	    transform.complexForward( fftData );
	    
	    float [] pwSpectrum = computePowerSpectrum( fftData );
	    swapQuadrants( pwSpectrum, w, h, d );
            imps[c - 1] = new ImagePlus("Power Spectrum", stackFromRealFloat( pwSpectrum, w,h,d));
        }
        
	ImagePlus impf = I1l.mergeChannels(imp.getTitle() + "FFT3D", imps);
        impf.setCalibration(cal);
        return impf;
    }
    
    
    /** Copy a stack into a complex float array.
     *	This follows standard C-style packing for compatibility
     *	with JTransforms, i.e. the index of a pixel will be
     *	i = 2 * ( width*height * z + width * y + x )
     * */
    public static float [] stackToCplxFloat(ImageStack inSt) {

	final int w = inSt.getWidth();
	final int h = inSt.getHeight();
	float [] ret =  new float[ 2 * w * h * inSt.size() ];

	// copy all processors
	for ( int z = 0 ; z < inSt.size() ; z++) {

	    ImageProcessor ip = inSt.getProcessor( z+1 );

	    for ( int y=0; y<h; y++) {
		for ( int x=0; x<w; x++ ){
		    ret[ ( x + y*w + z*w*h) *2 ] = ip.getf(x,y);
		}
	    }
	}

	return ret;
    }


    /** Compute the power spectrum of an FFT result */
    public static float [] computePowerSpectrum( float [] in ) {

	float [] ret = new float[ in.length / 2 ];
	
	for (int i=0; i < ret.length; i++) {

	    float re = in[ 2 * i + 0 ];
	    float im = in[ 2 * i + 1 ];

	    double abs = Math.sqrt( re*re + im*im );
	    ret[i]     = (float)Math.log( 1 + abs );
	    
	}

	return ret;
    }
    
    /** Swap quadrants of an array to give 0-centered FFT output */
    public static void swapQuadrants( float [] dat, int w, int h, int d) {
	
	if ( dat.length != w*h*d )
	    throw new IndexOutOfBoundsException("Array dimensions mismatch length");

	// swap x,y
	for ( int z=0; z<d; z++) {
	    for ( int y=0; y<h/2; y++) {
		for ( int x=0; x<w/2; x++) {
		    
		    int xh = x+w/2, yh = y+h/2, zh = z+d/2;
		
		    float t1 = dat[ x  + y *w + z*w*h ];
		    float t2 = dat[ xh + y *w + z*w*h ];
		    float t3 = dat[ x  + yh*w + z*w*h ];
		    float t4 = dat[ xh + yh*w + z*w*h ];
		    
		    dat[ x  + y *w + z*w*h ] = t4;
		    dat[ xh + y *w + z*w*h ] = t3;
		    dat[ x  + yh*w + z*w*h ] = t2;
		    dat[ xh +yh *w + z*w*h ] = t1;
		}
	    }
	}
    
	// swap z
	// TODO: doing this in one big loop with 8 intermediates
	// might be more efficient, but to tired to wrap my head around that
	for ( int z=0; z<d/2; z++) {
	    for ( int y=0; y<h; y++) {
		for ( int x=0; x<w; x++) {
		    
		    int xh = x+w/2, yh = y+h/2, zh = z+d/2;
		
		    float t1 = dat[ x + y *w + zh*w*h ];
		    float t2 = dat[ x + y *w + z *w*h ];
		    dat[ x + y *w + zh*w*h ] = t2;
		    dat[ x + y *w + z *w*h ] = t1;
		    
		}
	    }
	}
    
	
    
    
    }

    /** Create an ImageStack for a 3d real-valued float array */
    public static ImageStack stackFromRealFloat( float [] dat, int w, int h, int d ) {
	if ( dat.length != w*h*d )
	    throw new IndexOutOfBoundsException("Array dimensions mismatch length");

	ImageStack ret = new ImageStack( w, h);

	for ( int z=0; z<d; z++) {
	    FloatProcessor data = new FloatProcessor( w,h);
	    
	    for (int y=0; y<h; y++) {
		for (int x=0; x<w; x++) {
		    data.setf( x, y, dat[x + y*w + z*w*h]);
		}
	    }

	    ret.addSlice( data );
	}
    
	return ret;
    }
	
    /** Raise an error message if the FFT libs are not installed / not in the classpath */
    public static boolean ensureFftLibInstalled(boolean displayError) {
        // make sure the FFT library is actually available
	try {
	    Class.forName("org.jtransforms.fft.FloatFFT_3D");
	} catch ( ClassNotFoundException e ) {
	    if (displayError) {
		IJ.error("JTransforms missing", "JTransforms library not found in classpath");
	    }
	    return false;
	}
	
	try {
	    Class.forName("pl.edu.icm.jlargearrays.FloatLargeArray");
	} catch ( ClassNotFoundException e ) {
	    if (displayError) {
		IJ.error("JLargeArray missing", "JLargeArrray (dependency for JTransforms) missing");
	    }
	    return false;
	}
	
	try {
	    Class.forName("org.apache.commons.math3.util.FastMath");
	} catch ( ClassNotFoundException e ) {
	    if (displayError) {
		IJ.error("Apache common math3 missing", "Apache commons math3 (dependency for JTransforms) missing");
	    }
	    return false;
	}
	
	
	
	return true;
   }



    /** Method intended for interactive testing, e.g. from an IDE. */
    public static void main(String[] args) {
        new ImageJ();
        TestData.raw.show();
        //IJ.runPlugIn(FFT3D.class.getName(), "");
	FFT3D test = new FFT3D();
	test.run("");

    }
}
