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
 Raw Angle Diff    |  no colored differences?        |    threshold/stat?
 Raw Intensity Prf |  bleaching and angle intensity  |
 Raw Fourier plots |  SI pattern correct & regular?  |    TODO? k0 & linspc
 Raw Mod Contrast  |  feature MCNR acceptable?       |    Wiener par

-----------------------------
3: Post-reconstruction Checks
-----------------------------

    Check          |        Statistic(s)              |     Comments
------------------ | -------------------------------- | --------------------
 SIR Histogram     |  +ve/-ve ratio acceptable?       | top/bottom 0.01%
 SIR Z variation   |  stdev of miniumum vs. mean      | shows OTF mismatch
 SIR Fourier Plot  |  symmetry+profile OK? +res/angle | +radial profile
 SIR MCNR Map      |  None - for visual inspection    | MCNR + intensity


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
- docs/ - documentation (output from javadoc)


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
      - !! Make a 0.95 release ASAP with SIR Fourier fixed !!
        - fix MCM >max / green color bug, ensure camMax pref is saved/reloaded
        - decide on raw Fourier output format (currently montage in standalone)
      - documentation: 
        - make more self-documenting: improve names & log output
        - finish/improve docs, illustrate usage with pictures, examples
      - fixes:
        - N-SIM data: report raw 3D SIM data Bio-Formats bug
        - finish & refactor Cal_Phases: unwrap (+test case), stats and structure
        - Wiener filter parameter estimate - calibrate, document
      - features:
        - raw data angle difference (floaty): RMS error? (at least some stat)
        - SIR Fourier pattern angles (use "SIMcheck.angle1" pref), profiles
        - report per. angle modulation contrast and/or minimum of these?
        - project and/or montage Raw FFT to present as 1 image
          - OR, remove raw FFT from raw checks / just for calibration?
      - tests, structure:
        - final empirical tests, param calibration, tolerances etc.
        - tidy up tests:
          - .main() for interactive test, .test() to test private methods?
          - more tests to test/debug non-interactive code, preconditions / inputs
          - unit tests to run without test data (repo needs to build)
          - nice, compact test data suite for distribution
        - add ResultTable support to ResultSet class
        - make sure tests and debug not deployed
        - work out strategy for test data distribution
        - set up Fiji update site

* 1.1: future features
      - convert dialog & logging to non-blocking swing GUI
      - util: merge/shuffle:-
        - tool for merging SIM & widefield data (Julio)
        - raw -> WF same size as SIR by interpolation (& preserve type??)
        - re-order channels
      - pre: estimate angles & line-spacing for FFT, pattern focus
      - 3D FFT
      - post: SIR FFT automatic resolution estimation??
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
