# CR-1 Spec: `CBACT04C` → Java 21 (`modernized/cbact04c/`)

The legacy behaviour, files and defects are in [intent.md](intent.md). Line refs are `app/cbl/CBACT04C.cbl`.
**Behaviour-preserving: every defect below is reproduced on purpose.**

## Interface

`java -jar cbact04c.jar <PARM> <IN_DIR> <OUT_DIR>`, for example `2022071800 in/ out/`.

- Inputs are `<IN_DIR>/<DD>.txt`, outputs are `<OUT_DIR>/<DD>.txt`, in the harness canonical text form
  (`legacy/README.md` § Outputs).
- DISPLAY lines go to stdout (= `SYSOUT.txt`). The abend line goes to stderr.
- Exit code: 0 on success, 16 on abend.
- Clock: env `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`, the same as the harness). If it is unset, the
  program uses the system clock in UTC. A `Clock` is injectable for tests.
- PARM: the first 10 characters, padded with spaces (`:176-178`).

## Files and the fields used

| DD | Copybook | LRECL | Role |
|---|---|---|---|
| `TCATBALF` | `app/cpy/CVTRA01Y.cpy` | 50 | input, processed in key order (bytes 0-16) |
| `XREFFILE` | `app/cpy/CVACT03Y.cpy` | 50 | input, looked up by acct-id |
| `ACCTFILE` | `app/cpy/CVACT01Y.cpy` | 300 | input and output (I-O): the full master, written in acct-id order |
| `DISCGRP`  | `app/cpy/CVTRA02Y.cpy` | 50 | input, looked up by group+type+cat (bytes 0-15) |
| `TRANSACT` | `app/cpy/CVTRA05Y.cpy` | 350 | output, in write order |

Offsets are 0-based. Zoned values use overpunch on the last byte, as `BigDecimal`/`long` cents.

| Field | Off | Len | PIC | Java |
|---|---|---|---|---|
| `TRANCAT-ACCT-ID` / `-TYPE-CD` / `TRANCAT-CD` | 0 / 11 / 13 | 11 / 2 / 4 | 9(11) / X(2) / 9(4) | String |
| `TRAN-CAT-BAL` | 17 | 11 | S9(9)V99 | BigDecimal |
| `XREF-CARD-NUM` / `XREF-ACCT-ID` | 0 / 25 | 16 / 11 | X(16) / 9(11) | String |
| `ACCT-ID` | 0 | 11 | 9(11) | String |
| `ACCT-CURR-BAL` | 12 | 12 | S9(10)V99 | BigDecimal (rewritten) |
| `ACCT-CURR-CYC-CREDIT` / `-DEBIT` | 84 / 96 | 12 / 12 | S9(10)V99 | set to `00000000000{` |
| `ACCT-GROUP-ID` | 118 | 10 | X(10) | String (blank in the sample data) |
| `DIS-INT-RATE` | 16 | 6 | S9(4)V99 | BigDecimal |
| `TRANSACT` record | all | 350 | see copybook | built from spaces (the FILLER at 330-349 stays spaces) |

All other ACCTFILE bytes are copied through untouched.

## Read rules

- Strip `\r`. Pad short lines with spaces up to LRECL (`cardxref` lines are 36 bytes). A line longer than
  LRECL is a fatal input error.
- `TCATBALF` and `ACCTFILE` are sorted by primary key, as a KSDS delivers them.
- Overpunch decode/encode: `{`/`A`-`I` = +0..9, `}`/`J`-`R` = −0..9. Positive values are written with
  `{`/`A`-`I`, negative values with `}`/`J`-`R`. Zero is written as `{`.

