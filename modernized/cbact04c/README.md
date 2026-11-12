# cbact04c — INTCALC interest batch in Java 21

Like-for-like rewrite of `app/cbl/CBACT04C.cbl`, legacy defects included (F1–F7, see `aisdlc/<run>/intent.md`).

```sh
mvn -q verify            # build + tests (JUnit 5 only)
DD_TCATBALF=… DD_XREFFILE=… DD_DISCGRP=… DD_ACCTFILE=… \
DD_ACCTFILE_OUT=… DD_TRANSACT=… CARDDEMO_CURRENT_DATE=2022071800000000 \
  java -jar target/cbact04c.jar 2022071800
```

- Files: fixed-width text, LF, overpunch signs (same as `legacy/golden`). ACCTFILE is written to `DD_ACCTFILE_OUT`.
- `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`) fixes the clock; unset means system UTC.
- SYSOUT on stdout. Exit 0 OK, 16 abend (`ABENDING PROGRAM`).
