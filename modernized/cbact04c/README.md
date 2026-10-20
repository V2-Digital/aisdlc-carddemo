# cbact04c — INTCALC interest batch in Java 21

Like-for-like rewrite of `app/cbl/CBACT04C.cbl`. Plain Java 21, JUnit 5 only.

```sh
mvn -q verify
CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar 2022071800 --in <dir> --out <dir>
```
`<dir>` holds `TCATBALF.txt XREFFILE.txt ACCTFILE.txt DISCGRP.txt` (fixed-width, as in `legacy/golden/`).
Writes `TRANSACT.txt` and `ACCTFILE.txt` (only on RC 0). Exit code = RC (16 on abend).

## Preserved legacy defects (do not fix here)
F1 last account never rewritten; F2 interest truncated, not rounded; F3 rate keyed on blank
`ACCT-GROUP-ID` so always DEFAULT; F4 fees no-op; F5 silent overflow; F6 PARM length ignored, 6-digit
suffix wraps; F7 duplicate XREF: first wins.
