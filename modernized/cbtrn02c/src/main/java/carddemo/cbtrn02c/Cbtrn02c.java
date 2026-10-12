package carddemo.cbtrn02c;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Java rewrite of CBTRN02C (daily transaction posting). Like-for-like: legacy defects are kept
 * and marked F1-F8 (see spec.md / README.md).
 */
public class Cbtrn02c {
    static final int TRAN_LEN = 350, XREF_LEN = 50, ACCT_LEN = 300, TCAT_LEN = 50, REJ_LEN = 430;

    private final Path in;
    private final Path out;
    private final Clock clock;
    private final Consumer<String> sysout;

    private KsdsFile tranfile = new KsdsFile(16);
    private KsdsFile xref, acct, tcat;
    private final List<String> rejects = new ArrayList<>();

    private int tranCount, rejectCount;
    private int reason;
    private String reasonDesc = "";
    /** F1: TRAN-CAT-BAL-RECORD work area, leaks FILLER between records. */
    private String tcatWork = " ".repeat(TCAT_LEN);
    private String acctWork;
    private String xrefWork;

    public Cbtrn02c(Path in, Path out, Clock clock, Consumer<String> sysout) {
        this.in = in;
        this.out = out;
        this.clock = clock;
        this.sysout = sysout;
    }

    /** Test seam: lets a test replace the account store (F4). */
    void useAccountStore(KsdsFile store) {
        this.acct = store;
    }

    /** @return return code: 0, 4 or 16 */
    public int run() throws IOException {
        sysout.accept("START OF EXECUTION OF PROGRAM CBTRN02C");
        List<String> daly;
        try {
            daly = open();
        } catch (Abend a) {
            return 16;
        }
        try {
            for (String rec : daly) {
                process(rec);
            }
        } catch (Abend a) {
            save();
            return 16;
        }
        save();
        sysout.accept("TRANSACTIONS PROCESSED :" + String.format("%09d", tranCount));
        sysout.accept("TRANSACTIONS REJECTED  :" + String.format("%09d", rejectCount));
        sysout.accept("END OF EXECUTION OF PROGRAM CBTRN02C");
        return rejectCount > 0 ? 4 : 0;
    }

    /** Opens in COBOL order; a missing input is file status 35. TRANFILE starts empty (F5). */
    private List<String> open() throws IOException {
        List<String> daly = null;
        if (!Files.exists(in.resolve("DALYTRAN.txt"))) {
            abend("ERROR OPENING DALYTRAN", "35");
        }
        daly = KsdsFile.readLines(in.resolve("DALYTRAN.txt"), TRAN_LEN);
        if (!Files.exists(in.resolve("XREFFILE.txt"))) {
            abend("ERROR OPENING CROSS REF FILE", "35");
        }
        xref = KsdsFile.load(in.resolve("XREFFILE.txt"), XREF_LEN, 16);
        if (acct == null) {
            if (!Files.exists(in.resolve("ACCTFILE.txt"))) {
                abend("ERROR OPENING ACCOUNT MASTER FILE", "35");
            }
            acct = KsdsFile.load(in.resolve("ACCTFILE.txt"), ACCT_LEN, 11);
        }
        if (!Files.exists(in.resolve("TCATBALF.txt"))) {
            abend("ERROR OPENING TRANSACTION BALANCE FILE", "35");
        }
        tcat = KsdsFile.load(in.resolve("TCATBALF.txt"), TCAT_LEN, 17);
        return daly;
    }

    private void process(String rec) {
        tranCount++;
        reason = 0;
        reasonDesc = "";
        validate(rec);
        if (reason == 0) {
            post(rec);
        } else {
            rejectCount++;
            rejects.add(rec + String.format("%04d", reason) + KsdsFile.pad(reasonDesc, 76));
        }
    }

    private void validate(String rec) {
        xrefWork = xref.read(rec.substring(262, 278));
        if (xrefWork == null) {
            reason = 100;
            reasonDesc = "INVALID CARD NUMBER FOUND";
            return;
        }
        String a = acct.read(xrefWork.substring(25, 36));
        if (a == null) {
            reason = 101;
            reasonDesc = "ACCOUNT RECORD NOT FOUND";
            return;
        }
        acctWork = a;
        long credit = Zoned.decode(a.substring(78, 90));
        long debit = Zoned.decode(a.substring(90, 102));
        long amt = Zoned.decode(rec.substring(132, 143));
        long tempBal = Zoned.trunc(credit - debit + amt, 11); // F2, F7
        long limit = Zoned.decode(a.substring(24, 36));
        if (limit < tempBal) {
            reason = 102;
            reasonDesc = "OVERLIMIT TRANSACTION";
        }
        if (a.substring(58, 68).compareTo(rec.substring(278, 288)) < 0) { // F3: overwrites 102
            reason = 103;
            reasonDesc = "TRANSACTION RECEIVED AFTER ACCT EXPIRATION";
        }
    }

