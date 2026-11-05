package carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TranRecordTest {
    @Test
    void layout(@TempDir Path d) throws IOException {
        Fx fx = new Fx(d).basic("0000100000{", "0000100000{", "").run();
        String t = fx.lines("TRANSACT").get(0);
        assertEquals(350, t.length());
        assertEquals("2022071800000001", t.substring(0, 16));
        assertEquals("010005", t.substring(16, 22));
        assertEquals("System    ", t.substring(22, 32));
        assertEquals(Fx.pad("Int. for a/c 00000000001", 100), t.substring(32, 132));
        assertEquals("000000000", t.substring(143, 152));
        assertTrue(t.substring(152, 262).isBlank());
        assertEquals("4111111111111111", t.substring(262, 278));
        assertEquals("2022-07-18-00.00.00.000000", t.substring(278, 304));
        assertEquals("2022-07-18-00.00.00.000000", t.substring(304, 330));
        assertTrue(t.substring(330).isBlank());
        assertEquals("2022071800000002", fx.lines("TRANSACT").get(1).substring(0, 16));
    }

    @Test
    void timestampUsesHundredths(@TempDir Path d) throws IOException {
        Fx fx = new Fx(d).basic("0000100000{", "0000100000{", "");
        // clock is fixed by Fx; check format from a distinct clock via a direct run
        java.util.Map<String, String> env = new java.util.HashMap<>();
        for (String dd : List.of("TCATBALF", "XREFFILE", "DISCGRP", "ACCTFILE", "TRANSACT")) {
            env.put("DD_" + dd, d.resolve(dd + ".txt").toString());
        }
        env.put("CARDDEMO_CURRENT_DATE", "2023123123595987");
        Cbact04c.run(env, "2022071800", new java.io.PrintStream(java.io.OutputStream.nullOutputStream()));
        assertEquals("2023-12-31-23.59.59.870000", fx.lines("TRANSACT").get(0).substring(278, 304));
    }

    @Test
    void suffixWrapsAfter999999_D5(@TempDir Path d) throws IOException {
        Fx fx = new Fx(d);
        List<String> tc = new ArrayList<>();
        List<String> xr = new ArrayList<>();
        List<String> ac = new ArrayList<>();
        // 1,000,001 category records on one account: one account, many (type,cat) rows is not valid
        // key order, but the legacy reads sequentially without checking, so reuse the same key.
        for (int i = 0; i < 1_000_001; i++) {
            tc.add(Fx.tcat("00000000001", "01", "0001", "0000000100{"));
        }
        xr.add(Fx.xref("4111111111111111", "00000000001"));
        ac.add(Fx.acct("00000000001", "0000100000{", ""));
        fx.put("TCATBALF", tc);
        fx.put("XREFFILE", xr);
        fx.put("ACCTFILE", ac);
        fx.put("DISCGRP", List.of(Fx.disc("DEFAULT", "01", "0001", "00150{")));
        fx.run();
        List<String> t = fx.lines("TRANSACT");
        assertEquals(1_000_001, t.size());
        assertEquals("2022071800999999", t.get(999_998).substring(0, 16));
        assertEquals("2022071800000000", t.get(999_999).substring(0, 16));
        assertEquals("2022071800000001", t.get(1_000_000).substring(0, 16));
    }
}
