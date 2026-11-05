package carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterization tests: these pin legacy defects on purpose (see README D1-D6). */
class DefectTest {
    @Test
    void lastAccountNotRewritten_D1_butItsTransactionIsWritten(@TempDir Path d) throws IOException {
        Path gold = Fx.ROOT.resolve("legacy/golden");
        Fx fx = new Fx(d);
        fx.copy(gold.resolve("CBTRN02C/TCATBALF.txt"), "TCATBALF");
        fx.copy(Fx.ROOT.resolve("app/data/ASCII/cardxref.txt"), "XREFFILE");
        fx.copy(Fx.ROOT.resolve("app/data/ASCII/discgrp.txt"), "DISCGRP");
        fx.copy(gold.resolve("CBTRN02C/ACCTFILE.txt"), "ACCTFILE");
        fx.run();
        assertTrue(fx.lines("TRANSACT").stream().anyMatch(t -> t.startsWith("2022071800000050")
                && t.contains("Int. for a/c 00000000050") && t.substring(132, 143).equals("0000000187G")));
        var before = Files.readAllLines(gold.resolve("CBTRN02C/ACCTFILE.txt"));
        var after = fx.lines("ACCTFILE");
        assertEquals(before.get(49), after.get(49), "acct 50 unchanged");
        long changed = 0;
        for (int i = 0; i < 50; i++) {
            if (!before.get(i).equals(after.get(i))) {
                changed++;
            }
        }
        assertEquals(49, changed);
    }

    @Test
    void groupIdBlankInSampleData_D6_everyLookupFallsBack(@TempDir Path d) throws IOException {
        Fx fx = new Fx(d).basic("0000100000{", "0000100000{", "").run();
        assertEquals(2, fx.sysout.split("TRY WITH DEFAULT GROUP CODE", -1).length - 1);
    }
}
