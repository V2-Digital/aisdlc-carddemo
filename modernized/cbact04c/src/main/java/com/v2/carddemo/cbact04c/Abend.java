package com.v2.carddemo.cbact04c;

/** Program abend (PERFORM 9999-ABEND-PROGRAM). The SYSOUT lines were already emitted. */
public final class Abend extends RuntimeException {
    public Abend(String message) {
        super(message);
    }
}
