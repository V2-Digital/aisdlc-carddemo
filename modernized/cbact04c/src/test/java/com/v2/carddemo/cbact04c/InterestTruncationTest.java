package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** F2: interest is truncated toward zero, not rounded. */
class InterestTruncationTest {
    private String amountFor(Path dir, String bal, String rate) throws Exception {
        // two accounts so account 1 is rewritten and the txn amount is observable
        var in = Support.fixture(dir,
                Support.tcat("00000000001", "01", "0001", bal) + "\n" + Support.tcat("00000000002", "01", "0001", "0000000000{") + "\n",
                Support.xref("1111222233334444", "00000000001") + "\n" + Support.xref("5555666677778888", "00000000002") + "\n",
                Support.acct("00000000001", "00000001000{", "") + "\n" + Support.acct("00000000002", "00000001000{", "") + "\n",
                Support.disc("DEFAULT", "01", "0001", rate) + "\n");
        Support.Result r = Support.run(dir, in, "2022071800");
        assertEquals(0, r.rc());
        return r.text("TRANSACT").substring(16 + 2 + 4 + 10 + 100, 16 + 2 + 4 + 10 + 100 + 11);
    }

    @Test
    void truncatesTowardZeroNotHalfUp(@TempDir Path dir) throws Exception {
        // 2339.97 * 15.00 / 1200 = 29.249625 -> 29.24
        assertEquals("0000000292D", amountFor(dir, "0000023399G", "00150{"));
    }

    @Test
    void negativeBalanceTruncatesTowardZero(@TempDir Path dir) throws Exception {
        // -2339.97 * 15.00 / 1200 = -29.249625 -> -29.24 => 0000000292M
        assertEquals("0000000292M", amountFor(dir, "0000023399P", "00150{"));
    }

    @Test
    void negativeRateTruncatesTowardZero(@TempDir Path dir) throws Exception {
        assertEquals("0000000292M", amountFor(dir, "0000023399G", "00150}"));
    }

    @Test
    void accountBalanceIncludesTruncatedInterest(@TempDir Path dir) throws Exception {
        amountFor(dir, "0000023399G", "00150{");
        String acct1 = java.nio.file.Files.readAllLines(dir.resolve("ACCTFILE.txt")).get(0);
        assertEquals("00000001292D", acct1.substring(12, 24)); // 100.00 + 29.24
    }
}
