package com.v2.carddemo.cbact04c;

/** Mirrors CEE3ABD U0999; the harness maps it to exit 16. */
public class AbendException extends RuntimeException {
    public AbendException(String message) {
        super(message);
    }
}
