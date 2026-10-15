package com.v2.carddemo.cbact04c;

/** The legacy program's 9999-ABEND-PROGRAM (U0999). The DISPLAY lines have already been emitted. */
public final class AbendException extends RuntimeException {
    public AbendException(String message) {
        super(message);
    }
}
