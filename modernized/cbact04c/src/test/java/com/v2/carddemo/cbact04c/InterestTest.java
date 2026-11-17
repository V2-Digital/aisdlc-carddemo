package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class InterestTest {
    @TempDir Path tmp;

    /** Runs one account with balance {@code bal} at 15.00% (plus a second account so the first is rewritten); returns the first txn amount. */
    private String amount(String bal) throws Exception {
        Fx f = new Fx(tmp);
        f.base(List.of(Fx.tcat("00000000001", "01", "0001", bal), Fx.tcat("00000000002", "03", "0001", "0000000000{")),
                List.of(Fx.acct("00000000001", "00000100000{", "A"), Fx.acct("00000000002", "00000000000{", "A")));
        assertEquals(0, f.run("2022071800"));
        return f.read("TRANSACT.txt").substring(132, 143);
    }

    @Test
    void truncatesNotRounds() throws Exception {
        // golden a/c 1: 1164.87 * 15 / 1200 = 14.560875 -> 14.56 (half-up gives the same here)
        assertEquals("0000000145F", amount("0000011648G"));
        // 79.99 * 15 / 1200 = 0.999875 -> truncated 0.99; half-up would give 1.00
        assertEquals("0000000009I", amount("0000000799I"));
    }

    @Test
    void negativeBalanceGivesNegativeInterestTruncatedTowardZero() throws Exception {
        // D7: -79.99 -> -0.999875 -> -0.99 (no sign guard)
        assertEquals("0000000009R", amount("0000000799R"));
        // -0.24 * 15/1200 = -0.003 -> 0, no negative zero
        assertEquals("0000000000{", amount("0000000002M"));
    }
}
