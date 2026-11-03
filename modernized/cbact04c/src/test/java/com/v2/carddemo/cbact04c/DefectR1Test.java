package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterization of legacy defects R1 (last account never rewritten) and R2 (no fees). */
class DefectR1Test {
    @Test
    void r1_lastAccountNotRewritten_butItsTransactionIsWritten(@TempDir Path tmp) throws IOException {
        Path in = Files.createDirectory(tmp.resolve("in"));
        Fx.stage(in, true);
        Path out = tmp.resolve("out");
        assertEquals(0, Fx.run(in, out));

        String id = "00000000050";
        List<String> tx = Files.readAllLines(out.resolve("TRANSACT.txt"));
        String last = tx.stream().filter(l -> l.contains("Int. for a/c " + id)).findFirst().orElseThrow();
        assertEquals("0000000187G", Records.field(last, 133, 11));

        String before = acct(Fx.golden("CBTRN02C").resolve("ACCTFILE.txt"), id);
        String after = acct(out.resolve("ACCTFILE.txt"), id);
        assertEquals(before, after, "R1: account 50 must be unchanged");
        assertEquals("00000019458G", Records.field(after, 13, 12));

        // contrast: account 1 IS rewritten
        String a1 = acct(out.resolve("ACCTFILE.txt"), "00000000001");
        assertEquals("00000013026F", Records.field(a1, 13, 12));
        assertEquals("00000000000{", Records.field(a1, 79, 12));
    }

    private static String acct(Path f, String id) throws IOException {
        return Files.readAllLines(f).stream().filter(l -> l.startsWith(id)).findFirst().orElseThrow();
    }
}
