package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterization tests: these pin legacy DEFECTS. Do not "fix" without a new CR. */
class DefectTest {
    private Fx.Result afterPosting(Path tmp) throws Exception {
        Path g = Fx.GOLDEN.resolve("CBTRN02C");
        return Fx.run(tmp, Map.of("ACCTFILE", g.resolve("ACCTFILE.txt"), "TCATBALF", g.resolve("TCATBALF.txt")), Map.of());
    }

    @Test
    void F1_lastAccountIsNeverRewrittenButItsInterestIsPosted(@TempDir Path tmp) throws Exception {
        var r = afterPosting(tmp);
        List<String> in = java.nio.file.Files.readAllLines(Fx.GOLDEN.resolve("CBTRN02C/ACCTFILE.txt"));
        List<String> out = r.lines("ACCTFILE.txt");
        assertEquals("00000000050", out.get(49).substring(0, 11));
        assertEquals(in.get(49), out.get(49), "last account row must be unchanged");
        assertEquals("00000019458G", out.get(49).substring(12, 24));
        assertNotEquals(in.get(48), out.get(48), "earlier accounts ARE rewritten");
        String tx = r.lines("TRANSACT.txt").get(49);
        assertEquals("2022071800000050", tx.substring(0, 16));
        assertEquals("0000000187G", tx.substring(132, 143));
    }

    @Test
    void F4_cycleTotalsClearedForEveryRewrittenAccountExceptLast(@TempDir Path tmp) throws Exception {
        var r = afterPosting(tmp);
        List<String> out = r.lines("ACCTFILE.txt");
        for (int i = 0; i < 49; i++) {
            assertEquals("00000000000{", out.get(i).substring(78, 90), "cycle credit row " + i);
            assertEquals("00000000000{", out.get(i).substring(90, 102), "cycle debit row " + i);
        }
    }

    @Test
    void F3_blankGroupIdAlwaysWarnsAndFallsBackToDefault(@TempDir Path tmp) throws Exception {
        var r = Fx.run(tmp, Map.of(), Map.of());
        long n = r.sysout().lines().filter(l -> l.equals("DISCLOSURE GROUP RECORD MISSING")).count();
        assertEquals(50, n);
    }

    @Test
    void F6_transactionSuffixIsSixDigits(@TempDir Path tmp) throws Exception {
        var r = Fx.run(tmp, Map.of(), Map.of());
        assertEquals("2022071800000001", r.lines("TRANSACT.txt").get(0).substring(0, 16));
        assertEquals("2022071800000050", r.lines("TRANSACT.txt").get(49).substring(0, 16));
    }
}
