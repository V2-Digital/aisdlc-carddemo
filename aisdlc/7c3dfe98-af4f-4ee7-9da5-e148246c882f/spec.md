# CR-1 Spec: `CBACT04C` → Java 21 (behaviour-preserving)

Legacy behaviour, file table and defects: [intent.md](intent.md). Approver defaults assumed: SYSOUT + RC match (Q1), text datasets only (Q3).

## Interface

`java -jar cbact04c.jar --parm 2022071800 --in <dir> --out <dir> [--now YYYYMMDDHHMMSSss]`

- `--in`: reads `TCATBALF.txt XREFFILE.txt ACCTFILE.txt DISCGRP.txt` (harness DD names). The test copies stock `app/data/ASCII` files to these names.
- `--out`: writes `TRANSACT.txt`, `ACCTFILE.txt`, `SYSOUT.txt`. The exit code is the RC.
- `--now` defaults to the system clock (UTC). The format is the same as `COB_CURRENT_DATE`. The golden runs use `2022071800000000`.
- Input lines: strip `\r` and right-pad with spaces to LRECL. This matches the harness load: `cardxref.txt` is 36 bytes and `tcatbal.txt` is CRLF.
- Output lines: fixed LRECL, LF-terminated, no trailing truncation.

## Fields used (1-based cols)

| File / copybook | Field | Cols | PIC → Java |
|---|---|---|---|
| TCATBALF [`CVTRA01Y`](../../app/cpy/CVTRA01Y.cpy) | acct id / type / cat | 1-11 / 12-13 / 14-17 | `String` |
| | `TRAN-CAT-BAL` | 18-28 | S9(9)V99 → `BigDecimal` scale 2 |
| XREFFILE [`CVACT03Y`](../../app/cpy/CVACT03Y.cpy) | card num / acct id | 1-16 / 26-36 | `String` |
| ACCTFILE [`CVACT01Y`](../../app/cpy/CVACT01Y.cpy) | `ACCT-ID` | 1-11 | `String` (key) |
| | `ACCT-CURR-BAL` | 13-24 | S9(10)V99 → `BigDecimal` |
| | `ACCT-CURR-CYC-CREDIT` / `-DEBIT` | 83-94 / 95-106 | S9(10)V99, set to `00000000000{` |
| | `ACCT-GROUP-ID` | 113-122 | X(10) |
| DISCGRP [`CVTRA02Y`](../../app/cpy/CVTRA02Y.cpy) | key group/type/cat | 1-10 / 11-12 / 13-16 | `String` |
| | `DIS-INT-RATE` | 17-22 | S9(4)V99 → `BigDecimal` |
| TRANSACT [`CVTRA05Y`](../../app/cpy/CVTRA05Y.cpy) | all 350 bytes, see R5 | | |

The ACCTFILE output patches cols 13-24 and 83-106 of the input record **in place**. All other bytes pass through unchanged.

## Processing rules (must reproduce exactly)

- **R1 Zoned decimal.** Overpunch on the last digit: `{A-I` = +0..9, `}J-R` = -0..9. Encoding uses `{`/`A-I` for values ≥ 0 (zero is always `{`) and `}`/`J-R` for values < 0.
- **R2 Loading.** ACCTFILE is held in a `TreeMap` keyed by id. It is written back in key order, including the records that were never rewritten. XREF uses a map acct-id → first card in file order (D7). DISCGRP uses a map from the 16-byte key.
- **R3 Main loop.** Read TCATBALF in file order. For each record:
  1. Print the raw 50 bytes.
  2. On an account break (id ≠ last id, where last starts as spaces):
     - If not first, run R6 for the previous account.
     - Reset total = 0.
     - Look up the account. If missing, run E1.
     - Look up XREF. If missing, run E2.
  3. Look up the rate (R4).
  4. If the rate ≠ 0, compute R5.
- **R3a EOF (D1).** No update runs for the last account.
- **R4 Rate lookup.** Key = `ACCT-GROUP-ID` + type + cat.
  - On a miss, print `DISCLOSURE GROUP RECORD MISSING` and then `TRY WITH DEFAULT GROUP CODE`.
  - Then look up `DEFAULT   ` + type + cat. If that also misses, run E3.
