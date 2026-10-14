package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RateLookupTest {
    @TempDir Path tmp;

    @Test
    void groupSpecificRateIsUsedWithoutMissingLines_F1() throws IOException {
        Fixture f = Fixture.two(tmp, "1200.00", "10.00", "GRPX");
        f.disc.set(0, Fixture.disc("GRPX", "01", "0001", "12.00")); // group row 12%; DEFAULT row 10%
        assertEquals(0, f.run());
        assertFalse(f.sysout.contains("MISSING"));
        assertEquals(new BigDecimal("12.00"), Zoned.decode(f.lines("TRANSACT").get(0).substring(132, 143)));
    }

    @Test
    void fallsBackToDefault() throws IOException {
        Fixture f = Fixture.two(tmp, "1200.00", "10.00", "NOSUCH");
        f.disc.remove(0);
        assertEquals(0, f.run());
        assertTrue(f.sysout.contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
        assertEquals(new BigDecimal("10.00"), Zoned.decode(f.lines("TRANSACT").get(0).substring(132, 143)));
    }

    @Test
    void missingDefaultAbends() throws IOException {
        Fixture f = Fixture.two(tmp, "1200.00", "10.00", "");
        f.disc.clear();
        assertEquals(16, f.run());
        assertTrue(f.sysout.endsWith("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"
                + "ERROR READING DEFAULT DISCLOSURE GROUP\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), f.sysout);
    }

    @Test
    void missingAccountAbends() throws IOException {
        Fixture f = Fixture.two(tmp, "1200.00", "10.00", "");
        f.acct.remove(0);
        assertEquals(16, f.run());
        assertTrue(f.sysout.endsWith("ACCOUNT NOT FOUND: 00000000001\nERROR READING ACCOUNT FILE\n"
                + "FILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), f.sysout);
    }

    @Test
    void missingXrefAbends() throws IOException {
        Fixture f = Fixture.two(tmp, "1200.00", "10.00", "");
        f.xref.remove(0);
        assertEquals(16, f.run());
        assertTrue(f.sysout.endsWith("ACCOUNT NOT FOUND: 00000000001\nERROR READING XREF FILE\n"
                + "FILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"), f.sysout);
    }

    @Test
    void zeroRateWritesNothingAndDoesNotAdvanceSequence() throws IOException {
        Fixture f = Fixture.two(tmp, "1200.00", "10.00", "");
        f.tcat.add(0, Fixture.tcat("1", "03", "0001", "50.00"));
        f.disc.add(Fixture.disc("DEFAULT", "03", "0001", "0.00"));
        f.tcat.sort(null);
        assertEquals(0, f.run());
        assertEquals(2, f.lines("TRANSACT").size());
        assertEquals("2022071800000002", f.lines("TRANSACT").get(1).substring(0, 16));
    }
}
