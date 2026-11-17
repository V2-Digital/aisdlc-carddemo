package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.Fx.*;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterestTest {
    @TempDir Path dir;

    static String amt(String tx) { return tx.substring(132, 143); }
    static String bal(String acct) { return acct.substring(12, 24); }
    static final String A1 = "00000000001", A2 = "00000000002", A3 = "00000000003";

    List<String> xr() {
        return List.of(xref(A1, "1111111111111111"), xref(A2, "2222222222222222"), xref(A3, "3333333333333333"));
    }

    @Test void truncates() {
        assertEquals(new BigDecimal("14.56"), Zoned.truncDivide(
                new BigDecimal("1164.87").multiply(new BigDecimal("15.00")), BigDecimal.valueOf(1200)));
    }

    @Test void zeroRateAndAllZeroAccountStillZeroed() throws Exception {
        Result r = run(dir, List.of(tcat(A1, "03", "0001", "0000010000{"), tcat(A2, "01", "0001", "0000010000{")),
                xr(), List.of(disc("DEFAULT", "03", "0001", "00000{"), disc("DEFAULT", "01", "0001", "00120{")),
                List.of(acct(A1, "00000000100{", ""), acct(A2, "00000000100{", "")), "2022071800", FROZEN);
        assertEquals(0, r.rc());
        assertEquals(1, r.transact().size()); // rate 0 -> no txn, no seq increment
        assertTrue(r.transact().get(0).startsWith("2022071800000001"));
        assertEquals("0000000100{", bal(r.accts().get(0)).substring(1)); // unchanged balance
        assertEquals("00000000000{", r.accts().get(0).substring(78, 90));
        assertEquals("00000000000{", r.accts().get(0).substring(90, 102));
        assertEquals(acct(A2, "00000000100{", ""), r.accts().get(1)); // last: untouched (D1)
    }

    @Test void defaultFallback() throws Exception {
        Result r = run(dir, List.of(tcat(A1, "01", "0001", "0000010000{"), tcat(A2, "01", "0001", "0000000000{")),
                xr(), List.of(disc("DEFAULT", "01", "0001", "00150{")),
                List.of(acct(A1, "00000000100{", ""), acct(A2, "00000000100{", "")), "2022071800", FROZEN);
        assertEquals(0, r.rc());
        assertEquals(4, r.sysout().lines().filter(l -> l.startsWith("DISCLOSURE") || l.startsWith("TRY WITH")).count());
        assertEquals("0000000125{", amt(r.transact().get(0)));
        assertEquals(new BigDecimal("22.50"), Zoned.decode(bal(r.accts().get(0)), 2)); // 10.00 + 1000.00*15/1200 = 12.50
    }

    @Test void directHit() throws Exception {
        Result r = run(dir, List.of(tcat(A1, "01", "0001", "0000010000{"), tcat(A2, "01", "0001", "0000010000{")),
                xr(), List.of(disc("GOLD", "01", "0001", "00240{"), disc("DEFAULT", "01", "0001", "00150{")),
                List.of(acct(A1, "00000000100{", "GOLD"), acct(A2, "00000000100{", "GOLD")), "2022071800", FROZEN);
        assertEquals(0, r.rc());
        assertFalse(r.sysout().contains("MISSING"));
        assertEquals("0000000200{", amt(r.transact().get(0))); // 1000.00 * 24 / 1200 = 20.00
    }

    @Test void tranIdAndClock() throws Exception {
        Result r = run(dir, List.of(tcat(A1, "01", "0001", "0000010000{"), tcat(A2, "01", "0001", "0000010000{"),
                        tcat(A3, "01", "0001", "0000010000{")),
                xr(), List.of(disc("DEFAULT", "01", "0001", "00150{")),
                List.of(acct(A1, "00000000100{", ""), acct(A2, "00000000100{", ""), acct(A3, "00000000100{", "")),
                "2022071800", "2031050607080910");
        assertEquals("2022071800000001", r.transact().get(0).substring(0, 16));
        assertEquals("2022071800000003", r.transact().get(2).substring(0, 16));
        String ts = "2031-05-06-07.08.09.100000";
        assertEquals(ts, r.transact().get(1).substring(278, 304)); // ORIG-TS
        assertEquals(ts, r.transact().get(1).substring(304, 330)); // PROC-TS
        assertTrue(r.transact().get(1).startsWith("2022071800000002"));
    }

    @Test void emptyTcatbal() throws Exception {
        Result r = run(dir, List.of(), xr(), List.of(disc("DEFAULT", "01", "0001", "00150{")),
                List.of(acct(A1, "00000000100{", "")), "2022071800", FROZEN);
        assertEquals(0, r.rc());
        assertEquals("START OF EXECUTION OF PROGRAM CBACT04C\nEND OF EXECUTION OF PROGRAM CBACT04C\n", r.sysout());
        assertTrue(r.transact().isEmpty());
        assertEquals(List.of(acct(A1, "00000000100{", "")), r.accts());
    }
}
