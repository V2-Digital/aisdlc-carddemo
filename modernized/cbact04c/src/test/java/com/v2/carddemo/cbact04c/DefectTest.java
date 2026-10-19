package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterization tests: legacy defects that this like-for-like port deliberately preserves. */
class DefectTest {
    @TempDir
    Path tmp;

    private static final Path ROOT = Path.of(System.getProperty("basedir", ".")).toAbsolutePath()
            .resolve("../..").normalize();

    @Test
    void D1_lastAccountNotRewritten_afterCbtrn02c() throws Exception {
        Path g = ROOT.resolve("legacy/golden/CBTRN02C");
        Path s = ROOT.resolve("app/data/ASCII");
        Fx f = new Fx(tmp);
        Files.copy(g.resolve("TCATBALF.txt"), f.in.resolve("TCATBALF.txt"));
        Files.copy(g.resolve("ACCTFILE.txt"), f.in.resolve("ACCTFILE.txt"));
        Files.copy(s.resolve("cardxref.txt"), f.in.resolve("XREFFILE.txt"), StandardCopyOption.REPLACE_EXISTING);
        Files.copy(s.resolve("discgrp.txt"), f.in.resolve("DISCGRP.txt"), StandardCopyOption.REPLACE_EXISTING);
        f.run("2022071800");
        List<String> tx = f.outRecs("TRANSACT", 350);
        String last = tx.get(tx.size() - 1);
        assertEquals("2022071800000050", Records.field(last, 1, 16));
        assertEquals("0000000187G", Records.field(last, 133, 11)); // 18.77
        List<String> before = Records.read(g, "ACCTFILE", 300);
        List<String> after = f.outRecs("ACCTFILE", 300);
        assertEquals("00000000050", Records.field(after.get(49), 1, 11));
        assertEquals(before.get(49), after.get(49));
        assertEquals(new java.math.BigDecimal("1945.87"), Zoned.parse(Records.field(after.get(49), 13, 12)));
        assertEquals(new java.math.BigDecimal("1501.75"), Zoned.parse(Records.field(after.get(49), 79, 12)));
        assertEquals(new java.math.BigDecimal("-47.88"), Zoned.parse(Records.field(after.get(49), 91, 12)));
    }

    @Test
    void D1_lastAccountNotRewritten_synthetic() throws Exception {
        Fx f = new Fx(tmp)
                .file("TCATBALF", List.of(Fx.tcat(1, "01", "0001", "00000120000"), Fx.tcat(2, "01", "0001", "00000120000")))
                .file("XREFFILE", List.of(Fx.xref("1111222233334444", 1), Fx.xref("5555666677778888", 2)))
                .file("ACCTFILE", List.of(Fx.acct(1, Fx.Z12, "00000000500A", Fx.Z12, ""),
                        Fx.acct(2, Fx.Z12, "00000000500A", Fx.Z12, "")))
                .file("DISCGRP", List.of(Fx.disc("DEFAULT", "01", "0001", "00120{")))
                .run("2022071800");
        assertEquals(2, f.outRecs("TRANSACT", 350).size());
        List<String> a = f.outRecs("ACCTFILE", 300);
        assertEquals("00000000120{", Records.field(a.get(0), 13, 12)); // charged and rewritten
        assertEquals("00000000000{", Records.field(a.get(0), 79, 12));
        assertEquals(Fx.Z12, Records.field(a.get(1), 13, 12)); // charged 12.00 in TRANSACT, balance untouched
        assertEquals("00000000500A", Records.field(a.get(1), 79, 12));
    }

    @Test
    void D2_feesStepIsNoOp_onlyInterestTransactionsWritten() throws Exception {
        Fx f = new Fx(tmp)
                .file("TCATBALF", List.of(Fx.tcat(1, "01", "0001", "00000120000"), Fx.tcat(2, "01", "0001", "0000000000{")))
                .file("XREFFILE", List.of(Fx.xref("1111222233334444", 1), Fx.xref("5555666677778888", 2)))
                .file("ACCTFILE", List.of(Fx.acct(1, Fx.Z12, Fx.Z12, Fx.Z12, ""), Fx.acct(2, Fx.Z12, Fx.Z12, Fx.Z12, "")))
                .file("DISCGRP", List.of(Fx.disc("DEFAULT", "01", "0001", "00120{")))
                .run("2022071800");
        // only interest records (type 01 / cat 0005) exist; no fee transactions
        List<String> tx = f.outRecs("TRANSACT", 350);
        assertEquals(2, tx.size());
        for (String r : tx) {
            assertEquals("010005", Records.field(r, 17, 6));
        }
    }

    @Test
    void D5_balanceOverflowDropsHighOrderDigit() throws Exception {
        Fx f = new Fx(tmp)
                .file("TCATBALF", List.of(Fx.tcat(1, "01", "0001", "00000120000"), Fx.tcat(2, "01", "0001", "0000000000{")))
                .file("XREFFILE", List.of(Fx.xref("1111222233334444", 1), Fx.xref("5555666677778888", 2)))
                .file("ACCTFILE", List.of(Fx.acct(1, "99999999990{", Fx.Z12, Fx.Z12, ""),
                        Fx.acct(2, Fx.Z12, Fx.Z12, Fx.Z12, "")))
                .file("DISCGRP", List.of(Fx.disc("DEFAULT", "01", "0001", "00120{")))
                .run("2022071800");
        // 999999999.99... balance 9999999999.00 + 12.00 wraps to 11.00 in S9(10)V99
        assertEquals("00000000110{", Records.field(f.outRecs("ACCTFILE", 300).get(0), 13, 12));
    }
}
