package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransactLayoutTest {
    @TempDir Path tmp;

    @Test void layoutAndTimestamps() throws Exception {
        Support s = Support.run(Support.EDGE.resolve("ok"), tmp);
        for (int i = 0; i < s.transact.size(); i++) {
            String t = s.transact.get(i);
            assertEquals(350, t.length());
            assertEquals("2022071800" + String.format("%06d", i + 1), t.substring(0, 16));
            assertEquals("010005System    ", t.substring(16, 32));
            assertTrue(t.substring(32, 32 + 12 + 11).startsWith("Int. for a/c 0000000000"));
            assertTrue(t.substring(56, 132).isBlank(), "desc tail");
            assertEquals("000000000", t.substring(143, 152));
            assertTrue(t.substring(152, 262).isBlank(), "merchant name/city/zip");
            assertEquals("2022-07-18-00.00.00.000000", t.substring(278, 304));
            assertEquals(t.substring(278, 304), t.substring(304, 330));
            assertTrue(t.substring(330, 350).isBlank(), "filler");
        }
    }

    @Test void timestampFollowsInjectedClock() throws Exception {
        Path in = Support.EDGE.resolve("ok");
        var out = new java.io.ByteArrayOutputStream();
        new Cbact04c(in, tmp, "2023123123", Main.clock("2023123123594512"),
                new java.io.PrintStream(out), new java.io.PrintStream(out)).run();
        String t = java.nio.file.Files.readAllLines(tmp.resolve("TRANSACT.txt")).get(0);
        assertEquals("2023-12-31-23.59.45.120000", t.substring(278, 304));
        assertEquals("2023123123000001", t.substring(0, 16));
    }
}
