package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GoldenEquivalenceTest {
    private void compare(Support.Result r, String scenario, int lines) throws Exception {
        assertEquals(0, r.rc());
        for (String f : new String[] {"TRANSACT", "ACCTFILE"}) {
            assertArrayEquals(Files.readAllBytes(Support.golden(scenario, f + ".txt")), r.file(f), f);
        }
        byte[] goldSys = Files.readAllBytes(Support.golden(scenario, "SYSOUT.txt"));
        assertArrayEquals(goldSys, r.sysout().getBytes("ISO-8859-1"), "SYSOUT");
        assertEquals(lines, Files.readAllLines(Support.golden(scenario, "SYSOUT.txt")).size());
        assertEquals(Files.readString(Support.golden(scenario, "RC.txt")).trim(), String.valueOf(r.rc()));
    }

    @Test
    void stock(@TempDir Path dir) throws Exception {
        compare(Support.run(dir, Support.stock(), "2022071800"), "CBACT04C", 152);
    }

    @Test
    void afterCbtrn02c(@TempDir Path dir) throws Exception {
        Map<String, Path> in = Support.stock();
        in.put("ACCTFILE", Support.golden("CBTRN02C", "ACCTFILE.txt"));
        in.put("TCATBALF", Support.golden("CBTRN02C", "TCATBALF.txt"));
        compare(Support.run(dir, in, "2022071800"), "CBACT04C-after-CBTRN02C", 302);
    }
}
