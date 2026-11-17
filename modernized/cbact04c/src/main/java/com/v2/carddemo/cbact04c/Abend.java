package com.v2.carddemo.cbact04c;

import java.util.List;

/** COBOL 9999-ABEND-PROGRAM: carries the DISPLAY lines printed just before the abend. */
public final class Abend extends RuntimeException {
    private final List<String> lines;

    public Abend(String... lines) {
        super(lines.length == 0 ? "abend" : lines[0]);
        this.lines = List.of(lines);
    }

    public List<String> lines() {
        return lines;
    }
}
