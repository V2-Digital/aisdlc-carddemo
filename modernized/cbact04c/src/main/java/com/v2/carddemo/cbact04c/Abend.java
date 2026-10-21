package com.v2.carddemo.cbact04c;

/** Program abend U0999 (CEE3ABD), after the legacy DISPLAY lines have been written. */
final class Abend extends RuntimeException {
    Abend() {
        super("ABEND U0999", null, false, false);
    }
}
