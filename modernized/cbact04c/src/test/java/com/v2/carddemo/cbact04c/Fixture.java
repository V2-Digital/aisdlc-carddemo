package com.v2.carddemo.cbact04c;

import static com.v2.carddemo.cbact04c.Support.*;

import java.nio.file.Files;
import java.nio.file.Path;

/** Two accounts (so account 1 is rewritten at the break); account 2 has a zero balance. */
final class Fixture {
    static final String A1 = "00000000001", A2 = "00000000002";
    static final String ZERO12 = "00000000000{", ZERO11 = "0000000000{";

    static Path inputs(String tcatBal, String acctBal, String group, String discRows) throws Exception {
        Path in = Files.createTempDirectory("in");
        put(in, "TCATBALF", tcat(A1, "01", "0001", tcatBal) + tcat(A2, "01", "0001", ZERO11));
        put(in, "XREFFILE", xref("0500024453765740", A1) + xref("0500024453765741", A2));
        put(in, "ACCTFILE", acct(A1, acctBal, "00000001000{", "00000000100}", group)
                + acct(A2, ZERO12, ZERO12, ZERO12, ""));
        put(in, "DISCGRP", discRows);
        return in;
    }

    static String firstTran(Support.Result r) throws Exception {
        return r.file("TRANSACT").split("\n")[0];
    }

    static String amount(String tran) {
        return tran.substring(132, 143);
    }
}
