package carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenTest {
    private static final Path GOLDEN = Path.of(System.getProperty("golden.dir"));
    private static final Path DATA = Path.of(System.getProperty("data.dir"));

    private void check(Path in, String scenario, Path tmp) throws Exception {
        for (String[] m : new String[][] {{"TCATBALF", "tcatbal"}, {"XREFFILE", "cardxref"},
                {"ACCTFILE", "acctdata"}, {"DISCGRP", "discgrp"}}) {
            Path src = in.equals(DATA) ? DATA.resolve(m[1] + ".txt") : in.resolve(m[0] + ".txt");
            if (!Files.exists(src)) {
                src = DATA.resolve(m[1] + ".txt");
            }
            Files.copy(src, tmp.resolve(m[0] + ".txt"));
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        int rc = Cbact04c.run(tmp, "2022071800", () -> "2022071800000000",
                new PrintStream(bos, true, StandardCharsets.ISO_8859_1));
        Path g = GOLDEN.resolve(scenario);
        assertEquals(Integer.parseInt(Files.readString(g.resolve("RC.txt")).trim()), rc);
        for (String f : new String[] {"TRANSACT", "ACCTFILE"}) {
            assertArrayEquals(Files.readAllBytes(g.resolve(f + ".txt")),
                    Files.readAllBytes(tmp.resolve(f + ".txt")), f);
        }
        assertArrayEquals(Files.readAllBytes(g.resolve("SYSOUT.txt")), bos.toByteArray(), "SYSOUT");
    }

    @Test
    void stock(@TempDir Path tmp) throws Exception {
        check(DATA, "CBACT04C", tmp);
    }

    @Test
    void afterPost(@TempDir Path tmp) throws Exception {
        check(GOLDEN.resolve("CBTRN02C"), "CBACT04C-after-CBTRN02C", tmp);
    }
}
