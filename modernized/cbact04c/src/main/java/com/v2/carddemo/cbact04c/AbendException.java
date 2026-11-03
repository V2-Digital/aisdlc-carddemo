package com.v2.carddemo.cbact04c;

/** Program abend (CEE3ABD U0999); the message lines are already in SYSOUT. */
public final class AbendException extends RuntimeException {
    public AbendException() {
        super("ABEND U0999");
    }
}
