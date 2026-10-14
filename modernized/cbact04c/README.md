# cbact04c — INTCALC (`CBACT04C`) in Java 21

Like-for-like rewrite of `app/cbl/CBACT04C.cbl`. No frameworks; JUnit 5 only (tests: Test stage).

```
mvn -q package -DskipTests
java -jar target/cbact04c.jar <PARM> <IN_DIR> <OUT_DIR>      # e.g. 2022071800
```
- In: `<IN_DIR>/{TCATBALF,XREFFILE,ACCTFILE,DISCGRP}.txt`. Out: `<OUT_DIR>/{TRANSACT,ACCTFILE,SYSOUT}.txt`.
- Clock: env `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`), else system UTC. Exit 0, or 16 on abend.
- Cross-check against live COBOL: `legacy/tests/cbact04c-java-equiv.sh`.

## Preserved legacy defects (do not fix here)
- **D1** last account in TCATBAL is never rewritten (balance and cycle fields untouched).
- **D2** interest truncated, not rounded.
- **D3** silent overflow: TRAN-ID suffix wraps at 10^6; amounts lose high-order digits.
- **D4** blank `ACCT-GROUP-ID` in sample data, so every lookup falls back to `DEFAULT`.
- **D5** fees (`1400`) are a no-op (CR-2).
- **D6** DISCGRP open error says `DALY REJECTS FILE`.

On abend, ACCTFILE/TRANSACT are written with whatever was done so far; this is not golden-checked.
