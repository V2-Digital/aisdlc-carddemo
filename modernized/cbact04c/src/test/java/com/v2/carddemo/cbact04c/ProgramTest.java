package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Interest rule, layout, defect characterization and abend tests on synthetic inputs. */
class ProgramTest {
    @TempDir Path dir;

    // ---- AC5 truncation
    @Test
    void truncatesNotRounds() throws Exception {
        // 100.00 x 15.99 / 1200 = 1.3325 -> 1.33 ; rate 015.99 = "00159" + "I" -> 0015 99: S9(4)V99
        Fx f = Fx.simple(dir, "", "0000001000{", "001599", "001599");
        // DISCGRP rate field is "00159I"; build explicitly
        f.write("DISCGRP", List.of(Fx.disc("DEFAULT", "01", "0001", "00159I")));
        assertEquals(0, f.run("2022071800"));
        assertEquals("0000000013C", Fx.amountField(f.read("TRANSACT", 350).get(0)));
    }

    @Test
    void truncatesBelowHalfCent() throws Exception {
        // 1.00 x 15 / 1200 = 0.0125 -> 0.01 ; third decimal 2
        Fx f = Fx.simple(dir, "", "0000000010{", "00150{", "00150{");
        f.run("2022071800");
        assertEquals("0000000000A", Fx.amountField(f.read("TRANSACT", 350).get(0)));
    }

    @Test
    void truncatesThirdDecimalAtLeastFive() throws Exception {
        // 1.00 x 18 / 1200 = 0.015 -> 0.01 (rounding would give 0.02)
        Fx f = Fx.simple(dir, "", "0000000010{", "00180{", "00180{");
        f.run("2022071800");
        assertEquals("0000000000A", Fx.amountField(f.read("TRANSACT", 350).get(0)));
    }

    @Test
    void negativeBalanceTruncatesTowardZero() throws Exception {
        // -1.00 x 18 / 1200 = -0.015 -> -0.01
        Fx f = Fx.simple(dir, "", "0000000010}", "00180{", "00180{");
        f.run("2022071800");
        assertEquals("0000000000J", Fx.amountField(f.read("TRANSACT", 350).get(0)));
    }

    // ---- AC6
    @Test
    void zeroRateSkipped() throws Exception {
        Fx f = Fx.simple(dir, "", "0000001000{", "00000{", "00000{");
        f.write("TCATBALF", List.of(Fx.tcat("00000000001", "01", "0001", "0000001000{"),
                Fx.tcat("00000000002", "01", "0001", "0000001000{")));
        f.write("ACCTFILE", List.of(Fx.acct("00000000001", "00000010000{", ""), Fx.acct("00000000002", "00000010000{", "")));
        f.write("XREFFILE", List.of(Fx.xref("4111111111111111", "00000000001"), Fx.xref("4222222222222222", "00000000002")));
        f.run("2022071800");
        assertEquals(0, f.read("TRANSACT", 350).size());
        // account 1 is rewritten (cycle fields zeroed), balance unchanged
        String a1 = f.read("ACCTFILE", 300).get(0);
        assertEquals("00000010000{", a1.substring(12, 24));
        assertEquals("000000000000".substring(0, 11) + "{", a1.substring(78, 90));
    }

