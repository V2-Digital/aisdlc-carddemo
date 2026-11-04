# cbact04c — INTCALC (CBACT04C) in Java 21

    mvn -q package
    CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c-1.0.jar <PARM> <IN_DIR> <OUT_DIR>

Reads `<IN_DIR>/{TCATBALF,XREFFILE,DISCGRP,ACCTFILE}.txt`, writes `<OUT_DIR>/{TRANSACT,ACCTFILE}.txt`,
DISPLAY output on stdout. Exit 0, or 16 on abend. Unset clock = system UTC.

Preserved legacy defects (separate CR): D1 last account not updated; D2 interest truncated, not rounded;
D3 silent high-order truncation; D4 cycle fields zeroed regardless of interest; D5 wrong DISCGRP open-error text.
