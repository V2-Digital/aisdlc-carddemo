package carddemo.cbact04c;

import static carddemo.cbact04c.Base.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterization tests: each pins a legacy defect that must NOT be fixed in CR-1. */
class DefectTest {
    @TempDir Path tmp;

    private static String rate(String v) {
        return Zoned.encode(new BigDecimal(v), 4);
    }

    private List<String> two(String bal1, String bal2, String r) throws IOException {
        Fx.Result res = run(tmp,
                List.of(Fx.bal(A1, "010001", amt(bal1)), Fx.bal(A2, "010001", amt(bal2))),
                List.of(Fx.xref("4111111111111111", A1), Fx.xref("4222222222222222", A2)),
                List.of(acct(A1, "100.00"), acct(A2, "200.00")),
                List.of(Fx.disc("DEFAULT", "010001", rate(r))));
        assertEquals(0, res.rc());
        return Fx.lines(res.out().resolve("ACCTFILE.txt"));
    }

    @Test
    void d1_lastAccountIsNeverUpdated() throws IOException {
        List<String> before = List.of(acct(A1, "100.00"), acct(A2, "200.00"));
        List<String> after = two("1200.00", "1200.00", "12.00"); // 12.00 interest each
        assertEquals(before.get(1), after.get(1), "last account must stay untouched (D1)");
        assertNotEquals(before.get(0), after.get(0));
        assertEquals(bal("112.00"), after.get(0).substring(12, 24)); // 100 + 12.00
        assertEquals("00000000000{", after.get(0).substring(78, 90));
        assertEquals("00000000000{", after.get(0).substring(90, 102));
    }

    @Test
    void d1_singleAccountFileIsUnchanged() throws IOException {
        Fx.Result r = run(tmp, List.of(Fx.bal(A1, "010001", amt("1200.00"))),
                List.of(Fx.xref("4111111111111111", A1)), List.of(acct(A1, "100.00")),
                List.of(Fx.disc("DEFAULT", "010001", rate("12.00"))));
        assertEquals(List.of(acct(A1, "100.00")), Fx.lines(r.out().resolve("ACCTFILE.txt")));
        assertEquals(1, Fx.lines(r.out().resolve("TRANSACT.txt")).size()); // but interest was charged
    }

    @Test
    void d4_cycleFieldsZeroedEvenWhenRateIsZero() throws IOException {
        List<String> after = two("1200.00", "1200.00", "0.00");
        assertEquals(bal("100.00"), after.get(0).substring(12, 24)); // balance unchanged
        assertEquals("00000000000{", after.get(0).substring(78, 90)); // ...cycle credit was 500.00
        assertEquals("00000000000{", after.get(0).substring(90, 102));
        assertEquals(acct(A2, "200.00"), after.get(1)); // D1 still applies to the last one
    }

    @Test
    void zeroRateWritesNoTransactionAndKeepsCounter() throws IOException {
        Fx.Result r = run(tmp,
                List.of(Fx.bal(A1, "010001", amt("100.00")), Fx.bal(A1, "020001", amt("100.00")),
                        Fx.bal(A1, "030001", amt("100.00"))),
                List.of(Fx.xref("4111111111111111", A1)), List.of(acct(A1, "1.00")),
                List.of(Fx.disc("DEFAULT", "010001", rate("12.00")), Fx.disc("DEFAULT", "020001", rate("0.00")),
                        Fx.disc("DEFAULT", "030001", rate("12.00"))));
        List<String> t = Fx.lines(r.out().resolve("TRANSACT.txt"));
        assertEquals(2, t.size());
        assertEquals("2022071800000001", t.get(0).substring(0, 16));
        assertEquals("2022071800000002", t.get(1).substring(0, 16));
    }

    @Test
    void d3_monthlyAmountWrapsAbove1e9() throws IOException {
        // 900,000,000.00 x 9999.99 / 1200 = 7,499,992,500.00 -> high-order digit dropped: 499,992,500.00
        Fx.Result r = run(tmp, List.of(Fx.bal(A1, "010001", amt("900000000.00"))),
                List.of(Fx.xref("4111111111111111", A1)), List.of(acct(A1, "1.00")),
                List.of(Fx.disc("DEFAULT", "010001", rate("9999.99"))));
        String a = Fx.lines(r.out().resolve("TRANSACT.txt")).get(0).substring(132, 143);
        assertEquals(new BigDecimal("499992500.00"), Zoned.decode(a));
    }

