# cbact04c — INTCALC interest calculation in Java 21

Like-for-like rewrite of `app/cbl/CBACT04C.cbl`.

```sh
mvn -q package
CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar 2022071800 DATA_DIR
```

- Reads `DATA_DIR/{TCATBALF,XREFFILE,ACCTFILE,DISCGRP}.txt`; writes `TRANSACT.txt`, rewrites `ACCTFILE.txt`.
- SYSOUT goes to stdout; the exit code is the RC (0, or 16 on abend; no output files on abend).
- Clock: `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`), else system time in UTC.

## Preserved legacy defects (fix by separate CR)

- D1 The last account in TCATBAL is never rewritten.
- D2 Interest is truncated, not rounded.
- D3 The rate key uses the blank `ACCT-GROUP-ID`, so lookups fall back to `DEFAULT`.
- D4 Fees are a no-op.
- D5 Zero-amount transactions are written when the rate is not 0.
- D6 `ORIG-TS` is the run time, with hundredths precision only.
- D7 The counter wraps at 999,999; PARM length is not checked.
- D8 Cycle credit/debit are zeroed even when the interest is 0.
