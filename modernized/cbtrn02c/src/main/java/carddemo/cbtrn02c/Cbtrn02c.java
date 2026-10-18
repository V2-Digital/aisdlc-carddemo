package carddemo.cbtrn02c;

import static carddemo.cbtrn02c.RecordIo.put;
import static carddemo.cbtrn02c.RecordIo.str;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Java rewrite of CBTRN02C (daily transaction posting). Paragraph names follow the COBOL. */
public final class Cbtrn02c {
    /** Equivalent of 9999-ABEND-PROGRAM (CEE3ABD stub exits 16). */
    public static final class AbendException extends RuntimeException {
        public AbendException(String m) {
            super(m);
        }
    }

    private final Map<String, Path> dd;
    private final String clock; // YYYYMMDDHHMMSSss
    private final PrintStream sysout;

    private final KsdsFile xref = new KsdsFile(0, 16);
    private final KsdsFile acct = new KsdsFile(0, 11);
    private final KsdsFile tcat = new KsdsFile(0, 17);
    private final KsdsFile tran = new KsdsFile(0, 16);
    private final List<byte[]> rejects = new ArrayList<>();

    /** TRAN-CAT-BAL-RECORD work area: sticky between records (INITIALIZE leaves FILLER alone). */
    private byte[] tcatWork = RecordIo.pad(new byte[0], 50);
    private byte[] acctRec;
    private int reason;
    private String reasonDesc;
    private int txnCount;
    private int rejectCount;

    public Cbtrn02c(Map<String, Path> dd, String clock, PrintStream sysout) {
        this.dd = dd;
        this.clock = clock;
        this.sysout = sysout;
    }

    /** @return the program return code */
    public int run() {
        sysout.println("START OF EXECUTION OF PROGRAM CBTRN02C");
        List<byte[]> daily;
        try {
            daily = open();
        } catch (AbendException e) {
            return 16;
        }
        try {
            for (byte[] d : daily) {
                txnCount++;
                reason = 0;
                reasonDesc = "";
                validate(d);
                if (reason == 0) {
                    postTransaction(d);
                } else {
                    rejectCount++;
                    writeReject(d);
                }
            }
        } catch (AbendException e) {
            flush();
            return 16;
        }
        flush();
        sysout.println("TRANSACTIONS PROCESSED :" + String.format("%09d", txnCount));
        sysout.println("TRANSACTIONS REJECTED  :" + String.format("%09d", rejectCount));
        sysout.println("END OF EXECUTION OF PROGRAM CBTRN02C");
        return rejectCount > 0 ? 4 : 0;
    }

    private List<byte[]> open() {
        List<byte[]> daily = load("DALYTRAN", 350, null);
        load("XREFFILE", 50, xref);
        load("ACCTFILE", 300, acct);
        load("TCATBALF", 50, tcat);
        return daily;
    }

    private List<byte[]> load(String name, int lrecl, KsdsFile into) {
        try {
            List<byte[]> recs = RecordIo.read(dd.get(name), lrecl);
            if (into != null) {
                into.load(recs);
            }
            return recs;
        } catch (IOException | RuntimeException e) {
            sysout.println("ERROR OPENING " + name);
            sysout.println("FILE STATUS IS: NNNN0035");
            sysout.println("ABENDING PROGRAM");
            throw new AbendException("open " + name);
        }
    }

    private void flush() {
        try {
            RecordIo.write(dd.get("TRANFILE"), tran.records());
            RecordIo.write(dd.get("DALYREJS"), rejects);
            RecordIo.write(outPath("ACCTFILE"), acct.records());
            RecordIo.write(outPath("TCATBALF"), tcat.records());
        } catch (IOException e) {
            throw new AbendException("write outputs: " + e);
        }
    }

    private Path outPath(String name) {
        return dd.getOrDefault(name + "_OUT", dd.get(name));
    }

