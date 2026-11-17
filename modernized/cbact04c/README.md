# cbact04c — Java 21 rewrite of INTCALC / CBACT04C

Like-for-like port of `app/cbl/CBACT04C.cbl`. Spec: `aisdlc/<run>/spec.md`; defects D1–D7, R1–R4: `intent.md`.

```sh
mvn -q package -DskipTests
DD_TCATBALF=… DD_XREFFILE=… DD_DISCGRP=… DD_ACCTFILE=… DD_TRANSACT=… \
CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar 2022071800
```

- Files are harness text format (fixed width, LF). `ACCTFILE` is read and rewritten in place; `TRANSACT` is created.
- SYSOUT → stdout, `CEE3ABD: ABEND U0999` → stderr, exit code 0 or 16. No output files are written on abend.
- `CARDDEMO_CURRENT_DATE` = `YYYYMMDDHHMMSSss` (default: system clock, UTC).
- Live check against COBOL: `legacy/tests/cbact04c_java_vs_cobol.sh`.

Preserved defects: D1 last account not rewritten, D2 truncation, D3 silent overflow/counter wrap, D4 blank group-id →
DEFAULT, D5 empty fees hook, D6 wrong DISCGRP open-error text, D7 negative interest, R1 filler is spaces.
R4: duplicate xref acct-ids use the first card number (untested).
