package com.v2.carddemo.cbact04c;

/** Program abend: SYSOUT lines have already been emitted; Main maps this to exit 16. */
final class Abend extends RuntimeException {
    Abend(String message) {
        super(message);
    }
}
