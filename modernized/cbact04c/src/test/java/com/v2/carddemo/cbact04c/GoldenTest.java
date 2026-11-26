package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.Support.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GoldenTest {
    private void compare(Path in, String scenario, int records) throws Exception {
        Support.Result r = Support.run("2022071800", in);
        assertEquals(0, r.rc());
        assertEquals(read(golden(scenario, "RC.txt")).trim(), String.valueOf(r.rc()));
        String t = r.file("TRANSACT");
        assertEquals(records * 351, t.length());
        assertEquals(read(golden(scenario, "TRANSACT.txt")), t);
        String a = r.file("ACCTFILE");
        assertEquals(50 * 301, a.length());
        assertEquals(read(golden(scenario, "ACCTFILE.txt")), a);
        assertEquals(read(golden(scenario, "SYSOUT.txt")), r.sysout());
    }

    @Test
    void stock() throws Exception {
        compare(stockInputs(), "CBACT04C", 50);
    }

    @Test
    void afterCbtrn02c() throws Exception {
        Path in = stockInputs();
        Files.copy(golden("CBTRN02C", "ACCTFILE.txt"), in.resolve("ACCTFILE.txt"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(golden("CBTRN02C", "TCATBALF.txt"), in.resolve("TCATBALF.txt"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        compare(in, "CBACT04C-after-CBTRN02C", 50);
    }
}