- **R5 Interest.**
  - `m = bal × rate / 1200`, exact, then `setScale(2, DOWN)`. That is truncation toward zero (D3).
  - Then keep the low 9 integer digits (mod 10⁹, sign kept) (D5).
  - `total = trunc9(total + m)`.
  - Then `suffix = (suffix + 1) mod 10⁶` and write the record:
    - `TRAN-ID` = parm (10 bytes, right-padded with spaces if short, R2 of intent) + suffix `%06d`.
    - Type `01`, cat `0005`, source `System` padded to 10.
    - Desc `Int. for a/c ` + acct id, padded to 100.
    - Amount = zoned(m, 11). Merchant id = `000000000`. Name/city/zip = spaces (50/50/10).
    - Card = XREF card.
    - ORIG-TS = PROC-TS = `YYYY-MM-DD-HH.MM.SS.hh0000` from `--now`, taken once per record (D6).
    - Cols 331-350 = 20 spaces.
- **R6 Account update.**
  - `bal = trunc10(bal + total)` (D5).
  - Cycle credit and debit = 0.
  - Store the record back in the map.
- **R7 Normal end.** Print `END OF EXECUTION OF PROGRAM CBACT04C`. Write ACCTFILE. RC 0. The first SYSOUT line is `START OF EXECUTION OF PROGRAM CBACT04C`.
- No fee logic. `1400-COMPUTE-FEES` is empty (D2).

## Error paths (abend = RC 16)

Each error appends these lines to SYSOUT, then `ABENDING PROGRAM`. It prints `CEE3ABD: ABEND U0999` to stderr and exits 16. Outputs written so far are flushed. ACCTFILE is written with the rewrites made so far.

| # | Trigger | SYSOUT lines before `ABENDING PROGRAM` |
|---|---|---|
| E1 | account not in ACCTFILE | `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023` |
| E2 | no XREF for account | `ACCOUNT NOT FOUND: <id>`, `ERROR READING XREF FILE`, `FILE STATUS IS: NNNN0023` |
| E3 | DEFAULT key missing | (2 R4 messages), `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023` |
| E4 | input file missing / unreadable | `ERROR OPENING …` message per file (`:245-318`), then `FILE STATUS IS: NNNN0035` |

## Edge cases

- An empty TCATBALF gives the 2 banners, an empty TRANSACT and ACCTFILE = input, RC 0.
- A zero rate gives no transaction and no change to total. The account is still rewritten on the break, with cycles zeroed (AC8).
- A negative balance gives negative interest truncated toward zero. `-0.00` is encoded `{`.
- A non-numeric zoned field is out of scope (the COBOL result is undefined) and fails fast with RC 16.

## Preserved defects (fix later, deliberately)

- **D1** The last account is never rewritten (R3a).
- **D2** No fees.
- **D3** Truncation, not rounding (R5).
- **D4** Blank group id, so DEFAULT is always used (R4).
- **D5** Silent high-order truncation, and the TRAN-ID suffix wraps at 10⁶.
- **D6** Timestamps come from the clock, not the PARM.
- **D7** First card wins when XREF has duplicates.
- **R2** A short PARM is right-padded with spaces. This is the one documented deviation.

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -f modernized/cbact04c verify` green, JUnit 5 only | 1 |
| `GoldenEquivalenceTest` stock: TRANSACT, ACCTFILE, SYSOUT, RC byte-equal to `legacy/golden/CBACT04C/` | 2, 4 |
| same for `CBACT04C-after-CBTRN02C` (inputs ACCTFILE/TCATBALF from `legacy/golden/CBTRN02C/`) | 3, 4 |
| `InterestTest`: 2339.97 × 15.00 → `0000000292D`; negative truncation; overpunch round-trip | 5 |
| `DefectCharacterizationTest.lastAccountNotRewritten`: acct 50 has txn `0000000187G`, record = input | 6 |
| `…defaultFallback`: messages and DEFAULT rate; E1/E2/E3 → RC 16 and SYSOUT lines | 7 |
| `…zeroRate`: no txn, cycles zeroed, balance unchanged | 8 |
| `legacy/harness/golden.sh --check` passes; `git diff --stat legacy .github` is empty | 9 |
