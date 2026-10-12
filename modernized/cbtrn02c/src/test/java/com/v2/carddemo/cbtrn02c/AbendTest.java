package com.v2.carddemo.cbtrn02c;

import static com.v2.carddemo.cbtrn02c.Fx.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class AbendTest {
    private static final String START = "START OF EXECUTION OF PROGRAM CBTRN02C\n";

    @Test void missingDalytranStatus35() {
        Result r = run(null, List.of(xref(C, A)), List.of(acct(1000, 0, 0)), List.of());
        assertEquals(16, r.rc());
        assertEquals(START + "ERROR OPENING DALYTRAN\nFILE STATUS IS: NNNN0035\nABENDING PROGRAM\n", r.out());
    }

    @Test void missingXref() {
        Result r = run(List.of(), null, List.of(acct(1000, 0, 0)), List.of());
        assertEquals(16, r.rc());
        assertEquals(START + "ERROR OPENING CROSS REF FILE\nFILE STATUS IS: NNNN0035\nABENDING PROGRAM\n", r.out());
    }

    @Test void missingAccount() {
        Result r = run(List.of(), List.of(), null, List.of());
        assertEquals(START + "ERROR OPENING ACCOUNT MASTER FILE\nFILE STATUS IS: NNNN0035\nABENDING PROGRAM\n", r.out());
    }

    @Test void missingTcatbal() {
        Result r = run(List.of(), List.of(), List.of(), null);
        assertEquals(START + "ERROR OPENING TRANSACTION BALANCE FILE\nFILE STATUS IS: NNNN0035\nABENDING PROGRAM\n", r.out());
    }

    @Test void unwritableTranfileStatus30() throws Exception {
        Path d = Files.createTempDirectory("ab");
        Files.createDirectory(d.resolve("TRANFILE")); // a directory cannot be opened as output
        Fx.put(d, "DALYTRAN", List.of());
        Result r = run(d);
        assertEquals(16, r.rc());
        assertEquals(START + "ERROR OPENING TRANSACTION FILE\nFILE STATUS IS: NNNN0030\nABENDING PROGRAM\n", r.out());
    }

    @Test void duplicateTranIdLeavesBalancesPosted_D4() {
        Result r = run(List.of(tran("T1", 100), tran("T1", 200)), acct(100000, 0, 0));
        assertEquals(16, r.rc());
        assertEquals(START + "ERROR WRITING TO TRANSACTION FILE\nFILE STATUS IS: NNNN0022\nABENDING PROGRAM\n", r.out());
        assertEquals(1, r.lines("TRANFILE").size());
        // second transaction's 200 was posted to the account before the failed write
        assertEquals(z(300, 12), r.lines("ACCTFILE").get(0).substring(12, 24));
        assertEquals(z(300, 11), r.lines("TCATBALF").get(0).substring(17, 28));
    }

    @Test void tranfileReplacedNotAppended_D5() throws Exception {
        Path d = Files.createTempDirectory("d5");
        Fx.put(d, "TRANFILE", List.of("OLD".repeat(10)));
        Fx.put(d, "DALYTRAN", List.of(tran("T1", 100)));
        Fx.put(d, "XREFFILE", List.of(xref(C, A)));
        Fx.put(d, "ACCTFILE", List.of(acct(100000, 0, 0)));
        Fx.put(d, "TCATBALF", List.of(tcat(A, "01", "0001", 0, "0".repeat(22))));
        Result r = run(d);
        assertEquals(1, r.lines("TRANFILE").size());
        assertTrue(r.lines("TRANFILE").get(0).startsWith("T1"));
    }
}
