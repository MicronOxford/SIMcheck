#!/usr/bin/env python

# dump an ImageJ binary LUT file as comma-separated
# LUT values

import sys
import struct

if len(sys.argv) != 2:
    print "Usage: dump_lut.py some_lut.lut"

with open(sys.argv[1], "rb") as f:
    sys.stdout.write("# RED\n")
    byte = f.read(1)
    nbytes = 0
    while byte != b"":
        # Do stuff with byte.
        sys.stdout.write(str(struct.unpack('<B', byte)[0]))
        nbytes = nbytes + 1
        if nbytes == 256:
            sys.stdout.write("\n# GREEN\n")
        elif nbytes == 512:
            sys.stdout.write("\n# BLUE\n")
        elif nbytes == 768:
            pass
        else:
            sys.stdout.write(", ")
        byte = f.read(1)
