package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins preserved legacy behaviour (F1-F7). Do not "fix" these without a separate change request. */
class CharacterizationTest {
    @TempDir Path tmp;

    /** One account, one 01/0001 row, DEFAULT rate; account group blank so DEFAULT fallback is used (F3). */
    private Fx one(String bal, String rate) {
        Fx f = new Fx(tmp).rate(rate);
        f.tcat.add(Fx.tcatRec("00000000001", "01", "0001", bal));
        f.xref.add(Fx.xrefRec("4111111111111111", "00000000001"));
        f.acct.add(Fx.acctRec("00000000001", "100.00", ""));
        return f;
    }

    @Test void f1_singleAccount_neverWrittenBack() throws IOException {
        Fx f = one("100.00", "12.00").run();
        assertEquals(0, f.rc);
        assertEquals(1, f.trans().size());
        assertEquals(f.acct, f.accts()); // unchanged: balance and cycle fields untouched
    }

    @Test void f1_firstOfTwoAccountsIsWrittenBack_andCycleZeroed() throws IOException {
        Fx f = one("100.00", "12.00");
        f.tcat.add(Fx.tcatRec("00000000002", "01", "0001", "100.00"));
        f.xref.add(Fx.xrefRec("4222222222222222", "00000000002"));
        f.acct.add(Fx.acctRec("00000000002", "100.00", ""));
        f.run();
        assertEquals(Fx.acctRec("00000000001", "101.00", "").substring(0, 12) + Fx.z("101.00", 10)
                + f.acct.get(0).substring(24, 78) + Fx.z("0", 10) + Fx.z("0", 10) + f.acct.get(0).substring(102), f.accts().get(0));
        assertEquals(f.acct.get(1), f.accts().get(1));
    }

    @Test void f2_truncationNotRounding() throws IOException {
        assertEquals(Fx.z("1.25", 9), Fx.amt(one("100.40", "15.00").run().trans().get(0))); // 1.255, half-up = 1.26
        assertEquals(Fx.z("-1.54", 9), Fx.amt(one("-123.49", "15.00").run().trans().get(0))); // -1.543625
    }

    @Test void f2_negativeZeroNeverWritten() throws IOException {
        // -0.002 truncates to 0 -> "{" not "}"
        assertEquals("0000000000{", Fx.amt(one("-0.20", "1.00").run().trans().get(0)));
    }

