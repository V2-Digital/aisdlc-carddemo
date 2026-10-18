package carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/** Characterization tests: each pins a legacy defect that the rewrite deliberately preserves. */
class LegacyDefectTest {
    @Test
    void D1_overlimitFormulaSubtractsNegativeDebitSoItAddsExposure() throws IOException {
        // limit 1000.00, credit 0, debit -500.00 (stored negative), amt 600.00:
        // legacy temp = 0 - (-500) + 600 = 1100 > 1000 -> 102. credit+debit+amt = 100 would post.
        Fx f = new Fx().xref(Fx.CARD, "00000000001").acct("00000000001", 0, 100000, 0, -50000, "2030-01-01")
                .txn("T1", 60000, Fx.CARD).run();
        assertEquals(102, f.reasonOf(0));
    }

    @Test
    void D1_currentBalanceIsIgnoredByOverlimitCheck() throws IOException {
        Fx f = new Fx().xref(Fx.CARD, "00000000001").acct("00000000001", 99999900, 100000, 0, 0, "2030-01-01")
                .txn("T1", 100000, Fx.CARD).run();
        assertEquals(0, f.out("DALYREJS", 430).size());
    }

    @Test
    void D2_expiry103OverwritesOverlimit102() throws IOException {
        Fx f = new Fx().xref(Fx.CARD, "00000000001").acct("00000000001", 0, 100, 0, 0, "2020-01-01")
                .txn("T1", 999999, Fx.CARD).run();
        assertEquals(103, f.reasonOf(0));
    }

    @Test
    void D3_reason109IsNeverProducedAndRewriteFailureDoesNotReject() throws IOException {
        // The account is read just before the rewrite, so INVALID KEY cannot happen: no 109 in any output.
        Fx f = Fx.basic().txn("T1", 100, Fx.CARD).run();
        assertEquals(0, f.out("DALYREJS", 430).size());
    }

    @Test
    void D4_dalyrejsCloseErrorShowsXrefStatus() throws IOException {
        // Not reproducible with text I/O (no close failure). Documented in CBTRN02C.cbl:649 and the README.
        String src = java.nio.file.Files.readString(Fx.root().resolve("app/cbl/CBTRN02C.cbl"));
        int i = src.indexOf("ERROR CLOSING DAILY REJECTS FILE");
        assertEquals(true, i > 0);
        assertEquals(true, src.substring(i, i + 120).contains("XREFFILE-STATUS"));
    }

    @Test
    void D5_balanceOverflowSilentlyTruncatesAccountDigits() throws IOException {
        // CURR-BAL S9(10)V99: 9,999,999,999.99 + 0.02 -> 0.01 (high-order digit dropped)
        Fx f = new Fx().xref(Fx.CARD, "00000000001")
                .acct("00000000001", 999_999_999_999L, 100000, 0, 0, "2030-01-01").txn("T1", 2, Fx.CARD).run();
        assertEquals(1, f.bal(f.acctOut(0)));
        assertEquals(0, f.rc);
    }

    @Test
    void D5_tcatbalOverflowTruncatesAtElevenDigits() throws IOException {
        Fx f = Fx.basic().tcat("00000000001", "01", "0001", 99_999_999_999L, "0".repeat(22))
                .txn("T1", 2, Fx.CARD).run();
        assertEquals(1, f.field(f.out("TCATBALF", 50).get(0), 17, 11));
    }

    @Test
    void D6_duplicateTranIdAbendsAfterBalancesAlreadyUpdated() throws IOException {
        Fx f = Fx.basic().txn("DUP", 100, Fx.CARD).txn("DUP", 200, Fx.CARD).run();
        assertEquals(16, f.rc);
        // second txn already added to the account and TCATBAL before the failed TRANFILE write
        assertEquals(300, f.bal(f.acctOut(0)));
        assertEquals(300, f.field(f.out("TCATBALF", 50).get(0), 17, 11));
        assertEquals(1, f.out("TRANFILE", 350).size());
    }

    @Test
    void D7_blankExpiryComparesLowAndRejects103() throws IOException {
        Fx f = new Fx().xref(Fx.CARD, "00000000001").acct("00000000001", 0, 100000, 0, 0, "").txn("T1", 100, Fx.CARD)
                .run();
        assertEquals(103, f.reasonOf(0));
    }

    @Test
    void D8_tranfileIsReplacedNotAppended() throws IOException {
        Fx f = Fx.basic().txn("T1", 100, Fx.CARD);
        java.nio.file.Files.writeString(f.p("TRANFILE"), "OLD RECORD\n");
        f.run();
        assertEquals(1, f.out("TRANFILE", 350).size());
        assertEquals("T1", RecordIo.str(f.out("TRANFILE", 350).get(0), 0, 2));
    }

    @Test
    void D9_tempBalS9_9V99TruncatesExposureBeforeLimitCompare() throws IOException {
        // exposure 1,000,000,000.00 + 0 wraps to 0 in S9(9)V99, so a 1.00 limit does not reject
        Fx f = new Fx().xref(Fx.CARD, "00000000001").acct("00000000001", 0, 100, 0, 0, "2030-01-01")
                .txn("T1", 100_000_000_000L, Fx.CARD).run();
        assertEquals(0, f.out("DALYREJS", 430).size());
    }
}
