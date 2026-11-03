package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenStockTest {
    @Test
    void stockInputsMatchGolden(@TempDir Path tmp) throws IOException {
        Path in = Files.createDirectory(tmp.resolve("in"));
        Fx.stage(in, false);
        Path out = tmp.resolve("out");
        assertEquals(0, Fx.run(in, out));
        Fx.assertSame(Fx.golden("CBACT04C"), out, "TRANSACT", "ACCTFILE", "SYSOUT", "RC");
        assertEquals(50 * 350, Files.size(out.resolve("TRANSACT.txt")) - 50);
        assertEquals(50 * 300, Files.size(out.resolve("ACCTFILE.txt")) - 50);
    }
}
