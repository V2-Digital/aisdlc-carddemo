package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins legacy behaviour (including defects D1-D7) so a later fix is a conscious decision. */
class CharacterizationTest {
    @TempDir Path tmp;

    private static String bal(String acctRec) { return FixedRecord.get(acctRec, 13, 12); }

    private Fx base() {
        return new Fx().xref("4111111111111111", 1).xref("4222222222222222", 2)
                .acct(1, 1000, 55, 66, "").acct(2, 2000, 77, 88, "")
                .disc("DEFAULT", "01", "0001", 1200); // 12% -> 1%/month
    }

    @Test
    void lastAccountNotRewritten_D1() throws IOException {
        Fx f = base().tcat(1, "01", "0001", 10000).tcat(2, "01", "0001", 10000);
        Fx.Result r = f.run(tmp);
        assertEquals(2, r.tran.size());                       // both transactions written
        assertEquals(ZonedDecimal.encode(1100, 12), bal(r.acct.get(0)));  // acct 1 updated
        assertEquals(ZonedDecimal.encode(55 * 0, 12), FixedRecord.get(r.acct.get(0), 79, 12));
        assertEquals(f.acct.get(1), r.acct.get(1));           // acct 2 byte-identical (D1)
    }

    @Test
    void lastAccountNotRewrittenInGolden_D1() throws IOException {
        Path root = Path.of(Fx.repoRoot());
        List<String> in = Files.readAllLines(root.resolve("legacy/golden/CBTRN02C/ACCTFILE.txt"), StandardCharsets.ISO_8859_1);
        List<String> out = Files.readAllLines(root.resolve("legacy/golden/CBACT04C-after-CBTRN02C/ACCTFILE.txt"), StandardCharsets.ISO_8859_1);
        assertTrue(in.get(49).startsWith("00000000050"));
        assertEquals(in.get(49), out.get(49));
        assertNotEquals(in.get(48), out.get(48));
    }

    @Test
    void defaultFallback_D3() throws IOException {
        Fx.Result r = base().tcat(1, "01", "0001", 10000).tcat(2, "01", "0001", 0).run(tmp);
        assertTrue(r.sysout.contains("DISCLOSURE GROUP RECORD MISSING"));
        assertTrue(r.sysout.contains("TRY WITH DEFAULT GROUP CODE"));
        assertEquals(0, r.rc);
    }

    @Test
    void directGroupHit_D3() throws IOException {
        Fx f = new Fx().tcat(1, "01", "0001", 10000).tcat(2, "01", "0001", 0)
                .xref("4111111111111111", 1).xref("4222222222222222", 2)
                .acct(1, 0, 0, 0, "A000000000").acct(2, 0, 0, 0, "")
                .disc("A000000000", "01", "0001", 2400).disc("DEFAULT", "01", "0001", 1200);
        Fx.Result r = f.run(tmp);
        assertEquals(1, r.sysout.stream().filter(s -> s.equals("DISCLOSURE GROUP RECORD MISSING")).count()); // acct 2 only
        assertEquals(ZonedDecimal.encode(200, 11), r.tran.get(0).substring(132, 143)); // 2.00 = group rate 24%
    }

    @Test
    void zeroRateNoTx() throws IOException {
        Fx f = base().tcat(1, "01", "0001", 10000).tcat(2, "01", "0001", 10000);
        f.disc.clear();
        f.disc("DEFAULT", "01", "0001", 0);
        Fx.Result r = f.run(tmp);
        assertEquals(0, r.tran.size());
    }

    @Test
    void cycleZeroedWhenInterestZero_D6() throws IOException {
        Fx f = base().tcat(1, "01", "0001", 10000).tcat(2, "01", "0001", 10000);
        f.disc.clear();
        f.disc("DEFAULT", "01", "0001", 0);
        Fx.Result r = f.run(tmp);
        assertEquals(ZonedDecimal.encode(0, 12), FixedRecord.get(r.acct.get(0), 79, 12));
        assertEquals(ZonedDecimal.encode(0, 12), FixedRecord.get(r.acct.get(0), 91, 12));
        assertEquals(bal(f.acct.get(0)), bal(r.acct.get(0)));
    }

    @Test
    void untouchedAccountVerbatim() throws IOException {
        Fx f = base().acct(3, 5, 6, 7, "X").tcat(1, "01", "0001", 10000).tcat(2, "01", "0001", 10000);
        Fx.Result r = f.run(tmp);
        assertEquals(f.acct.get(2), r.acct.get(2));
    }

    @Test
    void emptyTcatbal() throws IOException {
        Fx f = base();
        Fx.Result r = f.run(tmp);
        assertEquals(0, r.rc);
        assertEquals(0, r.tran.size());
        assertEquals(f.acct, r.acct);
        assertEquals(List.of("START OF EXECUTION OF PROGRAM CBACT04C", "END OF EXECUTION OF PROGRAM CBACT04C"), r.sysout);
    }

    @Test
    void duplicateXrefFirstWins() throws IOException {
        Fx f = base().xref("9999999999999999", 1).tcat(1, "01", "0001", 10000).tcat(2, "01", "0001", 0);
        Fx.Result r = f.run(tmp);
        assertEquals("4111111111111111", FixedRecord.get(r.tran.get(0), 263, 16));
    }

    @Test
    void tranIdAndTimestamp() throws IOException {
        Fx.Result r = base().tcat(1, "01", "0001", 10000).tcat(2, "01", "0001", 0).run(tmp);
        assertEquals("2022071800000001", r.tran.get(0).substring(0, 16));
        assertEquals("2022-07-18-00.00.00.000000", FixedRecord.get(r.tran.get(0), 305, 26));
        assertEquals(350, r.tran.get(0).length());
    }

    @Test
    void abendMissingDefault() throws IOException {
        Fx f = base().tcat(1, "01", "0002", 10000);
        Fx.Result r = f.run(tmp);
        assertEquals(16, r.rc);
        assertEquals("ABENDING PROGRAM", r.sysout.get(r.sysout.size() - 1));
        assertTrue(r.err.contains("CEE3ABD: ABEND U0999"));
        assertEquals(List.of("DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE",
                "ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM"),
                r.sysout.subList(r.sysout.size() - 5, r.sysout.size()));
    }

    @Test
    void abendMissingAccount() throws IOException {
        Fx.Result r = base().tcat(9, "01", "0001", 10000).run(tmp);
        assertEquals(16, r.rc);
        assertEquals("ABENDING PROGRAM", r.sysout.get(r.sysout.size() - 1));
        assertTrue(r.sysout.contains("ACCOUNT NOT FOUND: 00000000009"));
    }

    @Test
    void abendMissingXref() throws IOException {
        Fx f = base().acct(3, 0, 0, 0, "").tcat(3, "01", "0001", 10000);
        Fx.Result r = f.run(tmp);
        assertEquals(16, r.rc);
        assertEquals("ABENDING PROGRAM", r.sysout.get(r.sysout.size() - 1));
        assertTrue(r.sysout.contains("ACCOUNT NOT FOUND: 00000000003"));
    }

    @Test
    void missingInputFileAbends() throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Fx.Result r = Fx.run(in, tmp.resolve("out"));
        assertEquals(16, r.rc);
    }

    @Test
    void crlfAndShortXrefLinesAreNormalised() throws IOException {
        Path f = tmp.resolve("x.txt");
        Files.writeString(f, "abc\r\ndef\n", StandardCharsets.ISO_8859_1);
        List<String> recs = FixedRecord.readAll(f, 6);
        assertEquals(List.of("abc   ", "def   "), recs);
    }
}
