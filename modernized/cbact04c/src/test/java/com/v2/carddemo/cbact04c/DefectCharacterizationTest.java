package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Pins legacy defects on purpose. A later fix CR flips the matching test. */
class DefectCharacterizationTest {
    @TempDir Path tmp;

    @Test
    void d1_lastAccountNotRewritten() throws IOException {
        Fixture f = Fixture.two(tmp, "1200.00", "10.00", "");
        String last = f.acct.get(1);
        assertEquals(0, f.run());
        assertEquals(2, f.lines("TRANSACT").size()); // interest was charged for both accounts...
        assertEquals(last, f.lines("ACCTFILE").get(1)); // ...but the last account is unchanged (bal, cycle credit/debit)
        assertEquals(new BigDecimal("1010.00"), Zoned.decode(f.lines("ACCTFILE").get(0).substring(12, 24)));
    }

    @Test
    void d2_noFeesCharged() throws IOException {
        Fixture f = Fixture.two(tmp, "1200.00", "10.00", "");
        f.run();
        // exactly one TRAN per non-zero-rate category, all of interest type 01/0005
        assertEquals(2, f.lines("TRANSACT").size());
        for (String t : f.lines("TRANSACT")) {
            assertEquals("010005", t.substring(16, 22));
        }
    }

    @Test
    void d3_interestTruncatedNotRounded() throws IOException {
        Fixture f = Fixture.two(tmp, "1164.67", "15.00", "");
        f.run();
        assertEquals(new BigDecimal("14.55"), Zoned.decode(f.lines("TRANSACT").get(0).substring(132, 143))); // rounded: 14.56
    }

    @Test
    void d4_discgrpOpenErrorSaysDalyRejects() throws IOException {
        Fixture f = Fixture.two(tmp, "1200.00", "10.00", "");
        FileIo.write(f.file("TCATBALF"), f.tcat);
        FileIo.write(f.file("XREFFILE"), f.xref);
        FileIo.write(f.file("ACCTFILE"), f.acct);
        java.io.ByteArrayOutputStream buf = new java.io.ByteArrayOutputStream();
        int rc = Main.run(new String[] {"--parm", "2022071800", "--TCATBALF", f.file("TCATBALF").toString(),
                "--XREFFILE", f.file("XREFFILE").toString(), "--DISCGRP", tmp.resolve("missing.txt").toString(),
                "--ACCTFILE", f.file("ACCTFILE").toString(), "--TRANSACT", f.file("TRANSACT").toString()}, null,
                new java.io.PrintStream(buf, true));
        assertEquals(16, rc);
        String out = buf.toString();
        assertTrue(out.contains("ERROR OPENING DALY REJECTS FILE\nFILE STATUS IS: NNNN0035\nABENDING PROGRAM\n"), out);
    }
}
