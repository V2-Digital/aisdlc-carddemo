package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbendTest {
    @TempDir Path tmp;

    private static final String A1 = "00000000001", A2 = "00000000002";
    private static final String TAIL = "FILE STATUS IS: NNNN0023\nABENDING PROGRAM\n";

    private Support.Result run(List<String> xref, List<String> accts, List<String> disc) throws IOException {
        return Support.run(tmp,
                List.of(Support.tcat(A1, "01", "0001", "1200.00"), Support.tcat(A2, "01", "0001", "2400.00")),
                xref, accts, disc);
    }

    private final List<String> x2 = List.of(Support.xref("1111111111111111", A1), Support.xref("2222222222222222", A2));
    private final List<String> d = List.of(Support.disc("DEFAULT", "01", "0001", "12.00"));
    private final String a1 = Support.account(A1, "100.00", "5.00", "6.00", "");
    private final String a2 = Support.account(A2, "200.00", "7.00", "8.00", "");

    @Test
    void missingAccount() throws IOException {
        Support.Result r = run(x2, List.of(a1), d);
        assertEquals(16, r.rc());
        assertTrue(r.sysout().endsWith("ACCOUNT NOT FOUND: " + A2 + "\nERROR READING ACCOUNT FILE\n" + TAIL), r.sysout());
        // account 1 was updated before the break, one transaction written
        assertEquals(1, r.transact().size());
        assertEquals(Support.account(A1, "112.00", "0", "0", ""), r.acct().get(0));
    }

    @Test
    void missingXref() throws IOException {
        Support.Result r = run(List.of(x2.get(0)), List.of(a1, a2), d);
        assertEquals(16, r.rc());
        assertTrue(r.sysout().endsWith("ACCOUNT NOT FOUND: " + A2 + "\nERROR READING XREF FILE\n" + TAIL), r.sysout());
        assertEquals(1, r.transact().size());
        assertEquals(a2, r.acct().get(1));
    }

    @Test
    void missingDefaultDisclosureGroup() throws IOException {
        Support.Result r = run(x2, List.of(a1, a2), List.of(Support.disc("DEFAULT", "02", "0001", "12.00")));
        assertEquals(16, r.rc());
        assertTrue(r.sysout().contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
        assertTrue(r.sysout().endsWith("ERROR READING DEFAULT DISCLOSURE GROUP\n" + TAIL), r.sysout());
        assertEquals(0, r.transact().size());
        assertEquals(List.of(a1, a2), r.acct());
    }

    @Test
    void noTempFilesLeftBehind() throws IOException {
        run(x2, List.of(a1), d);
        try (var s = Files.list(tmp.resolve("out"))) {
            assertEquals(List.of("ACCTFILE.txt", "TRANSACT.txt"), s.map(p -> p.getFileName().toString()).sorted().toList());
        }
    }
}
