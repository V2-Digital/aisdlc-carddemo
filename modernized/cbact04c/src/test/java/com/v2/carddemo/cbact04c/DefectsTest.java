package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.Fixture.*;
import static com.v2.carddemo.cbact04c.Support.*;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Characterization tests: each pins a legacy defect that must NOT be fixed in CR-1. */
class DefectsTest {
    @Test
    void F1_lastAccountNotPosted() throws Exception {
        Path in = Support.stockInputs();
        Files.copy(golden("CBTRN02C", "ACCTFILE.txt"), in.resolve("ACCTFILE.txt"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Files.copy(golden("CBTRN02C", "TCATBALF.txt"), in.resolve("TCATBALF.txt"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        Result r = run("2022071800", in);
        String[] before = read(in.resolve("ACCTFILE.txt")).split("\n");
        String[] after = r.file("ACCTFILE").split("\n");
        assertEquals(before[49], after[49]);
        assertTrue(after[49].startsWith("00000000050"));
        assertEquals(49, java.util.stream.IntStream.range(0, 50).filter(i -> !before[i].equals(after[i])).count());
        // yet interest was charged: 18.77
        assertEquals("0000000187G", amount(r.file("TRANSACT").split("\n")[49]));
    }

    @Test
    void F4_zeroBalanceNonZeroRateWritesZeroTransaction() throws Exception {
        Result r = run("2022071800", inputs(ZERO11, ZERO12, "", disc("DEFAULT", "01", "0001", "001500")));
        String[] t = r.file("TRANSACT").split("\n");
        assertEquals(2, t.length);
        assertEquals("0000000000{", amount(t[0]));
    }

    @Test
    void zeroRateWritesNoTransactionButAccountRewritten() throws Exception {
        Result r = run("2022071800", inputs("0000001164H", "00000019458G", "", disc("DEFAULT", "01", "0001", "000000")));
        assertEquals("", r.file("TRANSACT"));
        String a1 = r.file("ACCTFILE").split("\n")[0];
        assertEquals("00000019458G", a1.substring(12, 24)); // balance unchanged
        assertEquals(ZERO12, a1.substring(78, 90));          // cycle credit zeroed
        assertEquals(ZERO12, a1.substring(90, 102));         // cycle debit zeroed
    }

    @Test
    void interestIsAddedToBalanceAtAccountBreak() throws Exception {
        Result r = run("2022071800", inputs("0000001164H", "00000019458G", "", disc("DEFAULT", "01", "0001", "001500")));
        // 1945.87 + 1.45 = 1947.32
        assertEquals("00000019473B", r.file("ACCTFILE").split("\n")[0].substring(12, 24));
    }

    @Test
    void F6_shortParmIsSpacePadded() throws Exception {
        Result r = run("2022", inputs("0000001164H", "00000019458G", "", disc("DEFAULT", "01", "0001", "001500")));
        assertTrue(firstTran(r).startsWith("2022      000001"));
    }

    @Test
    void emptyTcatbalLeavesAccountsUnchanged() throws Exception {
        Path in = inputs(ZERO11, ZERO12, "", disc("DEFAULT", "01", "0001", "001500"));
        put(in, "TCATBALF", "");
        Result r = run("2022071800", in);
        assertEquals(0, r.rc());
        assertEquals("", r.file("TRANSACT"));
        assertEquals(read(in.resolve("ACCTFILE.txt")), r.file("ACCTFILE"));
    }
}
