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

The project was recently converted to the maven build and dependency
management tool (the previous ant build setup is described in the 
next paragraph below). A solution for local deployment of the latest
maven build is still being worked on, and there is no Fiji update site
yet. To build, run the following (.jar file appears in ./target/):-

    mvn package


The code has always been arranged in a conventional Maven-like structure,
and previously came with an ant build script that copied the resulting
SIMcheck_.jar to a local plugin folder. It was necessary, before building
the project, to create a ./lib/ directory containing a soft link to the
ij.jar from the desired ImageJ version, as well as a soft link to junit.jar.
The built result was then copied to ./plugins/ so this needed to be
soft-linked to your ImageJ plugins folder. Typing "ant" to build the
default (all) target and restarting ImageJ or Help->Refresh Menus gave
access to the newly built plugin package.

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
 Cal pattern focus | clean PSF, no "zipper" pattern | 

----------------------------------
2: Pre-processing, Raw Data Checks
----------------------------------

    Check          |        Statistic(s)             |      Comments
------------------ | ------------------------------- | ---------------------
 Angle Diff    |  no colored differences?        |    threshold/stat?
 Intensity Prf |  bleaching and angle intensity  |
 Fourier plots |  SI pattern correct & regular?  |    TODO? k0 & linspc
 Mod Contrast  |  feature MCNR acceptable?       |    Wiener par

-----------------------------
3: Post-reconstruction Checks
-----------------------------

    Check          |        Statistic(s)              |     Comments
------------------ | -------------------------------- | --------------------
 Histogram     |  +ve/-ve ratio acceptable?       | top/bottom 0.01%
 Z variation   |  stdev of miniumum vs. mean      | shows OTF mismatch
 Fourier Plot  |  symmetry+profile OK? +res/angle | +radial profile
 MCNR Map      |  None - for visual inspection    | MCNR + intensity


PROJECT STRUCTURE
=================

- README.md - This file
- LICENSE.txt - Project's license
- CHANGES.txt  - History of versions and changes
- pom.xml - maven Project Object Model describing dependencies, build etc.
- src/main/java/ - Application sources (in SIMcheck/ package)
- src/main/resources/ - Application resources (IJ menu config, html help text)
- src/test/java/ - Test sources
- src/test/resources/ - Test resources
- target/ - output SIMcheck_.jar file
- target/classes/ - build output .class files
- target/test-classes/ - classes produced by tests


Style Notes
===========

* simple, modular structure - each check is a standalone plugin
* plugin exec methods take input images and return ResultSet
  (no GUI calls within exec when easily avoidable)
* no dependencies other than ImageJ1, apart from JUnit for testing
* ImageJ1-like preference for pre- java 5 features (i.e. not many generics)
  and reliance on float primitive type for most calculations


TODO
====

* 1.0: integration/GUI, tests, documentation & write-up up for release
      - !! Make a 0.9.5 release ASAP with rec Fourier & other bugs fixed !!
        - renaming / dialog / output updates:-
          - tweak main dialog text
          - angles & phases sig figs
          - check / util renaming
          - calibration bar on MCNR & modcontrast map
          - rename Illumination Pattern Focus -> SI Pattern Focus
            (& SI Phase Steps)
          - plot titles & axis labels, distinguish plot / image
        - notification when all checks done, and tile windows
        - "Fourier Plots": option for non-mode zero-point (no BG)
        - "Fourier Plots": reslice then blur then apply LUT
        - crop function:
          - improve lay-out in main dialog, add "Zlast" or similar
        - utilties should autoscale (stack FFT, threshold & 16-bit)
        - SI pattern focus: reslice with interp, autoscale, flicker corr?
        - log text update & auto-format to 55 char line length
        - raw Fourier central Z: explain /annotate output

      - documentation: 
        - finish/improve docs, illustrate usage with pictures, examples
        - for ELYRA reconstructed .czi, discard WF and decon-WF
          (processed data have 3 channels: recon, decon pseudoWF, WF)
        - Fourier proj: document power-of-2 and that cropping causes problems
        - see google hit for "maven attach source and javadoc artifacts"
      - fixes:
        - test / finish spherical aberration mismatch check
        - Wiener filter parameter estimate - calibrate, document
        - finish & refactor Cal_Phases: unwrap (+test case), stats and structure
        - recon FT radial profile scale / units
        - get rid of IJ.run calls & show/hide of intermediate results 
        - angle labels etc. should be overlaid, not drawn
        - remove unused intermediate results from Windows list
      - features:
        - summary table of stats & results (pass/uncertain/fail)
        - raw data angle difference (floaty): RMS error? (at least some stat)
        - rec Fourier:-
          - lat: pattern angles (use "SIMcheck.angle1" pref), 3 color profiles
          - axial FFT: project over central slice range, not just 1
          - axial FFT: profile plot?
          - option to not discard negatives before FFT?
        - better name for motion check
        - window positioning: dialog to top left, ...
        - Intensity Histogram: different zero-points for images w/o BG
        - cropping selection after / during main dialog
        - spherical aberration mismatch check: axis always symmetrical about 0?
        - report per. angle modulation contrast and/or minimum of these?
        - Fourier proj stat(s)? spots over angles, 1st vs second, stability?
        - raw -> WF same size as rec by interpolation (& preserve type??)
        - cal check: intensity fluctuations
      - tests, structure:
        - final empirical tests, param calibration, tolerances etc.
        - move crop function code to separate utility
        - tidy up tests:
          - .main() for interactive test, .test() to test private methods?
          - more tests to test/debug non-interactive code, preconditions / inputs
          - unit tests to run without test data (download should build easily)
          - nice, compact test data suite for distribution
        - work out strategy for test data distribution
        - add ResultTable support to ResultSet class

* 1.1: future features
      - convert dialog & logging to non-blocking swing GUI
      - util: merge/shuffle:-
        - tool for merging SIM & widefield data (Julio)
        - re-order channels
      - pre: estimate angles & line-spacing for FFT, pattern focus
      - 3D FFT
      - post: rec FFT automatic resolution estimation??
      - cal: PSF symmetry within tolerance?
      - cal: OTF extent, shape & order separation?
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
