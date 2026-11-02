package carddemo.cbact04c;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Unit and characterization tests on small inline datasets (spec AC5-9). */
class BehaviourTest {
    private String out;
    private int rc;

    private static String pad(String s, int n) {
        return s + " ".repeat(n - s.length());
    }

    private static String tcat(String acct, String type, String cat, String bal) {
        return pad(acct + type + cat + Zoned.format(new BigDecimal(bal), 11), 50);
    }

    private static String xref(String card, String acct) {
        return pad(card + " ".repeat(9) + acct, 50);
    }

    private static String acct(String id, String bal, String group) {
        String zero = Zoned.format(BigDecimal.ZERO, 12);
        String r = id + "Y" + Zoned.format(new BigDecimal(bal), 12) + "X".repeat(54)
                + Zoned.format(new BigDecimal("5.00"), 12)
                + Zoned.format(new BigDecimal("7.00"), 12) + "Z".repeat(10) + pad(group, 10);
        return pad(r, 300) ;
    }

    private static String disc(String group, String type, String cat, String rate) {
        return pad(pad(group, 10) + type + cat + Zoned.format(new BigDecimal(rate), 6), 50);
    }

    private Path run(@TempDir Path d, List<String> tc, List<String> xr, List<String> ac, List<String> dg)
            throws Exception {
        put(d, "TCATBALF", tc);
        put(d, "XREFFILE", xr);
        put(d, "ACCTFILE", ac);
        put(d, "DISCGRP", dg);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        rc = Cbact04c.run(d, "2022071800", () -> "2022071800000000",
                new PrintStream(bos, true, StandardCharsets.ISO_8859_1));
        out = bos.toString(StandardCharsets.ISO_8859_1);
        return d;
    }

    private static void put(Path d, String dd, List<String> recs) throws Exception {
        if (recs != null) {
            Files.writeString(d.resolve(dd + ".txt"), String.join("\n", recs) + (recs.isEmpty() ? "" : "\n"),
                    StandardCharsets.ISO_8859_1);
        }
    }

    private static List<String> lines(Path f) throws Exception {
        return Files.readAllLines(f, StandardCharsets.ISO_8859_1);
    }

    private static final List<String> XR = List.of(xref("4111111111111111", "00000000001"),
            xref("4222222222222222", "00000000002"));
    private static final List<String> DG = List.of(disc("DEFAULT", "01", "0001", "15.00"),
            disc("DEFAULT", "03", "0001", "0.00"), disc("GRP", "01", "0001", "12.00"));

    @Test
    void interestIsTruncatedTowardZeroForPositiveAndNegative(@TempDir Path d) throws Exception {
        // 471.78*15/1200 = 5.8972 -> 5.89 ; -566.06*15/1200 = -7.0757 -> -7.07 (not -7.08)
        run(d, List.of(tcat("00000000001", "01", "0001", "471.78"), tcat("00000000002", "01", "0001", "-566.06")),
                XR, List.of(acct("00000000001", "100.00", ""), acct("00000000002", "100.00", "")), DG);
        assertEquals(0, rc);
        List<String> t = lines(d.resolve("TRANSACT.txt"));
        assertEquals(2, t.size());
        assertEquals(new BigDecimal("5.89"), Zoned.parse(t.get(0).substring(132, 143)));
        assertEquals(new BigDecimal("-7.07"), Zoned.parse(t.get(1).substring(132, 143)));
        assertEquals("0000000058I", t.get(0).substring(132, 143));
    }

    @Test
    void sourceHasNoFloatingPoint() throws Exception {
        String src = Files.readString(Path.of("src/main/java/carddemo/cbact04c/Cbact04c.java"));
        assertFalse(src.matches("(?s).*\\b(double|float)\\b.*"));
    }