    // 1500-VALIDATE-TRAN
    private void validate(byte[] d) {
        byte[] x = xref.read(str(d, 262, 16));
        if (x == null) {
            fail(100, "INVALID CARD NUMBER FOUND");
            return;
        }
        acctRec = acct.read(str(x, 25, 11));
        if (acctRec == null) {
            fail(101, "ACCOUNT RECORD NOT FOUND");
            return;
        }
        long amt = Overpunch.decode(d, 132, 11);
        long credit = Overpunch.decode(acctRec, 78, 12);
        long debit = Overpunch.decode(acctRec, 90, 12);
        long limit = Overpunch.decode(acctRec, 24, 12);
        long temp = Overpunch.truncate(credit - debit + amt, 11); // WS-TEMP-BAL S9(9)V99 (D1, D9)
        if (limit < temp) {
            fail(102, "OVERLIMIT TRANSACTION");
        }
        if (str(acctRec, 58, 10).compareTo(str(d, 278, 10)) < 0) { // text compare (D7)
            fail(103, "TRANSACTION RECEIVED AFTER ACCT EXPIRATION"); // overwrites 102 (D2)
        }
    }

    private void fail(int code, String desc) {
        reason = code;
        reasonDesc = desc;
    }

    // 2500-WRITE-REJECT-REC
    private void writeReject(byte[] d) {
        byte[] r = RecordIo.pad(d, 430);
        put(r, 350, String.format("%04d", reason));
        put(r, 354, reasonDesc);
        rejects.add(r);
    }

    // 2000-POST-TRANSACTION
    private void postTransaction(byte[] d) {
        byte[] t = RecordIo.pad(Arrays.copyOf(d, 304), 350);
        put(t, 304, timestamp()); // FILLER (330-349) stays spaces
        long amt = Overpunch.decode(d, 132, 11);
        updateTcatbal(d, amt);
        updateAccount(amt);
        if (!tran.write(t)) {
            sysout.println("ERROR WRITING TO TRANSACTION FILE");
            sysout.println("FILE STATUS IS: NNNN0022");
            sysout.println("ABENDING PROGRAM");
            throw new AbendException("duplicate TRAN-ID (D6: balances already updated)");
        }
    }

    // 2700-UPDATE-TCATBAL
    private void updateTcatbal(byte[] d, long amt) {
        String key = xrefAcct(d) + str(d, 16, 2) + str(d, 18, 4);
        byte[] found = tcat.read(key);
        if (found != null) {
            tcatWork = found;
            Overpunch.encode(Overpunch.truncate(Overpunch.decode(tcatWork, 17, 11) + amt, 11), tcatWork, 17, 11);
            tcat.rewrite(tcatWork);
        } else {
            sysout.println("TCATBAL record not found for key : " + key + ".. Creating.");
            // INITIALIZE: key and balance reset, FILLER (28-49) untouched
            put(tcatWork, 0, key);
            Overpunch.encode(Overpunch.truncate(amt, 11), tcatWork, 17, 11);
            tcat.write(tcatWork);
        }
    }

    private String xrefAcct(byte[] d) {
        return str(xref.read(str(d, 262, 16)), 25, 11);
    }

    // 2800-UPDATE-ACCOUNT-REC
    private void updateAccount(long amt) {
        add12(12, amt); // ACCT-CURR-BAL
        add12(amt >= 0 ? 78 : 90, amt); // CYC-CREDIT / CYC-DEBIT
        acct.rewrite(acctRec); // INVALID KEY (109) is unreachable here (D3)
    }

    private void add12(int off, long amt) {
        Overpunch.encode(Overpunch.truncate(Overpunch.decode(acctRec, off, 12) + amt, 12), acctRec, off, 12);
    }

    // Z-GET-DB2-FORMAT-TIMESTAMP
    private String timestamp() {
        String c = clock;
        return c.substring(0, 4) + "-" + c.substring(4, 6) + "-" + c.substring(6, 8) + "-"
                + c.substring(8, 10) + "." + c.substring(10, 12) + "." + c.substring(12, 14) + "."
                + c.substring(14, 16) + "0000";
    }
}