    @Test void f3_blankGroup_fallsBackToDefault_withWarnings() throws IOException {
        Fx f = one("100.00", "12.00").run();
        assertTrue(f.sysout.contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
        assertEquals(1, f.trans().size());
    }

    @Test void f3_specificGroupRowIsUsedWhenPresent_noWarning() throws IOException {
        Fx f = one("100.00", "12.00");
        f.acct.set(0, Fx.acctRec("00000000001", "100.00", "GRP1"));
        f.disc.add(Fx.discRec("GRP1", "01", "0001", "24.00"));
        f.run();
        assertEquals(Fx.z("2.00", 9), Fx.amt(f.trans().get(0)));
        assertFalse(f.sysout.contains("MISSING"));
    }

    @Test void f4_noFees_onlyType01Cat0005() throws IOException {
        for (String t : one("100.00", "12.00").run().trans()) {
            assertEquals("01", t.substring(16, 18));
            assertEquals("0005", t.substring(18, 22));
        }
    }

    @Test void zeroRate_writesNoTransaction() throws IOException {
        Fx f = one("100.00", "0.00").run();
        assertEquals(0, f.trans().size());
    }

    @Test void negativeZeroRate_countsAsZero() throws IOException {
        Fx f = one("100.00", "0.00");
        f.disc.set(0, f.disc.get(0).substring(0, 16) + "00000}" + f.disc.get(0).substring(22));
        assertEquals(0, f.run().trans().size());
    }

    @Test void zeroRateAccount_stillGetsCycleFieldsZeroed_whenNotLast() throws IOException {
        Fx f = one("100.00", "0.00");
        f.tcat.add(Fx.tcatRec("00000000002", "01", "0001", "1.00"));
        f.xref.add(Fx.xrefRec("4222222222222222", "00000000002"));
        f.acct.add(Fx.acctRec("00000000002", "100.00", ""));
        f.run();
        assertEquals(Fx.z("0", 10) + Fx.z("0", 10), f.accts().get(0).substring(78, 102));
        assertEquals(f.acct.get(0).substring(12, 24), f.accts().get(0).substring(12, 24));
    }

    @Test void f5_monthlyOverflow_dropsHighOrderDigits() throws IOException {
        // spec 5 / COBOL probe: 8,333,324,999.91 -> 333,324,999.91
        assertEquals("3333249999A", Fx.amt(one("999999999.99", "9999.99").run().trans().get(0)));
    }

    @Test void f5_totalOverflow_andBalance() throws IOException {
        // 4 x 333,324,999.91 accumulate to 333,299,999.64; +100.00 start balance. Cross-checked vs COBOL harness: 03333000996D
        Fx f = new Fx(tmp).rate("9999.99");
        String[] cats = {"0001", "0002", "0003", "0004"};
        for (String c : cats) {
            f.disc.add(Fx.discRec("DEFAULT", "01", c, "9999.99"));
            f.tcat.add(Fx.tcatRec("00000000001", "01", c, "999999999.99"));
        }
        f.disc.remove(0); // rate(...) added 01/0001 already; keep a single row per category
        f.xref.add(Fx.xrefRec("4111111111111111", "00000000001"));
        f.acct.add(Fx.acctRec("00000000001", "100.00", ""));
        f.tcat.add(Fx.tcatRec("00000000002", "01", "0001", "0.00"));
        f.xref.add(Fx.xrefRec("4222222222222222", "00000000002"));
        f.acct.add(Fx.acctRec("00000000002", "0.00", ""));
        f.run();
        assertEquals(Fx.z("333300099.64", 10), f.accts().get(0).substring(12, 24));
    }

    @Test void f5_suffixWrapsAt1000000() throws IOException {
        Fx f = new Fx(tmp).rate("12.00");
        f.parm = "2022071800";
        // 1,000,001 rows is too heavy for files; exercise the logic directly
        var calc = new InterestCalc(new java.io.PrintStream(java.io.OutputStream.nullOutputStream()), java.time.Clock.systemUTC());
        var tcat = new java.util.ArrayList<String>();
        var accts = new java.util.TreeMap<String, String>();
        var xr = new java.util.TreeMap<String, String>();
        for (int i = 1; i <= 1_000_001; i++) {
            String id = String.format("%011d", i);
            tcat.add(Fx.tcatRec(id, "01", "0001", "0"));
            accts.put(id, Fx.acctRec(id, "0", ""));
            xr.put(id, "4111111111111111");
        }
        var disc = new java.util.TreeMap<String, String>();
        disc.put("DEFAULT   010001", Fx.discRec("DEFAULT", "01", "0001", "12.00"));
        var r = calc.run(new InterestCalc.Inputs(tcat, xr, disc, accts), "2022071800");
        assertEquals("2022071800999999", r.transactions().get(999_998).substring(0, 16));
        assertEquals("2022071800000000", r.transactions().get(999_999).substring(0, 16));
        assertEquals("2022071800000001", r.transactions().get(1_000_000).substring(0, 16));
    }

    @Test void f6_rerunChargesInterestAgain() throws IOException {
        Fx f = one("100.00", "12.00");
        f.tcat.add(Fx.tcatRec("00000000002", "01", "0001", "100.00"));
        f.xref.add(Fx.xrefRec("4222222222222222", "00000000002"));
        f.acct.add(Fx.acctRec("00000000002", "100.00", ""));
        f.run();
        var first = f.trans();
        f.exec();
        assertEquals(first, f.trans());
        assertEquals(2, f.trans().size()); // same amounts charged again, TCATBAL never cleared
    }

    @Test void f7_missingDiscgrpFile_wrongMessage() throws IOException {
        Fx f = one("100.00", "12.00");
        f.write("TCATBALF", f.tcat); f.write("XREFFILE", f.xref); f.write("ACCTFILE", f.acct);
        f.exec();
        assertEquals(16, f.rc);
        assertTrue(f.sysout.contains("ERROR OPENING DALY REJECTS FILE\n"));
    }

    @Test void transactionLayout() throws IOException {
        String t = one("1164.87", "15.00").run().trans().get(0);
        assertEquals(350, t.length());
        assertEquals("2022071800000001", t.substring(0, 16));
        assertEquals("System    ", t.substring(22, 32));
        assertEquals(Fx.pad("Int. for a/c 00000000001", 100), t.substring(32, 132));
        assertEquals("0000000145F", t.substring(132, 143));
        assertEquals("4111111111111111", t.substring(262, 278));
        assertEquals("2022-07-18-00.00.00.000000", t.substring(278, 304));
        assertEquals(t.substring(278, 304), t.substring(304, 330));
    }

    @Test void timestampHundredths() throws IOException {
        Fx f = one("100.00", "12.00");
        Files.createDirectories(tmp);
        f.write("TCATBALF", f.tcat); f.write("XREFFILE", f.xref); f.write("DISCGRP", f.disc); f.write("ACCTFILE", f.acct);
        var o = new java.io.PrintStream(java.io.OutputStream.nullOutputStream());
        Main.run(new String[] {"--PARM=2022071800", "--CURRENT-DATE=2022071823595987", "--TCATBALF=" + tmp.resolve("TCATBALF.txt"),
                "--XREFFILE=" + tmp.resolve("XREFFILE.txt"), "--DISCGRP=" + tmp.resolve("DISCGRP.txt"),
                "--ACCTFILE=" + tmp.resolve("ACCTFILE.txt"), "--TRANSACT=" + tmp.resolve("T")}, o, o);
        assertEquals("2022-07-18-23.59.59.870000", Files.readAllLines(tmp.resolve("T")).get(0).substring(278, 304));
    }

    @Test void emptyTcatbal() throws IOException {
        Fx f = one("1.00", "12.00");
        f.tcat.clear();
        f.run();
        assertEquals(0, f.rc);
        assertEquals("START OF EXECUTION OF PROGRAM CBACT04C\nEND OF EXECUTION OF PROGRAM CBACT04C\n", f.sysout);
        assertEquals(0, f.trans().size());
        assertEquals(f.acct, f.accts());
    }

    @Test void unsortedTcatbal_isSortedByKey() throws IOException {
        Fx f = one("100.00", "12.00");
        f.tcat.add(0, Fx.tcatRec("00000000002", "01", "0001", "100.00"));
        f.xref.add(Fx.xrefRec("4222222222222222", "00000000002"));
        f.acct.add(Fx.acctRec("00000000002", "100.00", ""));
        f.run();
        assertEquals("2022071800000001", f.trans().get(0).substring(0, 16));
        assertTrue(f.trans().get(0).contains("a/c 00000000001"));
    }
}
