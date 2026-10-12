# cbact04c — INTCALC interest batch in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl` (CR-1). Spec: `aisdlc/dry5-10121214/spec.md`.

```sh
mvn -q package -DskipTests
CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar <PARM> <IN_DIR> <OUT_DIR>
```

- `IN_DIR`: `TCATBALF.txt`, `XREFFILE.txt`, `ACCTFILE.txt`, `DISCGRP.txt` (canonical fixed-width form).
- `OUT_DIR`: `TRANSACT.txt` and updated `ACCTFILE.txt`, written only on RC 0.
- SYSOUT on stdout; exit 0, 16 (abend), 2 (bad usage).
- Clock: `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`) or system UTC.

Legacy defects are preserved on purpose (last account not rewritten, truncated interest,
wall-clock timestamp, no overflow guard); see spec §6.