    @Test
    void groupHitPrintsNoMissingLines(@TempDir Path d) throws Exception {
        run(d, List.of(tcat("00000000001", "01", "0001", "1200.00")), XR,
                List.of(acct("00000000001", "0.00", "GRP"), acct("00000000002", "0.00", "")), DG);
        assertFalse(out.contains("MISSING"));
        // rate 12.00 from group GRP, not 15.00: 1200*12/1200 = 12.00
        assertEquals(new BigDecimal("12.00"),
                Zoned.parse(lines(d.resolve("TRANSACT.txt")).get(0).substring(132, 143)));
    }

    @Test
    void missFallsBackToDefaultWithTwoLines(@TempDir Path d) throws Exception {
        run(d, List.of(tcat("00000000001", "01", "0001", "1200.00")), XR,
                List.of(acct("00000000001", "0.00", "")), DG);
        assertEquals(1, out.split("DISCLOSURE GROUP RECORD MISSING", -1).length - 1);
        assertTrue(out.contains("DISCLOSURE GROUP RECORD MISSING\nTRY WITH DEFAULT GROUP CODE\n"));
        assertEquals(new BigDecimal("15.00"),
                Zoned.parse(lines(d.resolve("TRANSACT.txt")).get(0).substring(132, 143)));
    }

    @Test
    void rateZeroWritesNothingAndAddsNothing(@TempDir Path d) throws Exception {
        // acct 1 has a rate-0 category (03) and a charged one (01); acct 2 is last (D1)
        run(d, List.of(tcat("00000000001", "01", "0001", "1200.00"), tcat("00000000001", "03", "0001", "5000.00"),
                tcat("00000000002", "01", "0001", "0.00")), XR,
                List.of(acct("00000000001", "100.00", ""), acct("00000000002", "0.00", "")), DG);
        assertEquals(2, lines(d.resolve("TRANSACT.txt")).size()); // acct1 cat01 + acct2 (D5)
        String a1 = lines(d.resolve("ACCTFILE.txt")).get(0);
        assertEquals(new BigDecimal("115.00"), Zoned.parse(a1.substring(12, 24))); // only 15.00 added
    }

    @Test
    void zeroBalanceStillWritesZeroTransaction_D5(@TempDir Path d) throws Exception {
        run(d, List.of(tcat("00000000001", "01", "0001", "0.00")), XR,
                List.of(acct("00000000001", "1.00", "")), DG);
        assertEquals(1, lines(d.resolve("TRANSACT.txt")).size());
        assertEquals("0000000000{", lines(d.resolve("TRANSACT.txt")).get(0).substring(132, 143));
    }

    @Test
    void transactionLayout(@TempDir Path d) throws Exception {
        run(d, List.of(tcat("00000000001", "01", "0001", "1200.00"), tcat("00000000002", "01", "0001", "2400.00"),
                tcat("00000000002", "03", "0001", "1.00")), XR,
                List.of(acct("00000000001", "0.00", ""), acct("00000000002", "0.00", "")), DG);
        byte[] raw = Files.readAllBytes(d.resolve("TRANSACT.txt"));
        assertEquals(2 * 351, raw.length);
        List<String> t = lines(d.resolve("TRANSACT.txt"));
        for (String r : t) {
            assertEquals(350, r.length());
        }
        assertEquals("2022071800000001", t.get(0).substring(0, 16));
        assertEquals("2022071800000002", t.get(1).substring(0, 16)); // counter runs across accounts
        assertEquals("010005System    ", t.get(0).substring(16, 32));
        assertEquals(pad("Int. for a/c 00000000001", 100), t.get(0).substring(32, 132));
        assertEquals("000000000", t.get(0).substring(143, 152));
        assertEquals("4111111111111111", t.get(0).substring(262, 278));
        assertEquals("2022-07-18-00.00.00.000000", t.get(0).substring(278, 304));
        assertEquals(t.get(0).substring(278, 304), t.get(0).substring(304, 330));
        assertEquals(" ".repeat(20), t.get(0).substring(330));
        assertEquals(" ".repeat(110), t.get(0).substring(152, 262));
    }

    @Test
    void timestampUsesHundredthsOnly() {
        assertEquals("2022-07-18-13.45.59.870000", Cbact04c.timestamp("2022071813455987"));
    }

