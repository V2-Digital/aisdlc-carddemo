package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Characterization tests: these pin legacy defects on purpose. Do not "fix" them here (see README D1-D5). */
class DefectTest {
    private static String rec(List<String> lines, String prefix) {
        return lines.stream().filter(l -> l.startsWith(prefix)).findFirst().orElseThrow();
    }

    @Test
    void D1_lastAccountNotRewritten_yetItsInterestIsCharged() throws IOException {
        Path gold = Fx.ROOT.resolve("legacy/golden/CBTRN02C");
        Fx.Run r = Fx.runStock(Path.of("target/out/defect"), gold);
        List<String> after = Files.readAllLines(r.out().resolve("ACCTFILE.txt"));
        List<String> before = Files.readAllLines(gold.resolve("ACCTFILE.txt"));
        String a50 = rec(after, "00000000050");
        assertEquals(rec(before, "00000000050"), a50, "account 50 must be byte-identical (never rewritten)");
        assertEquals("00000019458G", a50.substring(12, 24));
        assertEquals("0000000187G", rec(Files.readAllLines(r.out().resolve("TRANSACT.txt")), "2022071800000050").substring(132, 143));
        assertNotEquals(rec(before, "00000000049"), rec(after, "00000000049"), "earlier accounts are updated");
    }

    @Test
    void D1_exactlyFortyNineAccountsChange() throws IOException {
        Path gold = Fx.ROOT.resolve("legacy/golden/CBTRN02C");
        Fx.Run r = Fx.runStock(Path.of("target/out/defect2"), gold);
        List<String> after = Files.readAllLines(r.out().resolve("ACCTFILE.txt"));
        List<String> before = Files.readAllLines(gold.resolve("ACCTFILE.txt"));
        long changed = 0;
        for (int i = 0; i < before.size(); i++) {
            if (!before.get(i).equals(after.get(i))) {
                changed++;
            }
        }
        assertEquals(49, changed);
    }

    @Test
    void D5_blankGroupIdAlwaysFallsBackToDefault() throws IOException {
        Fx.Run r = Fx.runStock(Path.of("target/out/d5"), null);
        long misses = r.sysout().lines().filter("DISCLOSURE GROUP RECORD MISSING"::equals).count();
        assertEquals(50, misses);
    }

    @Test
    void D3_noFeesTransactionsOnly_typeCat0005Interest() throws IOException {
        Fx.Run r = Fx.runStock(Path.of("target/out/d3"), null);
        for (String l : Files.readAllLines(r.out().resolve("TRANSACT.txt"))) {
            assertEquals("010005System", l.substring(16, 28));
        }
    }
}
