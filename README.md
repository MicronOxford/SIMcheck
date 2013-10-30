========
SIMcheck
========

SIMcheck is a package of ImageJ tools for assessing the quality and
reliability of Structured Illumination Microscopy (SIM) data.

To install the built result, copy ./build/dist/SIMcheck_.jar to your ImageJ
plugins/ directory and restart ImageJ. Note that the lib/ directory here must
contain a soft link to the ij.jar you are using for the build process to work.

Copyright Graeme Ball and Micron Oxford, Department of Biochemistry, 
University of Oxford. License GPL unless stated otherwise in a given file.

Code is arranged in a conventional Maven-like structure and includes an
ant build script that can copy the resulting SIMcheck_.jar to a local
plugin folder.

**The latest .jar can be downloaded from the 
[Micron Oxford Website](http://www.micron.ox.ac.uk/microngroup/software/SIMcheck_.jar)**

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
 Raw Z profile     |  bleaching and angle diffs OK?  |    
 Raw Fourier plots |  SI pattern correct & regular?  |    TODO? k0 & linspc
 Raw floaties      |  no floaties or drift detected? |    threshold/stat?
 Raw Mod Contrast  |  feature MCNR acceptable?       |    Wiener par

- TODO? camera correction? correlation coefficient & linearity

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

- README.txt - this file
- NOTICE.txt  - Notices and attributions required by libraries depended on
- LICENSE.txt - Project's license
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
* exec methods take input images and return results with no GUI calls
* ImageJ1-like preference for pre- java 5 features (i.e. not many generics)
  and reliance on float primitive type for most calculations
* no dependencies other than ImageJ1
* unconventional choices which some people may consider bad pratice -
  * significant use of a utility class with static methods to provide 
    functionality missing from java arrays, math, & IJ API
  * final not used to mark immutability (not worth increase in visual noise)
  * most methods package-private rather than private - less verbose & makes
    unit testing easier (I never call non-public methods between classes)


TODO
====

problems / ideas -
* Recon data histogram +ve/-ve ratio wrong
* bleaching calculation: combine per-9Z and inter-angle decay
* display / warn saturated pixels (try green)

* 1.0: integration/GUI, tests, documentation & write-up up for release
      - swing GUI runner & report
      - raw -> WF same size as SIR by interpolation (& preserve type??)
      - SIR_Fourier:
        - one orthogonal slice, not whole stack?
        - finalize scaling: separate for lat & ortho?
      - ImagePlus.isHyperstack() = true
      - SIR_hist with multiple frames
      - SIR_FFT, update window function to Rainer Heintzmann's
      - make sure tests and debug not deployed
      - convert dialog & logging to non-blocking swing GUI
      - final empirical tests, param calibration, tolerances etc.
      - public web page with EXAMPLES
      - improve log output: should be simple, concise & self-documenting
      - check preconditions & robustness w.r.t. input data (multi-d, type etc.)
      - junit test suite to test/debug non-interactive code
      - mavenize & make Fiji update site
      - try to install LUTs into IJ menu?
      - add ResultTable support to ResultSet class
      - finish & refactor Cal_Phases: unwrap (+test case), stats and structure

* 1.1: future features
      - bead puddle SI illumination image
        split angles, de-interleave 5 phases, rotate to orthogonal
      - pre: add statistic for floaty detection
      - post: k0 angle lines (manual entry?), resolution symmetrical /angle?
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
