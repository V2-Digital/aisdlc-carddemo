package com.v2.carddemo.cbact04c;

/** Equivalent of CEE3ABD with code 999; the harness maps it to exit code 16. */
public class AbendException extends RuntimeException {
    public static final int EXIT_CODE = 16;

    public AbendException(String message) {
        super(message);
    }
}
