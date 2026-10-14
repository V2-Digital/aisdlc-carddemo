package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins legacy behaviour (including defects) that must NOT be fixed in CR-1. */
class DefectCharacterizationTest {
    @TempDir Path tmp;

    private int run(Path in, Path out) throws IOException {
        return new InterestCalc("2022071800", Fixtures.NOW).run(in, out);
    }

    private Path synth(List<String> tcat, List<String> acct, List<String> xref, List<String> disc) throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        Fixtures.lines(in.resolve("TCATBALF.txt"), tcat);
        Fixtures.lines(in.resolve("ACCTFILE.txt"), acct);
        Fixtures.lines(in.resolve("XREFFILE.txt"), xref);
        Fixtures.lines(in.resolve("DISCGRP.txt"), disc);
        return in;
    }

    private static final String A1 = "00000000001";
    private static final String A2 = "00000000002";
    private static final String CARD = "1111222233334444";

    @Test void D1_lastAccountNotRewritten_interestBookedButBalanceUnchanged() throws IOException {
        Path in = Fixtures.stageInputs(tmp.resolve("in"), true);
        Path out = tmp.resolve("out");
        assertEquals(0, run(in, out));
        List<String> tx = Fixtures.read(out.resolve("TRANSACT.txt"));
        String last = tx.get(tx.size() - 1);
        assertTrue(last.contains("Int. for a/c 00000000050"));
        assertEquals("0000000187G", last.substring(132, 143));
        String inAcct50 = Fixtures.read(in.resolve("ACCTFILE.txt")).stream()
                .filter(l -> l.startsWith("00000000050")).findFirst().orElseThrow();
        String outAcct50 = Fixtures.read(out.resolve("ACCTFILE.txt")).stream()
                .filter(l -> l.startsWith("00000000050")).findFirst().orElseThrow();
        assertEquals(inAcct50, outAcct50);
    }

    @Test void D1_otherAccountsAreRewritten() throws IOException {
        Path in = synth(
                List.of(Fixtures.tcat(A1, "01", "0001", "0000000100{"), Fixtures.tcat(A2, "01", "0001", "0000000100{")),
                List.of(Fixtures.acct(A1, "00000001000{", "00000000005A", "00000000005B", ""),
                        Fixtures.acct(A2, "00000001000{", "00000000005A", "00000000005B", "")),
                List.of(Fixtures.xref(CARD, A1), Fixtures.xref(CARD, A2)),
                List.of(Fixtures.disc("DEFAULT", "01", "0001", "00120{")));
        // 10.00 * 12.00 / 1200 = 0.10; balance 100.00 -> 100.10
        assertEquals(0, run(in, tmp.resolve("out")));
        List<String> a = Fixtures.read(tmp.resolve("out/ACCTFILE.txt"));
        assertEquals("00000001001{", a.get(0).substring(12, 24));
        assertEquals("00000000000{", a.get(0).substring(78, 90));
        assertEquals("00000000000{", a.get(0).substring(90, 102));
        assertEquals(Fixtures.acct(A2, "00000001000{", "00000000005A", "00000000005B", ""), a.get(1));
    }

    @Test void D4_defaultFallbackPrintsBothMessagesAndUsesDefaultRate() throws IOException {
        Path in = synth(
                List.of(Fixtures.tcat(A1, "01", "0001", "0000000100{"), Fixtures.tcat(A2, "01", "0001", "0000000000{")),
                List.of(Fixtures.acct(A1, "00000000000{", "00000000000{", "00000000000{", "GRP1"),
                        Fixtures.acct(A2, "00000000000{", "00000000000{", "00000000000{", "")),
                List.of(Fixtures.xref(CARD, A1), Fixtures.xref(CARD, A2)),
                List.of(Fixtures.disc("DEFAULT", "01", "0001", "00120{")));
        Path out = tmp.resolve("out");
        assertEquals(0, run(in, out));
        List<String> s = Fixtures.read(out.resolve("SYSOUT.txt"));
        // A1 has group GRP1 (no DISCGRP row) -> fallback; A2 has blank group -> also DEFAULT lookup misses first
        assertEquals(8, s.size());
        assertEquals("DISCLOSURE GROUP RECORD MISSING", s.get(2));
        assertEquals("TRY WITH DEFAULT GROUP CODE", s.get(3));
        List<String> tx = Fixtures.read(out.resolve("TRANSACT.txt"));
        assertEquals(2, tx.size()); // zero balance at a non-zero rate still writes a 0.00 txn
        assertEquals("0000000001{", tx.get(0).substring(132, 143)); // 10.00*12.00/1200 = 0.10
    }

    @Test void zeroRate_noTransaction_cyclesZeroed_balanceUnchanged() throws IOException {
        Path in = synth(
                List.of(Fixtures.tcat(A1, "03", "0001", "0000000100{"), Fixtures.tcat(A2, "03", "0001", "0000000100{")),
                List.of(Fixtures.acct(A1, "00000000500{", "00000000005A", "00000000005B", ""),
                        Fixtures.acct(A2, "00000000500{", "00000000005A", "00000000005B", "")),
                List.of(Fixtures.xref(CARD, A1), Fixtures.xref(CARD, A2)),
                List.of(Fixtures.disc("DEFAULT", "03", "0001", "00000{")));
        Path out = tmp.resolve("out");
        assertEquals(0, run(in, out));
        assertEquals(0, Files.size(out.resolve("TRANSACT.txt")));
        List<String> a = Fixtures.read(out.resolve("ACCTFILE.txt"));
        assertEquals(Fixtures.acct(A1, "00000000500{", "00000000000{", "00000000000{", ""), a.get(0));
        assertEquals(Fixtures.acct(A2, "00000000500{", "00000000005A", "00000000005B", ""), a.get(1)); // last, D1
    }

    private void abends(Path in, String... tail) throws IOException {
        Path out = tmp.resolve("out");
        assertEquals(16, run(in, out));
        List<String> s = Fixtures.read(out.resolve("SYSOUT.txt"));
        assertEquals("ABENDING PROGRAM", s.get(s.size() - 1));
        for (int i = 0; i < tail.length; i++) {
            assertEquals(tail[i], s.get(s.size() - 1 - tail.length + i));
        }
    }

    @Test void E1_missingAccount() throws IOException {
        abends(synth(List.of(Fixtures.tcat(A1, "01", "0001", "0000000100{")), List.of(Fixtures.acct(A2, "00000000000{", "00000000000{", "00000000000{", "")),
                List.of(Fixtures.xref(CARD, A1)), List.of(Fixtures.disc("DEFAULT", "01", "0001", "00120{"))),
                "ACCOUNT NOT FOUND: " + A1, "ERROR READING ACCOUNT FILE", "FILE STATUS IS: NNNN0023");
    }

    @Test void E2_missingXref() throws IOException {
        abends(synth(List.of(Fixtures.tcat(A1, "01", "0001", "0000000100{")), List.of(Fixtures.acct(A1, "00000000000{", "00000000000{", "00000000000{", "")),
                List.of(Fixtures.xref(CARD, A2)), List.of(Fixtures.disc("DEFAULT", "01", "0001", "00120{"))),
                "ACCOUNT NOT FOUND: " + A1, "ERROR READING XREF FILE", "FILE STATUS IS: NNNN0023");
    }

    @Test void E3_missingDefaultGroup() throws IOException {
        abends(synth(List.of(Fixtures.tcat(A1, "01", "0001", "0000000100{")), List.of(Fixtures.acct(A1, "00000000000{", "00000000000{", "00000000000{", "")),
                List.of(Fixtures.xref(CARD, A1)), List.of(Fixtures.disc("DEFAULT", "02", "0001", "00120{"))),
                "DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE", "ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0023");
    }

    @Test void E4_missingInputFile() throws IOException {
        Path in = Files.createDirectories(tmp.resolve("in"));
        assertEquals(16, run(in, tmp.resolve("out")));
        assertFalse(Files.exists(tmp.resolve("out/ACCTFILE.txt")));
    }

    @Test void emptyTcatbal_acctfileUnchanged() throws IOException {
        String acct = Fixtures.acct(A1, "00000000500{", "00000000005A", "00000000005B", "");
        Path in = synth(List.of(), List.of(acct), List.of(Fixtures.xref(CARD, A1)), List.of(Fixtures.disc("DEFAULT", "01", "0001", "00120{")));
        Path out = tmp.resolve("out");
        assertEquals(0, run(in, out));
        assertEquals(0, Files.size(out.resolve("TRANSACT.txt")));
        assertEquals(List.of(acct), Fixtures.read(out.resolve("ACCTFILE.txt")));
        assertEquals(2, Fixtures.read(out.resolve("SYSOUT.txt")).size());
    }
}
