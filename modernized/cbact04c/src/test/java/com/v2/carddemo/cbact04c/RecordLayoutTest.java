package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.Support.*;
import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordLayoutTest {
    @Test
    void unsetBytesAreSpaces(@TempDir Path out) throws IOException {
        Path prev = GOLDEN.resolve("CBTRN02C");
        Result r = run(prev.resolve("TCATBALF.txt"), ASCII.resolve("cardxref.txt"), ASCII.resolve("discgrp.txt"),
                prev.resolve("ACCTFILE.txt"), out);
        assertEquals(0, r.rc());
        var recs = text(out.resolve("TRANSACT.txt")).split("\n");
        assertEquals(50, recs.length);
        for (String t : recs) {
            assertEquals(350, t.length());
            assertTrue(t.startsWith("2022071800"));
            assertEquals("010005System    Int. for a/c ", t.substring(16, 45));
            assertEquals(" ".repeat(75), t.substring(56, 131).substring(0, 75)); // desc tail
            assertEquals(" ".repeat(100 - 24), t.substring(32 + 24, 132));
            assertEquals("000000000", t.substring(143, 152));
            assertEquals(" ".repeat(110), t.substring(152, 262)); // merchant name/city/zip
            assertEquals(" ".repeat(20), t.substring(330, 350));
            assertEquals(TS, t.substring(278, 304));
            assertEquals(TS, t.substring(304, 330));
        }
    }
}
