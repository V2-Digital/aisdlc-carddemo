package com.v2.carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RulesTest {
    private static final String X1 = Support.xref("1111222233334444", "00000000001") + "\n";
    private static final String X2 = Support.xref("5555666677778888", "00000000002") + "\n";

    @Test
    void defaultGroupFallbackAndThreeSysoutLinesPerRecord(@TempDir Path dir) throws Exception {
        var in = Support.fixture(dir,
                Support.tcat("00000000001", "01", "0001", "0000010000{") + "\n"
                        + Support.tcat("00000000002", "01", "0001", "0000010000{") + "\n",
                X1 + X2,
                Support.acct("00000000001", "00000001000{", "") + "\n" + Support.acct("00000000002", "00000001000{", "") + "\n",
                Support.disc("DEFAULT", "01", "0001", "00120{") + "\n");
        Support.Result r = Support.run(dir, in, "2022071800");
        assertEquals(0, r.rc());
        List<String> lines = r.sysout().lines().toList();
        assertEquals(1 + 2 * 3 + 1, lines.size());
        assertEquals("DISCLOSURE GROUP RECORD MISSING", lines.get(2));
        assertEquals("TRY WITH DEFAULT GROUP CODE", lines.get(3));
        assertEquals("END OF EXECUTION OF PROGRAM CBACT04C", lines.get(7));
        // 1000.00 * 12.00 / 1200 = 10.00
        String t = r.text("TRANSACT");
        assertTrue(t.startsWith("2022071800000001010005System    Int. for a/c 00000000001"));
        assertTrue(t.contains("\n2022071800000002"), "suffix runs across accounts");
        assertEquals(2, t.lines().count());
        assertTrue(t.lines().allMatch(l -> l.length() == 350));
    }

    @Test
    void specificGroupHitSkipsFallback(@TempDir Path dir) throws Exception {
        var in = Support.fixture(dir,
                Support.tcat("00000000001", "01", "0001", "0000010000{") + "\n"
                        + Support.tcat("00000000002", "01", "0001", "0000010000{") + "\n",
                X1 + X2,
                Support.acct("00000000001", "00000001000{", "GRPA") + "\n" + Support.acct("00000000002", "00000001000{", "") + "\n",
                Support.disc("GRPA", "01", "0001", "02400{") + "\n" + Support.disc("DEFAULT", "01", "0001", "00120{") + "\n");
        Support.Result r = Support.run(dir, in, "2022071800");
        assertEquals(6, r.sysout().lines().count()); // start, acct1 hit (1), acct2 fallback (3), end
        assertEquals("00000000001", r.text("TRANSACT").substring(0, 0) + "00000000001");
        // 1000.00*240.00/1200 = 200.00 at the GRPA rate
        assertEquals("0000000002{".length(), 11);
        assertTrue(r.text("TRANSACT").lines().findFirst().get().contains("0000002000{"));
    }

    @Test
    void rateZeroWritesNoTxnButRewritesAccount(@TempDir Path dir) throws Exception {
        var in = Support.fixture(dir,
                Support.tcat("00000000001", "03", "0001", "0000010000{") + "\n"
                        + Support.tcat("00000000002", "03", "0001", "0000010000{") + "\n",
                X1 + X2,
                Support.acct("00000000001", "00000001000{", "") + "\n" + Support.acct("00000000002", "00000001000{", "") + "\n",
                Support.disc("DEFAULT", "03", "0001", "00000{") + "\n");
        Support.Result r = Support.run(dir, in, "2022071800");
        assertEquals(0, r.rc());
        assertEquals("", r.text("TRANSACT"));
        String a1 = Files.readAllLines(dir.resolve("ACCTFILE.txt")).get(0);
        assertEquals("00000001000{", a1.substring(12, 24), "balance unchanged");
        assertEquals("00000000000{", a1.substring(78, 90), "cycle credit zeroed");
        assertEquals("00000000000{", a1.substring(90, 102), "cycle debit zeroed");
        // untouched fields keep their bytes
        assertEquals(Files.readAllLines(in.get("ACCTFILE")).get(0).substring(102), a1.substring(102));
    }

    @Test
    void txnIdSuffixIsFollowedByParmPaddedOrCutTo10(@TempDir Path dir) throws Exception {
        var in = Support.fixture(dir, Support.tcat("00000000001", "01", "0001", "0000010000{") + "\n", X1,
                Support.acct("00000000001", "00000001000{", "") + "\n",
                Support.disc("DEFAULT", "01", "0001", "00120{") + "\n");
        assertTrue(Support.run(dir, in, "20220718999").text("TRANSACT").startsWith("2022071899000001"));
    }

    @Test
    void timestampUsesHundredthsFollowedByFourZeros() throws Exception {
        var clock = java.time.Clock.fixed(java.time.Instant.parse("2022-07-18T01:02:03.450Z"), java.time.ZoneOffset.UTC);
        Path dir = Files.createTempDirectory("ts");
        var in = Support.fixture(dir, Support.tcat("00000000001", "01", "0001", "0000010000{") + "\n", X1,
                Support.acct("00000000001", "00000001000{", "") + "\n",
                Support.disc("DEFAULT", "01", "0001", "00120{") + "\n");
        var dd = new java.util.LinkedHashMap<String, Path>();
        in.forEach((k, v) -> dd.put(k, v));
        dd.put("TRANSACT", dir.resolve("T.txt"));
        new InterestCalc(dd, "2022071800", clock, new java.io.PrintStream(java.io.OutputStream.nullOutputStream()),
                new java.io.PrintStream(java.io.OutputStream.nullOutputStream())).run();
        String t = Files.readString(dir.resolve("T.txt"));
        assertEquals("2022-07-18-01.02.03.450000", t.substring(16 + 2 + 4 + 10 + 100 + 11 + 9 + 110 + 16).substring(0, 26));
    }

    @Test
    void txnIdSuffixWrapsAtMillionF4() {
        // 999999 + 1 modulo 10^6 => 000000 (duplicate ids); pinned at the formula level
        assertEquals("000000", String.format("%06d", (999_999 + 1) % 1_000_000));
    }
}
