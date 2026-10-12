package com.v2.carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GoldenEquivalenceTest {
    @Test
    void stockInputsMatchGolden() throws Exception {
        Path root = Fx.repoRoot(), data = root.resolve("app/data/ASCII"), gold = root.resolve("legacy/golden/CBTRN02C");
        Path d = Files.createTempDirectory("golden");
        Files.copy(data.resolve("dailytran.txt"), d.resolve("DALYTRAN"));
        Files.copy(data.resolve("cardxref.txt"), d.resolve("XREFFILE"));
        Files.copy(data.resolve("acctdata.txt"), d.resolve("ACCTFILE"));
        Files.copy(data.resolve("tcatbal.txt"), d.resolve("TCATBALF"));
        Fx.Result r = Fx.run(d);
        assertEquals(Integer.parseInt(Files.readString(gold.resolve("RC.txt")).trim()), r.rc());
        assertEquals(4, r.rc());
        for (String dd : new String[] {"TRANFILE", "DALYREJS", "ACCTFILE", "TCATBALF"}) {
            assertArrayEquals(Files.readAllBytes(gold.resolve(dd + ".txt")), Files.readAllBytes(d.resolve(dd)), dd);
        }
        assertEquals(262, r.lines("TRANFILE").size());
        assertEquals(38, r.lines("DALYREJS").size());
        assertEquals(50, r.lines("ACCTFILE").size());
        assertEquals(100, r.lines("TCATBALF").size());
        assertEquals(Files.readString(gold.resolve("SYSOUT.txt")), r.out());
        assertEquals(54, r.out().split("\n").length);
    }
}
