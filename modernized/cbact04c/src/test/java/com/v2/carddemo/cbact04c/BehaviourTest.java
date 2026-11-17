package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Interest truncation, group fallback, zero rate, suffix and clock (AC5). */
class BehaviourTest {
    private static final List<String> XREF = List.of(Fx.xref("4111111111111111", "00000000001"),
            Fx.xref("4222222222222222", "00000000002"), Fx.xref("4333333333333333", "00000000003"));

    private static List<String> accts() {
        return List.of(Fx.acct("00000000001", "DEFAULT"), Fx.acct("00000000002", "DEFAULT"),
                Fx.acct("00000000003", "DEFAULT"));
    }

    private static final List<String> RATES = List.of(Fx.disc("DEFAULT", "01", "0001", "00150{"),
            Fx.disc("DEFAULT", "03", "0001", "00000{"));

    private static String amt(String bal) {
        InterestCalc.Result r = Fx.run(List.of(Fx.tcat("00000000001", "01", "0001", bal)), XREF, accts(), RATES);
        return Fx.amount(r.transact().get(0));
    }

    /** 1164.87 * 15 / 1200 = 14.5608 -> 14.56. */
    @Test
    void truncatesPositive() {
        assertEquals("0000000145F", amt("0000011648G"));
    }

    /** 79.99 * 15 / 1200 = 0.999875 -> 0.99 (rounding would give 1.00). */
    @Test
    void truncatesNotRounds() {
        assertEquals("0000000009I", amt("0000000799I"));
    }

    /** Negative: toward zero. Facts from legacy/tests/cbact04c-negative.sh (real COBOL). */
    @Test
    void negativeTruncatesTowardZero() {
        assertEquals("0000000145O", amt("0000011648P"));
        assertEquals("0000000000{", amt("0000000001J"));   // -0.01 -> 0.00, positive zero sign
        assertEquals("0000000000J", amt("0000000009R"));   // -0.99 -> -0.01
    }

    @Test
    void overflowKeepsLowNineIntegerDigits() {
        assertEquals(new BigDecimal("0.00"), Zoned.truncate(new BigDecimal("1000000000.00"), 11));
        assertEquals(new BigDecimal("5.00"), Zoned.truncate(new BigDecimal("1000000005.00"), 11));
        assertEquals(new BigDecimal("-5.00"), Zoned.truncate(new BigDecimal("-1000000005.00"), 11));
    }

    @Test
    void groupHitUsesOwnRateAndSkipsFallback() {
        List<String> rates = List.of(Fx.disc("GRP", "01", "0001", "00300{"), Fx.disc("DEFAULT", "01", "0001", "00150{"));
        InterestCalc c = Fx.calc();
        InterestCalc.Result r = c.run(List.of(Fx.tcat("00000000001", "01", "0001", "0000012000{"),
                Fx.tcat("00000000002", "01", "0001", "0000012000{")), XREF,
                List.of(Fx.acct("00000000001", "GRP"), Fx.acct("00000000002", "GRP"), Fx.acct("00000000003", "GRP")),
                rates);
        assertEquals("0000000300{", Fx.amount(r.transact().get(0)));
        assertTrue(r.stdout().stream().noneMatch(l -> l.contains("MISSING")));
    }

    @Test
    void missingGroupFallsBackToDefault() {
        InterestCalc.Result r = Fx.run(List.of(Fx.tcat("00000000001", "01", "0001", "0000012000{")), XREF,
                List.of(Fx.acct("00000000001", "NOSUCH"), Fx.acct("00000000002", "X"), Fx.acct("00000000003", "X")),
                RATES);
        assertEquals("0000000150{", Fx.amount(r.transact().get(0)));
        assertEquals(1, r.stdout().stream().filter(l -> l.equals("DISCLOSURE GROUP RECORD MISSING")).count());
        assertEquals(1, r.stdout().stream().filter(l -> l.equals("TRY WITH DEFAULT GROUP CODE")).count());
    }

    /** Rate 0: no transaction, no suffix use, yet the account is rewritten with cycle fields zeroed. */
    @Test
    void zeroRateNoTranButAccountRewritten() {
        List<String> t = List.of(Fx.tcat("00000000001", "03", "0001", "0000012000{"),
                Fx.tcat("00000000002", "01", "0001", "0000012000{"));
        InterestCalc.Result r = Fx.run(t, XREF, accts(), RATES);
        assertEquals(1, r.transact().size());
        assertEquals("2022071800000001", r.transact().get(0).substring(0, 16));
        String a1 = r.acctfile().get(0);
        assertEquals("00000001000{", a1.substring(12, 24));          // balance unchanged
        assertEquals("00000000000{", a1.substring(78, 90));          // cycle credit zeroed
        assertEquals("00000000000{", a1.substring(90, 102));         // cycle debit zeroed
        assertEquals(accts().get(1), r.acctfile().get(1));           // last row's account untouched (D1)
    }

    /** Interest accumulates into the balance of a non-last account. */
    @Test
    void balanceUpdatedForNonLastAccount() {
        List<String> t = List.of(Fx.tcat("00000000001", "01", "0001", "0000012000{"),
                Fx.tcat("00000000002", "03", "0001", "0000012000{"));
        InterestCalc.Result r = Fx.run(t, XREF, accts(), RATES);
        assertEquals("00000001150{", r.acctfile().get(0).substring(12, 24)); // 100.00 + 15.00
    }

    /** The transaction suffix runs across accounts, not per account. */
    @Test
    void suffixRunsAcrossAccounts() {
        List<String> t = List.of(Fx.tcat("00000000001", "01", "0001", "0000012000{"),
                Fx.tcat("00000000001", "01", "0002", "0000012000{"),
                Fx.tcat("00000000002", "01", "0001", "0000012000{"),
                Fx.tcat("00000000003", "01", "0001", "0000012000{"));
        List<String> rates = List.of(Fx.disc("DEFAULT", "01", "0001", "00150{"), Fx.disc("DEFAULT", "01", "0002", "00150{"));
        InterestCalc.Result r = Fx.run(t, XREF, accts(), rates);
        List<String> ids = new ArrayList<>();
        r.transact().forEach(x -> ids.add(x.substring(10, 16)));
        assertEquals(List.of("000001", "000002", "000003", "000004"), ids);
    }

    /** Timestamps come from the clock, never from PARM. */
    @Test
    void timestampsFromClockNotParm() {
        InterestCalc c = new InterestCalc("1999010100", LocalDateTime.of(2031, 12, 25, 13, 45, 59), 7);
        InterestCalc.Result r = c.run(List.of(Fx.tcat("00000000001", "01", "0001", "0000012000{")), XREF, accts(),
                RATES);
        String tran = r.transact().get(0);
        assertEquals("1999010100000001", tran.substring(0, 16));
        assertEquals("2031-12-25-13.45.59.070000", tran.substring(278, 304));
        assertEquals("2031-12-25-13.45.59.070000", tran.substring(304, 330));
        assertEquals("Int. for a/c 00000000001", tran.substring(32, 56));
    }
}
