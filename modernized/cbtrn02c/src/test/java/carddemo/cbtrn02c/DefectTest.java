package carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Characterization tests: each pins a legacy defect that must NOT be fixed (see spec section 6). */
class DefectTest {
    private static final Path ROOT = Path.of("../..").toAbsolutePath().normalize();
    private static final Path PROBES = Path.of("src/test/resources/probes");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), ZoneOffset.UTC);

    private static List<String> lines(Path p) throws Exception {
        return Files.readAllLines(p);
    }

    /** F1: first transaction creates a TCATBAL record, whose FILLER is the initial work area (spaces). */
    @Test
    void F1_createdTcatbalRecordInheritsFillerOfPreviousRecord(@TempDir Path out) throws Exception {
        new Cbtrn02c(PROBES.resolve("reasons/in"), out, CLOCK, l -> {}).run();
        String created = lines(out.resolve("TCATBALF.txt")).stream()
                .filter(l -> l.startsWith("00000000020030001")).findFirst().orElseThrow();
        assertEquals(" ".repeat(22), created.substring(28));
    }

    /** F1 (golden path): created records carry the zero FILLER of earlier stock records. */
    @Test
    void F1_goldenCreatedRecordsHaveZeroFiller() throws Exception {
        List<String> g = lines(ROOT.resolve("legacy/golden/CBTRN02C/TCATBALF.txt"));
        long created = g.stream().filter(l -> l.substring(11, 13).equals("03")).count();
        assertEquals(50, created);
        assertTrue(g.stream().filter(l -> l.substring(11, 13).equals("03"))
                .allMatch(l -> l.substring(28).equals("0".repeat(22))));
    }

    /** F2: overlimit formula adds debit headroom and ignores balance; golden count stays 38 (not 23). */
    @Test
    void F2_overlimitRejectCountPinnedAt38() throws Exception {
        List<String> rej = lines(ROOT.resolve("legacy/golden/CBTRN02C/DALYREJS.txt"));
        assertEquals(38, rej.size());
        assertTrue(rej.stream().allMatch(l -> l.startsWith("0102", 350)));
    }

    /** F3: 103 overwrites 102; the probe record has an overlimit amount and an expired account. */
    @Test
    void F3_expiryOverwritesOverlimit() throws Exception {
        List<String> rej = lines(PROBES.resolve("reasons/expected/DALYREJS.txt"));
        String r = rej.stream().filter(l -> l.startsWith("000000000068358X")).findFirst().orElseThrow();
        assertEquals("0103", r.substring(350, 354));
    }

    /** F4: a failing account REWRITE sets 109 but the tran is still posted, not rejected, RC unchanged. */
    @Test
    void F4_failedAccountRewriteIsIgnoredAndTranStillPosted(@TempDir Path out) throws Exception {
        Path in = PROBES.resolve("accept-only/in");
        KsdsFile real = KsdsFile.load(in.resolve("ACCTFILE.txt"), Cbtrn02c.ACCT_LEN, 11);
        KsdsFile failing = new KsdsFile(11) {
            @Override
            String rewrite(String record) {
                return "23";
            }
        };
        real.all().values().forEach(failing::write);
        List<String> sys = new ArrayList<>();
        Cbtrn02c c = new Cbtrn02c(in, out, CLOCK, sys::add);
        c.useAccountStore(failing);
        int rc = c.run();
        assertEquals(0, rc);
        assertEquals(1, lines(out.resolve("TRANFILE.txt")).size());
        assertEquals(0, lines(out.resolve("DALYREJS.txt")).size());
        assertTrue(sys.contains("TRANSACTIONS REJECTED  :000000000"));
    }

    /** F5: TRANFILE is rebuilt from scratch each run; a pre-existing master in the output dir is dropped. */
    @Test
    void F5_tranfileReplacedNotAppended(@TempDir Path out) throws Exception {
        Files.writeString(out.resolve("TRANFILE.txt"), "PREEXISTING MASTER RECORD\n");
        new Cbtrn02c(PROBES.resolve("accept-only/in"), out, CLOCK, l -> {}).run();
        List<String> t = lines(out.resolve("TRANFILE.txt"));
        assertEquals(1, t.size());
        assertTrue(t.get(0).startsWith("0000000000683580"));
    }

    /** F5: duplicate tran id abends with RC 16. */
    @Test
    void F5_duplicateTranIdAbendsRc16(@TempDir Path out) throws Exception {
        List<String> sys = new ArrayList<>();
        assertEquals(16, new Cbtrn02c(PROBES.resolve("dup-id/in"), out, CLOCK, sys::add).run());
        assertEquals("ABENDING PROGRAM", sys.get(sys.size() - 1));
        assertEquals(5, sys.size());
    }

    /** F6: the DALYREJS close error reports the XREFFILE status. */
    @Test
    void F6_rejsCloseErrorShowsXrefStatus() {
        assertTrue(Cbtrn02c.rejsCloseErrorMessage("35").contains("ERROR CLOSING DAILY REJECTS FILE"));
        assertTrue(Cbtrn02c.rejsCloseErrorMessage("35").contains("FILE STATUS IS: NNNN0035"));
    }

    /** F7: 10^9 cycle credit wraps in WS-TEMP-BAL (accepted), TCATBAL 999,999,999.99+504.77 wraps to 504.76. */
    @Test
    void F7_silentTruncation(@TempDir Path out) throws Exception {
        assertEquals(0, new Cbtrn02c(PROBES.resolve("f7-truncation/in"), out, CLOCK, l -> {}).run());
        assertTrue(lines(out.resolve("TCATBALF.txt")).contains("00000000007010001" + "0000005047F" + "0".repeat(22)));
    }

    /** F8: no check of account status, type/category; unknown type/category is posted. */
    @Test
    void F8_unknownTypeCategoryIsPosted(@TempDir Path out) throws Exception {
        Path in = out.resolve("in");
        Files.createDirectories(in);
        Path src = PROBES.resolve("accept-only/in");
        for (String f : new String[] {"XREFFILE", "ACCTFILE", "TCATBALF"}) {
            Files.copy(src.resolve(f + ".txt"), in.resolve(f + ".txt"));
        }
        StringBuilder r = new StringBuilder(Files.readString(src.resolve("DALYTRAN.txt")));
        r.replace(16, 22, "ZZ9999");
        Files.writeString(in.resolve("DALYTRAN.txt"), r);
        Path o = out.resolve("o");
        assertEquals(0, new Cbtrn02c(in, o, CLOCK, l -> {}).run());
        assertEquals(1, lines(o.resolve("TRANFILE.txt")).size());
    }
}
