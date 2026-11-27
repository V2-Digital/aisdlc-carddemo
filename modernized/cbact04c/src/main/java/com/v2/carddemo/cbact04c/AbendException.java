package com.v2.carddemo.cbact04c;

/** 9999-ABEND-PROGRAM: CEE3ABD stub in the harness exits with 16. */
public final class AbendException extends RuntimeException {
    public static final int EXIT_CODE = 16;

    public AbendException() {
        super("ABENDING PROGRAM");
    }
}
