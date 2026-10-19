# cbact04c - INTCALC interest calculation in Java 21

Like-for-like rewrite of `app/cbl/CBACT04C.cbl` (see `aisdlc/<run>/spec.md`). Legacy defects D1-D4 are preserved.

```sh
mvn -q package            # -> target/cbact04c.jar (JUnit 5 only)
COB_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar --parm 2022071800 \
  --tcatbalf F --xreffile F --discgrp F --acctfile-in F --acctfile-out F --transact F [--sysout F]
```

Files use the harness text format (`<DD>.txt`, one fixed-width record per line, overpunch signs).
`COB_CURRENT_DATE` is 16 digits `YYYYMMDDHHMMSSss`; unset means the UTC system clock.
Exit code 0, or 16 on abend (SYSOUT `ABENDING PROGRAM`).
