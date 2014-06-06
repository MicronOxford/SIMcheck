========
SIMcheck
========

SIMcheck is a package of ImageJ tools for assessing the quality and
reliability of Structured Illumination Microscopy (SIM) data.

* More information can be found on the 
[Micron Oxford Website](http://www.micron.ox.ac.uk/software/SIMCheck.shtml)
* **The latest .jar can be downloaded from
[here](http://www.micron.ox.ac.uk/microngroup/software/SIMcheck_.jar)**
* Further help is available
[here](http://www.micron.ox.ac.uk/microngroup/software/SIMcheck.html)

The code is arranged in a conventional Maven-like structure and includes
an ant build script that can copy the resulting SIMcheck_.jar to a local
plugin folder. Before building the project, first make a ./lib/ directory 
containing a soft link to the ij.jar from the desired ImageJ version,
as well as a soft link to junit.jar. The built result will be copied
to ./plugins/ so soft-link this to your ImageJ plugins folder. Type "ant"
to build the default (all) target and restart ImageJ or Help->Refresh Menus

Copyright Graeme Ball and Micron Oxford, Department of Biochemistry, 
University of Oxford. License GPL unless stated otherwise in a given file.


Features
========

-----------------------
0: SIMcheck main dialog
-----------------------

- dialog to choose and set up all checks with standard parameters
- results: images will appear and key statistics will be logged
- help button: link to instructions, help, documentation

---------------------
1: Calibration Checks
---------------------

    Check          |        Statistic(s)            |       Comments
------------------ | ------------------------------ | -----------------------
 Cal phases        | phase step & range stable?     | +k0 angles, linespacing
 Cal grating, TODO | grating clean?                 |  grating image
 Cal PSF, TODO     | SNR, symmetry, posn&focus OK?  | 

----------------------------------
2: Pre-processing, Raw Data Checks
----------------------------------

    Check          |        Statistic(s)             |      Comments
------------------ | ------------------------------- | ---------------------
 Raw Angle Diff    |  no colored differences?        |    threshold/stat?
 Raw Z Profile     |  bleaching and angle diffs OK?  | 
 Raw Fourier plots |  SI pattern correct & regular?  |    TODO? k0 & linspc
 Raw Mod Contrast  |  feature MCNR acceptable?       |    Wiener par

-----------------------------
3: Post-reconstruction Checks
-----------------------------

    Check          |        Statistic(s)              |     Comments
------------------ | -------------------------------- | --------------------
 SIR Histogram     |  +ve/-ve ratio acceptable?       | top/bottom 0.5%
 SIR Z variation   |  stdev of miniumum vs. mean      | shows mismatch
 SIR Fourier Plot  |  symmetry+profile OK? +res/angle | +radial profile
 SIR MCNR Map      |  None - for visual inspection    | MCNR + intensity


PROJECT STRUCTURE
=================

- README.md - This file
- LICENSE.txt - Project's license
- AUTHORS.txt  - Authors / contributions
- CHANGES.txt  - History of versions and changes
- NOTICE.txt  - Notices and attributions required by libraries depended on
- build.xml - ant buildfile
- lib/ - contains soft links to necessary libraries (i.e. ij.jar)
- src/main/java/ - Application sources (in SIMcheck/ package)
- src/main/resources/ - Application resources (IJ menu config, html help text)
- src/test/java/ - Test sources
- src/test/resources/ - Test resources
- target/ - output SIMcheck_.jar file
- target/classes/ - for build output .class files
- target/test-classes/ - classes produced by tests
- docs/ - documentation (output from javadoc)

Eclipse project created using File->New->Project->from Ant build file


Style Notes
===========

* simple, modular structure - each check is a standalone plugin
* plugin exec methods take input images and return ResultSet with no GUI calls
* no dependencies other than ImageJ1
* ImageJ1-like preference for pre- java 5 features (i.e. not many generics)
  and reliance on float primitive type for most calculations


TODO
====

* 1.0: integration/GUI, tests, documentation & write-up up for release
      - documentation: 
        - make more self-documenting: improve names & log output & names
        - finish/improve docs, illustrate usage with pictures, examples
      - fixes:
        - SIR Fourier scaling issues / maybe 3D?
          - also, replace crop-to-min/mean/mode with checkbox: crop to mode or
            not
          - gauss window: change to percentage instead of "0-1" (or perhaps
            checkbox)
          - axial crop to mode: needs to be done before edge padding
        - Mod Contrast Map should warn when no mod contrast data, not throw NPE
        - N-SIM data testing
        - Wiener filter parameter estimate - calibrate, document
        - check all with multiple frames (see SIR_hist) -- fix or document
        - bead puddle SI illumination image: test, add multi-channel support?
        - finish & refactor Cal_Phases: unwrap (+test case), stats and structure
      - features:
        - util for rescaling as per Lothar_macros (discard negatives) 
          -- use for FFT?
        - stack the "pattern focus" images in one file after auto-scaling the
          3 angles
        - save pattern angles as preference
        - find angles automatically for "pattern focus"?
        - raw data per angle difference: RMS error? (at least some stat)
        - raw -> WF same size as SIR by interpolation (& preserve type??)
        - tool for merging SIM & widefield data (Julio)
      - tests, structure:
        - final empirical tests, param calibration, tolerances etc.
        - mavenize & make Fiji update site
        - more tests to test/debug non-interactive code, preconditions / inputs
        - add ResultTable support to ResultSet class
        - make sure tests and debug not deployed

* 1.1: future features
      - convert dialog & logging to non-blocking swing GUI
      - post: option for per-angle profiles for SIR Fourier FFT (specified K0 values)
        - store k0 values? k0 values in degrees?
      - pre: add statistic for floaty / uneven illumination detection
      - post: k0 angle lines (manual entry?), resolution, symmetrical /angle?
      - post: SIR FFT automatic resolution estimation??
      - pre: FFT check to estimate angles & line-spacing
      - cali: PSF symmetry within tolerance?
      - cali: OTF extent, shape & order separation?
      - pre: plot channel color from channel metadata


SIM Reconstruction Problems & Remedies 
--------------------------------------
(Kai Wicker)

- **Camera background**: subtraction

- **optical distortions & uneven gain**: flat-field correction

- **illumination intensity fluctuations**: normalisation (after background subtraction)

- **sample drift between images**: drift correction by means of cross-correlation

- **unknown grating period**: find grating vector using component cross-correlations

- **fluctuations in grating phase**: optimization of mixing matrix

- **fluctuation of order strengths between images**

- **sample drift between focal slices**: drift correction through cross-correlation

- **unknown zero grating phase**: global phase via cross-correlation

- **drift between rotational orientations**: drift correction through cross-correlation

- **unknown order strengths**: compare different separated components
