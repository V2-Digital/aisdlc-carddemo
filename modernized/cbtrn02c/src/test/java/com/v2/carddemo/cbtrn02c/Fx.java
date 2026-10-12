package com.v2.carddemo.cbtrn02c;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

/** Test fixtures: record builders and a run helper. */
final class Fx {
    static final String A = "00000000001", C = "0000000000000001";
    static final Clock CLOCK = Clock.fixed(Instant.parse("2022-07-18T00:00:00Z"), ZoneOffset.UTC);

    static Path repoRoot() {
        return Path.of(System.getProperty("repo.root")).normalize();
    }

    static String z(long cents, int n) {
        return Zoned.encode(cents, n);
    }

    static String tran(String id, String amt, String card, String type, String cat, String origTs) {
        return String.format("%-16s%s%s%-10s%-100s%s%09d%-50s%-50s%-10s%s%-26s%-26s%s", id, type, cat, "POS", "d", amt,
                1, "M", "C", "Z", card, origTs, "2000-01-01-00.00.00.000000", "F".repeat(20));
    }

    static String tran(String id, long cents) {
        return tran(id, z(cents, 11), C, "01", "0001", "2022-06-10 19:27:53.000000");
    }

    static String xref(String card, String acct) {
        return String.format("%-50s", card + "000000001" + acct);
    }

    static String acct(String id, long bal, long limit, String exp, long cc, long cd) {
        return String.format("%-300s", id + "Y" + z(bal, 12) + z(limit, 12) + z(0, 12) + "2014-11-20" + exp + exp
                + z(cc, 12) + z(cd, 12) + String.format("%-10s%-10s", "ZIP", "GRP"));
    }

    static String acct(long limit, long cc, long cd) {
        return acct(A, 0, limit, "2030-01-01", cc, cd);
    }

    static String tcat(String acct, String type, String cat, long bal, String filler) {
        return acct + type + cat + z(bal, 11) + filler;
    }

    record Result(int rc, String out, Path dir) {
        String file(String dd) {
            try {
                return new String(Files.readAllBytes(dir.resolve(dd)), StandardCharsets.ISO_8859_1);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        List<String> lines(String dd) {
            String s = file(dd);
            return s.isEmpty() ? List.of() : List.of(s.substring(0, s.length() - 1).split("\n", -1));
        }
    }

    /** Writes the given datasets (null = leave missing) to a temp dir, runs the program, returns results. */
    static Result run(List<String> dalytran, List<String> xref, List<String> acct, List<String> tcat) {
        try {
            Path d = Files.createTempDirectory("cbtrn02c");
            put(d, "DALYTRAN", dalytran);
            put(d, "XREFFILE", xref);
            put(d, "ACCTFILE", acct);
            put(d, "TCATBALF", tcat);
            return run(d);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    static Result run(Path d) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        PrintStream ps = new PrintStream(o, true, StandardCharsets.ISO_8859_1);
        int rc = new Cbtrn02c(CLOCK, ps, new PrintStream(new ByteArrayOutputStream()), n -> d.resolve(n)).run();
        return new Result(rc, o.toString(StandardCharsets.ISO_8859_1), d);
    }

    static void put(Path d, String dd, List<String> recs) throws IOException {
        if (recs == null) return;
        StringBuilder sb = new StringBuilder();
        for (String r : recs) sb.append(r).append('\n');
        Files.write(d.resolve(dd), sb.toString().getBytes(StandardCharsets.ISO_8859_1));
    }

    /** Standard single account/card world. */
    static Result run(List<String> tran, String acct) {
        return run(tran, List.of(xref(C, A)), List.of(acct), List.of(tcat(A, "01", "0001", 0, "0".repeat(22))));
    }
}
