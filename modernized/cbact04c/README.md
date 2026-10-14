# cbact04c — INTCALC interest batch in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl` (CR-1). Plain Java, JUnit 5 only.

```
mvn -q verify
java -jar target/cbact04c.jar <PARM> <IN_DIR> <OUT_DIR>   # e.g. 2022071800
```
- In: `IN_DIR/{TCATBALF,XREFFILE,ACCTFILE,DISCGRP}.txt`. Out: `OUT_DIR/{TRANSACT,ACCTFILE}.txt`. SYSOUT on stdout.
- Clock: env `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`, default `2022071800000000`).
- Exit 0 ok, 16 on abend (no output files written).
- Fixed-width text, LF, overpunch signs; money is `BigDecimal`, interest truncated (`RoundingMode.DOWN`).

## Preserved legacy defects (not fixed)
- D1 last account in TCATBALF is never rewritten (its interest is lost).
- D2 `ACCT-GROUP-ID` is blank in the data, so every rate comes from `DEFAULT`.
- D3 fees never computed (`computeFees()` is an empty hook for CR-2).
- D4 silent overflow; TRAN-ID suffix wraps at 999,999.
- D5 running twice posts interest twice.
