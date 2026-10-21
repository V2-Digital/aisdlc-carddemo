package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
    private void compare(Fixture f, String scenario) throws IOException {
        Path g = Fixture.GOLDEN.resolve(scenario);
        assertEquals(Fixture.slurp(g.resolve("RC.txt")).trim(), String.valueOf(f.rc), "RC");
        assertEquals(Fixture.slurp(g.resolve("TRANSACT.txt")), f.read("TRANSACT"), "TRANSACT");
        assertEquals(Fixture.slurp(g.resolve("ACCTFILE.txt")), f.read("ACCTFILE"), "ACCTFILE");
        assertEquals(Fixture.slurp(g.resolve("SYSOUT.txt")), f.sysout, "SYSOUT");
    }

    @Test void stock(@TempDir Path tmp) throws IOException {
        Fixture f = new Fixture(tmp);
        f.stock(null);
        f.run();
        compare(f, "CBACT04C");
    }

    @Test void afterPosting(@TempDir Path tmp) throws IOException {
        Fixture f = new Fixture(tmp);
        f.stock(Fixture.GOLDEN.resolve("CBTRN02C"));
        f.run();
        compare(f, "CBACT04C-after-CBTRN02C");
    }
}
