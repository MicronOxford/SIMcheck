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

Copyright Graeme Ball and Lothar Schermelleh, Micron Oxford, Department of
Biochemistry, University of Oxford. License GPL unless stated otherwise in
a given file.


Features
========

-----------------------
0: SIMcheck main dialog
-----------------------

- dialog to choose and set up all checks with standard parameters
- results: images will appear and key statistics will be logged
- help button: link to instructions, help, documentation

----------------------------------
1: Pre-processing, Raw Data Checks
----------------------------------

    Check            |        Statistic(s)                 |      Comments   
-------------------- | ----------------------------------- | ------------------
 Intensity Profiles  | bleaching, flicker, angle intensity | TODO: flicker     
 Motion / Illum Var  | angle difference (motion, illum.)   | TODO: correlation 
 Fourier Projections | None: check pattern / spots OK      | TODO? k0 & linspc 
 Mod Contrast        | feature MCNR acceptable?            | Wiener estimate   

-----------------------------
2: Post-reconstruction Checks
-----------------------------

    Check            |        Statistic(s)                 |      Comments
-------------------- | ----------------------------------- | ------------------
 Intensity Histogram | +ve/-ve ratio acceptable?           | top/bottom 0.01%  
 Fourier Plots       | None: symmetry+profile OK?          | TODO: resolution  
 Mod Contrast Map    | None: inspect MCNR of features      | MCNR & intensity  

---------------------
3: Calibration Checks
---------------------

    Check            |        Statistic(s)                 |      Comments
-------------------- | ----------------------------------- | ------------------
 Illum. Phase Steps  | phase step & range stable?          | +k0, linespacing  
 Pattern focus       | None: check no "zipper" pattern     |                   
 SA Mismatch         | stdDev of miniumum vs. mean         | shows OTF mismatch

4: Utilities
------------

- Format Converter for SIM data
- Raw SI Data to Pseudo-Widefield conversion
- Threshold and 16-bit conversion (i.e. "discard neagtives")
- Stack FFT (2D)


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
* no run-time dependencies other than ImageJ1
* ImageJ1-like preference for pre- java 5 features (i.e. not many generics)
  and reliance on float primitive type for most calculations


TODO
====

* 0.9.7: final pre-submission features & fixes

      - use the term "slice" for the raw data and "z-section" for the
        reconstructed data
      - for the raw FFT projection: apply stack FFT with win func,
        bleach correction "simple ratio", subtract 50, max-intensity
        project, auto-contrast mode-max
      - correct flicker for illumination pattern check
      - pseudo-widefield: simple ratio bleach correction, project & perserve
        16-bit, bicubic interpolation
      - check CIP bleaching stat calculation
        (SIMcheck->0_good_3colormultinuclearstaining->DAPI_PNCA_514_15)
      - proper SAMismatch stat value check

      - sort out stats rounding to zero
      - FTO, project some slices, not just single central slice
      - improve feature means stat (MCN) -- stack hist? pseudo-wf?
      - z min variation, use >=10 pixels to estimate minimum
      - MCM: add note to overlay where saturated pixels present?
      - MCN, show saturated pixels in raw data?

* 0.9.8: post-submission refactoring & documentation updates

      - documentation: 
        - finish/improve docs, illustrate usage with pictures, examples
        - document examples of running checks from a macro
        - for ELYRA reconstructed .czi, discard WF and decon-WF?
          (processed data have 3 channels: recon, decon pseudoWF, WF)
        - citable code:
              https://github.com/blog/1840-improving-github-for-science

      - tests, structure:
        - test data:
          - compact test / example data suite for distribution
          - work out strategy for test data distribution
        - test crop utility & move to separate utility plugin
        - tidy up tests:
          - .main() for interactive test, .test() to unit-test private methods
          - unit tests to run without test data (download should build easily)
          - more tests to test/debug non-interactive code, preconditions (inputs)

* 0.9.9: additional features & updates suggested by referees

      - ???

* 1.0: final updates & documentation for release

      - fixes:
        - MCNR: auto-threshold pseudo-widefield, report per. angle MCNR
        - check MCN noise estimate
        - fix radial profile plot scaling
        - fix / re-introduce working MIV stats -- RMSE? peak RMSE?
        - turn CIP into plot (to be able to save raw data) and/or normalize
        - FTL/FTO no intensity cutoff option
        - better names for max/min ratio & SAM check
        - make sure all parameters chosen are logged
        - run multi-frame -- fix / document; all stats reported for current time-point only?
        - ortho rec FFT: option for full stack (for now, until 3D FFT)
        - make sure TLAs / filenames are in the log (Justin)

        - standalone MCM -- report av mod contrast
        - turn FTR profile into multi-color and/or plot
        - rename build output to include underscore!
        - improve "Fourier Transform Phases" info / log output
        - recon FT radial profile scale / units
        - spherical aberration mismatch check: axis always symmetrical about 0?
        - test & refactor Cal_Phases: unwrap (+test case), stats and structure
        - channel order: RGB vs. BGR
        - get rid of IJ.run calls & show/hide of intermediate results 
        - angle labels etc. should be overlaid, not drawn
        - remove unused intermediate results from Windows list

      - features:
        - display / warn about saturated pixels in raw data MCN check
        - report per. angle modulation contrast and/or minimum of these?
        - stats to detect motion & illumination variation?
        - rec Fourier:-
          - lat: pattern angles (use "SIMcheck.angle1" pref), 3 color profiles
        - window positioning: dialog to top left, ...

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
