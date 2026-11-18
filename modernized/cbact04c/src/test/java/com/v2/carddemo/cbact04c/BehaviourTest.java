package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Interest, zero-rate, characterization (D1-D3, D6, D7) and abend tests. */
class BehaviourTest {
    @TempDir Path tmp;

    private Fx base() {
        return new Fx(tmp).xref("4000000000000001", "00000000001").xref("4000000000000002", "00000000002")
                .disc("DEFAULT", "01", "0001", "15.00").disc("DEFAULT", "03", "0001", "0.00")
                .acct("00000000001", "100.00").acct("00000000002", "200.00");
    }


    // ---- InterestTest
    @Test
    void interestTruncatedNotRounded() throws IOException {
        Cbact04c c = Fx.run(base().tcat("00000000001", "01", "0001", "2339.97").tcat("00000000002", "01", "0001", "0").write());
        assertEquals(new BigDecimal("29.24"), Fx.amt(c.transact().get(0)));
        assertEquals(0, c.rc());
    }

    @Test
    void negativeBalanceTruncatesTowardZero() throws IOException {
        Cbact04c c = Fx.run(base().tcat("00000000001", "01", "0001", "-2339.97").tcat("00000000002", "01", "0001", "0").write());
        assertEquals(new BigDecimal("-29.24"), Fx.amt(c.transact().get(0)));
    }

    @Test
    void tinyNegativeInterestIsPositiveZero() throws IOException {
        Cbact04c c = Fx.run(base().tcat("00000000001", "01", "0001", "-0.01").tcat("00000000002", "01", "0001", "0").write());
        assertEquals(2, c.transact().size(), "non-zero rate writes a 0.00 transaction");
        assertEquals("0000000000{", c.transact().get(0).substring(Fx.AMT, Fx.AMT + 11));
    }

    // ---- ZeroRateTest
    @Test
    void zeroRateWritesNoTranButAccountRewritten() throws IOException {
        Cbact04c c = Fx.run(base().tcat("00000000001", "03", "0001", "500.00").tcat("00000000002", "03", "0001", "500.00").write());
        assertEquals(0, c.transact().size());
        String a1 = c.accounts().get(0);
        assertEquals(new BigDecimal("100.00"), Fx.bal(a1));
        assertEquals(Zoned.encode(BigDecimal.ZERO, 12, 2), a1.substring(78, 90));
        assertEquals(Zoned.encode(BigDecimal.ZERO, 12, 2), a1.substring(90, 102));
    }

    // ---- Characterization
    @Test
    void lastAccountNotRewritten_D1() throws IOException {
        Cbact04c c = Fx.run(base().tcat("00000000001", "01", "0001", "1200.00").tcat("00000000002", "01", "0001", "1200.00").write());
        assertEquals(2, c.transact().size(), "both accounts get a transaction");
        List<String> a = c.accounts();
        assertEquals(new BigDecimal("115.00"), Fx.bal(a.get(0)));
        assertEquals(Zoned.encode(BigDecimal.ZERO, 12, 2), a.get(0).substring(78, 90));
        assertEquals(new BigDecimal("200.00"), Fx.bal(a.get(1)), "D1: last account balance not updated");
        assertEquals(Zoned.encode(new BigDecimal("5.00"), 12, 2), a.get(1).substring(78, 90), "D1: cycle fields not zeroed");
    }

    @Test
    void fallbackToDefault_D2() throws IOException {
        Cbact04c c = Fx.run(base().tcat("00000000001", "01", "0001", "1200.00").tcat("00000000002", "03", "0001", "0").write());
        assertEquals(1, c.transact().size());
        assertTrue(c.sysout().contains("DISCLOSURE GROUP RECORD MISSING"));
        assertTrue(c.sysout().contains("TRY WITH DEFAULT GROUP CODE"));
        assertEquals(0, c.rc());
    }

    @Test
    void missingDefaultAbends() throws IOException {
        Cbact04c c = Fx.run(base().tcat("00000000001", "02", "0001", "1.00").write());
        assertEquals(16, c.rc());
        assertEquals("ERROR READING DEFAULT DISCLOSURE GROUP|FILE STATUS IS: NNNN0023|ABENDING PROGRAM", Fx.tail(c.sysout(), 3));
    }

    @Test
    void missingAccountAbends() throws IOException {
        Cbact04c c = Fx.run(base().tcat("00000000009", "01", "0001", "1.00").write());
        assertEquals(16, c.rc());
        assertEquals("ACCOUNT NOT FOUND: 00000000009|ERROR READING ACCOUNT FILE|FILE STATUS IS: NNNN0023|ABENDING PROGRAM",
                Fx.tail(c.sysout(), 4));
    }

