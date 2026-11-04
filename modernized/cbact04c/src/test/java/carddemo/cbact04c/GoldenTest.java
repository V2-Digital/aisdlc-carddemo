package carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** AC2-4: byte-identical to the COBOL golden outputs (TRANSACT, ACCTFILE, SYSOUT, RC). */
class GoldenTest {
    @TempDir Path tmp;

    private static final Path ASCII = Fx.ROOT.resolve("legacy/../app/data/ASCII").normalize();

    private void check(Path in, String scenario, int records) throws IOException {
        Path gold = Fx.ROOT.resolve("legacy/golden").resolve(scenario);
        Fx.Result r = Fx.run(in, tmp.resolve("out"));
        int expectedRc = Integer.parseInt(Files.readString(gold.resolve("RC.txt")).trim());
        assertEquals(expectedRc, r.rc());
        for (String f : new String[] {"TRANSACT.txt", "ACCTFILE.txt"}) {
            assertArrayEquals(Files.readAllBytes(gold.resolve(f)), Files.readAllBytes(r.out().resolve(f)), f);
        }
        assertEquals(records, Fx.lines(r.out().resolve("TRANSACT.txt")).size());
        assertEquals(50, Fx.lines(r.out().resolve("ACCTFILE.txt")).size());
        assertEquals(Files.readString(gold.resolve("SYSOUT.txt"), StandardCharsets.ISO_8859_1), r.stdout(), "SYSOUT");
        assertEquals("", r.stderr());
    }

    @Test
    void stock() throws IOException {
        Path in = tmp.resolve("in");
        Files.createDirectories(in);
        Files.copy(ASCII.resolve("tcatbal.txt"), in.resolve("TCATBALF.txt"));
        Files.copy(ASCII.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(ASCII.resolve("acctdata.txt"), in.resolve("ACCTFILE.txt"));
        Files.copy(ASCII.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
        check(in, "CBACT04C", 50);
    }

    @Test
    void afterCbtrn02c() throws IOException {
        Path in = tmp.resolve("in");
        Files.createDirectories(in);
        Path c = Fx.ROOT.resolve("legacy/golden/CBTRN02C");
        Files.copy(c.resolve("TCATBALF.txt"), in.resolve("TCATBALF.txt"));
        Files.copy(c.resolve("ACCTFILE.txt"), in.resolve("ACCTFILE.txt"));
        Files.copy(ASCII.resolve("cardxref.txt"), in.resolve("XREFFILE.txt"));
        Files.copy(ASCII.resolve("discgrp.txt"), in.resolve("DISCGRP.txt"));
        check(in, "CBACT04C-after-CBTRN02C", 50);
    }
}
