# cbact04c — INTCALC (`CBACT04C`) in Java 21

Like-for-like rewrite of `app/cbl/CBACT04C.cbl`. Defects are preserved on purpose; see the CR-1 spec.

```sh
mvn -q package -DskipTests
DD_TCATBALF=… DD_XREFFILE=… DD_DISCGRP=… DD_ACCTFILE=… DD_TRANSACT=… \
  java -jar target/cbact04c.jar 2022071800     # PARM = run date; exit 0, or 16 on abend
```

Files are the harness canonical text format (fixed width, LF, overpunch). `ACCTFILE` is rewritten in place.
`-Dcarddemo.clock=2022-07-18T00:00:00` fixes the clock (default: system clock).

Equivalence vs golden: `legacy/tests/cbact04c_java_equiv.sh`.

Preserved defects: D1 last account never rewritten; D2 truncation and silent overflow; D3 empty fees paragraph;
D4 blank group id → always DEFAULT; D5 timestamps from the clock; D6 TRAN-ID suffix wraps at 1,000,000;
D7 DISCGRP open error prints "DALY REJECTS FILE"; D8 missing account/xref abends.