    @Test
    void missingXrefAbends() throws IOException {
        Fx f = new Fx(tmp).xref("4000000000000001", "00000000001").disc("DEFAULT", "01", "0001", "15.00")
                .acct("00000000001", "1.00").acct("00000000002", "1.00").tcat("00000000002", "01", "0001", "1.00");
        Cbact04c c = Fx.run(f.write());
        assertEquals(16, c.rc());
        assertEquals("ERROR READING XREF FILE|FILE STATUS IS: NNNN0023|ABENDING PROGRAM", Fx.tail(c.sysout(), 3));
    }

    @Test
    void missingInputFileAbends() throws IOException {
        Path in = base().tcat("00000000001", "01", "0001", "1").write();
        java.nio.file.Files.delete(in.resolve("DISCGRP.txt"));
        Cbact04c c = Fx.run(in);
        assertEquals(16, c.rc());
        assertEquals("ERROR OPENING DALY REJECTS FILE|FILE STATUS IS: NNNN0035|ABENDING PROGRAM", Fx.tail(c.sysout(), 3)); // D5
    }

    @Test
    void emptyTcatbalNoOutputRc0() throws IOException {
        Cbact04c c = Fx.run(base().write());
        assertEquals(0, c.rc());
        assertEquals(0, c.transact().size());
        assertEquals(new BigDecimal("100.00"), Fx.bal(c.accounts().get(0)));
    }

    @Test
    void tranIdCounterSpansAccounts() throws IOException {
        Fx f = base().tcat("00000000001", "01", "0001", "100").tcat("00000000002", "01", "0001", "100")
                .tcat("00000000002", "01", "0002", "100").disc("DEFAULT", "01", "0002", "25.00");
        Cbact04c c = Fx.run(f.write());
        assertEquals(3, c.transact().size());
        for (int i = 0; i < 3; i++) {
            assertEquals("2022071800" + String.format("%06d", i + 1), c.transact().get(i).substring(0, 16));
        }
    }

    @Test
    void suffixWraps_D7() throws Exception {
        // 1,000,001 interest records in one account: id 999999 -> 000000 -> 000001
        Fx f = base();
        f.write();
        StringBuilder sb = new StringBuilder();
        String rec = Records.pad("00000000001" + "01" + "0001" + Zoned.encode(new BigDecimal("1.00"), 11, 2), 50) + "\n";
        // Same key repeated: sorted order is stable, account break happens once
        for (int i = 0; i < 1_000_001; i++) sb.append(rec);
        sb.append(Records.pad("00000000002" + "03" + "0001" + Zoned.encode(BigDecimal.ZERO, 11, 2), 50)).append('\n');
        java.nio.file.Files.writeString(tmp.resolve("TCATBALF.txt"), sb.toString());
        Cbact04c c = Fx.run(tmp);
        assertEquals(0, c.rc());
        assertEquals("999999", c.transact().get(999_998).substring(10, 16));
        assertEquals("000000", c.transact().get(999_999).substring(10, 16));
        assertEquals("000001", c.transact().get(1_000_000).substring(10, 16));
    }

    @Test
    void duplicateXrefFirstWins_D9() throws IOException {
        Fx f = new Fx(tmp).xref("4000000000000001", "00000000001").xref("4000000000000009", "00000000001")
                .disc("DEFAULT", "01", "0001", "15.00").acct("00000000001", "1.00").acct("00000000002", "1.00")
                .tcat("00000000001", "01", "0001", "100").tcat("00000000002", "01", "0001", "0");
        // second account has no xref -> abend; use only first account instead
        f.xref("4000000000000002", "00000000002");
        Cbact04c c = Fx.run(f.write());
        assertEquals("4000000000000001", c.transact().get(0).substring(Fx.CARD, Fx.CARD + 16));
    }

    @Test
    void transactionLayout() throws IOException {
        Cbact04c c = Fx.run(base().tcat("00000000001", "01", "0001", "1200.00").tcat("00000000002", "03", "0001", "0").write());
        String t = c.transact().get(0);
        assertEquals(350, t.length());
        assertEquals("2022071800000001" + "01" + "0005" + "System    ", t.substring(0, 32));
        assertEquals(Records.pad("Int. for a/c 00000000001", 100), t.substring(32, 132));
        assertEquals("2022-07-18-00.00.00.000000", t.substring(Fx.CARD + 16, Fx.CARD + 42));
        assertEquals(" ".repeat(20), t.substring(330));
    }
}
