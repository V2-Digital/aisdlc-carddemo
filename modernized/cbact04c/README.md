# cbact04c — INTCALC (`CBACT04C`) in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. Legacy defects are preserved on purpose.

```
mvn -f modernized/cbact04c package
java -jar modernized/cbact04c/target/cbact04c.jar --parm 2022071800 --in <dir> --out <dir> [--now YYYYMMDDHHMMSSss]
```

- `--in` holds `TCATBALF.txt XREFFILE.txt ACCTFILE.txt DISCGRP.txt` (harness text datasets).
- `--out` gets `TRANSACT.txt`, `ACCTFILE.txt`, `SYSOUT.txt`. The exit code is the RC (16 = abend).
- `--now` is the `COB_CURRENT_DATE` equivalent; the golden runs use `2022071800000000`.

Preserved defects: D1 last account never rewritten, D2 no fees, D3 truncation not rounding,
D4 blank group id so `DEFAULT` always wins, D5 silent high-order truncation, D6 wall-clock timestamps,
D7 first XREF card wins. Documented deviation: a short PARM is right-padded with spaces.
