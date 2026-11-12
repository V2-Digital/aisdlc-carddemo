package com.v2.carddemo.cbact04c;

/** Fatal condition; the legacy program calls CEE3ABD (U0999), the harness maps it to RC 16. */
final class Abend extends RuntimeException {
    Abend(String message) {
        super(message);
    }
}