## Processing rules (in order)

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`.
2. For each TCATBAL record, in key order:
   1. Print the raw 50-byte record.
   2. On an account break (acct-id ≠ previous):
      - If a previous account exists, run the update (rule 4) for it.
      - Set `total = 0`.
      - Look up ACCTFILE by acct-id, then XREFFILE by acct-id.
      - If either lookup misses, that is an abend (rule 6).
   3. Rate lookup:
      - Look up key `ACCT-GROUP-ID`+type+cat.
      - On a miss, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with
        group `DEFAULT` padded to 10 characters.
      - If that also misses, abend.
   4. If `rate ≠ 0`:
      - `monthly = (bal × rate) / 1200`, computed exactly and then **truncated toward zero to 2 dp**
        (`RoundingMode.DOWN`).
      - Keep the result modulo 10⁹ in the integer part (S9(9)V99, D3).
      - `total += monthly`, also modulo 10⁹ (D3).
      - Write a transaction (rule 3).
      - Fees: no-op (`1400-COMPUTE-FEES`).
3. Transaction fields:
   - `TRAN-ID` = PARM(10) + a 6-digit counter. The counter starts at 1 and increases only when a record is
     written.
   - Type `01`, cat `0005`, source `System` padded to 10.
   - Desc `Int. for a/c ` + acct-id (11), padded to 100.
   - Amount = `monthly`. Merchant id `000000000`.
   - Merchant name, city and zip are spaces.
   - Card number = `XREF-CARD-NUM`.
   - Orig-ts = proc-ts = `YYYY-MM-DD-HH.MM.SS.ss0000` from the clock (`:613-626`).
4. Account update:
   - `CURR-BAL = (CURR-BAL + total)`, keeping the integer part modulo 10¹⁰ (D3).
   - Cycle credit = 0 and cycle debit = 0, even when total = 0 (D4).
   - Replace the record in the master.
5. At EOF, **do not update the last account (D1)**. Write ACCTFILE (all records, by key) and TRANSACT,
   print `END OF EXECUTION OF PROGRAM CBACT04C`, and exit 0.
6. Abend. Print the legacy lines, then `ABENDING PROGRAM`. Print `CEE3ABD: ABEND U0999` to stderr and exit 16.
   Output files are not guaranteed after an abend.

   | Cause | Lines printed |
   |---|---|
   | Account missing | `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023` |
   | Xref missing | `ACCOUNT NOT FOUND: <id>`, `ERROR READING XREF FILE`, `FILE STATUS IS: NNNN0023` |
   | `DEFAULT` missing | the 2 MISSING lines, `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023` |
   | Input file absent | the open-error text (`:245-320`; DISCGRP keeps the wrong text, D5) + `FILE STATUS IS: NNNN0035` |

## Edge cases

| Case | Handling |
|---|---|
| Rate = 0 (all of type `03` in the after-scenario) | no transaction, no counter increment; the account is still updated |
| Negative balance | the monthly amount truncates toward zero (−1.239 → −1.23) |
| Empty TCATBALF | only start and end banners are printed; ACCTFILE is copied unchanged; TRANSACT is empty |
| Duplicate acct-id in XREFFILE | the first record in file order is used (no sample data has duplicates) |
| Counter > 999999 | wraps to 000000 (`9(6)` MOVE). Not reachable on current volumes |

## Preserved defects (one line each, to be fixed by a separate CR)

- **D1:** the last account's interest is never written back, and its cycle fields are not zeroed.
- **D2:** interest is truncated, not rounded.
- **D3:** silent high-order truncation of monthly/total (S9(9)V99) and of the balance (S9(10)V99).
- **D4:** cycle credit/debit are zeroed for every processed account (except the last one, D1), whatever the
  interest.
- **D5:** the DISCGRP open error reads `ERROR OPENING DALY REJECTS FILE`.

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -q verify`, JUnit 5 + Surefire only | 1 |
| `GoldenTest.stock`: TRANSACT, ACCTFILE and stdout = `legacy/golden/CBACT04C/*` | 2, Q1 |
| `GoldenTest.afterCbtrn02c`: same check, with ACCTFILE/TCATBALF from `golden/CBTRN02C` | 3, Q1 |
| exit code = `RC.txt` (0) in both golden tests | 4 |
| `InterestTest`: 1000.00 × 15.00 → 12.50; 1234.56 × 15.00 → 15.43 (15.432); −99.13 × 24.00 → −1.98 | 5 |
| `AbendTest`: group fallback works; missing DEFAULT, account or xref → exit 16 + the lines above | 6 |
| `DefectTest.d1`: 2 accounts; only the first changes in ACCTFILE | 7 |
| `DefectTest.d3`: a monthly amount over 10⁹ and a balance over 10¹⁰ wrap as pinned | 5, D3 |
| `legacy/harness/golden.sh --check` still passes | 8 |
