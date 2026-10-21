package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
    @TempDir Path tmp;

    private void compare(Path in, String scenario) throws IOException {
        Fx.Result r = Fx.run(in, tmp.resolve("out"));
        Path g = Fx.GOLDEN.resolve(scenario);
        assertEquals(Files.readString(g.resolve("RC.txt")).trim(), String.valueOf(r.rc()));
        assertEquals(Files.readString(g.resolve("SYSOUT.txt"), StandardCharsets.ISO_8859_1), r.sysout(), "SYSOUT");
        for (String f : new String[] {"TRANSACT.txt", "ACCTFILE.txt"}) {
            byte[] exp = Files.readAllBytes(g.resolve(f));
            byte[] act = Files.readAllBytes(r.out().resolve(f));
            org.junit.jupiter.api.Assertions.assertArrayEquals(exp, act, f);
        }
    }

    @Test
    void stock() throws IOException {
        Path in = Files.createDirectory(tmp.resolve("in"));
        compare(Fx.stockInputs(in), "CBACT04C");
    }

    @Test
    void afterCbtrn02c() throws IOException {
        Path in = Fx.stockInputs(Files.createDirectory(tmp.resolve("in")));
        Path c = Fx.GOLDEN.resolve("CBTRN02C");
        Files.copy(c.resolve("ACCTFILE.txt"), in.resolve("ACCTFILE.txt"),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(c.resolve("TCATBALF.txt"), in.resolve("TCATBALF.txt"),
            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        compare(in, "CBACT04C-after-CBTRN02C");
    }

    @Test
    void deterministic() throws IOException {
        Path in = Fx.stockInputs(Files.createDirectory(tmp.resolve("in")));
        Fx.run(in, tmp.resolve("a"));
        Fx.run(in, tmp.resolve("b"));
        assertEquals(Files.readString(tmp.resolve("a/TRANSACT.txt")), Files.readString(tmp.resolve("b/TRANSACT.txt")));
        assertEquals(Files.readString(tmp.resolve("a/ACCTFILE.txt")), Files.readString(tmp.resolve("b/ACCTFILE.txt")));
    }
}
