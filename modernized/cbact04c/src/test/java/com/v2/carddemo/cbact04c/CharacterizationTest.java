package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CharacterizationTest {
    private static final Path POSTED = Fixture.GOLDEN.resolve("CBTRN02C");

    private static String pad(String s, int n) { return RecordFile.pad(s, n); }

    private static String tcat(String acct, String type, String cat, String bal) {
        return pad(acct + type + cat + z(bal, 11), 50) + "\n";
    }
    private static String acct(String id, String group, String bal) {
        String r = pad(id + "Y" + z(bal, 12), 300);
        return r.substring(0, 112) + pad(group, 10) + r.substring(122) + "\n";
    }
    private static String xref(String card, String acct) {
        return pad(card, 25) + acct + "\n";
    }
    private static String disc(String group, String type, String cat, String rate) {
        return pad(pad(group, 10) + type + cat + z(rate, 6), 50) + "\n";
    }
    private static String z(String v, int digits) { return Zoned.format(new java.math.BigDecimal(v), digits); }
    private static List<String> lines(String s) { return s.lines().toList(); }

    private Fixture small(Path tmp, String tcat, String accts, String xref, String disc) throws IOException {
        Fixture f = new Fixture(tmp);
        f.put("TCATBALF", tcat);
        f.put("ACCTFILE", accts);
        f.put("XREFFILE", xref);
        f.put("DISCGRP", disc);
        return f;
    }

    @Test void truncatesNotRounds_D2(@TempDir Path tmp) throws IOException {
        // 0.67 * 15.00 / 1200 = 0.008375 -> 0.00 truncated; here 1945.87*15/1200 = 24.323375 -> 24.32
        Fixture f = new Fixture(tmp);
        f.stock(POSTED);
        f.run();
        // find a golden TCATBAL row where trunc != round
        List<String> tc = lines(Fixture.slurp(POSTED.resolve("TCATBALF.txt")));
        List<String> tr = lines(f.read("TRANSACT"));
        int differing = 0;
        for (String r : tc) {
            java.math.BigDecimal bal = Zoned.parse(r.substring(17, 28), 2);
            if (!r.substring(11, 13).equals("01")) continue;
            java.math.BigDecimal exact = bal.multiply(new java.math.BigDecimal("15")).divide(new java.math.BigDecimal(1200));
            if (exact.setScale(2, java.math.RoundingMode.DOWN).compareTo(exact.setScale(2, java.math.RoundingMode.HALF_UP)) != 0) {
                differing++;
                String id = r.substring(0, 11);
                String t = tr.stream().filter(x -> x.contains("a/c " + id)).findFirst().orElseThrow();
                assertEquals(Zoned.format(exact.setScale(2, java.math.RoundingMode.DOWN), 11), t.substring(132, 143), id);
                assertNotEquals(Zoned.format(exact.setScale(2, java.math.RoundingMode.HALF_UP), 11), t.substring(132, 143), id);
            }
        }
        assertTrue(differing > 0, "need at least one golden case where trunc != round");
    }

    @Test void lastAccountNotRewritten_D1(@TempDir Path tmp) throws IOException {
        Fixture f = new Fixture(tmp);
        f.stock(POSTED);
        f.run();
        List<String> in = lines(Fixture.slurp(POSTED.resolve("ACCTFILE.txt")));
        List<String> out = lines(f.read("ACCTFILE"));
        assertEquals(50, out.size());
        String in50 = in.stream().filter(x -> x.startsWith("00000000050")).findFirst().orElseThrow();
        String out50 = out.stream().filter(x -> x.startsWith("00000000050")).findFirst().orElseThrow();
        assertEquals(in50, out50, "D1: last account is not updated");
        String t50 = lines(f.read("TRANSACT")).stream().filter(x -> x.startsWith("2022071800000050")).findFirst().orElseThrow();
        assertEquals("0000000187G", t50.substring(132, 143), "interest still posted to TRANSACT");
        long changed = 0;
        for (int i = 0; i < 50; i++) if (!in.get(i).equals(out.get(i))) changed++;
        assertEquals(49, changed);
    }

    @Test void crlfInputAccepted(@TempDir Path tmp) throws IOException {
        String stock = Fixture.slurp(Fixture.ASCII.resolve("tcatbal.txt"));
        assertTrue(stock.contains("\r"), "stock fixture is CRLF");
        Fixture f = new Fixture(tmp);
        f.stock(null);
        f.run();
        assertEquals(0, f.rc);
        assertFalse(f.read("TRANSACT").contains("\r"));
        assertFalse(f.read("ACCTFILE").contains("\r"));
        assertEquals(Fixture.slurp(Fixture.GOLDEN.resolve("CBACT04C/TRANSACT.txt")), f.read("TRANSACT"));
    }

    @Test void unsortedTcatbalIsSortedByKey(@TempDir Path tmp) throws IOException {
        Fixture f = new Fixture(tmp);
        f.stock(POSTED);
        List<String> rows = new java.util.ArrayList<>(lines(Fixture.slurp(POSTED.resolve("TCATBALF.txt"))));
        java.util.Collections.reverse(rows);
        f.put("TCATBALF", String.join("\n", rows) + "\n");
        f.run();
        assertEquals(Fixture.slurp(Fixture.GOLDEN.resolve("CBACT04C-after-CBTRN02C/TRANSACT.txt")), f.read("TRANSACT"));
        assertEquals(Fixture.slurp(Fixture.GOLDEN.resolve("CBACT04C-after-CBTRN02C/ACCTFILE.txt")), f.read("ACCTFILE"));
    }

    @Test void fallsBackToDefault(@TempDir Path tmp) throws IOException {
        Fixture f = new Fixture(tmp);
        f.stock(POSTED);
        f.run();
        assertTrue(f.sysout.contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
        assertEquals(0, f.rc);
    }

    @Test void nonDefaultGroupUsedWhenPresent_F1(@TempDir Path tmp) throws IOException {
        // balance 12000.00 at 12.00% -> 120.00; DEFAULT row would give 0% (no tran)
        Fixture f = small(tmp,
                tcat("00000000001", "01", "0001", "12000.00") + tcat("00000000002", "01", "0001", "0.00"),
                acct("00000000001", "GOLD", "1.00") + acct("00000000002", "GOLD", "0.00"),
                xref("4111111111111111", "00000000001") + xref("4222222222222222", "00000000002"),
                disc("GOLD", "01", "0001", "12.00") + disc("DEFAULT", "01", "0001", "0.00"));
        f.run();
        assertEquals(0, f.rc);
        assertFalse(f.sysout.contains("MISSING"));
        List<String> t = lines(f.read("TRANSACT"));
        assertEquals(2, t.size());
        assertEquals("0000001200{", t.get(0).substring(132, 143));
        assertEquals("4111111111111111", t.get(0).substring(262, 278));
    }

    @Test void zeroRateWritesNothingButUpdatesAccount(@TempDir Path tmp) throws IOException {
        Fixture f = small(tmp,
                tcat("00000000001", "01", "0001", "12000.00") + tcat("00000000002", "01", "0001", "0.00"),
                acct("00000000001", "", "1.00") + acct("00000000002", "", "0.00"),
                xref("4111111111111111", "00000000001") + xref("4222222222222222", "00000000002"),
                disc("DEFAULT", "01", "0001", "0.00"));
        f.run();
        assertEquals(0, f.rc);
        assertEquals("", f.read("TRANSACT"));
        String a1 = lines(f.read("ACCTFILE")).get(0);
        assertEquals("00000000001", a1.substring(0, 11));
        assertEquals(z("1.00", 12), a1.substring(12, 24), "balance unchanged (no interest)");
        assertEquals(z("0", 12), a1.substring(78, 90), "cycle credit zeroed");
        assertEquals(z("0", 12), a1.substring(90, 102), "cycle debit zeroed");
    }

    @Test void negativeBalance_truncatesTowardZero(@TempDir Path tmp) throws IOException {
        // -100.01 * 15 / 1200 = -1.250125 -> -1.25 (toward zero)
        Fixture f = small(tmp,
                tcat("00000000001", "01", "0001", "-100.01") + tcat("00000000002", "01", "0001", "0.00"),
                acct("00000000001", "", "1.00") + acct("00000000002", "", "0.00"),
                xref("4111111111111111", "00000000001") + xref("4222222222222222", "00000000002"),
                disc("DEFAULT", "01", "0001", "15.00"));
        f.run();
        assertEquals(0, f.rc);
        assertEquals(z("-1.25", 11), lines(f.read("TRANSACT")).get(0).substring(132, 143));
    }

    @Test void firstXrefWins_F4(@TempDir Path tmp) throws IOException {
        Fixture f = small(tmp,
                tcat("00000000001", "01", "0001", "100.00") + tcat("00000000002", "01", "0001", "0.00"),
                acct("00000000001", "", "1.00") + acct("00000000002", "", "0.00"),
                xref("4111111111111111", "00000000001") + xref("4999999999999999", "00000000001")
                        + xref("4222222222222222", "00000000002"),
                disc("DEFAULT", "01", "0001", "15.00"));
        f.run();
        assertEquals("4111111111111111", lines(f.read("TRANSACT")).get(0).substring(262, 278));
    }

    @Test void suffixWraps_F4(@TempDir Path tmp) throws Exception {
        Fixture f = small(tmp,
                tcat("00000000001", "01", "0001", "100.00") + tcat("00000000001", "01", "0002", "100.00")
                        + tcat("00000000002", "01", "0001", "0.00"),
                acct("00000000001", "", "1.00") + acct("00000000002", "", "0.00"),
                xref("4111111111111111", "00000000001") + xref("4222222222222222", "00000000002"),
                disc("DEFAULT", "01", "0001", "15.00") + disc("DEFAULT", "01", "0002", "15.00"));
        java.util.Map<String, Path> dd = new java.util.HashMap<>();
        for (String n : new String[] {"TCATBALF", "XREFFILE", "DISCGRP", "ACCTFILE", "TRANSACT"}) dd.put(n, f.file(n));
        Cbact04c c = new Cbact04c(dd, "2022071800", () -> Fixture.CLOCK, new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
        java.lang.reflect.Field fld = Cbact04c.class.getDeclaredField("tranIdSuffix");
        fld.setAccessible(true);
        fld.setInt(c, 999_998);
        assertEquals(0, c.run());
        List<String> t = lines(f.read("TRANSACT"));
        assertEquals("2022071800999999", t.get(0).substring(0, 16));
        assertEquals("2022071800000000", t.get(1).substring(0, 16), "wraps silently");
    }
}
