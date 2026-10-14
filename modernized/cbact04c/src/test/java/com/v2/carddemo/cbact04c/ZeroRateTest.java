package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ZeroRateTest {
    @Test
    void zeroRateWritesNoTxnButAccountIsRewrittenWithCycleFieldsZero(@TempDir Path in, @TempDir Path out) throws Exception {
        Fx.inputs(in);
        // acct 1 only has a 03/0001 row (rate 0) followed by acct 2 (so acct 1 is rewritten)
        String t1 = "00000000001030001" + "0000000707P";
        String t2 = "00000000002010001" + "0000000000{";
        Fx.put(in, "TCATBALF", Fx.pad50(t1) + "\n" + Fx.pad50(t2) + "\n");
        Fx f = Fx.run(in, out);
        assertEquals(0, f.rc);
        // acct 2 is last -> balance 0 -> still rate 15% -> 0 interest txn; acct 1 gave none
        List<String> t = Fx.lines(out.resolve("TRANSACT.txt"));
        assertEquals(1, t.size());
        assertTrue(t.get(0).startsWith("2022071800000001"));
        assertTrue(t.get(0).contains("Int. for a/c 00000000002"));
        String a1 = Fx.lines(out.resolve("ACCTFILE.txt")).get(0);
        assertEquals("00000000000{", a1.substring(78, 90));
        assertEquals("00000000000{", a1.substring(90, 102));
        assertEquals(Fx.lines(in.resolve("ACCTFILE.txt")).get(0).substring(12, 24), a1.substring(12, 24), "balance unchanged");
    }
}
