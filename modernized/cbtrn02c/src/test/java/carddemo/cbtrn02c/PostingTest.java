package carddemo.cbtrn02c;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;

class PostingTest {
    @Test
    void positiveAmountGoesToBalanceAndCycleCredit() throws IOException {
        Fx f = Fx.basic().txn("T1", 2500, Fx.CARD).run();
        byte[] a = f.acctOut(0);
        assertEquals(2500, f.bal(a));
        assertEquals(2500, f.credit(a));
        assertEquals(0, f.debit(a));
    }

    @Test
    void negativeAmountGoesToCycleDebit() throws IOException {
        Fx f = Fx.basic().txn("T1", -1500, Fx.CARD).run();
        byte[] a = f.acctOut(0);
        assertEquals(-1500, f.bal(a));
        assertEquals(0, f.credit(a));
        assertEquals(-1500, f.debit(a));
    }

    @Test
    void zeroAmountGoesToCycleCredit() throws IOException {
        Fx f = Fx.basic().acct("00000000002", 0, 100000, 700, 0, "2030-01-01").xref("4000000000000002", "00000000002")
                .txn("T1", 0, "4000000000000002").run();
        byte[] a = f.acctOut(1);
        assertEquals(700, f.credit(a));
        assertEquals(0, f.debit(a));
        // zero result is written with '{' not '}'
        assertEquals('{', (char) a[90 + 11]);
    }

    @Test
    void tcatbalCreateThenUpdate() throws IOException {
        Fx f = Fx.basic().txn("T1", 100, Fx.CARD).txn("T2", 250, Fx.CARD).run();
        assertEquals(1, f.sysout.split("Creating\\.", -1).length - 1);
        assertEquals(true, f.sysout.contains("TCATBAL record not found for key : 00000000001010001.. Creating."));
        List<byte[]> t = f.out("TCATBALF", 50);
        assertEquals(1, t.size());
        assertEquals(350, f.field(t.get(0), 17, 11));
    }

    @Test
    void tcatbalExistingIsUpdatedWithoutCreateMessage() throws IOException {
        Fx f = Fx.basic().tcat("00000000001", "01", "0001", 1000, "0".repeat(22)).txn("T1", 100, Fx.CARD).run();
        assertEquals(false, f.sysout.contains("Creating"));
        assertEquals(1100, f.field(f.out("TCATBALF", 50).get(0), 17, 11));
    }

    @Test
    void rejectDoesNotTouchBalancesOrTcatbal() throws IOException {
        Fx f = Fx.basic().txn("T1", 999999, Fx.CARD).run();
        assertEquals(0, f.bal(f.acctOut(0)));
        assertEquals(0, f.out("TCATBALF", 50).size());
        assertEquals(0, f.out("TRANFILE", 350).size());
    }

    @Test
    void sameAccountTwiceSeesPostedBalances() throws IOException {
        // limit 1000.00: 600 posts, second 600 -> exposure 1200 > limit -> 102
        Fx f = Fx.basic().txn("T1", 60000, Fx.CARD).txn("T2", 60000, Fx.CARD).run();
        assertEquals(1, f.out("TRANFILE", 350).size());
        assertEquals(102, f.reasonOf(0));
    }

    @Test
    void postedRecordCarriesClockTimestampAndBlankFiller() throws IOException {
        Fx f = Fx.basic().txn("T1", 100, Fx.CARD).run();
        byte[] t = f.out("TRANFILE", 350).get(0);
        assertEquals("2022-07-18-00.00.00.000000", RecordIo.str(t, 304, 26));
        assertEquals(" ".repeat(20), RecordIo.str(t, 330, 20));
        assertEquals(RecordIo.str(f.daily.get(0), 0, 304), RecordIo.str(t, 0, 304));
    }

    @Test
    void firstTcatCreateHasSpaceFiller() throws IOException {
        Fx f = Fx.basic().txn("T1", 100, Fx.CARD).run();
        assertEquals(" ".repeat(22), RecordIo.str(f.out("TCATBALF", 50).get(0), 28, 22));
    }

    @Test
    void laterCreateInheritsFillerOfLastRecordRead() throws IOException {
        Fx f = Fx.basic().tcat("00000000001", "01", "0001", 0, "Z".repeat(22))
                .txn("T1", 100, Fx.CARD).txn("T2", "02", "0002", 100, Fx.CARD, "2022-06-10").run();
        List<byte[]> t = f.out("TCATBALF", 50);
        assertEquals(2, t.size());
        assertEquals("Z".repeat(22), RecordIo.str(t.get(1), 28, 22));
    }

    @Test
    void emptyDailyTranGivesRc0AndEmptyTranfile() throws IOException {
        Fx f = Fx.basic().run();
        assertEquals(0, f.rc);
        assertEquals(0, f.out("TRANFILE", 350).size());
        assertEquals(true, f.sysout.contains("TRANSACTIONS PROCESSED :000000000"));
    }
}
