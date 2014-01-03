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
* exec methods take input images and return ResulSet with no GUI calls
* no dependencies other than ImageJ1
* ImageJ1-like preference for pre- java 5 features (i.e. not many generics)
  and reliance on float primitive type for most calculations


TODO
====

* 1.0: integration/GUI, tests, documentation & write-up up for release
      - improve log output & names: should be simple, concise & self-explanatory
        - MCNR: change name of "Z window half-width"
        - MCNR: what does "raw Fourier" option do?
      - Wiener filter parameter estimate - document, calibrate
      - paper & public web page with EXAMPLES
      - display / warn saturated pixels (try green)
      - add ResultTable support to ResultSet class
      - finish & refactor Cal_Phases: unwrap (+test case), stats and structure
      - discard negatives macro / utility
      - bead puddle SI illumination image
        split angles, de-interleave 5 phases, rotate to orthogonal
      - mavenize & make Fiji update site
      - raw -> WF same size as SIR by interpolation (& preserve type??)
      - convert dialog & logging to non-blocking swing GUI
      - check preconditions & robustness w.r.t. input data (multi-d, type etc.)
      - better LUT loading scheme, try to install LUTs into IJ menu?
      - more junit tests to test/debug non-interactive code
      - make sure tests and debug not deployed
      - final empirical tests, param calibration, tolerances etc.
      - SIR_hist with multiple frames
      - SIR_FFT, try Rainer's window function

* 1.1: future features
      - pre: add statistic for floaty / uneven illumination detection
      - post: k0 angle lines (manual entry?), resolution, symmetrical /angle?
      - post: SIR FFT automatic resolution estimation??
      - pre: FFT check to estimate angles & line-spacing
      - cali: PSF symmetry within tolerance?
      - cali: OTF extent, shape & order separation?


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
