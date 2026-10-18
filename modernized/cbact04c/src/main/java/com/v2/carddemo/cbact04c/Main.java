package com.v2.carddemo.cbact04c;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** java -jar cbact04c.jar PARM --TCATBALF=p --XREFFILE=p --ACCTFILE=p --DISCGRP=p --TRANSACT=p --ACCTOUT=p [--SYSOUT=p] */
public final class Main {
    private Main() {}

    public static void main(String[] args) throws IOException {
        System.exit(run(args, System.getenv("CARDDEMO_CURRENT_DATE")));
    }

    /** Returns the exit status: 0 ok, 16 abend. */
    public static int run(String[] args, String currentDate) throws IOException {
        String parm = args.length > 0 ? args[0] : "";
        Map<String, String> dd = new HashMap<>();
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            int eq = a.indexOf('=');
            if (!a.startsWith("--") || eq < 0) throw new IllegalArgumentException("bad argument: " + a);
            dd.put(a.substring(2, eq), a.substring(eq + 1));
        }
        List<String> sysout = new ArrayList<>();
        int rc = 0;
        try {
            InterestCalc.Result r = new InterestCalc(parm, clock(currentDate), sysout).run(
                load(dd, "TCATBALF", Records.TCATBAL_LEN, true), load(dd, "XREFFILE", Records.XREF_LEN, false),
                load(dd, "ACCTFILE", Records.ACCT_LEN, false), load(dd, "DISCGRP", Records.DISCGRP_LEN, false));
            FixedText.write(Path.of(dd.get("TRANSACT")), r.transactions(), Records.TRAN_LEN);
            FixedText.write(Path.of(dd.get("ACCTOUT")), r.accounts(), Records.ACCT_LEN);
        } catch (Abend e) {
            sysout.addAll(e.sysout());
            sysout.add("ABENDING PROGRAM");
            System.err.println("CEE3ABD: ABEND U0999");
            rc = 16;
        }
        String text = String.join("\n", sysout) + "\n";
        if (dd.containsKey("SYSOUT")) {
            Files.writeString(Path.of(dd.get("SYSOUT")), text, StandardCharsets.ISO_8859_1);
        } else {
            System.out.print(text);
            System.out.flush();
        }
        return rc;
    }

    private static List<String> load(Map<String, String> dd, String name, int lrecl, boolean sortByKey) {
        String path = dd.get(name);
        try {
            List<String> recs = FixedText.read(Path.of(path), lrecl);
            if (sortByKey) recs.sort((a, b) -> a.substring(0, 17).compareTo(b.substring(0, 17)));
            return recs;
        } catch (IOException | RuntimeException e) {
            throw new Abend(openError(name), "FILE STATUS IS: NNNN0035");
        }
    }

    // Texts from CBACT04C.cbl 0000-0400 open paragraphs (DISCGRP text is the legacy typo, F7).
    private static String openError(String dd) {
        return switch (dd) {
            case "TCATBALF" -> "ERROR OPENING TRANSACTION CATEGORY BALANCE";
            case "XREFFILE" -> "ERROR OPENING CROSS REF FILE" + "35";
            case "DISCGRP" -> "ERROR OPENING DALY REJECTS FILE";
            case "ACCTFILE" -> "ERROR OPENING ACCOUNT MASTER FILE";
            default -> "ERROR OPENING TRANSACTION FILE";
        };
    }

    /** CARDDEMO_CURRENT_DATE = YYYYMMDDHHMMSSss; unset means the system clock in UTC. */
    static Clock clock(String s) {
        if (s == null || s.length() != 16) return Clock.system(ZoneOffset.UTC);
        LocalDateTime t = LocalDateTime.of(Integer.parseInt(s.substring(0, 4)), Integer.parseInt(s.substring(4, 6)),
            Integer.parseInt(s.substring(6, 8)), Integer.parseInt(s.substring(8, 10)),
            Integer.parseInt(s.substring(10, 12)), Integer.parseInt(s.substring(12, 14)),
            Integer.parseInt(s.substring(14, 16)) * 10_000_000);
        return Clock.fixed(t.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    }
}
