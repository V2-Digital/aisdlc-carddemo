# cbact04c — INTCALC interest calculation in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. See `aisdlc/<run>/spec.md`.

```sh
mvn -q package
java -jar target/cbact04c.jar --parm 2022071800 --in <dir> --out <dir> [--current-date YYYYMMDDHHMMSSss]
```

Inputs `<in>/{TCATBALF,XREFFILE,ACCTFILE,DISCGRP}.txt`; outputs `<out>/{TRANSACT,ACCTFILE}.txt`;
SYSOUT on stdout; exit code = RC (16 on abend).

## Preserved legacy defects (do not fix here)
- D1 last account in TCATBALF is never rewritten
- D2 interest truncated, not rounded
- D3 blank `ACCT-GROUP-ID` → every account priced at `DEFAULT`
- D4 no fees (`1400-COMPUTE-FEES` empty)
- F1 zero-amount transactions; F2 6-digit tran-id wraps / overflow drops high digits; F3 hundredths-only timestamps
