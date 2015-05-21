========
SIMcheck
========

SIMcheck is a package of ImageJ tools for assessing the quality and
reliability of Structured Illumination Microscopy (SIM) data.

* More information can be found on the 
[Micron Oxford Website](http://www.micron.ox.ac.uk/software/SIMCheck.php)
* **The .jar for the latest release can be downloaded from
[here](http://www.micron.ox.ac.uk/software/SIMcheck_-0.9.8.jar)**
* Further help is available
[here](http://www.micron.ox.ac.uk/microngroup/software/SIMcheck.html)

The project uses the maven build and dependency management tool, so to
build run the following command (.jar file appears in ./target/):-

    mvn package

There is currently no Fiji update site, but we plan to create one for
versions 1.0 and above.

Copyright Graeme Ball and Lothar Schermelleh, Micron Oxford, Department of
Biochemistry, University of Oxford. License GPL unless stated otherwise in
a given file (in particular, SIMcheck uses modified versions of ImageJ's
Slicer plugin and Paul Baggethun's Radial Profile Plot plugin).


Features
========

-----------------------
0: SIMcheck main dialog
-----------------------

- dialog to choose and set up all checks with standard parameters
- cropping utility for raw and reconstructed data
- results: images will appear and key statistics will be logged
- help button: link to instructions, help, documentation

----------------------------------
1: Pre-processing, Raw Data Checks
----------------------------------

    Check            |        Statistic(s)                 |      Comments   
-------------------- | ----------------------------------- | ------------------
 Intensity Profiles  | bleaching, flicker, angle intensity | 
 Motion / Illum Var  | angle difference (motion, illum.)   | 
 Fourier Projections | None: check pattern / spots OK      | 
 Modulation Contrast | feature MCNR acceptable?            | Wiener estimate   

-----------------------------
2: Post-reconstruction Checks
-----------------------------

    Check            |        Statistic(s)                 |      Comments
-------------------- | ----------------------------------- | ------------------
 Intensity Histogram | +ve/-ve ratio acceptable?           | top/bottom 0.01%  
 SA Mismatch         | stdDev of miniumum vs. mean         | shows OTF mismatch
 Fourier Plots       | None: symmetry+profile OK?          | 
 Mod Contrast Map    | None: inspect MCNR of features      | green=saturated

---------------------
3: Calibration Checks
---------------------

    Check            |        Statistic(s)                 |      Comments
-------------------- | ----------------------------------- | ------------------
 Illum. Phase Steps  | phase step & range stable?          | +k0, linespacing  
 Pattern Focus       | None: check for "zipper" pattern    |                   

4: Utilities
------------

- Format Converter for SIM data
- Raw SIM Data to Pseudo-Widefield conversion
- Threshold and 16-bit conversion (i.e. "discard negatives")
- Stack FFT, performs 2D FFT on each slice


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

* no run-time dependencies other than ImageJ1
* simple, modular structure - each check is a standalone plugin
* plugin exec methods take input images and return ResultSet
  (no GUI calls within exec when easily avoidable)
* ImageJ1-like preference for pre- java 5 features and reliance on float
  primitive type for most calculations


TODO
====

* 0.9.9: post-submission refactoring

      - documentation:
        - javadoc updates

      - tests, structure:
        - make sure all parameters chosen are logged
        - test data:
          - compact test / example data suite for distribution
          - work out strategy for test data distribution
        - refactor / test Cal_Phases:
          - unwrap (+test case)
          - stats and structure
          - choose direction of rotation
      
* 1.0: final updates & documentation for 1.0 release with publication

      - documentation:
        - SIMcheck manual revision & additions
        - citable code:
              https://github.com/blog/1840-improving-github-for-science

      - updates:
        - make compatible with running from a macro and document batch run
        - spherical aberration mismatch check: axis always symmetrical about 0?
        - thresh / 16-bit: explain steps in log file (& per. channel thresh)

* 1.1: post-release updates, bugfixes & suggestions from feedback

      - features:
        - replace 2D FFTs with 3D FFTs (at least in Fiji)
        - display / warn about saturated pixels in raw data MCN check
        - stat for motion & illumination variation
        - turn FTR profile into multi-color and/or plot

      - updates:
        - si2wf: add option without 2x size scaling
        - si2wf: option to select only one angle
        - MCN: auto-threshold using pseudo-widefield, report per. angle MCNR
        - SIR checks: exclude 0s from mode finding
        - improve "Fourier Transform Phases" info / log output
        - turn CIP into plot (to be able to save raw data) and/or normalize
        - angle labels etc. should be overlaid, not drawn
        - channel order: RGB vs. BGR
        - for ELYRA reconstructed .czi, discard WF and decon-WF?
        - progress bar for FPJ plugin (& others?)
        - for Rec data, auto-scale if data has >16-bit values
        - tidy raw FPJ & add target overlay & hide by default
        - try to find a more robust bleach estimation procedure (CIP)
        - MCM: add note to overlay where saturated pixels present?
        - MCN, show saturated pixels in raw data?
        - remove unused intermediate results from Windows list
        - Rec Fourier: RadioButtons for mutually exclusive cut-off options?

      - tests, structure:
        - multi-frame: test / document stats for current time-point only
        - more crop utility tests, move to separate utility plugin
        - get rid of IJ.run calls & show/hide of intermediate results 

      - documentation:

* 1.2: additional numerical stats, including resolution estimate

* 1.3: PSF and OTF symmetry, extent, shape & order separation

* 2.0: integrated swing GUI control


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
