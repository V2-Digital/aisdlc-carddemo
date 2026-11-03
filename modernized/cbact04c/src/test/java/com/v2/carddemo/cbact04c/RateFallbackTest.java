package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RateFallbackTest {
    @TempDir Path tmp;

    @Test void groupSpecificRateUsedWithoutNoise() throws Exception {
        Support s = Support.run(Support.EDGE.resolve("ok"), tmp);
        assertEquals("0000001666F", Support.amount(s.transact.get(2))); // 1000.00 @ 2.00% = 16.66, not 15%
        // acct 3 / cat 0001 hits GRPX: the only DEFAULT fallback for acct 3 is cat 0002 -> 5 misses in total
        assertEquals(5, s.sysout.lines().filter(l -> l.equals("DISCLOSURE GROUP RECORD MISSING")).count());
    }

    @Test void missingGroupFallsBackToDefault() throws Exception {
        Support s = Support.run(Support.EDGE.resolve("ok"), tmp);
        assertEquals("0000002083C", Support.amount(s.transact.get(3))); // 1000.00 @ DEFAULT 02/0002 2.50%
        assertTrue(s.sysout.contains("TRY WITH DEFAULT GROUP CODE"));
    }

    @Test void defaultMissingAbends16() throws Exception {
        Support s = Support.run(Support.EDGE.resolve("abend_default"), tmp);
        assertEquals(16, s.rc);
        assertTrue(s.err.contains("CEE3ABD: ABEND U0999"));
        assertTrue(s.sysout.contains("ERROR READING DEFAULT DISCLOSURE GROUP"));
        assertEquals(1, s.transact.size()); // only the first account's record written before the abend
    }

    @Test void xrefMissingAbends16() throws Exception {
        Support s = Support.run(Support.EDGE.resolve("abend_xref"), tmp);
        assertEquals(16, s.rc);
        assertTrue(s.sysout.contains("ACCOUNT NOT FOUND: 00000000099"));
        assertEquals(1, s.transact.size());
    }

    @Test void accountMissingAbends16() throws Exception {
        Path in = tmp.resolve("in");
        java.nio.file.Files.createDirectories(in);
        for (String f : new String[] {"TCATBALF", "XREFFILE", "DISCGRP"})
            java.nio.file.Files.copy(Support.EDGE.resolve("ok/" + f + ".txt"), in.resolve(f + ".txt"));
        java.nio.file.Files.writeString(in.resolve("ACCTFILE.txt"), "");
        Support s = Support.run(in, tmp.resolve("out"));
        assertEquals(16, s.rc);
        assertFalse(s.sysout.contains("END OF EXECUTION"));
        assertEquals(0, s.transact.size());
    }
}
