package carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import org.junit.jupiter.api.Test;

/** One test per validation rule (CBTRN02C.cbl 1500-VALIDATE-TRAN). */
class ValidationRulesTest {
    @Test
    void unknownCardRejects100AndNoAccountLookup() throws IOException {
        // XREF for an unknown card is absent; the account exists but must stay untouched
        Fx f = Fx.basic().txn("T1", 500, "9999999999999999").run();
        assertEquals(1, f.out("DALYREJS", 430).size());
        assertEquals(100, f.reasonOf(0));
        assertTrue(RecordIo.str(f.out("DALYREJS", 430).get(0), 354, 76).startsWith("INVALID CARD NUMBER FOUND"));
        assertEquals(0, f.bal(f.acctOut(0)));
        assertEquals(4, f.rc);
    }

    @Test
    void xrefToMissingAccountRejects101() throws IOException {
        Fx f = Fx.basic().xref("4000000000000002", "00000000099").txn("T1", 500, "4000000000000002").run();
        assertEquals(101, f.reasonOf(0));
        assertTrue(RecordIo.str(f.out("DALYREJS", 430).get(0), 354, 76).startsWith("ACCOUNT RECORD NOT FOUND"));
    }

    @Test
    void overlimitBoundaryEqualPosts() throws IOException {
        // limit 1000.00, temp = 0 - 0 + 1000.00 -> not over limit
        Fx f = Fx.basic().txn("T1", 100000, Fx.CARD).run();
        assertEquals(0, f.out("DALYREJS", 430).size());
        assertEquals(1, f.out("TRANFILE", 350).size());
        assertEquals(0, f.rc);
    }

    @Test
    void oneCentOverLimitRejects102() throws IOException {
        Fx f = Fx.basic().txn("T1", 100001, Fx.CARD).run();
        assertEquals(102, f.reasonOf(0));
        assertTrue(RecordIo.str(f.out("DALYREJS", 430).get(0), 354, 76).startsWith("OVERLIMIT TRANSACTION"));
        assertEquals(0, f.out("TRANFILE", 350).size());
    }

    @Test
    void expiredAndOverlimitReportsOnly103() throws IOException {
        Fx f = new Fx().xref(Fx.CARD, "00000000001").acct("00000000001", 0, 100000, 0, 0, "2022-01-01")
                .txn("T1", 999999, Fx.CARD).run();
        assertEquals(103, f.reasonOf(0));
    }

    @Test
    void expiredOnlyRejects103() throws IOException {
        Fx f = new Fx().xref(Fx.CARD, "00000000001").acct("00000000001", 0, 100000, 0, 0, "2022-06-09")
                .txn("T1", 100, Fx.CARD).run();
        assertEquals(103, f.reasonOf(0));
        // same day is not expired (text compare, equal)
        Fx g = new Fx().xref(Fx.CARD, "00000000001").acct("00000000001", 0, 100000, 0, 0, "2022-06-10")
                .txn("T1", 100, Fx.CARD).run();
        assertEquals(0, g.out("DALYREJS", 430).size());
    }

    @Test
    void rejectTrailerLayoutIs4DigitReasonPlus76CharDesc() throws IOException {
        Fx f = Fx.basic().txn("T1", 500, "9999999999999999").run();
        byte[] r = f.out("DALYREJS", 430).get(0);
        assertEquals(430, r.length);
        assertEquals("0100INVALID CARD NUMBER FOUND" + " ".repeat(76 - 25), RecordIo.str(r, 350, 80));
        assertEquals(RecordIo.str(f.daily.get(0), 0, 350), RecordIo.str(r, 0, 350));
    }
}
