package com.v2.carddemo.cbact04c;

/** CEE3ABD U0999: the program has already printed its messages; the job ends with RC 16. */
public final class AbendException extends RuntimeException {
    public static final int RC = 16;

    public AbendException() {
        super("ABEND U0999");
    }
}
