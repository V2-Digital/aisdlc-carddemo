# cbact04c — INTCALC (CBACT04C) in Java 21

Behaviour-preserving port of `app/cbl/CBACT04C.cbl`. No dependencies beyond JUnit 5 (test scope).

```sh
mvn -q package -DskipTests
DD_TCATBALF=… DD_XREFFILE=… DD_DISCGRP=… DD_ACCTFILE=… DD_TRANSACT=… \
CARDDEMO_CURRENT_DATE=2022071800000000 \
java -cp target/classes carddemo.cbact04c.Cbact04c 2022071800
```

Datasets are the harness's text form (fixed width, LF). `ACCTFILE` is rewritten in place; `TRANSACT` is
truncated and rewritten. SYSOUT goes to stdout; exit code 0, or 16 on abend. `CARDDEMO_CURRENT_DATE`
(`YYYYMMDDHHMMSSss`) overrides the clock; otherwise UTC system time.

## Preserved legacy defects (do not fix here)
- D1 last account in TCATBALF is never rewritten, though its transaction is written
- D2 fees not computed
- D3 zero-amount transactions when rate ≠ 0 and balance = 0
- D4 truncation, not rounding; overflow drops high-order digits
- D5 TRAN-ID suffix wraps at 999,999
- D6 `ACCT-GROUP-ID` used as stored (blank in sample data, so every lookup falls back to `DEFAULT`)
