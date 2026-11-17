package com.v2.carddemo.cbact04c;

/** Legacy 9999-ABEND-PROGRAM (CEE3ABD U0999, exit status 16). */
final class Abend extends RuntimeException {
    static final int RC = 16;

    Abend(String message) {
        super(message);
    }
}
