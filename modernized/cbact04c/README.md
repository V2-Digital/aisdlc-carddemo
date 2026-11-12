# cbact04c: INTCALC interest calculation in Java 21

Like-for-like rewrite of `app/cbl/CBACT04C.cbl`. Plain Maven, no frameworks.

    mvn -q package
    java -jar target/cbact04c.jar 2022071800 --dd TCATBALF=… XREFFILE=… ACCTFILE=… DISCGRP=… TRANSACT=… [--sysout file]

Files are the harness's fixed-width LF text form (`legacy/README.md`). `ACCTFILE` is read and rewritten **in place**
(copy it first). Exit code 0, or 16 on abend. Packages: `com.v2.carddemo.cbact04c`.

Preserved legacy defects (do not fix here): F1 last account never rewritten; F2 interest truncated; F3 fees empty;
F4 tran-id suffix wraps at 999999; F5 DISCGRP open error says "DALY REJECTS FILE"; F6 interest posted as 01/0005.
