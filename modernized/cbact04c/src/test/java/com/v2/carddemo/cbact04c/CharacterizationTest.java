package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins legacy defects. If one of these fails, behaviour has diverged from COBOL. */
class CharacterizationTest {
    @TempDir Path tmp;
    static final String A1 = "00000000001", A2 = "00000000002", A3 = "00000000003";
    static final String ORIG1 = Fx.acct(A1, "100.00", "5.00", "-2.00", "");
    static final String ORIG2 = Fx.acct(A2, "200.00", "7.00", "-3.00", "");
    static final String ORIG3 = Fx.acct(A3, "300.00", "9.00", "-4.00", "");

    private Fx.Result run(List<String> tcat, String rate) throws IOException {
        return Fx.simple(tmp, tcat, List.of(ORIG1, ORIG2, ORIG3), List.of(Fx.disc("DEFAULT", "01", "0001", rate)));
    }

    @Test
    void D1_lastAccountInterestIsPostedAsTransactionButNeverRewritten() throws IOException {
        Fx.Result r = run(List.of(Fx.tcat(A1, "01", "0001", "120.00"), Fx.tcat(A3, "01", "0001", "120.00")), "12.00");
        assertEquals(2, r.records("TRANSACT.txt").size());
        List<String> a = r.records("ACCTFILE.txt");
        assertEquals(Fx.bal("101.20", 12), a.get(0).substring(12, 24)); // 100 + 1.20
        assertEquals(Fx.bal("0", 12), a.get(0).substring(78, 90));
        assertEquals(ORIG3, a.get(2)); // last account untouched, incl. cycle fields
    }

    @Test
    void D1_singleAccountIsNeverRewritten() throws IOException {
        Fx.Result r = run(List.of(Fx.tcat(A1, "01", "0001", "120.00")), "12.00");
        assertEquals(1, r.records("TRANSACT.txt").size());
        assertEquals(ORIG1, r.records("ACCTFILE.txt").get(0));
    }

    @Test
    void D2_cycleFieldsZeroedEvenWhenInterestIsZero() throws IOException {
        Fx.Result r = run(List.of(Fx.tcat(A1, "01", "0001", "0"), Fx.tcat(A3, "01", "0001", "0")), "0");
        assertEquals(0, r.records("TRANSACT.txt").size());
        String a1 = r.records("ACCTFILE.txt").get(0);
        assertEquals(Fx.bal("100.00", 12), a1.substring(12, 24));
        assertEquals(Fx.bal("0", 12), a1.substring(78, 90));
        assertEquals(Fx.bal("0", 12), a1.substring(90, 102));
        assertNotEquals(ORIG1, a1);
    }

    @Test
    void accountsWithoutTcatbalPassThroughUntouched() throws IOException {
        Fx.Result r = run(List.of(Fx.tcat(A1, "01", "0001", "10.00"), Fx.tcat(A3, "01", "0001", "10.00")), "12.00");
        assertEquals(ORIG2, r.records("ACCTFILE.txt").get(1));
    }
}
