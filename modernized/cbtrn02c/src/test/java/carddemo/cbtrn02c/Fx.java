package carddemo.cbtrn02c;

import static carddemo.cbtrn02c.RecordIo.put;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Test fixture: builds fixed-width records, runs the program in a temp dir, exposes outputs. */
final class Fx {
    static final String CLOCK = "2022071800000000";
    final Path dir;
    final List<byte[]> daily = new ArrayList<>();
    final List<byte[]> xref = new ArrayList<>();
    final List<byte[]> acct = new ArrayList<>();
    final List<byte[]> tcat = new ArrayList<>();
    String sysout;
    int rc;

    Fx() throws IOException {
        dir = Files.createTempDirectory("cbtrn02c");
    }

    static Path root() {
        Path p = Path.of("").toAbsolutePath();
        while (p != null && !Files.isDirectory(p.resolve("legacy/golden"))) {
            p = p.getParent();
        }
        return p;
    }

    static String num(long v, int len) {
        return String.format("%0" + len + "d", v);
    }

    static byte[] signed(long v, int len) {
        byte[] b = new byte[len];
        Overpunch.encode(v, b, 0, len);
        return b;
    }

    /** amt in cents. */
    Fx txn(String id, String type, String cat, long amt, String card, String origTs) {
        byte[] r = RecordIo.pad(new byte[0], 350);
        put(r, 0, id);
        put(r, 16, type);
        put(r, 18, cat);
        put(r, 22, "POS TERM");
        put(r, 132, new String(signed(amt, 11), StandardCharsets.ISO_8859_1));
        put(r, 262, card);
        put(r, 278, origTs + " 19:27:53.000000");
        daily.add(r);
        return this;
    }

    Fx txn(String id, long amt, String card) {
        return txn(id, "01", "0001", amt, card, "2022-06-10");
    }

    Fx xref(String card, String acctId) {
        byte[] r = RecordIo.pad(new byte[0], 50);
        put(r, 0, card);
        put(r, 25, acctId);
        xref.add(r);
        return this;
    }

    Fx acct(String id, long bal, long limit, long credit, long debit, String expiry) {
        byte[] r = RecordIo.pad(new byte[0], 300);
        put(r, 0, id);
        put(r, 11, "Y");
        put(r, 12, new String(signed(bal, 12), StandardCharsets.ISO_8859_1));
        put(r, 24, new String(signed(limit, 12), StandardCharsets.ISO_8859_1));
        put(r, 58, expiry);
        put(r, 78, new String(signed(credit, 12), StandardCharsets.ISO_8859_1));
        put(r, 90, new String(signed(debit, 12), StandardCharsets.ISO_8859_1));
        acct.add(r);
        return this;
    }

    /** Account with a 1,000.00 limit, zero balances, far-future expiry. */
    Fx acct(String id) {
        return acct(id, 0, 100000, 0, 0, "2030-01-01");
    }

    Fx tcat(String acctId, String type, String cat, long bal, String filler) {
        byte[] r = RecordIo.pad(new byte[0], 50);
        put(r, 0, acctId + type + cat);
        put(r, 17, new String(signed(bal, 11), StandardCharsets.ISO_8859_1));
        put(r, 28, filler);
        tcat.add(r);
        return this;
    }

    /** One card 4000000000000001 -> account 00000000001 with default account. */
    static Fx basic() throws IOException {
        return new Fx().xref("4000000000000001", "00000000001").acct("00000000001");
    }

    static final String CARD = "4000000000000001";

    Path p(String n) {
        return dir.resolve(n + ".txt");
    }

    Fx run() throws IOException {
        RecordIo.write(p("DALYTRAN"), daily);
        RecordIo.write(p("XREFFILE"), xref);
        RecordIo.write(p("ACCTFILE"), acct);
        RecordIo.write(p("TCATBALF"), tcat);
        return runFiles();
    }

    Fx runFiles() {
        Map<String, Path> dd = new HashMap<>();
        for (String n : new String[] {"DALYTRAN", "XREFFILE", "ACCTFILE", "TCATBALF", "TRANFILE", "DALYREJS"}) {
            dd.put(n, p(n));
        }
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        rc = new Cbtrn02c(dd, CLOCK, new PrintStream(bo, true, StandardCharsets.ISO_8859_1)).run();
        sysout = bo.toString(StandardCharsets.ISO_8859_1);
        return this;
    }

    List<byte[]> out(String dd, int lrecl) throws IOException {
        return RecordIo.read(p(dd), lrecl);
    }

    byte[] acctOut(int i) throws IOException {
        return out("ACCTFILE", 300).get(i);
    }

    long field(byte[] r, int off, int len) {
        return Overpunch.decode(r, off, len);
    }

    long bal(byte[] a) {
        return field(a, 12, 12);
    }

    long credit(byte[] a) {
        return field(a, 78, 12);
    }

    long debit(byte[] a) {
        return field(a, 90, 12);
    }

    int reasonOf(int i) throws IOException {
        return Integer.parseInt(RecordIo.str(out("DALYREJS", 430).get(i), 350, 4));
    }
}
