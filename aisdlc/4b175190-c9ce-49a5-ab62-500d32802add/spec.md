# CR-1 Spec: INTCALC (`CBACT04C`) in Java 21

Behaviour-preserving rewrite of `app/cbl/CBACT04C.cbl`. Legacy behaviour, files and defects are described in [intent.md](intent.md) and not repeated here.

## Interface (resolves intent Q1/Q2 with the defaults)

- `java -jar cbact04c.jar <PARM> [DATA_DIR]`. `DATA_DIR` defaults to `.`.
- Inputs are `DATA_DIR/<DD>.txt` for `TCATBALF`, `XREFFILE`, `ACCTFILE`, `DISCGRP`.
- Outputs:
  - `TRANSACT.txt` is created or overwritten.
  - `ACCTFILE.txt` is rewritten in place, which matches I-O semantics.
  - SYSOUT (DISPLAY) goes to stdout.
  - The exit code is the RC.
- PARM: only the first 10 characters are used. A shorter PARM is right-padded with spaces. Length is not checked (D7).
- Clock: env `CARDDEMO_CURRENT_DATE` = `YYYYMMDDHHMMSSss` (16 digits). If it is not set, the system clock in UTC is used. It is read **once per transaction write**, as `CURRENT-DATE` is per call.
- Testable core: `Cbact04c.run(Path dataDir, String parm, Supplier<String> clock16, PrintStream sysout) → int rc`.

## Record I/O (canonical text, see `legacy/README.md` Outputs and Quirks)

- On read: strip `\r`, then right-pad each line with spaces to the LRECL. Lines longer than the LRECL are an error.
- On write: exactly LRECL bytes followed by `\n`. Text is ASCII/ISO-8859-1 and is handled as bytes.
- Zoned decimal `S9(n)V99`: the last byte is an overpunch (`{`/`A-I` positive, `}`/`J-R` negative). The value maps to `BigDecimal` with scale 2.
- Encoding: a result of 0 is written as `{`, never `}`. `-0` on input reads as 0.

| File | Copybook | Fields read / written (1-based col, len, PIC → Java) |
|---|---|---|
| TCATBALF (50) | `app/cpy/CVTRA01Y.cpy` | key 1-17 (acct 1-11, type 12-13, cat 14-17) `String`; `TRAN-CAT-BAL` 18-28 S9(9)V99 → `BigDecimal` |
| XREFFILE (50) | `app/cpy/CVACT03Y.cpy` | `XREF-CARD-NUM` 1-16 `String`; `XREF-ACCT-ID` 26-36 `String` (alt key) |
| ACCTFILE (300) | `app/cpy/CVACT01Y.cpy` | `ACCT-ID` 1-11; `ACCT-CURR-BAL` 13-24 S9(10)V99 (R/W); `CURR-CYC-CREDIT` 79-90, `CURR-CYC-DEBIT` 91-102 S9(10)V99 (W); `ACCT-GROUP-ID` 113-122 `String` |
| DISCGRP (50) | `app/cpy/CVTRA02Y.cpy` | key 1-16 (group 1-10, type 11-12, cat 13-16); `DIS-INT-RATE` 17-22 S9(4)V99 |
| TRANSACT (350) | `app/cpy/CVTRA05Y.cpy` | all 14 fields written (see R6) |

ACCTFILE rewrite: start from the original 300 bytes and overwrite only cols 13-24, 79-90 and 91-102. All other bytes are kept verbatim.

## Processing rules (in order)

- **R1 Load.** Each KSDS is loaded into a `TreeMap` keyed by its primary key, which gives VSAM key order.
  - XREF also gets an alternate index acct → card. The first record in primary-key order wins.
  - TCATBALF is iterated in key order.
- **R2 Start.** Print `START OF EXECUTION OF PROGRAM CBACT04C`. A load or open failure on any DD prints `ERROR OPENING …` with the COBOL text for that DD, then abends (R9).
- **R3 Per TCATBAL record.** Print the raw 50-byte record. If acct ≠ last acct:
  - Unless this is the first record, rewrite the previous account (R7).
  - Set total = 0.
  - Look up the account. If it is missing, print `ACCOUNT NOT FOUND: <acct>`, `ERROR READING ACCOUNT FILE`, then abend.
  - Look up the xref by acct. If it is missing, print `ACCOUNT NOT FOUND: <acct>`, `ERROR READING XREF FILE`, then abend.
