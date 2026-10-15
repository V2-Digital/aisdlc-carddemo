# cbact04c — INTCALC interest batch in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. Spec: `aisdlc/<run>/spec.md`.

```sh
mvn -f modernized/cbact04c package
java -jar target/cbact04c.jar --parm 2022071800 --clock 2022071800000000 --out /tmp/j \
  TCATBALF=<f> XREFFILE=<f> ACCTFILE=<f> DISCGRP=<f>
```

Writes `TRANSACT.txt` and `ACCTFILE.txt` to `--out`; DISPLAY lines go to stdout. Exit 0 on success, 16 on abend.

## Preserved legacy defects (do not fix here)

- D1: the last account is never rewritten (its interest and cycle reset are lost).
- D2: interest is truncated, not rounded.
- D3: blank `ACCT-GROUP-ID` always falls back to `DEFAULT`.
- D4: the fees step is empty (CR-2).
- D5: the DISCGRP open error says `DALY REJECTS FILE`.
- D6: no size-error handling; values wrap silently.
- D7: duplicate cards per account: the first card in key order wins.
