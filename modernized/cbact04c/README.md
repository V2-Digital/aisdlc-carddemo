# cbact04c: INTCALC interest batch in Java 21

Like-for-like rewrite of `app/cbl/CBACT04C.cbl`. Spec: `aisdlc/<run>/spec.md`.

```sh
mvn -q package -DskipTests
DD_TCATBALF=… DD_XREFFILE=… DD_ACCTFILE=… DD_DISCGRP=… DD_TRANSACT=… \
CARDDEMO_CURRENT_DATE=2022071800000000 \
  java -cp target/classes carddemo.cbact04c.Cbact04c 2022071800
```

Files are the harness's fixed-width text datasets. `ACCTFILE` is rewritten in place. SYSOUT goes to stdout and the exit code is the RC (16 on abend).
`CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`) freezes the clock.

Legacy defects are preserved on purpose (last account not rewritten, truncated interest, empty fees paragraph, tran-ID suffix wrap, wall-clock timestamps).
