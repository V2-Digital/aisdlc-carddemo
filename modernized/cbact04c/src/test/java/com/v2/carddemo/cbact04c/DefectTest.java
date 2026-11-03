package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterization tests: each pins a legacy defect that must NOT be fixed in CR-1. */
class DefectTest {
    /** D1: last account's interest is in TRANSACT, but its ACCTFILE record is untouched. */
    @Test void lastAccountNotRewritten_D1(@TempDir Path t) throws Exception {
        Path r = Fx.repo();
        Path in = r.resolve("legacy/golden/CBTRN02C");
        Path out = t.resolve("out");
        assertEquals(0, new InterestCalc(r.resolve("app/data/ASCII"), in, "2022071800", Fx.CLOCK).run(out));
        List<String> inAcct = Files.readAllLines(in.resolve("ACCTFILE.txt"));
        List<String> outAcct = Files.readAllLines(out.resolve("ACCTFILE.txt"));
        assertEquals(50, outAcct.size());
        assertEquals(inAcct.get(49), outAcct.get(49));
        assertNotEquals(inAcct.get(48), outAcct.get(48));
        String last = Files.readAllLines(out.resolve("TRANSACT.txt")).get(49);
        assertTrue(last.startsWith("2022071800000050"));
        assertEquals("0000000187G", last.substring(132, 143));
        // account 50 cycle fields not zeroed
        assertNotEquals("00000000000{", outAcct.get(49).substring(78, 90));
    }

    /** D1 in miniature: two accounts, only the first is updated. */
    @Test void lastAccountNotRewrittenSynthetic_D1(@TempDir Path t) throws Exception {
        InterestCalc c = twoAccounts(t, "");
        assertEquals(0, c.run(t.resolve("out")));
        assertEquals(2, c.transact().size());
        // acct 1: 100.00 + 12% interest (1.00) = 101.00, cycle fields zeroed
        String a1 = c.accounts().get("00000000001");
        assertEquals(Zoned.encode(new java.math.BigDecimal("101.00"), 12, 2), a1.substring(12, 24));
        assertEquals("00000000000{00000000000{", a1.substring(78, 102));
        // acct 2 (last): untouched, though its 2.00 interest is in TRANSACT
        assertEquals(Fx.acct("00000000002", "200.00", ""), c.accounts().get("00000000002"));
        assertEquals("0000000020{", c.transact().get(1).substring(132, 143));
    }

    /** D6: zero balance with non-zero rate still writes a zero-amount transaction. */
    @Test void zeroAmountTransactionWritten_D6(@TempDir Path t) throws Exception {
        Path d = Fx.data(t.resolve("in"), List.of(Fx.tcat("00000000001", "01", "0001", "0")),
                List.of(Fx.xref("4111111111111111", "00000000001")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "15.00")), List.of(Fx.acct("00000000001", "5", "")));
        InterestCalc c = Fx.calc(d);
        assertEquals(0, c.run(t.resolve("out")));
        assertEquals(1, c.transact().size());
        assertEquals("0000000000{", c.transact().get(0).substring(132, 143));
    }

    /** D9: DISCGRP open error text says "DALY REJECTS FILE". */
    @Test void discgrpOpenErrorSaysDalyRejects_D9(@TempDir Path t) throws Exception {
        Path d = Fx.data(t.resolve("in"), List.of(), List.of(), List.of(), List.of());
        Files.delete(d.resolve("discgrp.txt"));
        InterestCalc c = Fx.calc(d);
        assertEquals(16, c.run(t.resolve("out")));
        assertTrue(c.sysout().contains("ERROR OPENING DALY REJECTS FILE"));
    }

    /** D8: for an account with several cards, the first card (lowest card number) is used. */
    @Test void firstCardUsed_D8(@TempDir Path t) throws Exception {
        Path d = Fx.data(t.resolve("in"), List.of(Fx.tcat("00000000001", "01", "0001", "100")),
                List.of(Fx.xref("5555555555555555", "00000000001"), Fx.xref("4111111111111111", "00000000001")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "15.00")), List.of(Fx.acct("00000000001", "5", "")));
        InterestCalc c = Fx.calc(d);
        assertEquals(0, c.run(t.resolve("out")));
        assertEquals("4111111111111111", c.transact().get(0).substring(262, 278));
    }

    /** D5: timestamps come from the clock, not the PARM date. */
    @Test void timestampFromClockNotParm_D5(@TempDir Path t) throws Exception {
        InterestCalc c = twoAccounts(t, "");
        assertEquals(0, c.run(t.resolve("out")));
        assertTrue(c.transact().get(0).contains("2022-07-18-00.00.00.000000"));
    }

    static InterestCalc twoAccounts(Path t, String group) throws Exception {
        Path d = Fx.data(t.resolve("in"),
                List.of(Fx.tcat("00000000001", "01", "0001", "100.00"), Fx.tcat("00000000002", "01", "0001", "200.00")),
                List.of(Fx.xref("4111111111111111", "00000000001"), Fx.xref("4222222222222222", "00000000002")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "12.00")),
                List.of(Fx.acct("00000000001", "100.00", group), Fx.acct("00000000002", "200.00", group)));
        return Fx.calc(d);
    }
}
