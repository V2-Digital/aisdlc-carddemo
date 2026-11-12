# cbact04c — INTCALC interest batch in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. Spec: `aisdlc/9d4047bc-b4c3-4584-9e48-83f976174981/spec.md`.

```sh
mvn -q -f modernized/cbact04c package
java -cp modernized/cbact04c/target/classes com.v2.carddemo.cbact04c.Cbact04c \
  --parm 2022071800 --in <dir> --out <dir> [--clock 2022071800000000]
```
`--in`: `TCATBALF.txt XREFFILE.txt ACCTFILE.txt DISCGRP.txt`. `--out`: `TRANSACT.txt ACCTFILE.txt SYSOUT.txt`. Exit code = RC (16 on abend).

Abend cross-check against real COBOL: `bash legacy/tests/cbact04c_abend.sh`.

## Preserved legacy defects (do not fix here)
- D1 last account never rewritten; D2 interest truncates; D3 group key from blank `ACCT-GROUP-ID` (always `DEFAULT`);
  D4 silent overflow wrap; D5 tran-id suffix wraps at 999999, PARM length unchecked; D6 cycle fields zeroed even at 0 interest;
  D7 fees stub; DISCGRP open error text says `DALY REJECTS FILE`.
