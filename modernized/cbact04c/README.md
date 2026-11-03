# cbact04c — INTCALC interest batch in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. Reads/writes the harness text files (fixed width, LF, overpunch signs).

```
mvn -q package
java -jar target/cbact04c.jar --in <dir> --out <dir> --parm 2022071800 [--now YYYYMMDDHHMMSSss]
```
`<in>`: `TCATBALF XREFFILE ACCTFILE DISCGRP` `.txt`. `<out>`: `TRANSACT ACCTFILE SYSOUT RC` `.txt`. Exit code = RC (0, or 16 on abend).

## Legacy defects preserved on purpose (see CR-1 intent.md)
- R1: last account in TCATBAL is never rewritten (its transactions are written).
- R2: fees (`1400-COMPUTE-FEES`) not implemented.
- R3: blank `ACCT-GROUP-ID` in sample data, so every rate falls back to `DEFAULT`.
- R5: tran-id suffix and amounts wrap silently (mod 10^6 / 10^9 / 10^10).
- R7: duplicate XREF per account: first record in file order.
- R8: DISCGRP open error says "DALY REJECTS FILE".
