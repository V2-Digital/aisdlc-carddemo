package com.v2.carddemo.cbact04c;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Builds small in-memory fixtures and runs the program on them. */
final class Fx {
    List<String> tcat = new ArrayList<>(), xref = new ArrayList<>(), acct = new ArrayList<>(), disc = new ArrayList<>();
    String sysout, err;
    int rc;
    List<String> transact, acctOut;

    static String tc(String acct, String type, String cat, String bal) {
        return Records.pad(acct + type + cat + bal, 50);
    }
    static String xr(String card, String acct) {
        return Records.pad(card + "000000000" + acct, 50);
    }
    static String ac(String id, String bal) {
        String r = Records.pad(id + "Y" + bal, 300);
        r = Records.splice(r, 79, "000000012345");
        r = Records.splice(r, 91, "000000067890");
        return r;
    }
    static String dg(String group, String type, String cat, String rate) {
        return Records.pad(Records.pad(group, 10) + type + cat + rate, 50);
    }

    Fx run(Path tmp) throws Exception {
        Path in = Files.createDirectories(tmp.resolve("in")), out = Files.createDirectories(tmp.resolve("out"));
        Records.write(in.resolve("TCATBALF.txt"), tcat, 50);
        Records.write(in.resolve("XREFFILE.txt"), xref, 50);
        Records.write(in.resolve("ACCTFILE.txt"), acct, 300);
        Records.write(in.resolve("DISCGRP.txt"), disc, 50);
        ByteArrayOutputStream o = new ByteArrayOutputStream(), e = new ByteArrayOutputStream();
        rc = Cbact04c.run("2022071800", new DdFiles(Map.of(), in, out), () -> "2022071800000000",
                new PrintStream(o, true), new PrintStream(e, true));
        sysout = o.toString();
        err = e.toString();
        transact = Records.read(out.resolve("TRANSACT.txt"), 350);
        acctOut = Records.read(out.resolve("ACCTFILE.txt"), 300);
        return this;
    }

    /** Two accounts so that account 1 is rewritten (not the last one). */
    static Fx twoAccounts(String bal, String rate) {
        Fx f = new Fx();
        f.tcat.add(tc("00000000001", "01", "0001", bal));
        f.tcat.add(tc("00000000002", "01", "0001", "00000000000{"));
        f.xref.add(xr("1111111111111111", "00000000001"));
        f.xref.add(xr("2222222222222222", "00000000002"));
        f.acct.add(ac("00000000001", "00000010000{"));
        f.acct.add(ac("00000000002", "00000010000{"));
        f.disc.add(dg("DEFAULT", "01", "0001", rate));
        return f;
    }
}
