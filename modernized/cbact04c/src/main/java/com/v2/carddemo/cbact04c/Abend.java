package com.v2.carddemo.cbact04c;

/** Program abend (CEE3ABD 999). SYSOUT lines have already been displayed. */
final class Abend extends RuntimeException {
    Abend() {
        super("CEE3ABD: ABEND U0999 TIMING=0", null, false, false);
    }
}
