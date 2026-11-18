# cbact04c — INTCALC interest batch in Java 21

Like-for-like rewrite of `app/cbl/CBACT04C.cbl`. Legacy defects D1-D9 are **kept on purpose**; see `aisdlc/<run>/intent.md` and `spec.md`.

```sh
mvn -q package
java -jar target/cbact04c.jar --in <dir> --out <dir> [--parm 2022071800] [--clock 2022071800000000]
```

- In: `TCATBALF.txt XREFFILE.txt DISCGRP.txt ACCTFILE.txt` (harness text format).
- Out: `TRANSACT.txt ACCTFILE.txt SYSOUT.txt RC.txt`. Exit code = RC (16 on abend).
- Without `--clock` the UTC system clock is used.
