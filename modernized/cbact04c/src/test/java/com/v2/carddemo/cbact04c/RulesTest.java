package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RulesTest {
    @TempDir Path tmp;
    static final String A1 = "00000000001", A2 = "00000000002", A3 = "00000000003";

    private Fx.Result run(List<String> tcat, List<String> disc) throws IOException {
        return Fx.simple(tmp, tcat,
                List.of(Fx.acct(A1, "100.00", "5.00", "-2.00", ""), Fx.acct(A2, "200.00", "0", "0", ""),
                        Fx.acct(A3, "300.00", "0", "0", "")), disc);
    }

    @Test
    void rateZeroWritesNoTransactionAndNoCounterIncrement() throws IOException {
        Fx.Result r = run(List.of(Fx.tcat(A1, "03", "0001", "50.00"), Fx.tcat(A2, "01", "0001", "120.00")),
                List.of(Fx.disc("DEFAULT", "03", "0001", "0"), Fx.disc("DEFAULT", "01", "0001", "12.00")));
        List<String> t = r.records("TRANSACT.txt");
        assertEquals(1, t.size());
        assertEquals("2022071800000001", t.get(0).substring(0, 16));
    }

    @Test
    void zeroBalanceNonZeroRateWritesTransaction() throws IOException {
        Fx.Result r = run(List.of(Fx.tcat(A1, "01", "0001", "0")), List.of(Fx.disc("DEFAULT", "01", "0001", "15.00")));
        assertEquals(1, r.records("TRANSACT.txt").size());
        assertEquals("0000000000{", r.records("TRANSACT.txt").get(0).substring(132, 143));
    }

    @Test
    void counterSpansAccountsAndFormatsAreExact() throws IOException {
        Fx.Result r = run(List.of(Fx.tcat(A1, "01", "0001", "100.00"), Fx.tcat(A2, "01", "0001", "100.00"),
                Fx.tcat(A3, "01", "0001", "100.00")), List.of(Fx.disc("DEFAULT", "01", "0001", "12.00")));
        List<String> t = r.records("TRANSACT.txt");
        assertEquals(3, t.size());
        for (int i = 0; i < 3; i++) {
            String x = t.get(i);
            assertEquals(350, x.length());
            assertEquals("202207180" + "0" + String.format("%06d", i + 1), x.substring(0, 16));
            assertEquals("010005System    Int. for a/c 0000000000" + (i + 1), x.substring(16, 56));
            assertEquals("2022-07-18-00.00.00.000000", x.substring(278, 304));
            assertEquals(x.substring(278, 304), x.substring(304, 330));
            assertEquals(" ".repeat(20), x.substring(330));
        }
    }

    @Test
    void groupSpecificHitPrintsNoMissingLines() throws IOException {
        Path in = Fx.dir(tmp, "in", List.of(Fx.tcat(A1, "01", "0001", "100.00")),
                List.of(Fx.xref("4000000000010000", A1)),
                List.of(Fx.disc("GRP", "01", "0001", "12.00")), List.of(Fx.acct(A1, "1.00", "0", "0", "GRP")));
        Fx.Result r = Fx.run(in, tmp.resolve("out"));
        assertEquals(0, r.rc());
        assertTrue(r.sysout().stream().noneMatch(s -> s.contains("MISSING")));
        assertEquals("0000000010{", r.records("TRANSACT.txt").get(0).substring(132, 143));
    }

    @Test
    void emptyTcatbal() throws IOException {
        Fx.Result r = run(List.of(), List.of(Fx.disc("DEFAULT", "01", "0001", "12.00")));
        assertEquals(0, r.rc());
        assertEquals(List.of("START OF EXECUTION OF PROGRAM CBACT04C", "END OF EXECUTION OF PROGRAM CBACT04C"), r.sysout());
        assertEquals(0, r.records("TRANSACT.txt").size());
    }
}