- **R4 Rate.** The key is `ACCT-GROUP-ID` (10 raw bytes) + type + cat.
  - On a miss, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with `DEFAULT   ` (padded to 10).
  - If `DEFAULT` also misses, print `ERROR READING DEFAULT DISCLOSURE GROUP`, then abend.
- **R5 Interest, only if rate ≠ 0.**
  - `monthly = (bal × rate) / 1200`, computed exactly and then truncated toward zero to scale 2 (`RoundingMode.DOWN`).
  - Then `total += monthly`.
  - Rate 0 means no transaction and nothing added.
- **R6 Transaction write** (one per R5):
  - counter += 1, mod 1,000,000 (`PIC 9(6)`).
  - `TRAN-ID` = PARM(10) + counter as 6 digits.
  - Type `01`, cat `0005`, source `System    `.
  - Description `Int. for a/c ` + acct(11), space-padded to 100.
  - Amount = monthly as S9(9)V99.
  - Merchant ID `000000000`. Name, city and zip are spaces.
  - Card = xref card.
  - `ORIG-TS` = `PROC-TS` = `YYYY-MM-DD-HH.MM.SS.hh0000` from the clock.
  - FILLER 20 spaces.
  - Fees: a no-op hook (D4).
- **R7 Account rewrite.**
  - `CURR-BAL = (CURR-BAL + total)` truncated to S9(10)V99. High-order digits are lost (COBOL `ADD` without `SIZE ERROR`).
  - Cycle credit and debit are set to 0 (D8).
  - The record is stored back in the map.
- **R8 End.**
  - At EOF the last account is **not** rewritten (D1).
  - Write ACCTFILE: all records in key order, including untouched ones.
  - Print `END OF EXECUTION OF PROGRAM CBACT04C` and return RC 0.
- **R9 Abend.**
  - After the error lines, print `FILE STATUS IS: NNNN` immediately followed by the 4-digit status, e.g. `FILE STATUS IS: NNNN0023` (COBOL `9910`, `:634-645`).
  - Then print `ABENDING PROGRAM`.
  - Return RC 16, and write `CEE3ABD: ABEND U0999` to stderr.
  - On an abend, no output file is written. This is a deliberate simplification of partial-file semantics, flagged for review.

## Edge cases

| Case | Handling |
|---|---|
| Short lines (`cardxref.txt` is 36 bytes), CRLF | Pad and strip (R1 I/O) |
| Negative balance (type 03 rows, if the rate is ≠ 0) | Truncate toward zero: −7.0758 → −7.07 |
| `-0` input, zero result | Read as 0. Written as `{` |
| Real group hit (non-blank `ACCT-GROUP-ID`) | Used directly, no MISSING lines (unit test, D3) |
| Empty TCATBALF | Only START/END printed. No transactions. ACCTFILE unchanged. RC 0 |
| Monthly or balance overflow | High-order truncation, as COBOL does. Not reachable with sample data |

## Preserved defects (one line each, fix later by separate CR)

- **D1** The last account in TCATBAL is never rewritten.
- **D2** Interest is truncated, not rounded.
- **D3** The rate key uses blank `ACCT-GROUP-ID`, so every lookup falls back to `DEFAULT`.
- **D4** Fees are a no-op.
- **D5** Zero-amount transactions are written when the rate ≠ 0.
- **D6** `ORIG-TS` is the run time, with only hundredths precision.
- **D7** The counter wraps at 999,999. PARM length is not checked.
- **D8** Cycle fields are zeroed even when the interest is 0.

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -f modernized/cbact04c test`, with JUnit 5 and Surefire only | 1 |
| `GoldenStockTest`: stock inputs → TRANSACT, ACCTFILE, SYSOUT byte-equal to `legacy/golden/CBACT04C/`, RC = `RC.txt` (0) | 2, 4 |
| `GoldenAfterPostTest`: ACCTFILE/TCATBALF from `golden/CBTRN02C` → `golden/CBACT04C-after-CBTRN02C/` | 3, 4 |
| `InterestMathTest`: positive, negative and boundary values are truncated; there are no `double`s | 5 |
| `RateLookupTest`: a group hit prints no MISSING lines; a miss falls back to DEFAULT and prints 2 lines; rate 0 writes no transaction and leaves the total unchanged | 6 |
| `TranLayoutTest`: ID and counter across accounts, desc padding, FILLER, timestamps, 350-byte length | 7 |
| `DefectD1Test`: the last account's bytes are unchanged while TRANSACT charges it | 8 |
| `AbendTest`: missing DD, account, xref or DEFAULT → RC 16 and the matching SYSOUT lines | 9 |
