package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.Fx.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterization (D1-D4, D6, D7, D8), rate lookup, tran id and abend tests on small fixtures. */
class BehaviourTest {
    @TempDir Path tmp;

    static final String P = "2022071800";
    static final List<String> DISC = List.of(disc("DEFAULT", "01", "0001", "00150{"), disc("DEFAULT", "03", "0001", "00000{"));
    static final List<String> X = List.of(xref("1111111111111111", "00000000001"), xref("2222222222222222", "00000000002"));
    static final List<String> A = List.of(acct("0000000000" + "1", "00000010000{", ""), acct("00000000002", "00000010000{", ""));

    // two accounts so that account 1 is rewritten; account 2 is the (never rewritten) last one
    Fx two(String bal1) throws IOException {
        return new Fx(tmp).run(P, List.of(cat("00000000001", "01", "0001", bal1), cat("00000000002", "03", "0001", "0000000000{")), X, A, DISC);
    }

    @Test void defectD2_truncatesNotRounds() throws IOException {
        Fx fx = two("0000000004{"); // 0.40 @ 15% = 0.005
        assertEquals(0, fx.rc);
        assertEquals("0000000000{", fx.tran().get(0).substring(132, 143));
    }

    @Test void defectD3_negativeInterestIsWritten() throws IOException {
        Fx fx = two("0000001000}"); // -100.00 @ 15% = -1.25
        assertEquals("0000000012N", fx.tran().get(0).substring(132, 143));
        assertEquals("00000009987E", fx.acct().get(0).substring(12, 24)); // 1000.00 - 1.25 = 998.75
    }

    @Test void defectD4_zeroAmountTransactionWritten() throws IOException {
        Fx fx = two("0000000000{");
        assertEquals(1, fx.tran().size());
        assertEquals("0000000000{", fx.tran().get(0).substring(132, 143));
    }

    @Test void defectD1_lastAccountNotRewritten() throws IOException {
        Fx fx = two("0000011648G");
        assertEquals(A.get(1), fx.acct().get(1));
        assertNotEquals(A.get(0), fx.acct().get(0));
        assertEquals("00000000000{00000000000{", fx.acct().get(0).substring(78, 102));
    }

    @Test void defectD1_singleAccountNeverRewritten() throws IOException {
        Fx fx = new Fx(tmp).run(P, List.of(cat("00000000001", "01", "0001", "0000011648G")), X, A, DISC);
        assertEquals(A, fx.acct());
        assertEquals(1, fx.tran().size());
    }

    @Test void defectD6_interestWrapsToNineDigits() throws IOException {
        List<String> d = List.of(disc("DEFAULT", "01", "0001", "99999I"), disc("DEFAULT", "03", "0001", "00000{"));
        Fx fx = new Fx(tmp).run(P, List.of(cat("00000000001", "01", "0001", "9999999999I"), cat("00000000002", "03", "0001", "0000000000{")), X, A, d);
        // 999999999.99 * 9999.99 / 1200 = 8333324999.9 -> exact, truncated, then low 9 integer digits kept
        java.math.BigDecimal exact = new java.math.BigDecimal("999999999.99").multiply(new java.math.BigDecimal("9999.99"))
                .divide(java.math.BigDecimal.valueOf(1200), 2, java.math.RoundingMode.DOWN);
        assertTrue(exact.compareTo(new java.math.BigDecimal("1000000000")) >= 0, "fixture must overflow S9(9)V99");
        assertEquals(Zoned.encode(exact, 11), fx.tran().get(0).substring(132, 143));
        assertNotEquals(exact.setScale(2).unscaledValue().toString().length(), 11);
    }

    @Test void defectD7_firstXrefCardUsed() throws IOException {
        List<String> x = List.of(xref("1111111111111111", "00000000001"), xref("3333333333333333", "00000000001"), X.get(1));
        Fx fx = new Fx(tmp).run(P, List.of(cat("00000000001", "01", "0001", "0000011648G"), cat("00000000002", "03", "0001", "0000000000{")), x, A, DISC);
        assertEquals("1111111111111111", fx.tran().get(0).substring(262, 278));
    }

    @Test void defectD8_discgrpOpenErrorText() throws IOException {
        Fx fx = new Fx(tmp);
        fx.run(P, fx.w("t", List.of()), fx.w("x", X), fx.w("a", A), tmp.resolve("missing"));
        assertEquals(16, fx.rc);
        assertTrue(fx.out.contains("ERROR OPENING DALY REJECTS FILE"));
    }

