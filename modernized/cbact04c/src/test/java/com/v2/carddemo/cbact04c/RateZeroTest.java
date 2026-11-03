package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RateZeroTest {
    @Test void zeroRateWritesNothingAndAddsNothing(@TempDir Path t) throws Exception {
        // acct 1: zero-rate row only; acct 2 follows so acct 1 is rewritten
        Path d = Fx.data(t.resolve("in"),
                List.of(Fx.tcat("00000000001", "03", "0001", "500.00"), Fx.tcat("00000000002", "03", "0001", "1.00")),
                List.of(Fx.xref("4111111111111111", "00000000001"), Fx.xref("4222222222222222", "00000000002")),
                List.of(Fx.disc("DEFAULT", "03", "0001", "0")),
                List.of(Fx.acct("00000000001", "100.00", ""), Fx.acct("00000000002", "5.00", "")));
        InterestCalc c = Fx.calc(d);
        assertEquals(0, c.run(t.resolve("out")));
        assertTrue(c.transact().isEmpty());
        String a1 = c.accounts().get("00000000001");
        assertEquals(Zoned.encode(new java.math.BigDecimal("100.00"), 12, 2), a1.substring(12, 24));
        assertEquals("00000000000{00000000000{", a1.substring(78, 102)); // rewrite still zeroes cycle fields
    }

    @Test void suffixNotIncrementedForZeroRate(@TempDir Path t) throws Exception {
        Path d = Fx.data(t.resolve("in"),
                List.of(Fx.tcat("00000000001", "01", "0001", "10.00"), Fx.tcat("00000000001", "03", "0001", "10.00"),
                        Fx.tcat("00000000002", "01", "0001", "10.00")),
                List.of(Fx.xref("4111111111111111", "00000000001"), Fx.xref("4222222222222222", "00000000002")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "12.00"), Fx.disc("DEFAULT", "03", "0001", "0")),
                List.of(Fx.acct("00000000001", "0", ""), Fx.acct("00000000002", "0", "")));
        InterestCalc c = Fx.calc(d);
        assertEquals(0, c.run(t.resolve("out")));
        assertEquals(2, c.transact().size());
        assertEquals("2022071800000002", c.transact().get(1).substring(0, 16));
    }
}
