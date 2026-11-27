# cbact04c: INTCALC / CBACT04C in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. Plain Maven, Java 21, no runtime dependencies.

```sh
mvn -q package -DskipTests
java -jar target/cbact04c.jar 2022071800 \
  --TCATBALF=app/data/ASCII/tcatbal.txt --XREFFILE=app/data/ASCII/cardxref.txt \
  --ACCTFILE=app/data/ASCII/acctdata.txt --DISCGRP=app/data/ASCII/discgrp.txt \
  --out=/tmp/out --clock=2022071800000000
```

Writes `<out>/TRANSACT.txt` and `<out>/ACCTFILE.txt`. SYSOUT goes to stdout. Exit code is 0, or 16 on abend.
The clock comes from `--clock`, else `CARDDEMO_CURRENT_DATE`, else the system clock in UTC.

## Preserved legacy behaviour (not fixed here)

- D1: the last account in TCATBAL order is never rewritten.
- D2: blank `ACCT-GROUP-ID` means every lookup falls back to `DEFAULT`.
- D3: `1400-COMPUTE-FEES` is a no-op.
- R2: overflow truncates high-order digits silently.
