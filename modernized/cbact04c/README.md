# cbact04c — INTCALC interest batch in Java 21

Behaviour-preserving port of `app/cbl/CBACT04C.cbl`. Reference outputs: `legacy/golden/`.

```sh
mvn -q package
DD_TCATBALF=… DD_XREFFILE=… DD_DISCGRP=… DD_ACCTFILE=… DD_TRANSACT=… \
CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar 2022071800
```

- Datasets are fixed-width text files (the harness `<DD>.txt` format), bound by `DD_<ddname>` env vars.
- `ACCTFILE` is rewritten in place (temp file + atomic rename). `TRANSACT` is written fresh.
- SYSOUT goes to stdout. Exit code 0, or 16 on abend (`CEE3ABD: ABEND U0999` on stderr).
- `CARDDEMO_CURRENT_DATE` (16 digits) fixes the clock; otherwise UTC system time.

## Preserved legacy defects (do not fix here)
- D1: the last account in key order is never rewritten (its interest is in TRANSACT only).
- D2: interest is truncated, not rounded.
- D3: `1400-COMPUTE-FEES` is empty (CR-2).
- F5: DISCGRP open error prints `ERROR OPENING DALY REJECTS FILE`.
- TRAN-ID suffix wraps silently after 999999.
