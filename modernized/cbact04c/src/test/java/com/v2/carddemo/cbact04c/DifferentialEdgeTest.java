package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.TestSupport.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Edge dataset (negative balance, group hit, miss->DEFAULT, zero rate, last account) vs outputs captured from COBOL. */
class DifferentialEdgeTest {
    static final Path EDGE = Path.of(System.getProperty("user.dir"), "src/test/resources/edge");

    @Test
    void edgeMatchesCobol(@TempDir Path dir) throws IOException {
        Path in = EDGE.resolve("in"), ex = EDGE.resolve("expected");
        Path acct = copy(in.resolve("ACCTFILE.txt"), dir, "ACCTFILE.txt");
        Result r = TestSupport.run(dir, "2022071800", CLOCK, in.resolve("TCATBALF.txt"), in.resolve("XREFFILE.txt"),
                in.resolve("DISCGRP.txt"), acct);
        assertBytes(ex.resolve("TRANSACT.txt"), dir.resolve("TRANSACT.txt"), "TRANSACT");
        assertBytes(ex.resolve("ACCTFILE.txt"), dir.resolve("ACCTFILE.txt"), "ACCTFILE");
        assertBytes(Files.readAllBytes(ex.resolve("SYSOUT.txt")), r.sysout().getBytes(StandardCharsets.ISO_8859_1), "SYSOUT");
        org.junit.jupiter.api.Assertions.assertEquals(0, r.rc());
    }
}
