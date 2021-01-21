// Copyright (c) 2021, Lothar Schermelleh and Micron Oxford,
// University of Oxford, Department of Biochemistry.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/ .

function MCF (THR, MCN, min_thresh) {
  setBatchMode(true);
  selectWindow(MCN);
  run("Gaussian Blur...", "sigma="+sigma+" stack");
  Stack.getDimensions(width, height, channels, slices, frames);
  run("Scale...",
      "x=2 y=2 z=1.0"
      + " width=" + width
      + " height=" + height
      + " depth=" + slices
      + " interpolation=Bicubic"
      + " average"
      + " process"
      + " create"
      + " title="+MCN+"Scale");
  selectWindow(MCN+"Scale");
  getMinAndMax(min,max);
  for (i=1; i<=nSlices; i++) {
    setSlice(i);
    getMinAndMax(min, max);
    setThreshold(min_thresh, max);
    run("Create Mask");
    run("Copy");
    selectWindow(MCN+"Scale");
    run("Paste");
    setSlice(1);
    resetThreshold;
  }
  run("32-bit");
  run("Divide...", "value=255 stack");
  rename("Mask "+THR);
  imageCalculator("Multiply create stack", "Mask "+THR, THR);
  selectWindow("Result of Mask "+THR);
  rename(THR+"_MCF");
  run("Gaussian Blur 3D...",
      "x="+sigma_xy
      + " y="+sigma_xy
      + " z="+sigma_z
      + " stack");
  run("Threshold and 16-bit Conversion", "  channel=0 channel_0=0");
  if (is("composite")) {
    Stack.setDisplayMode("composite");
  }
  setBatchMode("show");
}

if (nImages() == 0) {
  Dialog.create("No open images");
  Dialog.addMessage("Open the 16-bit Thresholded and MCNR images first");
  Dialog.show();
} else {
  //Get list of open images for the dialog menu
  list = newArray(nImages());
  for (i=1; i<=nImages(); i++) {
    selectImage(i);
    list[i-1] = getTitle;
  }

  //Create dialog menu of open images
  Dialog.create("Select raw and reconstructed data");
  Dialog.addChoice("Thresholded 16-bit reconstruction data", list);
  Dialog.addChoice("MCNR image for mask", list);
  Dialog.addNumber("Pre MCN Gaussian blur sigma xy = ", 1.0);
  Dialog.addNumber("Post 3D Gaussian blur sigma xy = ", 1.0);
  Dialog.addNumber("Post 3D Gaussian blur sigma z = ", 1.0);
  Dialog.addNumber("MCN Threshold value = ", 4.0);
  Dialog.show();

  th = Dialog.getChoice();
  MC = Dialog.getChoice();
  sigma = Dialog.getNumber();
  sigma_xy = Dialog.getNumber();
  sigma_z = Dialog.getNumber();
  min_thresh = Dialog.getNumber();

  thresh = File.getNameWithoutExtension(th);
  selectWindow(th);
  rename(thresh);

  MCNR = File.getNameWithoutExtension(MC);
  selectWindow(MC);
  rename(MCNR);

  print("MCN threshold value: " + min_thresh);
  MCF(thresh, MCNR, min_thresh);
}
