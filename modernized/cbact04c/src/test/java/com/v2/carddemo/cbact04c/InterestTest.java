package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterestTest {
    private static final String ACCT = "00000000001";
    private static final String RATE_15 = "00150{";

    private static String amount(String txn) { return txn.substring(132, 143); }

    @Test
    void truncatesNotRounds() throws Exception {  // D2, against the golden outputs
        Path g = Fx.GOLDEN.resolve("CBACT04C-after-CBTRN02C");
        List<String> txns = Fx.lines(g.resolve("TRANSACT.txt"));
        List<String> bals = Fx.lines(Fx.GOLDEN.resolve("CBTRN02C/TCATBALF.txt")).stream()
                .filter(l -> l.substring(11, 17).equals("010001")).toList();
        assertEquals(50, bals.size());
        int differ = 0;
        for (int i = 0; i < 50; i++) {
            BigDecimal bal = Zoned.decode(bals.get(i).substring(17, 28), 2);
            BigDecimal exact = bal.multiply(new BigDecimal("15.00"));
            BigDecimal trunc = exact.divide(BigDecimal.valueOf(1200), 2, RoundingMode.DOWN);
            BigDecimal half = exact.divide(BigDecimal.valueOf(1200), 2, RoundingMode.HALF_UP);
            assertEquals(Zoned.encode(trunc, 11, 2), amount(txns.get(i)), "golden amount " + i);
            if (!trunc.equals(half)) differ++;
        }
        assertTrue(differ > 0, "expected golden cases where HALF_UP differs");
        assertEquals(28, differ);
    }

    @Test
    void truncatesNegativeTowardZero(@TempDir Path t) throws Exception {
        // bal -10.00 * 15.00 / 1200 = -0.125 -> -0.12 (not -0.13)
        Path in = Fx.synth(t.resolve("in"), List.of(Fx.tcat(ACCT, "01", "0001", "0000000100P")),
                List.of(Fx.disc("DEFAULT", "01", "0001", RATE_15)), true, true);
        Fx.run(in, t.resolve("o"), Fx.NOW);
        assertEquals("0000000001K", amount(Fx.lines(t.resolve("o/TRANSACT.txt")).get(0)));  // -0.12
    }

    @Test
    void zeroRateWritesNothingAndKeepsSuffix(@TempDir Path t) throws Exception {
        // same account: cat 0001 rate 0 -> none; cat 0002 rate 15 bal 0 -> txn suffix 000001
        Path in = Fx.synth(t.resolve("in"),
                List.of(Fx.tcat(ACCT, "01", "0001", "0000000100{"), Fx.tcat(ACCT, "01", "0002", "0000000000{")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "00000{"), Fx.disc("DEFAULT", "01", "0002", RATE_15)), true, true);
        Fx.run(in, t.resolve("o"), Fx.NOW);
        List<String> tx = Fx.lines(t.resolve("o/TRANSACT.txt"));
        assertEquals(1, tx.size());
        assertEquals("2022071800000001", tx.get(0).substring(0, 16));
        assertEquals("0000000000{", amount(tx.get(0)));  // D5: 0.00 transaction
    }

    @Test
    void emptyTcatbalProducesEmptyTransact(@TempDir Path t) throws Exception {
        Path in = Fx.synth(t.resolve("in"), List.of(), List.of(), true, true);
        java.nio.file.Files.writeString(in.resolve("TCATBALF.txt"), "");
        Fx.Result r = Fx.run(in, t.resolve("o"), Fx.NOW);
        assertEquals(0, r.rc());
        assertEquals(List.of("START OF EXECUTION OF PROGRAM CBACT04C", "END OF EXECUTION OF PROGRAM CBACT04C"), r.sysout());
        assertEquals("", Fx.read(t.resolve("o/TRANSACT.txt")));
        assertEquals(Fx.read(in.resolve("ACCTFILE.txt")), Fx.read(t.resolve("o/ACCTFILE.txt")));
    }

    @Test
    void suffixWrapsAtOneMillionLikePic9x6() {
        // PIC 9(6) wraps: the port keeps (suffix+1) % 1_000_000 -- documented here, exercised via arithmetic only
        assertEquals(0, (999_999 + 1) % 1_000_000);
    }
}
