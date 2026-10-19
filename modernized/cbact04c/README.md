# cbact04c — INTCALC (CBACT04C) in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. Plain Maven, JUnit 5 only.

```
mvn -q verify
java -jar target/cbact04c.jar <PARM-DATE> <IN_DIR> <OUT_DIR>
```
Inputs in `IN_DIR`: `TCATBALF.txt XREFFILE.txt DISCGRP.txt ACCTFILE.txt`. Outputs in `OUT_DIR`: `TRANSACT.txt`, `ACCTFILE.txt`.
SYSOUT goes to stdout, exit code is the RC (0, or 16 on abend).
`CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss` freezes the clock (UTC).

## Preserved legacy defects
- D1 the last account in TCATBALF is never rewritten
- D2 fee step is empty
- D3 lookups use `ACCT-GROUP-ID` (blank in sample data), so everything falls back to `DEFAULT`
- D4 DISCGRP open failure says `ERROR OPENING DALY REJECTS FILE`
- D5 silent overflow / tran-ID suffix wrap
