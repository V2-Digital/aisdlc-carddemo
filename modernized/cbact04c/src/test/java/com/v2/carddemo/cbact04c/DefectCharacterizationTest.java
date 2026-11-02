package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins preserved legacy defects. If one of these fails, behaviour changed: decide it in a separate CR. */
class DefectCharacterizationTest {
    @TempDir Path tmp;

    private static final List<String> XREF = List.of(
            Fx.xref("1111111111111111", "00000000001"), Fx.xref("2222222222222222", "00000000002"));
    private static final List<String> DISC = List.of(
            Fx.disc("DEFAULT", "01", "0001", "15"), Fx.disc("DEFAULT", "03", "0001", "0"));

    @Test
    void D1_lastAccountGetsTransactionButIsNotRewritten() throws IOException {
        String a1 = Fx.acct("00000000001", "1000.00", "50.00", "60.00", "");
        String a2 = Fx.acct("00000000002", "1000.00", "50.00", "60.00", "");
        Path in = Fx.dir(tmp, "in", List.of(Fx.tcat("00000000001", "01", "0001", "1200.00"),
                Fx.tcat("00000000002", "01", "0001", "1200.00")), XREF, DISC, List.of(a1, a2));
        Fx.Run r = Fx.run(in, tmp.resolve("out"));
        assertEquals(0, r.rc());
        assertEquals(2, r.lines("TRANSACT").size(), "both accounts get an interest transaction");
        List<String> acc = r.lines("ACCTFILE");
        assertNotEquals(a1, acc.get(0), "first account is rewritten");
        // 1000.00 + 15.00 interest, cycle credit/debit zeroed
        assertEquals(Zoned.encode(new java.math.BigDecimal("1015.00"), 12, 2), acc.get(0).substring(12, 24));
        assertEquals("00000000000{", acc.get(0).substring(78, 90));
        assertEquals("00000000000{", acc.get(0).substring(90, 102));
        assertEquals(a2, acc.get(1), "last account unchanged: balance and cycle fields not updated");
    }

    @Test
    void F1_zeroBalanceWritesZeroAmountTransaction() throws IOException {
        Path in = Fx.dir(tmp, "in", List.of(Fx.tcat("00000000001", "01", "0001", "0")),
                XREF, DISC, List.of(Fx.acct("00000000001", "5", "0", "0", "")));
        Fx.Run r = Fx.run(in, tmp.resolve("out"));
        assertEquals(1, r.lines("TRANSACT").size());
        assertEquals("0000000000{", r.lines("TRANSACT").get(0).substring(132, 143));
    }

    @Test
    void rateZeroWritesNoTransactionButAccountIsStillRewritten() throws IOException {
        String a1 = Fx.acct("00000000001", "5", "7", "8", "");
        Path in = Fx.dir(tmp, "in", List.of(Fx.tcat("00000000001", "03", "0001", "-50"),
                Fx.tcat("00000000002", "03", "0001", "-50")), XREF, DISC,
                List.of(a1, Fx.acct("00000000002", "5", "0", "0", "")));
        Fx.Run r = Fx.run(in, tmp.resolve("out"));
        assertEquals(0, r.lines("TRANSACT").size());
        assertEquals("00000000000{", r.lines("ACCTFILE").get(0).substring(78, 90));
    }

    @Test
    void F2_tranIdCounterWrapsAfter999999() throws IOException {
        // 1,000,001 categories would be needed in one run; use many accounts' worth of categories instead:
        // type 01 with categories 0000..9999 across 101 accounts -> 1,010,000 transactions is too heavy,
        // so check the wrap through the public Cbact04c constructor with a synthetic TCATBAL map.
        java.util.TreeMap<String, String> tcat = new java.util.TreeMap<>();
        java.util.TreeMap<String, String> disc = new java.util.TreeMap<>();
        java.util.TreeMap<String, String> xref = new java.util.TreeMap<>();
        java.util.TreeMap<String, String> acct = new java.util.TreeMap<>();
        acct.put("00000000001", Fx.acct("00000000001", "0", "0", "0", ""));
        xref.put("1111111111111111", Fx.xref("1111111111111111", "00000000001"));
        for (int t = 0; t < 100; t++) {
            String type = String.format("%02d", t);
            for (int c = 0; c < 10_000; c++) {
                String cat = String.format("%04d", c);
                tcat.put("00000000001" + type + cat, Fx.tcat("00000000001", type, cat, "0"));
                disc.put("DEFAULT   " + type + cat, Fx.disc("DEFAULT", type, cat, "1"));
            }
        }
        var out = new java.io.PrintStream(java.io.OutputStream.nullOutputStream());
        Cbact04c.Result r = new Cbact04c("2022071800", () -> Fx.TS, out, tcat, xref, disc, acct).run();
        assertEquals(1_000_000, r.transact().size());
        assertEquals("2022071800" + "000000", r.transact().get(999_999).substring(0, 16), "999999+1 wraps to 000000");
        assertEquals("2022071800" + "000001", r.transact().get(0).substring(0, 16));
    }

    @Test
    void F2_interestOverflowDropsHighOrderDigits() throws IOException {
        // 9,999,999,999.99 * 1200% / 1200 -> 9,999,999,999.99 -> stored in S9(9)V99 loses the leading 9
        String bal = "99999999.99"; // S9(9)V99 max is 9999999.99 for 9 int digits: use rate to overflow
        Path in = Fx.dir(tmp, "in", List.of(Fx.tcat("00000000001", "01", "0001", "999999999.99"),
                Fx.tcat("00000000002", "01", "0001", "0")), XREF,
                List.of(Fx.disc("DEFAULT", "01", "0001", "2400")),
                List.of(Fx.acct("00000000001", "0", "0", "0", ""), Fx.acct("00000000002", "0", "0", "0", "")));
        Fx.Run r = Fx.run(in, tmp.resolve("out"));
        // 999,999,999.99 * 2400 / 1200 = 1,999,999,999.98 -> 999,999,999.98 (leading 1 dropped)
        assertEquals(Zoned.encode(new java.math.BigDecimal("999999999.98"), 11, 2),
                r.lines("TRANSACT").get(0).substring(132, 143));
    }
}
