package com.v2.carddemo.cbact04c;

import java.util.List;

/** Program abend (CEE3ABD U0999). Carries the SYSOUT lines that precede ABENDING PROGRAM. */
public class Abend extends RuntimeException {
    private final List<String> sysout;

    public Abend(String... sysout) {
        super(sysout.length > 0 ? sysout[0] : "abend");
        this.sysout = List.of(sysout);
    }

    public List<String> sysout() {
        return sysout;
    }
}
