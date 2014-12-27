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

* 0.9.7: final pre-submission features & fixes

      - log output formatting
      - update docs before release (javadoc, README cross-ref manual)
      - bump version number, update manual ref


* 0.9.8: post-submission refactoring & documentation updates

      - documentation: 
        - finish/improve docs, illustrate usage with pictures, examples
        - document examples of running checks from a macro
        - for ELYRA reconstructed .czi, discard WF and decon-WF?
          (processed data have 3 channels: recon, decon pseudoWF, WF)
        - citable code:
              https://github.com/blog/1840-improving-github-for-science

      - tests, structure:
        - ensure MCN noise estimate includes Poisson
        - test data:
          - compact test / example data suite for distribution
          - work out strategy for test data distribution
        - test crop utility & move to separate utility plugin
        - tidy up tests:
          - .main() for interactive test, .test() to unit-test private methods
          - unit tests to run without test data (download should build easily)
          - more tests to test/debug non-interactive code, preconditions (inputs)
        - rename build output to include underscore!
        - make sure all parameters chosen are logged
        - Rec Fourier: RadioButtons for mutually exclusive cut-off options
        - run multi-frame -- fix / document; all stats reported for current time-point only?
        - get rid of IJ.run calls & show/hide of intermediate results 

      - fixes:
        - for Rec data, auto-scale if data has >16-bit values
        - tidy raw FPJ & add target overlay & hide by default
        - try to find a more robust bleach estimation procedure (CIP)
        - proper SAMismatch stat value check
        - z min variation, ensure >=10 pixels to estimate minimum
        - MCM: add note to overlay where saturated pixels present?
        - MCN, show saturated pixels in raw data?
        - FTO, project some slices, not just single central slice
        - turn CIP into plot (to be able to save raw data) and/or normalize
        - better names for max/min ratio & SAM check

* 0.9.9: additional features & updates suggested by referees

      - ???

* 1.0: final updates & documentation for release

      - fixes:
        - improve "Fourier Transform Phases" info / log output
        - spherical aberration mismatch check: axis always symmetrical about 0?
        - test & refactor Cal_Phases: unwrap (+test case), stats and structure
        - channel order: RGB vs. BGR
        - angle labels etc. should be overlaid, not drawn
        - remove unused intermediate results from Windows list

      - features:
        - turn FTR profile into multi-color and/or plot
        - display / warn about saturated pixels in raw data MCN check
        - MCN: auto-threshold pseudo-widefield, report per. angle MCNR
        - report per. angle modulation contrast and/or minimum of these?
        - stats to detect motion & illumination variation?
        - rec Fourier:-
          - lat: pattern angles (use "SIMcheck.angle1" pref), 3 color profiles

* 1.1: integrated swing GUI control

* 1.2: 3D FFT and estimation of phase drift, angles and line-spacing

* 1.3: additional numerical stats, including resolution estimate

* 1.4: PSF and OTF symmetry, extent, shape & order separation


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
