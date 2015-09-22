SIMcheck
========

SIMcheck is a package of ImageJ tools for assessing the quality and
reliability of Structured Illumination Microscopy (SIM) data.

* More information can be found on the 
[Micron Oxford Website](http://www.micron.ox.ac.uk/software/SIMCheck.php)
* Further help is available
[here](http://www.micron.ox.ac.uk/microngroup/software/SIMcheck.html)

Copyright Graeme Ball and Lothar Schermelleh, Micron Oxford, Department of
Biochemistry, University of Oxford. License GPL unless stated otherwise in
a given file (in particular, SIMcheck uses modified versions of ImageJ's
Slicer plugin and Paul Baggethun's Radial Profile Plot plugin).


Installation
============

ImageJ updater
--------------

The simplest way to install SIMcheck and keep it up to date on your system is
using the ImageJ updater.  SIMcheck is available via an ImageJ update site so
you only need to activate it from the list of sites.  See
["How to follow an update site"](http://fiji.sc/How_to_follow_a_3rd_party_update_site)
for more details

Manual install
--------------

The jar file for the latest release can also be
[downloaded manually](http://downloads.micron.ox.ac.uk/fiji_update/SIMcheck/plugins/)
for cases where the IMageJ updater is not available.  Older versions of
SIMcheck can be obtained by navigating the
[SIMcheck update site](http://downloads.micron.ox.ac.uk/fiji_update/SIMcheck/).

Building from source
--------------------

The project uses the maven build and dependency management tool, so to
build it, run the following command (.jar file appears in `target/`):

    mvn package

In addition, SIMcheck is also released via Sonatype to ease its use by
other projects:

    <dependency>
      <groupId>uk.ac.ox.micron</groupId>
      <artifactId>SIMcheck</artifactId>
      <version></version>
    </dependency>


Features
========

0: SIMcheck main dialog
-----------------------

- dialog to choose and set up all checks with standard parameters
- cropping utility for raw and reconstructed data
- results: images will appear and key statistics will be logged
- help button: link to instructions, help, documentation

1: Pre-processing, Raw Data Checks
----------------------------------

    Check            |        Statistic(s)                 |      Comments
-------------------- | ----------------------------------- | ------------------
 Intensity Profiles  | bleaching, flicker, angle intensity |
 Motion / Illum Var  | None: angle differences colored     |
 Fourier Projections | None: check pattern / spots OK      |
 Modulation Contrast | feature MCNR acceptable?            | Wiener estimate

2: Post-reconstruction Checks
-----------------------------

    Check            |        Statistic(s)                 |      Comments
-------------------- | ----------------------------------- | ------------------
 Intensity Histogram | +ve/-ve ratio acceptable?           | top/bottom 0.01%
 SA Mismatch         | stdDev of miniumum vs. mean         | shows OTF mismatch
 Fourier Plots       | None: symmetry+profile OK?          |
 Mod Contrast Map    | None: inspect MCNR of features      | green=saturated

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

* 1.1: post-release updates, bugfixes & suggestions from feedback

      - features:
        - replace 2D FFTs with 3D FFTs (at least in Fiji)
        - display / warn about saturated pixels in raw data MCN check
        - stat for motion & illumination variation
        - turn FTR profile into multi-color and/or plot

      - updates:
        - si2wf: add options for no 2x size scaling, select 1 angle
        - MCN: auto-threshold using pseudo-widefield, report per. angle MCNR
        - Rec Fourier: rename ortho to axial?
        - SIR checks: exclude 0s from mode finding
        - improve "Fourier Transform Phases" info / log output
        - angle labels etc. should be overlaid, not drawn
        - CIP channel order: RGB vs. BGR
        - for ELYRA reconstructed .czi, discard WF and decon-WF?
        - progress bar for FPJ plugin (& others?)
        - raw FPJ: add target overlay? (hide by default)
        - MCN, show saturated pixels in raw data? (add to LUT? also MCM?)
        - Rec Fourier: reduce options / RadioButtons for mutually exclusive options?

      - tests, structure:
        - expand sample / test data set
        - optional tests using failsafe plugin? (check data & pass/fail)
        - get rid of IJ.run calls & intermediate results from window list
        - multi-frame: test / document stats for current time-point only
        - more crop utility tests, move to separate utility plugin

      - documentation:
        - macro examples / document batch running

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
