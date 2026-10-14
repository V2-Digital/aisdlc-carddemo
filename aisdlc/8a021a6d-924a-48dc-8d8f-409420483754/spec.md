# Spec — CR-1: `CBACT04C` → Java 21 (`modernized/cbact04c/`)

Legacy behaviour, file roles and defects: see [intent.md](intent.md). This spec fixes only what the Java code must do byte for byte.

## Invocation

`java -jar cbact04c.jar <PARM> <IN_DIR> <OUT_DIR>`
- `PARM`: 10 chars, e.g. `2022071800` (JCL PARM). Used as the TRAN-ID prefix.
- Clock: env `CARDDEMO_CURRENT_DATE` = `YYYYMMDDHHMMSSss` (same as the harness), default `2022071800000000`. In code: an injected `Supplier<String>` with the same format.
- Reads `IN_DIR/{TCATBALF,XREFFILE,ACCTFILE,DISCGRP}.txt`. Writes `OUT_DIR/{TRANSACT,ACCTFILE}.txt`. DISPLAY lines go to stdout (= SYSOUT).
- Exit code 0 on success, 16 on abend.

## Files (text form: one record per line, LF, `\r` stripped on read; short lines right-padded with spaces to LRECL)

| DD | Copybook | Fields used (1-based offset, len, PIC → Java) |
|---|---|---|
| TCATBALF in | `app/cpy/CVTRA01Y.cpy` | acct-id @1 11 `9(11)` → String; type @12 2; cat @14 4; `TRAN-CAT-BAL` @18 11 `S9(9)V99` → `BigDecimal` |
| XREFFILE in | `app/cpy/CVACT03Y.cpy` | card-num @1 16; acct-id @26 11 (alt key; first record per acct wins) |
| ACCTFILE in/out | `app/cpy/CVACT01Y.cpy` | `ACCT-CURR-BAL` @13 12, `CURR-CYC-CREDIT` @83 12, `CURR-CYC-DEBIT` @95 12 (all `S9(10)V99`); `ACCT-GROUP-ID` @117 10. Other bytes copied unchanged. |
| DISCGRP in | `app/cpy/CVTRA02Y.cpy` | key = group @1 10 + type @11 2 + cat @13 4; `DIS-INT-RATE` @17 6 `S9(4)V99` |
| TRANSACT out | `app/cpy/CVTRA05Y.cpy` | all 350 bytes are written, see rule 5 |

**Zoned overpunch codec**: last byte holds the sign plus digit. `{`/`A`–`I` mean +0..9, `}`/`J`–`R` mean −0..9. Plain digits `0`–`9` are read as positive. On write, positive gives `{A-I` and negative gives `}J-R`. Zero is written as `{` (positive).

**Keyed files** are loaded into maps by key. If a key appears twice, the last record wins, as with a KSDS load (golden data has no duplicates). TCATBALF is processed in ascending key order: stable sort on cols 1-17. ACCTFILE output is all input accounts in ascending acct-id order, i.e. the KSDS unload order.

## Processing rules (in order)

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`.
2. For each TCATBAL record, print the record itself (50 bytes). Then:
   a. If acct-id ≠ the previous one: if this is not the first record, **rewrite the buffered previous account** (rule 6). Then set total = 0. Look up the account; if missing, print `ACCOUNT NOT FOUND: <acct-id>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM` → exit 16. Look up the xref; if missing, the same sequence with `ERROR READING XREF FILE`.
   b. Rate key = buffered account's `ACCT-GROUP-ID` + type + cat. If missing, print `DISCLOSURE GROUP RECORD MISSING`, `TRY WITH DEFAULT GROUP CODE`, then retry with group `DEFAULT   `. If that is also missing, print `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM` → exit 16.
   c. If rate ≠ 0: `interest = bal × rate / 1200`, exact `BigDecimal`, `setScale(2, RoundingMode.DOWN)`. Then keep the value modulo 10^9 (S9(9)V99, sign kept). Then `total = (total + interest)` modulo 10^9, keeping S9(9)V99 (WS-TOTAL-INT). Write one transaction (rule 5).
3. At EOF: **do not** rewrite the last account (D1).
4. Write ACCTFILE, print `END OF EXECUTION OF PROGRAM CBACT04C`, exit 0. On abend, output files are not written. The harness compares only stdout and RC on abend.
5. **TRANSACT record** (350 bytes, space-filled first):
   - @1 `TRAN-ID` = PARM + seq, zero-padded to 6 digits, seq starting at 1 for the run. The suffix wraps modulo 10^6 (D4).
   - @17 `01`; @19 `0005`; @23 `System` padded to 10.
   - @33 `Int. for a/c ` + acct-id, space-padded to 100.
   - @133 amount, 11-byte overpunch; @144 `000000000`; @153–@212 and @213–@222 spaces (merchant name, city, zip).
   - @223 card num, 16 bytes.
   - @239 and @265 timestamp `YYYY-MM-DD-HH.MM.SS.hh0000` from the clock (both identical).
   - @291–@350 spaces.
6. **Account rewrite**: `CURR-BAL = (CURR-BAL + total)` modulo 10^10, S9(10)V99. Set `CYC-CREDIT` and `CYC-DEBIT` to `+0` (`00000000000{`). Done even when total = 0. All other bytes stay unchanged.

## Edge cases

| Case | Handling |
|---|---|
| Rate = 0 (e.g. `03/0001`) | No transaction and no seq increment. The account is still rewritten (rule 6) unless it is the last one. |
| Negative balance or rate | Truncation is toward zero (`DOWN`), so −0.005 → `0.00`. Zero results are written as `{`. |
| Group-specific rate present | It is used. Not exercised by the golden data (D2). |
| Empty TCATBALF | Only the START/END lines are printed. TRANSACT is empty and ACCTFILE equals the input. |
| Overflow beyond the PIC | High-order digits are dropped silently (D4). |

### Preserved defects (pinned, not fixed)
- **D1** The last account in TCATBALF is never rewritten: its interest is lost and its cycle fields are not reset.
- **D2** Group id is taken from blank `ACCT-GROUP-ID`, so every lookup falls back to DEFAULT.
- **D3** Fees are never computed (no-op hook `computeFees()` kept for CR-2).
- **D4** Silent truncation on overflow; the TRAN-ID suffix wraps at 999,999.
- **D5** Running twice posts interest twice.

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -q verify`, JUnit 5 + Surefire only | 1 |
| `GoldenEquivalenceTest.stock`: TRANSACT, ACCTFILE and stdout equal `legacy/golden/CBACT04C/*` byte for byte | 2, 9 |
| `GoldenEquivalenceTest.afterCbtrn02c`: inputs ACCTFILE and TCATBALF from `legacy/golden/CBTRN02C/` | 3, 9 |
| `AbendTest`: missing account / xref / DEFAULT row → exit 16 and the exact SYSOUT lines | 4 |
| `InterestTest`: acct 2, 2339.97 × 15 → `29.24`; acct 1 → `14.56` (`0000000145F`) | 5 |
| `DefectD1Test`: acct 50 output == input, while the 18.77 txn `0000000187G` is written | 6 |
| `ZeroRateTest`: rate 0 → no txn; account rewritten with cycle fields `+0` | 7 |
| `TransactionLayoutTest`: ID sequence across accounts; spaces at @153–222 and @291–350 and in the desc tail | 8 |
| `ZonedTest`: overpunch round-trip for ±0..9 | R1 |
