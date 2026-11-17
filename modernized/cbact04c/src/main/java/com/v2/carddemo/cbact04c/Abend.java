package com.v2.carddemo.cbact04c;

/** Program abend (CEE3ABD 999). The SYSOUT lines have already been emitted. */
public final class Abend extends RuntimeException {
    public Abend() {
        super("CEE3ABD: ABEND U0999");
    }
}
