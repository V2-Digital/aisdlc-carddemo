# cbact04c — INTCALC (`CBACT04C`) in Java 21

Like-for-like port of `app/cbl/CBACT04C.cbl`. See `aisdlc/<run>/intent.md` and `spec.md`.

## Build and run
```
mvn -q -f modernized/cbact04c package -DskipTests
CARDDEMO_CURRENT_DATE=2022071800000000 \
  java -jar modernized/cbact04c/target/cbact04c.jar 2022071800 <OUT_DIR> [INPUT_DIR]
```
- Inputs: `INPUT_DIR/<DD>.txt` if present, else stock `DATA_DIR` (default `app/data/ASCII`).
- Outputs: `OUT_DIR/TRANSACT.txt`, `OUT_DIR/ACCTFILE.txt`; DISPLAY lines on stdout. Exit 0, or 16 on abend.
- Clock: `CARDDEMO_CURRENT_DATE`, else `COB_CURRENT_DATE` (`YYYYMMDDHHMMSSss`), else UTC system clock.
- After an abend the outputs hold only what was staged so far (not gated).

## Legacy defects preserved on purpose (for a follow-up CR)
- **D1** The last account in TCATBALF is never rewritten, but its interest transaction is written (high priority, customer-impacting).
- **D2** Interest is truncated, not rounded.
- **D3** Fees are not computed (`1400-COMPUTE-FEES` is empty).
- **D4** No size-error checks: overflow drops high-order digits.
- **D5** Sample data leaves `ACCT-GROUP-ID` blank, so every lookup falls back to `DEFAULT`. The DISCGRP open-error text says `DALY REJECTS FILE`.
