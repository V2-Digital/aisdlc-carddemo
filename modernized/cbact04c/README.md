# cbact04c — INTCALC (CBACT04C) in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. Legacy defects D1–D8 are preserved on purpose.

## Build
    cd modernized/cbact04c && mvn -q package -DskipTests   # -> target/cbact04c.jar

## Run
Files are canonical fixed-width text (same as `legacy/golden`, LF, overpunch signs). Bound by env vars:
`DD_TCATBALF`, `DD_XREFFILE`, `DD_ACCTFILE` (read, then overwritten with the updated master), `DD_DISCGRP`, `DD_TRANSACT` (output).
`CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss` fixes the clock (default: system UTC).

    DD_TCATBALF=tcatbal.txt DD_XREFFILE=cardxref.txt DD_ACCTFILE=acct.txt \
    DD_DISCGRP=discgrp.txt DD_TRANSACT=TRANSACT.txt \
    CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar 2022071800 > SYSOUT.txt

SYSOUT goes to stdout; exit code is the RC (0, or 16 on abend; the output files must then be discarded).
The PARM must be exactly 10 characters (Q3), otherwise exit 16.
