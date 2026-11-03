# cbact04c — INTCALC (CBACT04C) in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. Reads/writes the harness's canonical text datasets.

```sh
mvn -q package
CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar <inDir> <outDir> 2022071800
```
`inDir` holds `TCATBALF.txt XREFFILE.txt ACCTFILE.txt DISCGRP.txt`; `outDir` receives `TRANSACT.txt` and
`ACCTFILE.txt`. SYSOUT goes to stdout; exit 0 ok, 16 abend. `DATA_DIR`/`OUT_DIR` env can replace the dir args
(then pass only the PARM). Clock defaults to system UTC.

## Preserved legacy defects (do not fix here)
- D1 last account in TCATBALF is never rewritten
- D2 fees (`1400-COMPUTE-FEES`) are a no-op
- D3 blank `ACCT-GROUP-ID` in sample data: every lookup falls back to `DEFAULT`
- D4 DISCGRP open error says `ERROR OPENING DALY REJECTS FILE`
- D5 interest truncated, not rounded
- D6 no size-error handling (high-order digits dropped)
- D7 zero-amount transactions written; cycle fields zeroed even without interest
