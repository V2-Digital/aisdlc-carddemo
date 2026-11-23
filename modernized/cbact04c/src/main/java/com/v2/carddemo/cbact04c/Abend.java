package com.v2.carddemo.cbact04c;

/** The legacy program's CEE3ABD U999 abend; the harness maps it to RC 16. */
public final class Abend extends RuntimeException {
    public static final int RC = 16;

    public Abend(String message) {
        super(message);
    }
}
