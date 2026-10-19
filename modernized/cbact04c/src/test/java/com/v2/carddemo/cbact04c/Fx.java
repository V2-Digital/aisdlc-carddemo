package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Synthetic-dataset helpers shared by the rule tests. */
final class Fx {
    final Path in;
    final Path out;
    String sysout;
    int rc;

    Fx(Path tmp) throws IOException {
        in = Files.createDirectory(tmp.resolve("in"));
        out = Files.createDirectory(tmp.resolve("out"));
    }

    static String tcat(long acct, String type, String cat, String bal) {
        return Records.pad(String.format("%011d", acct) + type + cat + bal, 50);
    }

    static String xref(String card, long acct) {
        return Records.pad(card + "000000000" + String.format("%011d", acct), 50);
    }

    static String disc(String group, String type, String cat, String rate) {
        return Records.pad(Records.pad(group, 10) + type + cat + rate, 50);
    }

    /** bal, credit and debit are 12-char zoned fields; the rest of the record is spaces. */
    static String acct(long id, String bal, String credit, String debit, String group) {
        String r = Records.pad(String.format("%011d", id) + "Y" + bal, 300);
        r = Records.put(r, 79, credit);
        r = Records.put(r, 91, debit);
        return Records.put(r, 113, Records.pad(group, 10));
    }

    static final String Z12 = "00000000000{";

    Fx file(String dd, List<String> recs) throws IOException {
        Records.write(in, dd, recs);
        return this;
    }

    Fx run(String parm) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        rc = new Cbact04c(Main.clock("2022071800000000"), new PrintStream(b, true, StandardCharsets.ISO_8859_1))
                .run(parm, in, out);
        sysout = b.toString(StandardCharsets.ISO_8859_1);
        return this;
    }

    List<String> outRecs(String dd, int lrecl) throws IOException {
        return Records.read(out, dd, lrecl);
    }
}
