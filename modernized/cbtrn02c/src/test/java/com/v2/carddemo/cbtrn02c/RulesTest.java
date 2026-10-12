package com.v2.carddemo.cbtrn02c;

import static com.v2.carddemo.cbtrn02c.Fx.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** Reason codes, overlimit (D1/D2/D7/D8), TCATBAL, posting. */
class RulesTest {
    static final String OKTS = "2022-06-10 19:27:53.000000";

    private static String trailer(Result r, int i) {
        return r.lines("DALYREJS").get(i).substring(350);
    }

    private static String t(int reason, String desc) {
        return String.format("%04d%-76s", reason, desc);
    }

    @Test void reason100UnknownCard() {
        Result r = run(List.of(tran("T1", z(100, 11), "9999999999999999", "01", "0001", OKTS)), acct(100000, 0, 0));
        assertEquals(t(100, "INVALID CARD NUMBER FOUND"), trailer(r, 0));
        assertEquals(430, r.lines("DALYREJS").get(0).length());
        assertEquals(tran("T1", z(100, 11), "9999999999999999", "01", "0001", OKTS), r.lines("DALYREJS").get(0).substring(0, 350));
        assertEquals(4, r.rc());
    }

    @Test void reason101AccountMissing() {
        Result r = run(List.of(tran("T1", 100)), List.of(xref(C, "00000000002")), List.of(acct(100000, 0, 0)),
                List.of(tcat(A, "01", "0001", 0, "0".repeat(22))));
        assertEquals(t(101, "ACCOUNT RECORD NOT FOUND"), trailer(r, 0));
        assertEquals(4, r.rc());
    }

    @Test void reason102Overlimit() {
        Result r = run(List.of(tran("T1", 1001)), acct(1000, 0, 0));
        assertEquals(t(102, "OVERLIMIT TRANSACTION"), trailer(r, 0));
        assertEquals(0, r.lines("TRANFILE").size());
    }

    @Test void reason103Expired() {
        Result r = run(List.of(tran("T1", 100)), acct(A, 0, 100000, "2022-01-01", 0, 0));
        assertEquals(t(103, "TRANSACTION RECEIVED AFTER ACCT EXPIRATION"), trailer(r, 0));
    }

    @Test void both102And103ReportsOnly103_D2() {
        Result r = run(List.of(tran("T1", 5000)), acct(A, 0, 1000, "2022-01-01", 0, 0));
        assertEquals(t(103, "TRANSACTION RECEIVED AFTER ACCT EXPIRATION"), trailer(r, 0));
    }

    @Test void expiryEqualToOrigDateIsAccepted() {
        assertEquals(0, run(List.of(tran("T1", 100)), acct(A, 0, 100000, "2022-06-10", 0, 0)).rc());
    }

    @Test void overlimitUsesRunningCycleTotals() {
        // limit 150.00: first 100.00 posts (cyc credit 100), second 100.00 -> temp 200 > 150 rejected
        Result r = run(List.of(tran("T1", 10000), tran("T2", 10000)), acct(15000, 0, 0));
        assertEquals(1, r.lines("TRANFILE").size());
        assertEquals(t(102, "OVERLIMIT TRANSACTION"), trailer(r, 0));
        assertTrue(r.lines("DALYREJS").get(0).startsWith("T2"));
    }

    @Test void negativeAmountRaisesTempBalance_D1() {
        // existing debit 20.00 => credit-debit = -20 ... a payment of -100 posts to cyc debit, then the next purchase
        // sees credit - debit(-100) = +100 on the sheet. Limit 150: purchase 100 (temp 200) is rejected after payment.
        Result r = run(List.of(tran("T1", -10000), tran("T2", 10000)), acct(15000, 0, 0));
        assertEquals(t(102, "OVERLIMIT TRANSACTION"), trailer(r, 0));
        assertTrue(r.lines("DALYREJS").get(0).startsWith("T2"));
        // payment itself: temp = 0 - 0 + (-100) = -100 fits. And a payment bigger than limit-less balance still posts.
        assertEquals(1, r.lines("TRANFILE").size());
        // CURR-BAL is ignored by the check (D1): huge balance does not reject
        Result r2 = run(List.of(tran("T1", 100)), acct(A, 999999999, 1000, "2030-01-01", 0, 0));
        assertEquals(0, r2.rc());
    }

    @Test void inactiveAccountStillPosts_D8() {
        String a = acct(A, 0, 100000, "2030-01-01", 0, 0);
        a = a.substring(0, 11) + "N" + a.substring(12);
        assertEquals(0, run(List.of(tran("T1", 100)), a).rc());
    }

    @Test void tcatbalCreatedWithCreatingLine() {
        Result r = run(List.of(tran("T1", z(700, 11), C, "02", "0003", OKTS)), acct(100000, 0, 0));
        assertTrue(r.out().contains("TCATBAL record not found for key : " + A + "020003.. Creating.\n"));
        assertEquals(2, r.lines("TCATBALF").size());
        assertEquals(A + "020003" + z(700, 11) + " ".repeat(22), r.lines("TCATBALF").get(1)); // no prior read: spaces (D9)
    }

    @Test void tcatbalExistingGetsAmountAdded() {
        Result r = run(List.of(tran("T1", 250)), acct(100000, 0, 0));
        assertFalse(r.out().contains("Creating"));
        assertEquals(A + "010001" + z(250, 11) + "0".repeat(22), r.lines("TCATBALF").get(0));
    }

    @Test void tcatbalFirstCreateInRunHasSpaceFiller_D9() {
        Result r = run(List.of(tran("T1", z(5, 11), C, "05", "0005", OKTS)), List.of(xref(C, A)), List.of(acct(100000, 0, 0)),
                List.of());
        assertEquals(A + "050005" + z(5, 11) + " ".repeat(22), r.lines("TCATBALF").get(0));
    }

    @Test void postingFields_PROCTSfromClockFillerSpacesAmtKept_D8() {
        Result r = run(List.of(tran("T1", 12345)), acct(100000, 0, 0));
        String rec = r.lines("TRANFILE").get(0);
        assertEquals(350, rec.length());
        assertEquals("2022-07-18-00.00.00.000000", rec.substring(304, 330));
        assertEquals(" ".repeat(20), rec.substring(330));
        assertEquals(tran("T1", 12345).substring(0, 304), rec.substring(0, 304));
    }

    @Test void accountUpdate_creditVsDebit() {
        Result r = run(List.of(tran("T1", 1000), tran("T2", -300)), acct(100000, 0, 0));
        String a = r.lines("ACCTFILE").get(0);
        assertEquals(z(700, 12), a.substring(12, 24)); // CURR-BAL
        assertEquals(z(1000, 12), a.substring(78, 90)); // CYC-CREDIT
        assertEquals(z(-300, 12), a.substring(90, 102)); // CYC-DEBIT
    }

    @Test void noRejectsRc0() {
        Result r = run(List.of(tran("T1", 100)), acct(100000, 0, 0));
        assertEquals(0, r.rc());
        assertEquals(0, r.lines("DALYREJS").size());
        assertTrue(r.out().contains("TRANSACTIONS PROCESSED :000000001\nTRANSACTIONS REJECTED  :000000000\n"));
    }

    @Test void emptyDalytran() {
        Result r = run(List.of(), acct(100000, 0, 0));
        assertEquals(0, r.rc());
        assertEquals(0, r.lines("TRANFILE").size());
    }
}
