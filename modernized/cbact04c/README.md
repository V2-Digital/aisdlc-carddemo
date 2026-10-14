# cbact04c: INTCALC interest batch in Java 21

Like-for-like rewrite of `app/cbl/CBACT04C.cbl`. Legacy defects are preserved on purpose (see
`aisdlc/*/intent.md`, D1-D8).

```sh
mvn -q package -DskipTests
CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar 2022071800 OUT \
  TCATBALF=tcatbal.txt XREFFILE=cardxref.txt ACCTFILE=acctdata.txt DISCGRP=discgrp.txt
```

Writes `OUT/{TRANSACT,ACCTFILE,SYSOUT,RC}.txt`; exit code = RC (16 on abend, then only SYSOUT/RC).
Duplicate cards per account: first card number wins (not proven against COBOL).