    private void post(String rec) {
        String tran = rec.substring(0, 304) + timestamp() + " ".repeat(20);
        String amtRaw = rec.substring(132, 143);
        long amt = Zoned.decode(amtRaw);
        updateTcatbal(rec, amt);
        updateAccount(amt);
        if (!tranfile.write(tran).equals("00")) {
            abend("ERROR WRITING TO TRANSACTION FILE", "22");
        }
    }

    private void updateTcatbal(String rec, long amt) {
        String key = xrefWork.substring(25, 36) + rec.substring(16, 18) + rec.substring(18, 22);
        String found = tcat.read(key);
        if (found == null) {
            sysout.accept("TCATBAL record not found for key : " + key + ".. Creating.");
            // INITIALIZE touches cols 1-28 only; FILLER keeps the previous work-area content (F1)
            tcatWork = key + Zoned.encode(amt, 11) + tcatWork.substring(28);
            if (!tcat.write(tcatWork).equals("00")) {
                abend("ERROR WRITING TRANSACTION BALANCE FILE", "22");
            }
        } else {
            tcatWork = found;
            long bal = Zoned.decode(found.substring(17, 28));
            tcatWork = found.substring(0, 17) + Zoned.encode(bal + amt, 11) + found.substring(28);
            if (!tcat.rewrite(tcatWork).equals("00")) {
                abend("ERROR REWRITING TRANSACTION BALANCE FILE", "23");
            }
        }
    }

    private void updateAccount(long amt) {
        String a = acctWork;
        long bal = Zoned.decode(a.substring(12, 24));
        String credit = a.substring(78, 90);
        String debit = a.substring(90, 102);
        if (amt >= 0) {
            credit = Zoned.encode(Zoned.decode(credit) + amt, 12);
        } else {
            debit = Zoned.encode(Zoned.decode(debit) + amt, 12);
        }
        acctWork = a.substring(0, 12) + Zoned.encode(bal + amt, 12) + a.substring(24, 78)
                + credit + debit + a.substring(102);
        if (!acct.rewrite(acctWork).equals("00")) {
            reason = 109; // F4: set but never acted on; TRAN is still written
            reasonDesc = "ACCOUNT RECORD NOT FOUND";
        }
    }

    /** DB2 format timestamp YYYY-MM-DD-HH.MM.SS.hh0000 from the clock. */
    String timestamp() {
        ZonedDateTime t = ZonedDateTime.now(clock.withZone(ZoneOffset.UTC));
        return String.format("%04d-%02d-%02d-%02d.%02d.%02d.%02d0000", t.getYear(), t.getMonthValue(),
                t.getDayOfMonth(), t.getHour(), t.getMinute(), t.getSecond(), t.getNano() / 10_000_000);
    }

    private void save() throws RuntimeExceptionIO {
        try {
            Files.createDirectories(out);
            tranfile.save(out.resolve("TRANFILE.txt"));
            KsdsFile.writeLines(out.resolve("DALYREJS.txt"), rejects);
            acct.save(out.resolve("ACCTFILE.txt"));
            tcat.save(out.resolve("TCATBALF.txt"));
        } catch (IOException e) {
            throw new RuntimeExceptionIO(e);
        }
    }

    static final class RuntimeExceptionIO extends RuntimeException {
        RuntimeExceptionIO(IOException e) {
            super(e);
        }
    }

    /** Paragraphs 9910 + 9999: message, FILE STATUS IS, ABENDING PROGRAM. */
    private void abend(String message, String status) {
        sysout.accept(message);
        sysout.accept("FILE STATUS IS: NNNN" + "00" + status);
        sysout.accept("ABENDING PROGRAM");
        throw new Abend();
    }

    /** F6: the DALYREJS close error reports the XREFFILE status. Exposed for the unit test. */
    static String rejsCloseErrorMessage(String xrefStatus) {
        return "ERROR CLOSING DAILY REJECTS FILE\nFILE STATUS IS: NNNN" + "00" + xrefStatus + "\nABENDING PROGRAM";
    }
}
