package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbendTest {
    private List<String> sysout(Path out) throws IOException {
        return Files.readAllLines(out.resolve("SYSOUT.txt"));
    }

    private Path staged(Path tmp) throws IOException {
        Path in = Files.createDirectory(tmp.resolve("in"));
        Fx.stage(in, false);
        return in;
    }

    private void dropLines(Path f, java.util.function.Predicate<String> drop) throws IOException {
        Files.write(f, Files.readAllLines(f).stream().filter(drop.negate()).collect(Collectors.toList()));
    }

    @Test
    void missingAccount(@TempDir Path tmp) throws IOException {
        Path in = staged(tmp);
        dropLines(in.resolve("ACCTFILE.txt"), l -> l.startsWith("00000000001"));
        assertEquals(16, Fx.run(in, tmp.resolve("out")));
        List<String> s = sysout(tmp.resolve("out"));
        assertEquals(List.of("ACCOUNT NOT FOUND: 00000000001", "ERROR READING ACCOUNT FILE",
                "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM"), s.subList(s.size() - 4, s.size()));
        assertEquals("16", Files.readString(tmp.resolve("out/RC.txt")).trim());
    }

    @Test
    void missingXref(@TempDir Path tmp) throws IOException {
        Path in = staged(tmp);
        dropLines(in.resolve("XREFFILE.txt"), l -> l.length() >= 36 && l.substring(25, 36).equals("00000000001"));
        assertEquals(16, Fx.run(in, tmp.resolve("out")));
        List<String> s = sysout(tmp.resolve("out"));
        assertEquals(List.of("ACCOUNT NOT FOUND: 00000000001", "ERROR READING XREF FILE",
                "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM"), s.subList(s.size() - 4, s.size()));
    }

    @Test
    void missingDefaultDisclosureGroup(@TempDir Path tmp) throws IOException {
        Path in = staged(tmp);
        dropLines(in.resolve("DISCGRP.txt"), l -> l.startsWith("DEFAULT"));
        assertEquals(16, Fx.run(in, tmp.resolve("out")));
        List<String> s = sysout(tmp.resolve("out"));
        assertEquals(List.of("DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE",
                "ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM"),
                s.subList(s.size() - 5, s.size()));
    }
}
