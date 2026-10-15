package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RateLookupTest {
    @Test
    void groupSpecificRateIsUsedInsteadOfDefaultWithoutWarning(@TempDir Path tmp) throws Exception {
        String acct = Files.readAllLines(Fx.stock("ACCTFILE")).get(0);
        acct = acct.substring(0, 112) + "GRPTEST123" + acct.substring(122);
        String disc = Files.readString(Fx.stock("DISCGRP")).replace("\r", "")
                + Fx.pad("GRPTEST123" + "010001" + "00200{", 50) + "\n";   // 2.00% vs DEFAULT 15.00%
        var r = Fx.run(tmp, Map.of(), Map.of("TCATBALF",
                Fx.tcat("00000000001", "01", "0001", "0000023399G"),
                "ACCTFILE", Fx.pad(acct, 300) + "\n", "DISCGRP", disc));
        assertEquals(0, r.rc());
        // 2339.97 * 2 / 1200 = 3.8999 -> 3.89 (vs 29.24 at DEFAULT)
        assertEquals("0000000389I", r.lines("TRANSACT.txt").get(0).substring(132, 143));
        assertFalse(r.sysout().lines().limit(3).anyMatch(l -> l.startsWith("DISCLOSURE")), "no warning for account 1");
    }
}
