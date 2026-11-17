# cbact04c: INTCALC in Java 21

Like-for-like rewrite of `app/cbl/CBACT04C.cbl`. See `aisdlc/<run>/spec.md` for the contract.

```sh
mvn -q package
java -jar target/cbact04c.jar <PARM> <IN_DIR> <OUT_DIR>   # IN: TCATBALF XREFFILE ACCTFILE DISCGRP .txt
```
Writes `TRANSACT.txt` and `ACCTFILE.txt` to OUT_DIR. SYSOUT goes to stdout, RC 16 on abend.
Clock: `CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss`, else system UTC.

Legacy defects kept on purpose: D1 last account never rewritten, D2 blank group id so DEFAULT rates,
D3 interest truncated, D4 `DALY REJECTS` text, zero-amount txns, no counter guard, no fees.
