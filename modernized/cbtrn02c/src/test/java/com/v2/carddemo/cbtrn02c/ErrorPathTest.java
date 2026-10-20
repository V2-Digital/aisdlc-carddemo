package com.v2.carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ErrorPathTest {
    @Test void missingDdAbends16(@TempDir Path w) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        int rc = new Cbtrn02c(Map.of(), java.time.Clock.systemUTC(), new PrintStream(o), new PrintStream(e)).run();
        assertEquals(16, rc);
        String s = o.toString();
        assertTrue(s.startsWith("START OF EXECUTION OF PROGRAM CBTRN02C\n"));
        assertTrue(s.contains("ERROR OPENING DALYTRAN\nFILE STATUS IS: NNNN0035\nABENDING PROGRAM"));
        assertTrue(e.toString().contains("CEE3ABD: ABEND U0999"));
    }

    @Test void clockFromEnvPrefersCarddemoThenCob() {
        assertEquals(java.time.Instant.parse("2022-07-18T00:00:00Z"),
            Cbtrn02c.clockFrom(Map.of("CARDDEMO_CURRENT_DATE", "2022071800000000", "COB_CURRENT_DATE", "2000010100000000")).instant());
        assertEquals(java.time.Instant.parse("2000-01-01T00:00:00Z"),
            Cbtrn02c.clockFrom(Map.of("COB_CURRENT_DATE", "2000010100000000")).instant());
    }
}
