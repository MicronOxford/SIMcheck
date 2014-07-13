/* Copyright (c) 2013, Graeme Ball - see I1l.java */

package SIMcheck;

import static org.junit.Assert.*;
import org.junit.Test;

public class I1lTest {

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
    
    @Test
    public final void testMergeChannels() {
        // TODO: test copyChannel then mergeChannels recovers input
        assertTrue(true);
    }    
    
    @Test
        public final void testCat() {
            assertArrayEquals(JM.cat(new int[] {1, 2}, new int[] {3}), 
                    new int[] {1, 2, 3});
            assertArrayEquals(JM.cat(new int[0], new int[] {1, 2}), 
                    new int[] {1, 2});
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

}
