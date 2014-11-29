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
* no dependencies other than ImageJ1, apart from JUnit for testing
* ImageJ1-like preference for pre- java 5 features (i.e. not many generics)
  and reliance on float primitive type for most calculations


TODO
====

* 1.0: integration/GUI, tests, documentation & write-up up for release

      - priority for submission
        - MIV RMSE stat: statOK=NA and pre-normalise over angles
        - normalise raw fourier before projection
        - MCM/MCN: note that green is saturated in log,
          & ideally add to overlay where saturated pixels present
        - MCM: show saturated if *any* angle saturated
        - CIP components & per-angle stdev/mean for flicker
        - proper SAMismatch stat value check
        - improve feature means stat (MCN) -- stack hist? pseudo-wf?
        - correct flicker for illumination pattern check

      - documentation: 
        - finish/improve docs, illustrate usage with pictures, examples
        - document examples of running checks from a macro
        - for ELYRA reconstructed .czi, discard WF and decon-WF?
          (processed data have 3 channels: recon, decon pseudoWF, WF)
        - citable code:
              https://github.com/blog/1840-improving-github-for-science

      - fixes:
        - MCNR: auto-threshold pseudo-widefield, report per. angle MCNR
        - check MCN noise estimate
        - fix radial profile plot scaling
        - handle MCM w/o MCN ticked elegantly in main dialog
        - turn CIP into plot (to be able to save raw data) and/or normalize
        - FTL/FTO no intensity cutoff option
        - move spherical aberration mismatch back into reconstructed data checks??
          (needs a bead lawn)
        - better names for max/min ratio & SAM check
        - min-max range stat name: signal-to-artefact ratio??
        - make sure all parameters chosen are logged
        - run multi-frame -- fix / document; all stats reported for current time-point only?
        - ortho rec FFT: option for full stack (for now, until 3D FFT)
        - make sure TLAs / filenames are in the log (Justin)

        - standalone MCM -- report av mod contrast
        - turn FTR profile into multi-color and/or plot
        - rename build output to include underscore!
        - debug issues with crop utility & move to separate utility plugin
        - improve "Fourier Transform Phases" info / log output
        - Rec MCM: saturated if *any* of 15 input pixels are saturated
        - recon FT radial profile scale / units
        - channel order: RGB vs. BGR
        - test / finish spherical aberration mismatch check
        - finish & refactor Cal_Phases: unwrap (+test case), stats and structure
        - get rid of IJ.run calls & show/hide of intermediate results 
        - angle labels etc. should be overlaid, not drawn
        - remove unused intermediate results from Windows list

      - features:
        - display / warn about saturated pixels in raw data MCN check
        - report per. angle modulation contrast and/or minimum of these?
        - SI pattern focus flicker corr?
        - raw -> WF same size as rec by interpolation (& preserve type??)
        - re-introduce individual stats from CIV intensity fuctuation?:
          - frame-to-frame flicker
          - phase-to-phase variability?
          - angle-to-angle variability?
        - stat to detect motion? ("peak" angle difference?)
        - rec Fourier:-
          - lat: pattern angles (use "SIMcheck.angle1" pref), 3 color profiles
          - axial FFT: project over central slice range, not just 1
          - axial FFT: profile plot?
        - "target plot" on raw fourier projection?
        - Fourier proj stat(s)? spots over angles, 1st vs second, stability?
        - spherical aberration mismatch check: axis always symmetrical about 0?
        - window positioning: dialog to top left, ...

      - tests, structure:
        - final empirical tests, param calibration, tolerances etc.
        - test data:
          - compact test / example data suite for distribution
          - work out strategy for test data distribution
        - tidy up tests:
          - .main() for interactive test, .test() to unit-test private methods
          - unit tests to run without test data (download should build easily)
          - more tests to test/debug non-interactive code, preconditions (inputs)

* 1.1: future features
      - convert dialog & logging to swing GUI
      - rec: FFT automatic resolution estimation??
      - 3D FFT
      - estimate phase drift & correct in MCNR calc
      - raw: estimate angles & line-spacing for FFT, pattern focus?
      - cal: PSF symmetry within tolerance?
      - cal: OTF extent, shape & order separation?
      - util: merge/shuffle:-
        - tool for merging SIM & widefield data (Julio)
        - re-order channels
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
