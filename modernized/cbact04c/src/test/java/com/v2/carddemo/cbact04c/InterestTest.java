package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterestTest {
    @Test
    void truncatesTowardZero_acct2_is_29_24_not_29_25(@TempDir Path in, @TempDir Path out) throws Exception {
        Fx.inputs(in);
        Fx f = Fx.run(in, out);
        List<String> t = Fx.lines(out.resolve("TRANSACT.txt"));
        assertEquals("0000000145F", t.get(0).substring(132, 143)); // acct 1: 14.56
        assertTrue(t.get(1).startsWith("2022071800000002"));
        assertEquals("0000000292D", t.get(1).substring(132, 143)); // acct 2: 29.24 (29.2496)
        assertEquals(50, t.size());
        assertEquals(0, f.rc);
    }
}
