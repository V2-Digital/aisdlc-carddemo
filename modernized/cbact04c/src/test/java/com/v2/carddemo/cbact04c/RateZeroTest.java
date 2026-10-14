package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RateZeroTest {
    @Test
    void zeroRateCategoryGivesNoTransactionAndAddsNothing(@TempDir Path t) throws Exception {
        Fx f = Fx.twoAccounts("0000001288A", "00150{");
        f.tcat.add(1, Fx.tc("00000000001", "03", "0001", "00000099999{"));
        f.disc.add(Fx.dg("DEFAULT", "03", "0001", "00000{"));
        f.run(t);
        assertEquals(0, f.rc);
        // account 1: 01/0001 only; account 2 (zero balance, non-zero rate) legitimately gets a 0.00 one
        assertEquals(2, f.transact.size());
        assertEquals("00000000001", Records.slice(f.transact.get(0), 46, 11));
        assertEquals("00000000002", Records.slice(f.transact.get(1), 46, 11));
        // 128.81 * 15 / 1200 = 1.61 (1.6101..)
        assertEquals("0000000016A", Records.slice(f.transact.get(0), 133, 11));
        // account 1 balance = 1000.00 + 1.61 (rate-0 category adds nothing); cycle fields zeroed
        assertEquals("00000010016A", Records.slice(f.acctOut.get(0), 13, 12));
        assertEquals("00000000000{", Records.slice(f.acctOut.get(0), 79, 12));
    }

    @Test
    void zeroRateOnlyAccountIsStillRewrittenWithoutTransaction(@TempDir Path t) throws Exception {
        Fx f = Fx.twoAccounts("0000001288A", "00000{").run(t);
        assertEquals(0, f.transact.size());
        assertEquals("00000000000{", Records.slice(f.acctOut.get(0), 91, 12));
    }
}