    @Test void fallbackToDefault() throws IOException {
        Fx fx = new Fx(tmp).run(P, List.of(cat("00000000001", "01", "0001", "0000011648G"), cat("00000000002", "03", "0001", "0000000000{")),
                X, List.of(acct("00000000001", "00000010000{", "GRP1"), A.get(1)), DISC);
        assertEquals(0, fx.rc);
        assertTrue(fx.out.contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
        assertEquals("0000000145F", fx.tran().get(0).substring(132, 143));
    }

    @Test void groupSpecificRateIsUsedWhenPresent() throws IOException {
        List<String> d = List.of(disc("GRP1", "01", "0001", "00300{"), disc("DEFAULT", "01", "0001", "00150{"));
        Fx fx = new Fx(tmp).run(P, List.of(cat("00000000001", "01", "0001", "0000011648G"), cat("00000000002", "01", "0001", "0000000000{")),
                X, List.of(acct("00000000001", "00000010000{", "GRP1"), acct("00000000002", "00000010000{", "GRP1")), d);
        assertFalse(fx.out.contains("MISSING"));
        assertEquals("0000000291B", fx.tran().get(0).substring(132, 143)); // 29.12
    }

    @Test void defaultMissingExit16() throws IOException {
        Fx fx = new Fx(tmp).run(P, List.of(cat("00000000001", "01", "0001", "0000011648G")), X, A, List.of(disc("DEFAULT", "03", "0001", "00000{")));
        assertEquals(16, fx.rc);
        assertTrue(fx.out.contains("ERROR READING DEFAULT DISCLOSURE GROUP"));
        assertTrue(fx.out.contains("ABENDING PROGRAM"));
        assertTrue(fx.err.contains("CEE3ABD"));
    }

    @Test void zeroRateRewritesNoTx() throws IOException {
        Fx fx = new Fx(tmp).run(P, List.of(cat("00000000001", "03", "0001", "0000011648G"), cat("00000000002", "01", "0001", "0000011648G")), X, A, DISC);
        assertEquals(1, fx.tran().size());
        assertTrue(fx.tran().get(0).contains("Int. for a/c 00000000002"));
        assertEquals("00000000000{00000000000{", fx.acct().get(0).substring(78, 102));
        assertEquals(A.get(1), fx.acct().get(1));
    }

    @Test void tranIdsContiguousAcrossAccountsAndFillerBlank() throws IOException {
        List<String> b = List.of(cat("00000000001", "01", "0001", "0000011648G"), cat("00000000002", "01", "0001", "0000011648G"),
                cat("00000000002", "03", "0001", "0000000000{"));
        Fx fx = new Fx(tmp).run(P, b, X, A, DISC);
        assertEquals(P + "000001", fx.tran().get(0).substring(0, 16));
        assertEquals(P + "000002", fx.tran().get(1).substring(0, 16));
        for (String t : fx.tran()) {
            assertEquals(350, t.length());
            assertEquals(" ".repeat(20), t.substring(330));
            assertEquals(" ".repeat(100 - 24), t.substring(32 + 24, 132));
            assertEquals("2022-07-18-00.00.00.000000", t.substring(278, 304));
        }
    }

    @Test void defectD6_suffixWrapsAfter999999() throws IOException {
        // 1,000,001 balance rows on one account would be huge; instead drive via many rows (cheap: 1M x 50B)
        StringBuilder sb = new StringBuilder();
        int n = 1_000_001;
        String r = cat("00000000001", "01", "0001", "0000000001{") + "\n";
        sb.append(r.repeat(n));
        sb.append(cat("00000000002", "03", "0001", "0000000000{")).append("\n");
        Path t = tmp.resolve("big");
        try { Files.writeString(t, sb, java.nio.charset.StandardCharsets.ISO_8859_1); } catch (IOException e) { throw new RuntimeException(e); }
        Fx fx = new Fx(tmp);
        fx.run(P, t, fx.w("x", X), fx.w("a", A), fx.w("d", DISC));
        List<String> tr = fx.tran();
        assertEquals(n, tr.size());
        assertEquals(P + "999999", tr.get(999_998).substring(0, 16));
        assertEquals(P + "000000", tr.get(999_999).substring(0, 16));
        assertEquals(P + "000001", tr.get(1_000_000).substring(0, 16));
    }

    @Test void abendMissingAccount() throws IOException {
        Fx fx = new Fx(tmp).run(P, List.of(cat("00000000009", "01", "0001", "0000011648G")), X, A, DISC);
        assertEquals(16, fx.rc);
        assertTrue(fx.out.contains("ACCOUNT NOT FOUND: 00000000009"));
    }

    @Test void abendMissingXref() throws IOException {
        Fx fx = new Fx(tmp).run(P, List.of(cat("00000000003", "01", "0001", "0000011648G")), X, List.of(acct("00000000003", "00000010000{", "")), DISC);
        assertEquals(16, fx.rc);
        assertTrue(fx.out.contains("ERROR READING XREF FILE"));
    }

    @Test void abendMissingFile() throws IOException {
        Fx fx = new Fx(tmp);
        fx.run(P, tmp.resolve("nope"), fx.w("x", X), fx.w("a", A), fx.w("d", DISC));
        assertEquals(16, fx.rc);
    }

    @Test void abendBadParm() throws IOException {
        for (String p : new String[] {"", "202207180", "20220718000"}) {
            Fx fx = new Fx(tmp).run(p, List.of(), X, A, DISC);
            assertEquals(16, fx.rc, p);
            assertTrue(fx.out.contains("INVALID PARM: EXPECTED 10 CHARACTERS"));
        }
    }

    @Test void emptyTcatbalIsRc0AndUnchanged() throws IOException {
        Fx fx = new Fx(tmp).run(P, List.of(), X, A, DISC);
        assertEquals(0, fx.rc);
        assertEquals(A, fx.acct());
        assertEquals(List.of(), fx.tran());
    }
}
