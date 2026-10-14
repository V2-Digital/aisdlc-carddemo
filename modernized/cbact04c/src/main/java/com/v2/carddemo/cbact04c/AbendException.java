package com.v2.carddemo.cbact04c;

/** Mirrors CEE3ABD U0999: the harness reports it as exit code 16. */
final class AbendException extends RuntimeException {
    private final int returnCode;

    AbendException(int returnCode) {
        super("CEE3ABD: ABEND U0999");
        this.returnCode = returnCode;
    }

    int returnCode() {
        return returnCode;
    }
}
