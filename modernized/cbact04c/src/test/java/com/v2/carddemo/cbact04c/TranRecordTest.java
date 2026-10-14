package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class TranRecordTest {
    @Test
    void idsTimestampsAndLayout() {
        var r = Fx.run(
                List.of(Fx.tcat("00000000001", "01", "0001", "100.00"), Fx.tcat("00000000001", "01", "0002", "200.00")),
                List.of(Fx.xref("1111222233334444", "00000000001")),
                List.of(Fx.acct("00000000001", "0", "0", "0", ""), Fx.acct("00000000002", "0", "0", "0", "")),
                List.of(Fx.disc("DEFAULT", "01", "0001", "12.00"), Fx.disc("DEFAULT", "01", "0002", "12.00")));
        assertEquals(0, r.rc());
        assertEquals(2, r.transact().size());
        String t = r.transact().get(0);
        assertEquals(350, t.length());
        assertEquals("2022071800000001", t.substring(0, 16));
        assertEquals("2022071800000002", r.transact().get(1).substring(0, 16));
        assertEquals("010005System    Int. for a/c 00000000001", t.substring(16, 16 + 40));
        assertEquals(Fx.z("1.00", 2, 11), t.substring(132, 143));
        assertEquals("000000000", t.substring(143, 152));
        assertEquals("1111222233334444", t.substring(262, 278));
        assertEquals("2022-07-18-00.00.00.000000", t.substring(278, 304));
        assertEquals("2022-07-18-00.00.00.000000", t.substring(304, 330));
        assertEquals(" ".repeat(20), t.substring(330));
    }
}
