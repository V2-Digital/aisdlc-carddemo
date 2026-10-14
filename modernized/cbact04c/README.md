# cbact04c

Java 21 rewrite of INTCALC (`app/cbl/CBACT04C.cbl`). Like-for-like: legacy defects are preserved (see `aisdlc/*/spec.md`).

```sh
mvn -f modernized/cbact04c verify
INPUT_DIR=in OUTPUT_DIR=out java -jar modernized/cbact04c/target/cbact04c.jar 2022071800
```

- Inputs `TCATBALF XREFFILE ACCTFILE DISCGRP`, outputs `TRANSACT ACCTFILE`: `<DD>.txt` in `INPUT_DIR`/`OUTPUT_DIR`, or `DD_<DD>` env per file.
- Clock: `CARDDEMO_CURRENT_DATE` / `COB_CURRENT_DATE` (`YYYYMMDDHHMMSSss`), else system UTC.
- SYSOUT on stdout; exit code 0, or 16 on abend.
