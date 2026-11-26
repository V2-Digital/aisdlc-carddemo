# cbact04c: INTCALC (`CBACT04C`) in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. Plain Maven, no runtime dependencies.

```sh
mvn -q package -DskipTests
CARDDEMO_CURRENT_DATE=2022071800000000 \
  java -jar target/cbact04c.jar 2022071800 <IN_DIR> <OUT_DIR>
```

- Reads `<IN_DIR>/{TCATBALF,XREFFILE,ACCTFILE,DISCGRP}.txt`, writes `<OUT_DIR>/{TRANSACT,ACCTFILE}.txt`.
- SYSOUT goes to stdout. The exit code is the RC (0, or 16 on abend; partial output is kept).
- `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`) freezes the clock. Unset means system UTC.

## Preserved legacy defects (do not fix here)

- F1 The last account's interest is never posted to `ACCTFILE`.
- F2 Interest is truncated toward zero, not rounded.
- F3 Overflow wraps silently (no ON SIZE ERROR).
- F4 Zero-amount interest transactions are written.
- F5 Lookup uses the blank `ACCT-GROUP-ID`, so sample data always falls back to `DEFAULT`.
- F6 DISCGRP open error says `DALY REJECTS`. PARM length is ignored. Timestamps have hundredths only.
