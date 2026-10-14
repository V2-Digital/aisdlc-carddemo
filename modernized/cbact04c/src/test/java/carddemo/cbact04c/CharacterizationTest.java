package carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins legacy behaviour, including defects. Names say which defect. */
class CharacterizationTest {
    private static final String A1 = "00000000001", A2 = "00000000002";
    private static final String CARD = "1111222233334444";
    private static final int AMT = 132;
    private static final String ZERO = "0000000000{";

    private static Map<String, String> base(String tcat, String accts, String disc) {
        Map<String, String> m = new HashMap<>();
        m.put("TCATBALF", tcat);
        m.put("XREFFILE", Fx.xref(CARD, A1) + Fx.xref("5555666677778888", A2));
        m.put("ACCTFILE", accts);
        m.put("DISCGRP", disc);
        return m;
    }

    private static String defRate(String rate) { return Fx.disc("DEFAULT", "01", "0001", rate); }

    @Test
    void d1_lastAccountNotRewritten_butItsTranIsWritten(@TempDir Path tmp) {
        String accts = Fx.acct(A1, "00000010000{", "00000000500{", "00000000200}", "")
                + Fx.acct(A2, "00000020000{", "00000000300{", "00000000100}", "");
        String tcat = Fx.tcat(A1, "01", "0001", "0000010000{") + Fx.tcat(A2, "01", "0001", "0000010000{");
        Fx.Result r = Fx.run(tmp, base(tcat, accts, defRate("00120{")));
        assertEquals(0, r.rc());
        assertEquals(2, r.lines("TRANSACT").size());
        // acct 1: balance + 1000.00*12/1200 = 10.00, cycle reset
        assertEquals("00000010100{", r.lines("ACCTFILE").get(0).substring(12, 24));
        assertEquals("000000000000", r.lines("ACCTFILE").get(0).substring(75, 87).replace("{", "0"));
        // acct 2 is the last one: untouched, though its tran was posted
        assertEquals(accts.lines().toList().get(1), r.lines("ACCTFILE").get(1));
        assertTrue(r.lines("TRANSACT").get(1).contains("Int. for a/c " + A2));
    }

    @Test
    void d3_interestTruncatesNotRounds(@TempDir Path tmp) {
        // 1164.87 * 15.00 / 1200 = 14.5609 -> 14.56 ; 1000.99 * 15 / 1200 = 12.5123 -> 12.51 ; 0.99*? see below
        String accts = Fx.acct(A1, ZERO, ZERO, ZERO, "") + Fx.acct(A2, ZERO, ZERO, ZERO, "");
        String tcat = Fx.tcat(A1, "01", "0001", "0000011648G") + Fx.tcat(A2, "01", "0001", "0000000007G");
        Fx.Result r = Fx.run(tmp, base(tcat, accts, defRate("00150{")));
        assertEquals("0000000145F", r.lines("TRANSACT").get(0).substring(AMT, AMT + 11));
        // 0.77 * 15 / 1200 = 0.009625 -> 0.00 (rounding would give 0.01)
        assertEquals(ZERO, r.lines("TRANSACT").get(1).substring(AMT, AMT + 11));
    }

    @Test
    void d3_negativeBalanceGivesNegativeTruncatedTowardZero(@TempDir Path tmp) {
        // -1000.99 * 10.00 / 1200 = -8.3415 -> -8.34
        String accts = Fx.acct(A1, ZERO, ZERO, ZERO, "") + Fx.acct(A2, ZERO, ZERO, ZERO, "");
        String tcat = Fx.tcat(A1, "01", "0001", "0000010009R") + Fx.tcat(A2, "01", "0001", "0000000000{");
        Fx.Result r = Fx.run(tmp, base(tcat, accts, defRate("00100{")));
        assertEquals("0000000083M", r.lines("TRANSACT").get(0).substring(AMT, AMT + 11));
        // acct 1 (not last) gets -8.34 added to 0
        assertEquals("00000000083M", r.lines("ACCTFILE").get(0).substring(12, 24));
    }

