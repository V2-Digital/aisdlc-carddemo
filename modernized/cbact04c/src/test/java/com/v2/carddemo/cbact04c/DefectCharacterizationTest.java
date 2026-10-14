package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class DefectCharacterizationTest {
    static final String X1 = Fx.xref("1111222233334444", "00000000001");
    static final String X2 = Fx.xref("5555666677778888", "00000000002");

    @Test
    void d1_lastAccountIsNotRewritten() {
        String a1 = Fx.acct("00000000001", "1000.00", "5.00", "6.00", "");
        String a2 = Fx.acct("00000000002", "2000.00", "7.00", "8.00", "");
        var r = Fx.run(
                List.of(Fx.tcat("00000000001", "01", "0001", "1200.00"), Fx.tcat("00000000002", "01", "0001", "1200.00")),
                List.of(X1, X2), List.of(a1, a2), List.of(Fx.disc("DEFAULT", "01", "0001", "12.00")));
        assertEquals(0, r.rc());
        assertEquals(2, r.transact().size(), "transaction is still written for the last account");
        assertNotEquals(a1, r.acctfile().get(0));
        assertEquals(Fx.z("1012.00", 2, 12), r.acctfile().get(0).substring(12, 24)); // 1000 + 12
        assertEquals(Fx.z("0", 2, 12), r.acctfile().get(0).substring(78, 90));
        assertEquals(a2, r.acctfile().get(1), "D1: last account untouched");
    }

    @Test
    void d4_rateZeroNoTxn_nonZeroRateZeroBalanceWritesZeroTxn() {
        var r = Fx.run(
                List.of(Fx.tcat("00000000001", "01", "0001", "500.00"), Fx.tcat("00000000001", "01", "0002", "0")),
                List.of(X1), List.of(Fx.acct("00000000001", "0", "0", "0", "")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "0"), Fx.disc("DEFAULT", "01", "0002", "10.00")));
        assertEquals(1, r.transact().size());
        assertEquals("0000000000{", r.transact().get(0).substring(132, 143));
    }

    @Test
    void d2_fallbackToDefaultEmitsTwoLines() {
        var r = Fx.run(List.of(Fx.tcat("00000000001", "01", "0001", "1200.00")), List.of(X1),
                List.of(Fx.acct("00000000001", "0", "0", "0", "GRPX")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "12.00")));
        assertEquals(List.of("START OF EXECUTION OF PROGRAM CBACT04C",
                Fx.tcat("00000000001", "01", "0001", "1200.00"),
                "DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE",
                "END OF EXECUTION OF PROGRAM CBACT04C"), r.sysout());
        assertEquals(Fx.z("12.00", 2, 11), r.transact().get(0).substring(132, 143));
    }

    @Test
    void realGroupHitUsesOwnRateAndNoFallbackLines() {
        var r = Fx.run(List.of(Fx.tcat("00000000001", "01", "0001", "1200.00")), List.of(X1),
                List.of(Fx.acct("00000000001", "0", "0", "0", "GRPX")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "12.00"), Fx.disc("GRPX", "01", "0001", "24.00")));
        assertEquals(Fx.z("24.00", 2, 11), r.transact().get(0).substring(132, 143));
        assertFalse(r.sysout().contains("DISCLOSURE GROUP RECORD MISSING"));
    }

    @Test
    void missingDefaultAbends() {
        var r = Fx.run(List.of(Fx.tcat("00000000001", "01", "0001", "1.00")), List.of(X1),
                List.of(Fx.acct("00000000001", "0", "0", "0", "")), List.of());
        assertEquals(16, r.rc());
        assertEquals(List.of("DISCLOSURE GROUP RECORD MISSING", "TRY WITH DEFAULT GROUP CODE",
                "ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM"),
                r.sysout().subList(2, 7));
        assertTrue(r.transact().isEmpty());
    }

    @Test
    void missingAccountAbends() {
        var r = Fx.run(List.of(Fx.tcat("00000000009", "01", "0001", "1.00")), List.of(X1), List.of(), List.of());
        assertEquals(16, r.rc());
        assertEquals(List.of("ACCOUNT NOT FOUND: 00000000009", "ERROR READING ACCOUNT FILE",
                "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM"), r.sysout().subList(2, 6));
    }

    @Test
    void missingXrefAbends() {
        var r = Fx.run(List.of(Fx.tcat("00000000001", "01", "0001", "1.00")), List.of(),
                List.of(Fx.acct("00000000001", "0", "0", "0", "")), List.of());
        assertEquals(16, r.rc());
        assertEquals(List.of("ACCOUNT NOT FOUND: 00000000001", "ERROR READING XREF FILE",
                "FILE STATUS IS: NNNN0023", "ABENDING PROGRAM"), r.sysout().subList(2, 6));
    }

    @Test
    void d6_accountBalanceOverflowWraps() {
        String a1 = Fx.acct("00000000001", "9999999999.99", "0", "0", "");
        var r = Fx.run(
                List.of(Fx.tcat("00000000001", "01", "0001", "1200.00"), Fx.tcat("00000000002", "01", "0001", "0")),
                List.of(X1, X2), List.of(a1, Fx.acct("00000000002", "0", "0", "0", "")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "12.00")));
        // 9999999999.99 + 12.00 = 10000000011.99 -> 0000000011.99 (high digit dropped)
        assertEquals(Fx.z("11.99", 2, 12), r.acctfile().get(0).substring(12, 24));
    }
}
