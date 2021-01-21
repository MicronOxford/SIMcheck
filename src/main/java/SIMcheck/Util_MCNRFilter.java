/*
 * Copyright (c) 2021, David Miguel Susano Pinto and Micron Oxford,
 * University of Oxford, Department of Biochemistry.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see http://www.gnu.org/licenses/ .
 */

package SIMcheck;

import ij.plugin.PlugIn;
import ij.plugin.Macro_Runner;

public class Util_MCNRFilter implements PlugIn {
    public void run(String arg) {
        Macro_Runner.runMacroFromJar("SIMcheck/MCNR-filter.ijm", "");
    }
}
