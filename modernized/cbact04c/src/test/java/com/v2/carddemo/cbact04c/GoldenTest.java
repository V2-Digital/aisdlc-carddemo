package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
    private void compare(Path out, Path golden) throws Exception {
        for (String f : new String[] {"TRANSACT.txt", "ACCTFILE.txt", "SYSOUT.txt", "RC.txt"}) {
            assertArrayEquals(Files.readAllBytes(golden.resolve(f)), Files.readAllBytes(out.resolve(f)), f);
        }
    }

    @Test void stock(@TempDir Path out) throws Exception {
        Path r = Fx.repo();
        int rc = new InterestCalc(r.resolve("app/data/ASCII"), null, "2022071800", Fx.CLOCK).run(out);
        assertEquals(0, rc);
        compare(out, r.resolve("legacy/golden/CBACT04C"));
        assertEquals(50, Files.readAllLines(out.resolve("TRANSACT.txt")).size());
        assertEquals(152, Files.readAllLines(out.resolve("SYSOUT.txt")).size());
    }

    @Test void afterCbtrn02c(@TempDir Path out) throws Exception {
        Path r = Fx.repo();
        int rc = new InterestCalc(r.resolve("app/data/ASCII"), r.resolve("legacy/golden/CBTRN02C"), "2022071800", Fx.CLOCK).run(out);
        assertEquals(0, rc);
        compare(out, r.resolve("legacy/golden/CBACT04C-after-CBTRN02C"));
        assertEquals(302, Files.readAllLines(out.resolve("SYSOUT.txt")).size());
    }

    @Test void cliEntryPointMatchesGolden(@TempDir Path out) throws Exception {
        Path r = Fx.repo();
        int rc = Main.run(new String[] {"--data", r.resolve("app/data/ASCII").toString(), "--out", out.toString(),
                "--parm", "2022071800", "--clock", "2022071800000000"});
        assertEquals(0, rc);
        compare(out, r.resolve("legacy/golden/CBACT04C"));
    }
}