    @Test
    void defaultFallbackPrintsMessages() throws Exception {
        Fx f = Fx.simple(dir, "GRP", "0000001000{", "00150{", "00150{");
        assertEquals(0, f.run("2022071800"));
        assertTrue(f.sysout.contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
        assertEquals(1, f.read("TRANSACT", 350).size());
    }

    @Test
    void specificGroupUsedWhenPresent() throws Exception {
        Fx f = Fx.simple(dir, "GRP", "0000001000{", "00000{", "00150{");
        f.write("DISCGRP", List.of(Fx.disc("DEFAULT", "01", "0001", "00150{"), Fx.disc("GRP", "01", "0001", "00300{")));
        f.run("2022071800");
        assertTrue(!f.sysout.contains("MISSING"));
        assertEquals("0000000025{", Fx.amountField(f.read("TRANSACT", 350).get(0))); // 100.00 x 30.00 / 1200 = 2.50
    }

    // ---- AC7 layout
    @Test
    void transactLayout() throws Exception {
        Fx f = Fx.simple(dir, "", "0000001000{", "00150{", "00150{");
        f.run("2022071800");
        String t = f.read("TRANSACT", 350).get(0);
        assertEquals(350, FixedFile.read(f.dd.get("TRANSACT"), 350).get(0).length());
        assertEquals(351, java.nio.file.Files.size(f.dd.get("TRANSACT")));
        assertEquals("2022071800000001", t.substring(0, 16));
        assertEquals("010005System    Int. for a/c 00000000001", t.substring(16, 56));
        assertTrue(t.substring(56, 132).isBlank() && t.substring(56, 132).length() == 76);
        assertTrue(t.substring(330, 350).isBlank());
        assertEquals("000000000", t.substring(143, 152));
        assertEquals("4111111111111111", t.substring(262, 278));
        assertEquals("2022-07-18-00.00.00.000000", t.substring(278, 304));
        assertEquals(t.substring(278, 304), t.substring(304, 330));
    }

    // ---- AC4 / D1
    @Test
    void lastAccountNotRewritten_D1() throws Exception {
        Fx f = Fx.simple(dir, "", "0000001000{", "00150{", "00150{");
        String before = f.read("ACCTFILE", 300).get(0);
        f.run("2022071800");
        assertEquals(1, f.read("TRANSACT", 350).size());
        assertEquals(before, f.read("ACCTFILE", 300).get(0));
    }

    @Test
    void previousAccountRewrittenWhenAccountChanges() throws Exception {
        Fx f = Fx.simple(dir, "", "0000001000{", "00150{", "00150{");
        f.write("TCATBALF", List.of(Fx.tcat("00000000001", "01", "0001", "0000001000{"),
                Fx.tcat("00000000002", "01", "0001", "0000001000{")));
        f.write("ACCTFILE", List.of(Fx.acct("00000000001", "00000010000{", ""), Fx.acct("00000000002", "00000010000{", "")));
        f.write("XREFFILE", List.of(Fx.xref("4111111111111111", "00000000001"), Fx.xref("4222222222222222", "00000000002")));
        f.run("2022071800");
        List<String> a = f.read("ACCTFILE", 300);
        assertEquals("00000010012E", a.get(0).substring(12, 24)); // 1000.00 + 1.25
        assertEquals("00000000000{", a.get(0).substring(78, 90)); // cycle credit zeroed
        assertEquals("00000010000{", a.get(1).substring(12, 24)); // last account untouched (D1)
        assertEquals("00000000999{".substring(0, 0) + "00000000999{", a.get(1).substring(78, 90));
    }

    // ---- D6 suffix wrap is covered by arithmetic: (999999+1) mod 1e6 = 0
    @Test
    void suffixCountsPerTransaction() throws Exception {
        Fx f = Fx.simple(dir, "", "0000001000{", "00150{", "00150{");
        f.write("TCATBALF", List.of(Fx.tcat("00000000001", "01", "0001", "0000001000{"),
                Fx.tcat("00000000001", "01", "0002", "0000001000{")));
        f.write("DISCGRP", List.of(Fx.disc("DEFAULT", "01", "0001", "00150{"), Fx.disc("DEFAULT", "01", "0002", "00150{")));
        f.run("2022071800");
        List<String> t = f.read("TRANSACT", 350);
        assertEquals("2022071800000001", t.get(0).substring(0, 16));
        assertEquals("2022071800000002", t.get(1).substring(0, 16));
    }

    // ---- AC8
    @Test
    void missingAccountAbends() throws Exception {
        Fx f = Fx.simple(dir, "", "0000001000{", "00150{", "00150{");
        f.write("ACCTFILE", List.of(Fx.acct("00000000009", "00000010000{", "")));
        assertEquals(16, f.run("2022071800"));
        assertTrue(f.sysout.contains("ACCOUNT NOT FOUND: 00000000001\nERROR READING ACCOUNT FILE\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"));
        assertTrue(!f.sysout.contains("END OF EXECUTION"));
    }

    @Test
    void missingXrefAbends() throws Exception {
        Fx f = Fx.simple(dir, "", "0000001000{", "00150{", "00150{");
        f.write("XREFFILE", List.of(Fx.xref("4111111111111111", "00000000009")));
        assertEquals(16, f.run("2022071800"));
        assertTrue(f.sysout.contains("ERROR READING XREF FILE\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"));
    }

    @Test
    void missingDefaultGroupAbends() throws Exception {
        Fx f = Fx.simple(dir, "", "0000001000{", "00150{", "00150{");
        f.write("DISCGRP", List.of(Fx.disc("DEFAULT", "02", "0001", "00150{")));
        assertEquals(16, f.run("2022071800"));
        assertTrue(f.sysout.contains("ERROR READING DEFAULT DISCLOSURE GROUP\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"));
    }

    @Test
    void missingDiscgrpFileReportsLegacyWrongMessage_D7() throws Exception {
        Fx f = Fx.simple(dir, "", "0000001000{", "00150{", "00150{");
        java.nio.file.Files.delete(f.dd.get("DISCGRP"));
        assertEquals(16, f.run("2022071800"));
        assertTrue(f.sysout.contains("ERROR OPENING DALY REJECTS FILE\n"));
        assertTrue(f.sysout.contains("ABENDING PROGRAM"));
    }

    @Test
    void emptyTcatbalWritesNothing() throws Exception {
        Fx f = Fx.simple(dir, "", "0000001000{", "00150{", "00150{");
        String before = f.read("ACCTFILE", 300).get(0);
        f.write("TCATBALF", List.of());
        assertEquals(0, f.run("2022071800"));
        assertEquals(0, f.read("TRANSACT", 350).size());
        assertEquals(before, f.read("ACCTFILE", 300).get(0));
    }
}