    @Test
    void lastAccountNotRewritten_D1(@TempDir Path d) throws Exception {
        List<String> ac = List.of(acct("00000000001", "100.00", ""), acct("00000000002", "100.00", ""));
        run(d, List.of(tcat("00000000001", "01", "0001", "1200.00"), tcat("00000000002", "01", "0001", "1200.00")),
                XR, ac, DG);
        List<String> out2 = lines(d.resolve("ACCTFILE.txt"));
        assertNotEquals(ac.get(0), out2.get(0));
        assertEquals(new BigDecimal("115.00"), Zoned.parse(out2.get(0).substring(12, 24)));
        assertEquals(ac.get(1), out2.get(1), "D1: last account unchanged although TRANSACT charges it");
        assertEquals(2, lines(d.resolve("TRANSACT.txt")).size());
        // cycle fields zeroed on the rewritten account (D8), other bytes verbatim
        assertEquals(Zoned.format(BigDecimal.ZERO, 12), out2.get(0).substring(78, 90));
        assertEquals(ac.get(0).substring(24, 78), out2.get(0).substring(24, 78));
        assertEquals(ac.get(0).substring(102), out2.get(0).substring(102));
    }

    @Test
    void emptyTcatbalOnlyBanners(@TempDir Path d) throws Exception {
        run(d, List.of(), XR, List.of(acct("00000000001", "1.00", "")), DG);
        assertEquals(0, rc);
        assertEquals("START OF EXECUTION OF PROGRAM CBACT04C\nEND OF EXECUTION OF PROGRAM CBACT04C\n", out);
        assertEquals(0, Files.size(d.resolve("TRANSACT.txt")));
    }

    @Test
    void abendMissingDd(@TempDir Path d) throws Exception {
        run(d, List.of(tcat("00000000001", "01", "0001", "1.00")), XR, null, DG);
        assertEquals(16, rc);
        assertTrue(out.contains("ERROR OPENING ACCOUNT MASTER FILE"));
        assertTrue(out.contains("FILE STATUS IS: NNNN00"));
        assertTrue(out.endsWith("ABENDING PROGRAM\n"));
        assertFalse(Files.exists(d.resolve("TRANSACT.txt")));
    }

    @Test
    void abendMissingAccount(@TempDir Path d) throws Exception {
        run(d, List.of(tcat("00000000009", "01", "0001", "1.00")), XR, List.of(acct("00000000001", "1.00", "")), DG);
        assertEquals(16, rc);
        assertTrue(out.contains("ACCOUNT NOT FOUND: 00000000009\nERROR READING ACCOUNT FILE\n"));
        assertTrue(out.contains("FILE STATUS IS: NNNN0023\nABENDING PROGRAM\n"));
    }

    @Test
    void abendMissingXref(@TempDir Path d) throws Exception {
        run(d, List.of(tcat("00000000003", "01", "0001", "1.00")), XR, List.of(acct("00000000003", "1.00", "")), DG);
        assertEquals(16, rc);
        assertTrue(out.contains("ACCOUNT NOT FOUND: 00000000003\nERROR READING XREF FILE\n"));
    }

    @Test
    void abendMissingDefaultGroup(@TempDir Path d) throws Exception {
        run(d, List.of(tcat("00000000001", "01", "0001", "1.00")), XR, List.of(acct("00000000001", "1.00", "")),
                List.of(disc("GRP", "01", "0001", "12.00")));
        assertEquals(16, rc);
        assertTrue(out.contains("TRY WITH DEFAULT GROUP CODE\nERROR READING DEFAULT DISCLOSURE GROUP\n"));
        assertFalse(Files.exists(d.resolve("ACCTFILE.txt.new")));
    }

    @Test
    void zonedMinusZeroReadsAsZeroAndZeroWritesPositive() {
        assertEquals(0, Zoned.parse("0000000000}").signum());
        assertEquals("0000000000{", Zoned.format(Zoned.parse("0000000000}"), 11));
    }
}
