package com.v2.carddemo.cbact04c;

import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: cbact04c <PARM-DATE> [--TCATBALF=f] [--XREFFILE=f] [--DISCGRP=f] [--ACCTFILE=f] [--out=dir]");
            System.exit(2);
        }
        Path tcatbalf = Path.of("TCATBALF.txt");
        Path xreffile = Path.of("XREFFILE.txt");
        Path discgrp = Path.of("DISCGRP.txt");
        Path acctfile = Path.of("ACCTFILE.txt");
        Path out = Path.of(".");
        for (int i = 1; i < args.length; i++) {
            String a = args[i];
            if (a.startsWith("--TCATBALF=")) {
                tcatbalf = Path.of(a.substring(11));
            } else if (a.startsWith("--XREFFILE=")) {
                xreffile = Path.of(a.substring(11));
            } else if (a.startsWith("--DISCGRP=")) {
                discgrp = Path.of(a.substring(10));
            } else if (a.startsWith("--ACCTFILE=")) {
                acctfile = Path.of(a.substring(11));
            } else if (a.startsWith("--out=")) {
                out = Path.of(a.substring(6));
            } else {
                System.err.println("unknown argument: " + a);
                System.exit(2);
            }
        }
        java.io.PrintStream sysout = new java.io.PrintStream(new java.io.FileOutputStream(java.io.FileDescriptor.out), false,
                java.nio.charset.StandardCharsets.ISO_8859_1);
        int rc = new Cbact04c(tcatbalf, xreffile, discgrp, acctfile, out, Clock.fromEnv(), sysout).run(args[0]);
        sysout.flush();
        if (rc == AbendException.EXIT_CODE) {
            System.err.println("CEE3ABD: ABEND U0999");
        }
        System.exit(rc);
    }
}
