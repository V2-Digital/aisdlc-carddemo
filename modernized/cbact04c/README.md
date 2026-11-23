# cbact04c: INTCALC interest batch in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl` (CR-1). Plain Maven, JUnit 5 only.

```sh
mvn -q package            # target/cbact04c.jar
java -jar target/cbact04c.jar 2022071800 \
  --TCATBALF=… --XREFFILE=… --DISCGRP=… --ACCTFILE=… [--ACCTFILE-OUT=…] --TRANSACT=… [--clock=YYYYMMDDHHMMSSss]
```

Paths fall back to env `DD_<DD>`; clock falls back to `CARDDEMO_CURRENT_DATE`, then the system clock.
Files are the harness's canonical fixed-width text. SYSOUT goes to stdout. Abend: exit 16.

Tests: `mvn -q test` (golden scenarios, COBOL-generated oracle cases in `legacy/tests/cbact04c/`,
regenerate with `legacy/tests/cbact04c-oracle.sh`).

## Legacy defects preserved (decide separately)

- **F1** last account in TCATBAL order is never rewritten (interest charged, balance and cycle fields stale).
- **F2** fees never computed (`1400-COMPUTE-FEES` is empty).
- **F3** interest truncated toward zero, not rounded.
- **F4** lookup uses blank `ACCT-GROUP-ID`, so sample data always gets DEFAULT rates.
- **F5** DISCGRP open error says `DALY REJECTS FILE`.
- **F6** no size-error handling (high-order digits dropped); tran-id suffix wraps at 1,000,000.
