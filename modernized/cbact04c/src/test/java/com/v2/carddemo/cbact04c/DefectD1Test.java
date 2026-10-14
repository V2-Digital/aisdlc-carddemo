package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** D1: the last account in TCATBALF is never rewritten (legacy defect, preserved). */
class DefectD1Test {
    @Test
    void lastAccountNotRewritten_butItsTransactionIsWritten(@TempDir Path in, @TempDir Path out) throws Exception {
        Fx.inputs(in);
        Fx.run(in, out);
        List<String> before = Fx.lines(in.resolve("ACCTFILE.txt"));
        List<String> after = Fx.lines(out.resolve("ACCTFILE.txt"));
        assertEquals(before.get(49), after.get(49), "acct 50 must be byte-identical to input");
        assertTrue(after.get(49).startsWith("00000000050"));
        assertNotEquals(before.get(48), after.get(48), "acct 49 is rewritten");
        List<String> t = Fx.lines(out.resolve("TRANSACT.txt"));
        String last = t.get(t.size() - 1);
        assertTrue(last.startsWith("2022071800000050"));
        assertEquals("0000000187G", last.substring(132, 143)); // 18.77 posted to no balance
    }
}
