# cbact04c: INTCALC (`CBACT04C`) in Java 21

```
mvn -q package
java -jar target/cbact04c-1.0.0.jar --parm 2022071800 --clock 2022071800000000 --in <dir> --out <dir>
```
`--in` needs `TCATBALF.txt XREFFILE.txt ACCTFILE.txt DISCGRP.txt`. `--out` gets `TRANSACT.txt` and `ACCTFILE.txt`.
DISPLAY lines go to stdout. `--clock` is `YYYYMMDDHHMMSSss` (default: system clock, UTC).
Exit 16 on abend (missing account, xref or DEFAULT rate), and no output files are written.

## Preserved legacy defects (do not fix here)
- D1 The last account's interest is posted but its ACCTFILE record is never rewritten.
- D2 Interest is truncated, not rounded.
- D3 TCATBALF is never reset, so interest accrues again on the next run.
- D4 Blank `ACCT-GROUP-ID` means DEFAULT rates only on sample data.
- D5 `1400-COMPUTE-FEES` is empty.
- D6 The suffix wraps at 999,999, timestamps use the clock rather than PARM, and PARM is not validated.
