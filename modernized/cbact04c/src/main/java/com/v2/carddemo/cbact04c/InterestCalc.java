package com.v2.carddemo.cbact04c;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Port of CBACT04C. Legacy defects are preserved on purpose: the last account is never rewritten (D1),
 * interest is truncated (D2), fields silently lose high-order digits (D3), fees are a no-op (D5),
 * and the transaction counter wraps at 999999 (D6).
 */
public final class InterestCalc {
    private final String parmDate;
    private final Clock clock;
    private final Consumer<String> sysout;

    /** Account records, updated in place; same order as the input. */
    public final List<String> accounts;
    public final List<String> transactions = new ArrayList<>();

    private final List<String> tcatbal;
    private final List<String> xref;
    private final Map<String, String> discgrp = new HashMap<>();
    private final Map<String, Integer> acctIndex = new HashMap<>();

    private int counter;
    private long totalInt;

    public InterestCalc(String parm, Clock clock, Consumer<String> sysout, List<String> tcatbal,
                        List<String> xref, List<String> discgrp, List<String> accounts) {
        this.parmDate = Records.pad(parm, 10);
        this.clock = clock;
        this.sysout = sysout;
        this.tcatbal = tcatbal;
        this.xref = xref;
        this.accounts = new ArrayList<>(accounts);
        for (String r : discgrp) {
            this.discgrp.put(Records.discKey(r), r);
        }
        for (int i = 0; i < accounts.size(); i++) {
            acctIndex.putIfAbsent(Records.acctId(accounts.get(i)), i);
        }
    }

    /** Runs the job; throws {@link AbendException} where the COBOL abends. */
    public void run() {
        sysout.accept("START OF EXECUTION OF PROGRAM CBACT04C");
        String lastAcct = null;
        int acctPos = -1;
        String card = null;
        for (String rec : tcatbal) {
            sysout.accept(rec);
            String acct = Records.tcatAcct(rec);
            if (!acct.equals(lastAcct)) {
                if (lastAcct != null) {
                    updateAccount(acctPos);
                }
                lastAcct = acct;
                totalInt = 0;
                acctPos = findAccount(acct);
                card = findXref(acct);
            }
            long rate = findRate(acctGroup(acctPos), Records.tcatType(rec), Records.tcatCat(rec));
            if (rate != 0) {
                long monthly = Zoned.truncate(Records.tcatBal(rec) * rate / 120000, 11);
                totalInt = Zoned.truncate(totalInt + monthly, 11);
                writeTran(Records.acctId(accounts.get(acctPos)), monthly, card);
                // 1400-COMPUTE-FEES: no-op (D5)
            }
        }
        // D1: the final account is never rewritten.
        sysout.accept("END OF EXECUTION OF PROGRAM CBACT04C");
    }

    private String acctGroup(int pos) {
        return Records.acctGroup(accounts.get(pos));
    }

    private void updateAccount(int pos) {
        String r = accounts.get(pos);
        accounts.set(pos, Records.rewriteAccount(r, Zoned.truncate(Records.acctBal(r) + totalInt, 12)));
    }

    private int findAccount(String acct) {
        Integer i = acctIndex.get(acct);
        if (i == null) {
            sysout.accept("ACCOUNT NOT FOUND: " + acct);
            abend("ERROR READING ACCOUNT FILE", "0023");
        }
        return i;
    }

    private String findXref(String acct) {
        for (String r : xref) {
            if (Records.xrefAcct(r).equals(acct)) {
                return Records.xrefCard(r);
            }
        }
        sysout.accept("ACCOUNT NOT FOUND: " + acct);
        return abend("ERROR READING XREF FILE", "0023");
    }

    private long findRate(String group, String type, String cat) {
        String r = discgrp.get(group + type + cat);
        if (r == null) {
            sysout.accept("DISCLOSURE GROUP RECORD MISSING");
            sysout.accept("TRY WITH DEFAULT GROUP CODE");
            r = discgrp.get(Records.pad("DEFAULT", 10) + type + cat);
            if (r == null) {
                abend("ERROR READING DEFAULT DISCLOSURE GROUP", "0023");
            }
        }
        return Records.discRate(r);
    }

    private void writeTran(String acctId, long amt, String card) {
        counter = (counter + 1) % 1_000_000;
        transactions.add(Records.tran(parmDate, counter, acctId, amt, card, clock));
    }

    private <T> T abend(String error, String status) {
        sysout.accept(error);
        sysout.accept("FILE STATUS IS: NNNN" + status);
        sysout.accept("ABENDING PROGRAM");
        throw new AbendException(error);
    }
}
