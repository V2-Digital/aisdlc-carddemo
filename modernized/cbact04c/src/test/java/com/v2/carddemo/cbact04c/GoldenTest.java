package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.Support.read;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
    @TempDir Path tmp;
    final Path root = Support.root();
    final Path ascii = root.resolve("app/data/ASCII");

    private void compare(Support.Result r, Path expected) throws Exception {
        assertEquals(0, r.rc());
        assertEquals(read(expected.resolve("TRANSACT.txt")), read(r.out().resolve("TRANSACT.txt")), "TRANSACT");
        assertEquals(read(expected.resolve("ACCTFILE.txt")), read(r.out().resolve("ACCTFILE.txt")), "ACCTFILE");
        assertEquals(read(expected.resolve("SYSOUT.txt")), r.sysout(), "SYSOUT");
        assertEquals("0", read(expected.resolve("RC.txt")).trim());
    }

    @Test
    void stock() throws Exception {
        Path g = root.resolve("legacy/golden/CBACT04C");
        compare(Support.run(ascii.resolve("tcatbal.txt"), ascii.resolve("cardxref.txt"), ascii.resolve("discgrp.txt"),
                ascii.resolve("acctdata.txt"), tmp), g);
        assertEquals(50, Files.readAllLines(tmp.resolve("TRANSACT.txt")).size());
    }

    @Test
    void afterCbtrn02c() throws Exception {
        Path pre = root.resolve("legacy/golden/CBTRN02C");
        compare(Support.run(pre.resolve("TCATBALF.txt"), ascii.resolve("cardxref.txt"), ascii.resolve("discgrp.txt"),
                pre.resolve("ACCTFILE.txt"), tmp), root.resolve("legacy/golden/CBACT04C-after-CBTRN02C"));
    }

    /** Edge fixtures whose expected outputs were produced by the real COBOL (legacy/tests/cbact04c_edges.sh). */
    @Test
    void edges() throws Exception {
        Path e = root.resolve("legacy/tests/cbact04c_edges");
        Support.Result r = Support.run(e.resolve("TCATBALF.txt"), e.resolve("XREFFILE.txt"), e.resolve("DISCGRP.txt"),
                e.resolve("ACCTFILE.txt"), tmp);
        compare(r, e.resolve("expected"));
    }
}
