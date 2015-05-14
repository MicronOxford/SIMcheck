/* Copyright (c) 2014, Graeme Ball - see I1l.java */

package SIMcheck;

import org.junit.*;
import static org.junit.Assert.*;
import ij.*;
import ij.process.*;

public class I1lTest {
    
    private static ImagePlus impBleach;
    
    @BeforeClass
    public static void setUp() {
        // generate "impBleach", an 8-bit bleaching time series test image
        int nChannels = 2;
        int nTime = 5;
        impBleach = IJ.createHyperStack("multiChannelBleach",
                64, 64, nChannels, 1, nTime, 8);
        for (int c = 1; c <= nChannels; c++) {
            for (int t = 1; t <= nTime; t++) {
                impBleach.setSlice(impBleach.getStackIndex(c, 1, t));
                ImageProcessor ip = impBleach.getProcessor();
                ip.min(5.0d * c / t); // c1 decays 5 to 1, c2 10 to 1
//                J.out("impBleach(C" + c + "/T" + t + "): "
//                        + impBleach.getProcessor().getStatistics().mean);
            }
        }
//        J.out("impBleach is composite? " + impBleach.isComposite());
    }

    @Test
    public final void testCat() {
        assertArrayEquals(J.cat(new int[] {1, 2}, new int[] {3}), 
                new int[] {1, 2, 3});
        assertArrayEquals(J.cat(new int[0], new int[] {1, 2}), 
                new int[] {1, 2});
    }

    @Test
    public final void testNormalizeImp() {
        // also exercises & tests copyChannel, mergeChannels, normalizeStack
        ImagePlus nImp = I1l.normalizeImp(impBleach);
        assertTrue(nImp.isComposite());  // 1. make sure we preserve composite
        int nChannels = impBleach.getNChannels();
        int nTime = impBleach.getNFrames();
        for (int c = 1; c <= nChannels; c++) {
            nImp.setSlice(nImp.getStackIndex(c, 1, 1));
            double firstMean = nImp.getProcessor().getStatistics().mean;
            // 2. check original first slice mean was preserved
            impBleach.setSlice(nImp.getStackIndex(c, 1, 1));
            double originalMean = impBleach.getProcessor().getStatistics().mean;
            assertTrue(J.approxEq(firstMean, originalMean));
            // 3. after normalization, all slice means should equal the first
            for (int t = 1; t <= nTime; t++) {
                nImp.setSlice(nImp.getStackIndex(c, 1, t));
                double thisMean = nImp.getProcessor().getStatistics().mean;
                assertTrue(J.approxEq(firstMean, thisMean));
            }
        }
    }
    

    @Test
    public final void testSliceList() {
        try {
            I1l.sliceList(1,2);
            assertTrue("sliceList argument number check failed", false);
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            I1l.sliceList(10, 1, 11);
            assertTrue("invalid slice check failed for range 1-11/10", false);
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        try {
            I1l.sliceList(10, 10, 1);
            assertTrue("invalid range check failed for range 10-1/10", false);
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }
        assertEquals(5, I1l.sliceList(10, 1, 5).length);
        assertEquals(10, I1l.sliceList(10, 1, 5, 2, 1, 2).length);
        assertArrayEquals(new int[] {1, 2, 3, 4, 5, 11, 12, 13, 14, 15}, 
                I1l.sliceList(10, 1, 5, 2, 1, 2));
        assertArrayEquals(new int[] {1, 2, 3, 4, 5, 11, 12, 13, 14, 15}, 
                I1l.sliceList(10, 1, 5, 2, 1, 2, 1, 1, 1));
        assertArrayEquals(new int[] {31, 32, 33}, 
                I1l.sliceList(10, 1, 3, 2, 2, 2, 3, 2, 2));
    }    
    
    @Test
    public final void testStackSliceNo() {
        // 1 dimension
        assertEquals(1, I1l.stackSliceNo(1, 10));
        assertEquals(5, I1l.stackSliceNo(5, 10));
        assertEquals(10, I1l.stackSliceNo(10, 10));
        // 2 dimensions
        assertEquals(1, I1l.stackSliceNo(1, 10, 1, 2));
        assertEquals(11, I1l.stackSliceNo(1, 10, 2, 3));
        assertEquals(15, I1l.stackSliceNo(5, 10, 2, 3));
        assertEquals(20, I1l.stackSliceNo(10, 10, 2, 3));
        // 3 dimensions
        assertEquals(1, I1l.stackSliceNo(1, 10, 1, 2, 1, 7));
        assertEquals(51, I1l.stackSliceNo(1, 10, 2, 2, 3, 7));
        assertEquals(65, I1l.stackSliceNo(5, 10, 1, 2, 4, 7));
        assertEquals(140, I1l.stackSliceNo(10, 10, 2, 2, 7, 7));
        assertTrue(true);
    }

    @AfterClass
    public static void tearDown() {
        impBleach.close();
    }
}
