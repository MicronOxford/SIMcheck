/*
 *  Copyright (c) 2015, Graeme Ball.
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
import ij.ImagePlus;

/**
 * Define paths to test data: for use in main() interactive test methods.
 * @author Graeme Ball <graemeball@gmail.com>
 */
class TestData {

    /** Utility class should not be instantiated. */
    private TestData() {}

    public static final ImagePlus raw =
            IJ.openImage("src/test/resources/TestRaw.tif");

    public static final ImagePlus recon =
            IJ.openImage("src/test/resources/TestRecon.tif");

    public static final ImagePlus lawn =
            IJ.openImage("src/test/resources/BeadLawn.tif");

    public static final ImagePlus asymm =
            IJ.openImage("src/test/resources/TestAsymm.tif");

    public static final ImagePlus elyra =
            IJ.openImage("src/test/resources/TestElyra.tif");

}
