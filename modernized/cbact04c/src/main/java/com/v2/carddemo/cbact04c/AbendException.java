package com.v2.carddemo.cbact04c;

/** Mirrors 9999-ABEND-PROGRAM (CEE3ABD U999, harness exit 16). */
public class AbendException extends RuntimeException {
    public static final int EXIT_CODE = 16;

    public AbendException(String message) {
        super(message);
    }
}