    @Test
    void d3_totalAndBalanceWrap() throws IOException {
        // A1: 2 x (900,000,000.00 @ 100.00 -> 75,000,000.00) is fine; use 2 cats of 7.5e8 monthly each.
        // monthly = 9e8 x 100 / 1200 = 75,000,000.00 ; 2 cats -> total 150,000,000.00. Balance near 1e10 wraps.
        Fx.Result r = run(tmp,
                List.of(Fx.bal(A1, "010001", amt("900000000.00")), Fx.bal(A1, "020001", amt("900000000.00")),
                        Fx.bal(A2, "010001", amt("1.00"))),
                List.of(Fx.xref("4111111111111111", A1), Fx.xref("4222222222222222", A2)),
                List.of(acct(A1, "9900000000.00"), acct(A2, "1.00")),
                List.of(Fx.disc("DEFAULT", "010001", rate("100.00")), Fx.disc("DEFAULT", "020001", rate("100.00"))));
        // 9,900,000,000.00 + 150,000,000.00 = 10,050,000,000.00 -> wraps to 50,000,000.00
        BigDecimal b = Zoned.decode(Fx.lines(r.out().resolve("ACCTFILE.txt")).get(0).substring(12, 24));
        assertEquals(new BigDecimal("50000000.00"), b);
    }

    @Test
    void totalWrapsAt1e9() throws IOException {
        // 2 cats, each monthly 600,000,000.00 (7.2e9 x ... too big for bal) -> use rate 9999.99 on 72,000,000.00:
        // 72e6 x 9999.99 / 1200 = 599,999,400.00 ; two of them = 1,199,998,800.00 -> total wraps to 199,998,800.00
        Fx.Result r = run(tmp,
                List.of(Fx.bal(A1, "010001", amt("72000000.00")), Fx.bal(A1, "020001", amt("72000000.00")),
                        Fx.bal(A2, "010001", amt("1.00"))),
                List.of(Fx.xref("4111111111111111", A1), Fx.xref("4222222222222222", A2)),
                List.of(acct(A1, "0.00"), acct(A2, "1.00")),
                List.of(Fx.disc("DEFAULT", "010001", rate("9999.99")), Fx.disc("DEFAULT", "020001", rate("9999.99"))));
        BigDecimal b = Zoned.decode(Fx.lines(r.out().resolve("ACCTFILE.txt")).get(0).substring(12, 24));
        assertEquals(new BigDecimal("199998800.00"), b);
    }

    @Test
    void unsortedTcatbalIsProcessedInKeyOrder() throws IOException {
        Fx.Result r = run(tmp,
                List.of(Fx.bal(A2, "010001", amt("1200.00")), Fx.bal(A1, "010001", amt("1200.00"))),
                List.of(Fx.xref("4111111111111111", A1), Fx.xref("4222222222222222", A2)),
                List.of(acct(A1, "100.00"), acct(A2, "200.00")),
                List.of(Fx.disc("DEFAULT", "010001", rate("12.00"))));
        List<String> t = Fx.lines(r.out().resolve("TRANSACT.txt"));
        assertTrue(t.get(0).contains("Int. for a/c " + A1));
        assertEquals(bal("112.00"), Fx.lines(r.out().resolve("ACCTFILE.txt")).get(0).substring(12, 24));
    }

    @Test
    void emptyTcatbalCopiesAccountsUnchanged() throws IOException {
        Fx.Result r = run(tmp, List.of(), List.of(Fx.xref("4111111111111111", A1)), List.of(acct(A1, "100.00")),
                List.of(Fx.disc("DEFAULT", "010001", rate("12.00"))));
        assertEquals(0, r.rc());
        assertEquals("START OF EXECUTION OF PROGRAM CBACT04C\nEND OF EXECUTION OF PROGRAM CBACT04C\n", r.stdout());
        assertEquals(List.of(), Fx.lines(r.out().resolve("TRANSACT.txt")));
        assertEquals(List.of(acct(A1, "100.00")), Fx.lines(r.out().resolve("ACCTFILE.txt")));
    }
}
