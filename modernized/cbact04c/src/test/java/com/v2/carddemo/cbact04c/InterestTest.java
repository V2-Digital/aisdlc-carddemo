package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterestTest {
    @TempDir Path tmp;

    /** Two accounts so that account 1 is not "last" (D1) and its txn is checked. */
    @Test
    void truncatesNotRounds() throws IOException {
        Path in = Fx.stockInputs(Files.createDirectory(tmp.resolve("in")));
        // 3577.41 -> 357741 -> last digit 1 => "A"
        Fx.put(in, "TCATBALF", List.of(Fx.tcat("00000000001", "01", "0001", "0000035774A"),
            Fx.tcat("00000000002", "01", "0001", "0000000000{")));
        Fx.Result r = Fx.run(in, tmp.resolve("out"));
        assertEquals(0, r.rc());
        String t = Fx.lines(r.out().resolve("TRANSACT.txt")).get(0);
        // 3577.41 * 15 / 1200 = 44.7176 -> 44.71 (rounding would give 44.72)
        assertEquals("0000000447A", t.substring(132, 143));
        assertEquals("2022071800000001", t.substring(0, 16));
    }
}
