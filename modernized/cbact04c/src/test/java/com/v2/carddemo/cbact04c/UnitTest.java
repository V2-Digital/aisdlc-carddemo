package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class UnitTest {
    // ---- Zoned
    @Test
    void zonedRoundTrip() {
        for (String s : new String[] {"0000000145F", "0000000187G", "0000000000{", "0000000019R", "0000000012}".replace('}', 'J')}) {
            assertEquals(s, Zoned.encode(Zoned.decode(s), 11));
        }
        assertEquals(new BigDecimal("-1.99"), Zoned.decode("0000000019R"));
        assertEquals("0000000000{", Zoned.encode(new BigDecimal("-0.00"), 11));
        assertEquals("0000000000J", Zoned.encode(new BigDecimal("-0.01"), 11));
    }

    // ---- AC4 interest truncation
    /** D2 characterization: 999.00 * 15% / 12 = 12.4875 -> truncated 12.48 (HALF_UP would be 12.49). */
    @Test
    void interestTruncatedNotRounded_D2() throws IOException {
        Fx fx = Fx.basic("001500{").tcat("00000000001", "01", "0001", "0000009990{")
                .tcat("00000000002", "01", "0001", "0000009990{")
                .xref("1111111111111111", "00000000001").xref("2222222222222222", "00000000002")
                .acct("00000000001", "00000000100{", "").acct("00000000002", "00000000100{", "").run();
        assertEquals(0, fx.rc);
        assertEquals("0000000124H", fx.trans().get(0).substring(132, 143)); // 12.48; HALF_UP would give 12.49 (I)
    }

    /** Negative balance truncates toward zero; -0.01 -> -0.00 written as positive zero. */
    @Test
    void negativeInterestTruncatesTowardZero() throws IOException {
        Fx fx = Fx.basic("001500{").tcat("00000000001", "01", "0001", "0000000000J")
                .tcat("00000000002", "01", "0001", "0000009990}")
                .xref("1111111111111111", "00000000001").xref("2222222222222222", "00000000002")
                .acct("00000000001", "00000000100{", "").acct("00000000002", "00000000100{", "").run();
        assertEquals("0000000000{", fx.trans().get(0).substring(132, 143));
        assertEquals("0000000124Q", fx.trans().get(1).substring(132, 143)); // -12.48
    }

    // ---- AC5 D1
    /** D1 characterization: last account is never rewritten, but its interest transaction is. */
    @Test
    void lastAccountNotRewritten_D1() throws IOException {
        Fx fx = Fx.basic("001500{").tcat("00000000001", "01", "0001", "0000009990{")
                .tcat("00000000002", "01", "0001", "0000009990{")
                .xref("1111111111111111", "00000000001").xref("2222222222222222", "00000000002")
                .acct("00000000001", "00000000100{", "").acct("00000000002", "00000000100{", "").run();
        List<String> a = fx.accts();
        assertNotEquals(fx.acct.get(0), a.get(0));
        assertEquals(Zoned.encode(new BigDecimal("22.48"), 12), a.get(0).substring(12, 24));
        assertEquals(fx.acct.get(1), a.get(1), "last account must be byte-unchanged (D1)");
        assertEquals(2, fx.trans().size());
        assertEquals("0000000124H", fx.trans().get(1).substring(132, 143));
    }

    @Test
    void goldenAccount50UnchangedAndTranAmount() throws IOException {
        var g = Fx.ROOT.resolve("legacy/golden");
        var before = FixedRecords.read(g.resolve("CBTRN02C/ACCTFILE.txt"), 300);
        var after = FixedRecords.read(g.resolve("CBACT04C-after-CBTRN02C/ACCTFILE.txt"), 300);
        assertEquals(before.get(49), after.get(49));
        assertTrue(after.get(49).startsWith("00000000050"));
        var t = FixedRecords.read(g.resolve("CBACT04C-after-CBTRN02C/TRANSACT.txt"), 350);
        assertTrue(t.get(49).startsWith("2022071800000050"));
        assertEquals("0000000187G", t.get(49).substring(132, 143));
    }

    // ---- AC6 rates
    @Test
    void defaultFallbackAndSysout() throws IOException {
        Fx fx = Fx.basic("001500{").tcat("00000000001", "01", "0001", "0000009990{")
                .xref("1111111111111111", "00000000001").acct("00000000001", "00000000100{", "").run();
        assertEquals(0, fx.rc);
        assertTrue(fx.sysout.contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
        assertTrue(fx.sysout.endsWith("END OF EXECUTION OF PROGRAM CBACT04C\n"));
    }

    @Test
    void specificGroupHitNoFallback() throws IOException {
        Fx fx = Fx.basic("001500{").disc("GOLD", "01", "0001", "002400{")
                .tcat("00000000001", "01", "0001", "0000012000{")
                .xref("1111111111111111", "00000000001").acct("00000000001", "00000000100{", "GOLD").run();
        assertFalse(fx.sysout.contains("MISSING"));
        assertEquals("0000000240{", fx.trans().get(0).substring(132, 143)); // 120.00*24/1200
    }

    @Test
    void rateZeroNoTransactionNoSuffixButAccountRewritten() throws IOException {
        Fx fx = Fx.basic("001500{").disc("DEFAULT", "02", "0001", "000000{")
                .tcat("00000000001", "02", "0001", "0000009990{")
                .tcat("00000000002", "01", "0001", "0000009990{")
                .tcat("00000000003", "01", "0001", "0000009990{")
                .xref("1111111111111111", "00000000001").xref("2222222222222222", "00000000002")
                .xref("3333333333333333", "00000000003")
                .acct("00000000001", "00000000100{", "").acct("00000000002", "00000000100{", "")
                .acct("00000000003", "00000000100{", "").run();
        List<String> t = fx.trans();
        assertEquals(2, t.size());
        assertEquals("2022071800000001", t.get(0).substring(0, 16)); // suffix not consumed by rate-0 record
        List<String> a = fx.accts();
        assertEquals(Zoned.encode(new BigDecimal("10.00"), 12), a.get(0).substring(12, 24));
        assertEquals("000000000000", a.get(0).substring(78, 90).replace('{', '0'));
        assertEquals("000000000000", a.get(0).substring(90, 102).replace('{', '0'));
        assertEquals("{", a.get(0).substring(89, 90));
        assertNotEquals(fx.acct.get(0), a.get(0));
    }

    // ---- AC7 abends
    @Test
    void abendMissingAccount() throws IOException {
        Fx fx = Fx.basic("001500{").tcat("00000000009", "01", "0001", "0000009990{")
                .tcat("00000000001", "01", "0001", "0000009990{")
                .xref("1111111111111111", "00000000001").acct("00000000001", "00000000100{", "").run();
        assertEquals(16, fx.rc);
        assertTrue(fx.sysout.contains("ACCOUNT NOT FOUND: 00000000009\nERROR READING ACCOUNT FILE\nFILE STATUS IS: NNNN0023\n"));
        assertTrue(fx.sysout.endsWith("ABENDING PROGRAM\n"));
        assertEquals(0, fx.trans().size());
    }

    @Test
    void abendMissingXrefStopsProcessing() throws IOException {
        Fx fx = Fx.basic("001500{").tcat("00000000001", "01", "0001", "0000009990{")
                .tcat("00000000002", "01", "0001", "0000009990{")
                .tcat("00000000003", "01", "0001", "0000009990{")
                .xref("1111111111111111", "00000000001").xref("3333333333333333", "00000000003")
                .acct("00000000001", "00000000100{", "").acct("00000000002", "00000000100{", "")
                .acct("00000000003", "00000000100{", "").run();
        assertEquals(16, fx.rc);
        assertTrue(fx.sysout.contains("ERROR READING XREF FILE"));
        assertEquals(1, fx.trans().size());
        assertFalse(fx.sysout.contains("00000000003"));
    }

    @Test
    void abendMissingDefaultGroup() throws IOException {
        Fx fx = new Fx().disc("DEFAULT", "01", "0001", "001500{")
                .tcat("00000000001", "05", "0009", "0000009990{")
                .xref("1111111111111111", "00000000001").acct("00000000001", "00000000100{", "").run();
        assertEquals(16, fx.rc);
        assertTrue(fx.sysout.contains("ERROR READING DEFAULT DISCLOSURE GROUP\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"));
        assertEquals(0, fx.trans().size());
    }

    @Test
    void missingInputFileAbends_D6MessageText() throws IOException {
        var in = java.nio.file.Files.createTempDirectory("in");
        FixedRecords.write(in.resolve("TCATBALF.txt"), List.of());
        FixedRecords.write(in.resolve("XREFFILE.txt"), List.of());
        FixedRecords.write(in.resolve("ACCTFILE.txt"), List.of());
        Fx fx = new Fx().run(in, java.nio.file.Files.createTempDirectory("out"));
        assertEquals(16, fx.rc);
        assertTrue(fx.sysout.contains("ERROR OPENING DALY REJECTS FILE"));
    }

    @Test
    void emptyTcatbalLeavesAccountsUnchanged() throws IOException {
        Fx fx = Fx.basic("001500{").xref("1111111111111111", "00000000001")
                .acct("00000000001", "00000000100{", "").run();
        assertEquals(0, fx.rc);
        assertEquals(0, fx.trans().size());
        assertEquals(fx.acct, fx.accts());
    }

    // ---- AC8 tran record
    @Test
    void tranRecordLayoutAndCounterAcrossAccounts() throws IOException {
        Fx fx = Fx.basic("001500{").tcat("00000000001", "01", "0001", "0000009990{")
                .tcat("00000000002", "01", "0001", "0000009990{")
                .xref("1111111111111111", "00000000001").xref("2222222222222222", "00000000002")
                .acct("00000000001", "00000000100{", "").acct("00000000002", "00000000100{", "").run();
        List<String> t = fx.trans();
        assertEquals("2022071800000001", t.get(0).substring(0, 16));
        assertEquals("2022071800000002", t.get(1).substring(0, 16));
        String r = t.get(0);
        assertEquals(350, r.length());
        assertEquals("010005System    ", r.substring(16, 32));
        assertEquals(FixedRecords.pad("Int. for a/c 00000000001", 100), r.substring(32, 132));
        assertEquals("000000000", r.substring(143, 152));
        assertEquals(" ".repeat(110), r.substring(152, 262));
        assertEquals("1111111111111111", r.substring(262, 278));
        assertEquals("2022-07-18-00.00.00.000000", r.substring(278, 304));
        assertEquals("2022-07-18-00.00.00.000000", r.substring(304, 330));
        assertEquals(" ".repeat(20), r.substring(330));
    }

    /** D3 characterization: suffix PIC 9(6) wraps -> duplicate TRAN-IDs. Needs 1,000,000 records. */
    @Test
    void suffixWrapsAt999999_D3() throws IOException {
        Fx fx = Fx.basic("001500{").xref("1111111111111111", "00000000001").acct("00000000001", "00000000100{", "");
        for (int i = 0; i < 1_000_001; i++) {
            fx.tcat("00000000001", "01", "0001", "0000000100{");
        }
        fx.run();
        List<String> t = fx.trans();
        assertEquals("2022071800999999", t.get(999_998).substring(0, 16));
        assertEquals("2022071800000000", t.get(999_999).substring(0, 16));
        assertEquals("2022071800000001", t.get(1_000_000).substring(0, 16));
    }

    /** D3 characterization: high-order digits silently lost on overflow. */
    @Test
    void amountOverflowTruncatesHighOrder_D3() throws IOException {
        // 999999999.99 * 9999.99 / 1200 = 8333324999.9... -> mod 10^9 (cents mod 10^11)
        Fx fx = Fx.basic("999999{").tcat("00000000001", "01", "0001", "9999999999G")
                .xref("1111111111111111", "00000000001").acct("00000000001", "00000000100{", "").run();
        BigDecimal exact = new BigDecimal("999999999.97").multiply(new BigDecimal("9999.99"))
                .divide(BigDecimal.valueOf(1200), 2, java.math.RoundingMode.DOWN);
        String expect = Zoned.encode(exact, 11);
        assertEquals(expect, fx.trans().get(0).substring(132, 143));
        assertNotEquals(exact.setScale(2).unscaledValue().toString().length(), 11);
    }
}