    @Test
    void zeroRateProducesNoTranAndNoInterest(@TempDir Path tmp) {
        String accts = Fx.acct(A1, "00000010000{", ZERO, ZERO, "") + Fx.acct(A2, ZERO, ZERO, ZERO, "");
        String tcat = Fx.tcat(A1, "01", "0001", "0000010000{") + Fx.tcat(A2, "01", "0001", "0000010000{");
        Fx.Result r = Fx.run(tmp, base(tcat, accts, defRate("00000{")));
        assertEquals(0, r.rc());
        assertEquals(0, r.lines("TRANSACT").size());
        assertEquals("00000010000{", r.lines("ACCTFILE").get(0).substring(12, 24));
    }

    @Test
    void defaultFallbackPrintsBothMessages(@TempDir Path tmp) {
        String accts = Fx.acct(A1, ZERO, ZERO, ZERO, "");
        Fx.Result r = Fx.run(tmp, base(Fx.tcat(A1, "01", "0001", "0000010000{"), accts, defRate("00120{")));
        assertEquals(0, r.rc());
        assertTrue(r.sysout().contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
        assertEquals(1, r.lines("TRANSACT").size());
    }

    @Test
    void groupRateHit_usesAccountGroupNotDefault(@TempDir Path tmp) {
        // the "00" path: not covered by golden because sample accounts have a blank group
        String accts = Fx.acct(A1, ZERO, ZERO, ZERO, "GOLD");
        String disc = Fx.disc("GOLD", "01", "0001", "00240{") + defRate("00100{");
        Fx.Result r = Fx.run(tmp, base(Fx.tcat(A1, "01", "0001", "0000010000{"), accts, disc));
        assertEquals(0, r.rc());
        assertTrue(!r.sysout().contains("DISCLOSURE GROUP RECORD MISSING"));
        // 1000.00 * 24 / 1200 = 20.00
        assertEquals("0000000200{", r.lines("TRANSACT").get(0).substring(AMT, AMT + 11));
    }

    @Test
    void missingAccountAbends(@TempDir Path tmp) {
        Fx.Result r = Fx.run(tmp, base(Fx.tcat("00000000009", "01", "0001", ZERO), Fx.acct(A1, ZERO, ZERO, ZERO, ""), defRate("00120{")));
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("ACCOUNT NOT FOUND: 00000000009\nERROR READING ACCOUNT FILE\n"));
        assertTrue(r.sysout().endsWith("ABENDING PROGRAM\n"));
    }

    @Test
    void missingXrefAbends(@TempDir Path tmp) {
        Map<String, String> m = base(Fx.tcat("00000000009", "01", "0001", ZERO), Fx.acct("00000000009", ZERO, ZERO, ZERO, ""), defRate("00120{"));
        Fx.Result r = Fx.run(tmp, m);
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("ERROR READING XREF FILE"));
        assertTrue(r.sysout().endsWith("ABENDING PROGRAM\n"));
    }

    @Test
    void missingDefaultRateAbends(@TempDir Path tmp) {
        Fx.Result r = Fx.run(tmp, base(Fx.tcat(A1, "01", "0001", ZERO), Fx.acct(A1, ZERO, ZERO, ZERO, ""), Fx.disc("DEFAULT", "02", "0002", "00120{")));
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("TRY WITH DEFAULT GROUP CODE\nERROR READING DEFAULT DISCLOSURE GROUP\n"));
        assertTrue(r.sysout().endsWith("ABENDING PROGRAM\n"));
    }

    @Test
    void emptyTcatbalIsTwoLinesRc0(@TempDir Path tmp) {
        Fx.Result r = Fx.run(tmp, base("", Fx.acct(A1, ZERO, ZERO, ZERO, ""), defRate("00120{")));
        assertEquals(0, r.rc());
        assertEquals(2, r.sysout().lines().count());
        assertEquals(0, r.lines("TRANSACT").size());
    }
}
