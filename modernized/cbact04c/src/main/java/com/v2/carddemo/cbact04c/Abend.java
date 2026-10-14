package com.v2.carddemo.cbact04c;

/** The program's 9999-ABEND-PROGRAM; the messages are already on SYSOUT when thrown. */
public final class Abend extends RuntimeException {
    public Abend(String message) {
        super(message);
    }
}
