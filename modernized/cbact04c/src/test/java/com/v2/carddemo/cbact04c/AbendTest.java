package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AbendTest {
    @TempDir Path dir;
    static final String A1 = "00000000001", A2 = "00000000002";

    private static String tail(String s, int n) {
        List<String> l = s.lines().toList();
        return String.join("\n", l.subList(l.size() - n, l.size()));
    }

    @Test void A1_accountMissing() throws Exception {
        Fx.Result r = Fx.run(dir, List.of(Fx.tcat(A1, "01", "0001", "0000010000{")),
                List.of(Fx.xref(A1, "1111111111111111")), List.of(Fx.disc("DEFAULT", "01", "0001", "00150{")),
                List.of(Fx.acct(A2, "00000000100{", "")), "2022071800", Fx.FROZEN);
        assertEquals(16, r.rc());
        assertEquals("ACCOUNT NOT FOUND: 00000000001\nERROR READING ACCOUNT FILE\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM",
                tail(r.sysout(), 4));
        assertTrue(r.err().contains("CEE3ABD: ABEND U0999"));
    }

    @Test void A2_xrefMissing() throws Exception {
        Fx.Result r = Fx.run(dir, List.of(Fx.tcat(A1, "01", "0001", "0000010000{")),
                List.of(Fx.xref(A2, "2222222222222222")), List.of(Fx.disc("DEFAULT", "01", "0001", "00150{")),
                List.of(Fx.acct(A1, "00000000100{", "")), "2022071800", Fx.FROZEN);
        assertEquals(16, r.rc());
        assertEquals("ACCOUNT NOT FOUND: 00000000001\nERROR READING XREF FILE\nFILE STATUS IS: NNNN0023\nABENDING PROGRAM",
                tail(r.sysout(), 4));
    }

    @Test void A3_defaultRateMissing() throws Exception {
        Fx.Result r = Fx.run(dir, List.of(Fx.tcat(A1, "01", "0001", "0000010000{")),
                List.of(Fx.xref(A1, "1111111111111111")), List.of(Fx.disc("DEFAULT", "02", "0001", "00150{")),
                List.of(Fx.acct(A1, "00000000100{", "")), "2022071800", Fx.FROZEN);
        assertEquals(16, r.rc());
        assertEquals("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\nERROR READING DEFAULT DISCLOSURE GROUP\n"
                + "FILE STATUS IS: NNNN0023\nABENDING PROGRAM", tail(r.sysout(), 5));
    }

    @Test void missingInputFile() throws Exception {
        java.util.Map<String, String> env = new java.util.HashMap<>();
        env.put("DD_TCATBALF", dir.resolve("none").toString());
        Fx.Result r = Fx.exec(dir, env, "2022071800");
        assertEquals(16, r.rc());
        assertEquals("ERROR OPENING TRANSACTION CATEGORY BALANCE\nFILE STATUS IS: NNNN0035\nABENDING PROGRAM", tail(r.sysout(), 3));
    }
}
