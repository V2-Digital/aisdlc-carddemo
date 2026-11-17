# cbact04c — INTCALC in Java 21

Like-for-like rewrite of `app/cbl/CBACT04C.cbl`. Spec: `aisdlc/<run>/spec.md`.

```sh
mvn -q verify
java -jar target/cbact04c.jar --parm 2022071800 --in <DIR> --out <DIR> [--now YYYYMMDDHHMMSSss]
```

Inputs `<DIR>/{TCATBALF,XREFFILE,ACCTFILE,DISCGRP}.txt`; outputs `TRANSACT.txt`, `ACCTFILE.txt`.
stdout is SYSOUT; exit code is RC (0, or 16 on abend).

Preserved legacy defects (do not fix here): D1 last account never rewritten, D2 cycle fields zeroed
at zero interest, D3 truncation not rounding, D4 silent overflow, D5 misleading messages.
