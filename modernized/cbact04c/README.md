# cbact04c — INTCALC interest batch in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. Plain Java 21, no runtime dependencies.

## Build / run

```sh
mvn -q package -DskipTests
java -jar target/cbact04c-1.0.0.jar <PARM> <IN_DIR> <OUT_DIR>
```

Reads `<IN_DIR>/{TCATBALF,XREFFILE,DISCGRP,ACCTFILE}.txt` (harness canonical text form),
writes `<OUT_DIR>/ACCTFILE.txt` and `TRANSACT.txt`. SYSOUT goes to stdout; exit 0, or 16 on abend.

## Preserved legacy defects (do not fix here)

- F1 the last account in TCATBALF is never rewritten (interest posted, balance and cycle totals untouched)
- F2 interest truncates (no ROUNDED)
- F3 blank `ACCT-GROUP-ID` means every lookup falls back to DEFAULT with a warning
- F4 cycle credit/debit zeroed for every rewritten account
- F5 fees paragraph is empty
- F6 silent overflow wrap of amount, balance and TRAN-ID suffix
