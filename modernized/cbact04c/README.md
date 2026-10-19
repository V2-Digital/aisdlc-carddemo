# cbact04c — INTCALC (CBACT04C) in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. No frameworks; JUnit 5 for tests only.

```sh
mvn -q package -DskipTests
CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar 2022071800 <IN_DIR> <OUT_DIR>
```

- Inputs `<IN_DIR>/{TCATBALF,XREFFILE,DISCGRP,ACCTFILE}.txt`; stock names (`tcatbal`, `cardxref`, `discgrp`, `acctdata`) are the fallback. Override a DD with env `CARDDEMO_PATH_<DD>`.
- Outputs `<OUT_DIR>/TRANSACT.txt`, `ACCTFILE.txt`; SYSOUT goes to stdout.
- Exit 0 on success, 16 on abend.
- Clock: env `CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss`, else system UTC.

## Preserved legacy defects (do not fix here)
- D1 last account is never rewritten
- D2 interest is truncated, not rounded
- D3 silent high-order truncation (S9(09)V99 / S9(10)V99)
- D4 ACCT-GROUP-ID blank in sample data, so everything uses DEFAULT
- D5 fees are a no-op
- D6 TRAN-ID counter wraps after 999999

Note: the spec's ACCTFILE column numbers for cycle credit/debit/group (87-110, 121-130) are wrong; the copybook gives 79-102 and 113-122, which the code uses.
