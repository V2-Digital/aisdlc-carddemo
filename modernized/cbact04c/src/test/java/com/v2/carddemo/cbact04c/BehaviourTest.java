package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Interest, zero rate/balance, last-account defect (D1), overflow (D6) and abend paths on small inputs. */
class BehaviourTest {
    private static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    private static final Path ASCII = ROOT.resolve("app/data/ASCII");
    private static final Path GOLDEN = ROOT.resolve("legacy/golden");
    private static final String DEFAULT_GRP = "DEFAULT   ";

    @TempDir Path tmp;
    String sysout;

    static String pad(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    static String tcat(String acct, String type, String cat, String bal) {
        return pad(acct + type + cat + bal, 50);
    }

    static String xref(String card, String acct) {
        return pad(card + "         " + acct, 50);
    }

    static String acct(String id, String bal, String credit, String debit, String group) {
        StringBuilder b = new StringBuilder(pad(id + "Y" + bal, 300));
        b.replace(78, 90, credit);
        b.replace(90, 102, debit);
        b.replace(112, 122, pad(group, 10));
        return b.toString();
    }

    static String disc(String group, String type, String cat, String rate) {
        return pad(pad(group, 10) + type + cat + rate, 50);
    }

    Path write(String name, String... lines) throws Exception {
        Path p = tmp.resolve(name);
        Files.writeString(p, lines.length == 0 ? "" : String.join("\n", lines) + "\n", StandardCharsets.ISO_8859_1);
        return p;
    }

    int run(List<String> tcat, List<String> xref, List<String> acct, List<String> disc) throws Exception {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        PrintStream out = new PrintStream(bo, false, StandardCharsets.ISO_8859_1);
        int rc = Main.run(new String[] {"--parm", "2022071800",
                "--tcatbalf", write("t.txt", tcat.toArray(String[]::new)).toString(),
                "--xreffile", write("x.txt", xref.toArray(String[]::new)).toString(),
                "--acctfile", write("a.txt", acct.toArray(String[]::new)).toString(),
                "--discgrp", write("d.txt", disc.toArray(String[]::new)).toString(),
                "--transact", tmp.resolve("TRANSACT.txt").toString(),
                "--acctfile-out", tmp.resolve("ACCTOUT.txt").toString()}, "2022071800000000", out);
        out.flush();
        sysout = bo.toString(StandardCharsets.ISO_8859_1);
        return rc;
    }

    List<String> lines(String f) throws Exception {
        return Files.readAllLines(tmp.resolve(f), StandardCharsets.ISO_8859_1);
    }

    String amt(String tranLine) {
        return tranLine.substring(16 + 2 + 4 + 10 + 100, 16 + 2 + 4 + 10 + 100 + 11);
    }

    int simple(String bal, String rate) throws Exception {
        return run(List.of(tcat("00000000001", "01", "0001", bal)),
                List.of(xref("1111111111111111", "00000000001")),
                List.of(acct("00000000001", "00000000010{", "00000000000{", "00000000000{", "")),
                List.of(disc("DEFAULT", "01", "0001", rate)));
    }

    // ---- D2 / AC4 ----
    @Test
    void interestIsTruncatedNotRounded_acct48() throws Exception {
        // 1512.77 * 15.00 / 1200 = 18.909625 -> 18.90 (rounding would give 18.91)
        assertEquals(0, simple("0000015127G", "00150{"));
        assertEquals("0000000189{", amt(lines("TRANSACT.txt").get(0)));
    }

    @Test
    void negativeBalanceTruncatesTowardZero() throws Exception {
        // -1512.77 -> -18.909625 -> -18.90, written with a negative overpunch
        assertEquals(0, simple("0000015127P", "00150{"));
        assertEquals("0000000189}", amt(lines("TRANSACT.txt").get(0)));
    }

    // ---- AC6 ----
    @Test
    void zeroRateWritesNoTransaction() throws Exception {
        assertEquals(0, simple("0000012000{", "00000{"));
        assertEquals(0, lines("TRANSACT.txt").size());
    }

    @Test
    void zeroBalanceNonZeroRateWritesAmountZeroTransaction() throws Exception {
        assertEquals(0, simple("0000000000{", "00150{"));
        List<String> t = lines("TRANSACT.txt");
        assertEquals(1, t.size());
        assertEquals("0000000000{", amt(t.get(0)));
        assertEquals("2022071800000001", t.get(0).substring(0, 16));
    }

    // ---- D1 / AC5 ----
    @Test
    void lastAccountIsNeverRewrittenButItsInterestTransactionIsWritten() throws Exception {
        List<String> before = Files.readAllLines(GOLDEN.resolve("CBTRN02C/ACCTFILE.txt"), StandardCharsets.ISO_8859_1);
        List<String> after = Files.readAllLines(GOLDEN.resolve("CBACT04C-after-CBTRN02C/ACCTFILE.txt"),
                StandardCharsets.ISO_8859_1);
        List<String> trans = Files.readAllLines(GOLDEN.resolve("CBACT04C-after-CBTRN02C/TRANSACT.txt"),
                StandardCharsets.ISO_8859_1);
        int last = before.size() - 1;
        assertTrue(before.get(last).startsWith("00000000050"));
        assertEquals(before.get(last), after.get(last), "acct 50 unchanged (D1)");
        assertEquals(false, before.get(last - 1).equals(after.get(last - 1)), "acct 49 updated");
        String t = trans.get(trans.size() - 1);
        assertTrue(t.startsWith("2022071800000050"));
        assertEquals("0000000187G", amt(t));
    }

    @Test
    void lastAccountDefect_syntheticTwoAccounts() throws Exception {
        assertEquals(0, run(
                List.of(tcat("00000000001", "01", "0001", "0000012000{"),
                        tcat("00000000002", "01", "0001", "0000012000{")),
                List.of(xref("1111111111111111", "00000000001"), xref("2222222222222222", "00000000002")),
                List.of(acct("00000000001", "00000000010{", "00000000005{", "00000000006{", ""),
                        acct("00000000002", "00000000010{", "00000000005{", "00000000006{", "")),
                List.of(disc("DEFAULT", "01", "0001", "00120{"))));
        List<String> a = lines("ACCTOUT.txt");
        assertEquals(2, lines("TRANSACT.txt").size());
        // acct 1: 1200.00*12.00/1200 = 12.00 -> balance 1.00+12.00 = 13.00, cycle fields zeroed
        assertEquals("00000000130{", a.get(0).substring(12, 24));
        assertEquals("00000000000{", a.get(0).substring(78, 90));
        assertEquals("00000000000{", a.get(0).substring(90, 102));
        // acct 2 (last): untouched
        assertEquals("00000000010{", a.get(1).substring(12, 24));
        assertEquals("00000000005{", a.get(1).substring(78, 90));
    }

    // ---- D6 overflow ----
    @Test
    void overflowTruncatesHighOrderDigitsSilently() throws Exception {
        // acct 1 balance 9999999999.99 (S9(10)V99 max) + 12.00 wraps to 11.99; acct 2 only makes acct 1 non-last
        assertEquals(0, run(
                List.of(tcat("00000000001", "01", "0001", "0000012000{"),
                        tcat("00000000002", "01", "0001", "0000000000{")),
                List.of(xref("1111111111111111", "00000000001"), xref("2222222222222222", "00000000002")),
                List.of(acct("00000000001", "99999999999I", "00000000000{", "00000000000{", ""),
                        acct("00000000002", "00000000000{", "00000000000{", "00000000000{", "")),
                List.of(disc("DEFAULT", "01", "0001", "00120{"))));
        assertEquals("00000000119I", lines("ACCTOUT.txt").get(0).substring(12, 24));
    }

    // ---- fallback + abends (AC7) ----
    @Test
    void missingSpecificGroupFallsBackToDefault() throws Exception {
        assertEquals(0, simple("0000012000{", "00120{"));
        assertTrue(sysout.contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
        assertEquals(1, lines("TRANSACT.txt").size());
    }

    @Test
    void missingDefaultGroupAbends() throws Exception {
        int rc = run(List.of(tcat("00000000001", "01", "0001", "0000012000{")),
                List.of(xref("1111111111111111", "00000000001")),
                List.of(acct("00000000001", "00000000010{", "00000000000{", "00000000000{", "")),
                List.of(disc("DEFAULT", "02", "0002", "00120{")));
        assertEquals(16, rc);
        assertTrue(sysout.contains("ERROR READING DEFAULT DISCLOSURE GROUP"));
        assertTrue(sysout.endsWith("ABENDING PROGRAM\n"));
    }

    @Test
    void missingAccountAbends() throws Exception {
        int rc = run(List.of(tcat("00000000009", "01", "0001", "0000012000{")),
                List.of(xref("1111111111111111", "00000000009")),
                List.of(acct("00000000001", "00000000010{", "00000000000{", "00000000000{", "")),
                List.of(disc("DEFAULT", "01", "0001", "00120{")));
        assertEquals(16, rc);
        assertTrue(sysout.contains("ACCOUNT NOT FOUND: 00000000009"));
        assertTrue(sysout.endsWith("ABENDING PROGRAM\n"));
    }

    @Test
    void missingXrefAbends() throws Exception {
        int rc = run(List.of(tcat("00000000001", "01", "0001", "0000012000{")),
                List.of(xref("1111111111111111", "00000000002")),
                List.of(acct("00000000001", "00000000010{", "00000000000{", "00000000000{", "")),
                List.of(disc("DEFAULT", "01", "0001", "00120{")));
        assertEquals(16, rc);
        assertTrue(sysout.contains("ERROR READING XREF FILE"));
        assertTrue(sysout.endsWith("ABENDING PROGRAM\n"));
    }

    @Test
    void duplicateXrefCardsAbend_D7() throws Exception {
        int rc = run(List.of(tcat("00000000001", "01", "0001", "0000012000{")),
                List.of(xref("1111111111111111", "00000000001"), xref("3333333333333333", "00000000001")),
                List.of(acct("00000000001", "00000000010{", "00000000000{", "00000000000{", "")),
                List.of(disc("DEFAULT", "01", "0001", "00120{")));
        assertEquals(16, rc);
        assertTrue(sysout.contains("ERROR READING XREF FILE"));
    }

    @Test
    void emptyTcatbalLeavesAccountsUnchanged() throws Exception {
        String a = acct("00000000001", "00000000010{", "00000000005{", "00000000000{", "");
        assertEquals(0, run(List.of(), List.of(xref("1111111111111111", "00000000001")), List.of(a),
                List.of(disc("DEFAULT", "01", "0001", "00120{"))));
        assertEquals(0, lines("TRANSACT.txt").size());
        assertEquals(List.of(a), lines("ACCTOUT.txt"));
    }
}
