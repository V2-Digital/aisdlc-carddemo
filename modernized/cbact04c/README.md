# cbact04c — INTCALC interest batch in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. Legacy defects D1–D9 are kept on purpose (see `aisdlc/*/spec.md`).

```sh
mvn -q package -DskipTests
CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar \
  --parm 2022071800 --in <dir with TCATBALF/XREFFILE/ACCTFILE/DISCGRP .txt> --out <dir> [--dd DD=path]
```

Writes `TRANSACT.txt`, `ACCTFILE.txt`, `SYSOUT.txt`, `RC.txt`; exit code = RC (0, or 16 on abend).
