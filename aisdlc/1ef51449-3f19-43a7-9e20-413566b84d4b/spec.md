# CR-1 Spec — `CBACT04C` in Java 21

Legacy behaviour, defects and evidence are in [intent.md](intent.md). This spec fixes only what the Java code must do.
The intent's default answers to Q1–Q3 are assumed: SYSOUT matches too, I/O uses text files, and the clock comes from an env var.

## Interface

`java -jar cbact04c.jar <PARM-DATE> <OUT_DIR> [INPUT_DIR]`
- Each input DD `X` resolves to `INPUT_DIR/X.txt` if that file exists. Otherwise it falls back to the stock file in
  `DATA_DIR` (default `app/data/ASCII`): `TCATBALF`→`tcatbal.txt`, `XREFFILE`→`cardxref.txt`, `ACCTFILE`→`acctdata.txt`,
  `DISCGRP`→`discgrp.txt`. This matches the harness's `INPUT_DIR` override.
- `PARM-DATE` is used as given, padded or truncated to 10 characters.
- Outputs: `OUT_DIR/TRANSACT.txt` and `OUT_DIR/ACCTFILE.txt`. DISPLAY lines go to stdout (= `SYSOUT.txt`). Exit code 0, or 16 on abend.
- Clock: `CARDDEMO_CURRENT_DATE`, else `COB_CURRENT_DATE` (`YYYYMMDDHHMMSSss`), else the system clock in UTC. The core class takes a `Supplier<LocalDateTime>`.

## Record I/O (canonical text)

- Reading: split on LF, strip a trailing CR, and right-pad each line with spaces to the LRECL. This covers `cardxref.txt` (36 bytes) and `tcatbal.txt` (CRLF).
  Use ISO-8859-1 so the mapping is 1 byte = 1 char.
- Writing: LRECL bytes per record, each followed by LF.
- KSDS semantics: `TCATBALF` is processed sorted by key 1–17. `ACCTFILE` is written sorted by acct-id with every record present.
  For `XREFFILE`, acct-id maps to the **first** record in card-number order (R2).
- Zoned `S9(n)V99`: the last byte is an overpunch (`{A-I`=+0..9, `}J-R`=−0..9). Positive values are written with `{A-I`, negative with `}J-R`.
  Unsigned `9(n)` is written as plain digits.

## Fields read / written (1-based columns)

| File (copybook) | Field | Cols | PIC → Java |
|---|---|---|---|
| TCATBALF [`CVTRA01Y`](../../app/cpy/CVTRA01Y.cpy), 50 | acct-id / type / cat | 1-11 / 12-13 / 14-17 | `9(11)` String, `X(2)`, `9(4)` String |
| | `TRAN-CAT-BAL` | 18-28 | `S9(9)V99` → `BigDecimal` |
| XREFFILE [`CVACT03Y`](../../app/cpy/CVACT03Y.cpy), 50 | card num / acct-id | 1-16 / 26-36 | `X(16)`, `9(11)` |
| ACCTFILE [`CVACT01Y`](../../app/cpy/CVACT01Y.cpy), 300 | `ACCT-ID` | 1-11 | `9(11)` |
| | `ACCT-CURR-BAL` (rw) | 13-24 | `S9(10)V99` → `BigDecimal` |
| | `CURR-CYC-CREDIT` / `-DEBIT` (w) | 79-90 / 91-102 | `S9(10)V99`, set to 0 (`00000000000{`) |
| | `ACCT-GROUP-ID` | 113-122 | `X(10)` |
| DISCGRP [`CVTRA02Y`](../../app/cpy/CVTRA02Y.cpy), 50 | group / type / cat | 1-10 / 11-12 / 13-16 | key |
| | `DIS-INT-RATE` | 17-22 | `S9(4)V99` → `BigDecimal` |
| TRANSACT [`CVTRA05Y`](../../app/cpy/CVTRA05Y.cpy), 350 | id, type, cat, source | 1-16, 17-18, 19-22, 23-32 | `PARM(10)+9(6)`, `01`, `0005`, `System` + 4 spaces |
| | desc | 33-132 | `Int. for a/c ` + ACCT-ID (cols 1-11), rest spaces |
| | amt | 133-143 | `S9(9)V99` overpunch |
| | merchant id / name / city / zip | 144-152 / 153-202 / 203-252 / 253-262 | `000000000` / spaces |
| | card / orig-ts / proc-ts / filler | 263-278 / 279-304 / 305-330 / 331-350 | xref card / ts / ts / spaces |

All other ACCTFILE bytes are copied through unchanged.

## Processing rules (in order)

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`. Load all inputs. A missing or unreadable input file is an open error and abends (rule 9).
2. For each TCATBALF record in key order, print the 50-byte record.
3. On an account change (compare acct-id with the previous record): if this is not the first record, stage the previous account's update (rule 7). Then set total = 0
   and look up the account (miss: `ACCOUNT NOT FOUND: <acct>` and abend with status 23) and the xref (miss: same message and status 23, `ERROR READING XREF FILE`).
4. Rate lookup uses the key `ACCT-GROUP-ID + type + cat`. On a miss, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then
   retry with group `DEFAULT` (padded to 10). If that also misses, print `ERROR READING DEFAULT DISCLOSURE GROUP` and abend.
5. If rate ≠ 0: `monthly = trunc2(bal × rate / 1200)`, where trunc2 is `RoundingMode.DOWN` at scale 2 (toward zero) of the exact quotient,
   followed by `mod 10^9` (D4). Then `total = (total + monthly) mod 10^9`, keeping the sign as COBOL does. Write one TRANSACT record (rule 6).
   If rate = 0, no transaction is written and the counter does not advance.
6. Transaction: increment the counter (`9(6)`, wraps at 10^6). The timestamp is taken from the clock **per record** as `yyyy-MM-dd-HH.mm.ss.hh0000`, where hh = hundredths.
7. Account update: `CURR-BAL = (CURR-BAL + total) mod 10^10`. Set cycle credit and cycle debit to 0. Accounts with a total of 0 are updated as well.
8. At EOF, **do not** update the last account (D1). Write the outputs. Print `END OF EXECUTION OF PROGRAM CBACT04C`. Exit 0.
9. Abend: print the error line, then `FILE STATUS IS: NNNN00<st>` and `ABENDING PROGRAM`, and write `CEE3ABD: ABEND U0999` to stderr. Exit 16.
   The transactions and account updates staged so far are flushed, mirroring VSAM in-place writes. Outputs after an abend are not gated (see plan, risk 3).

## Edge cases

| Case | Handling |
|---|---|
| Negative balance, rate ≠ 0 | Truncate toward zero: −100.00 × 15 / 1200 = −1.25, and −1.27 × 15 / 1200 = −0.0158… gives −0.01. Write a `}J-R` overpunch. |
| Rate 0 | No transaction is written, but the account is still updated (cycle fields set to 0). |
| `ACCT-GROUP-ID` blank (all sample data, D5) | Falls back to `DEFAULT`, with 2 SYSOUT lines per category record. |
| Overflow of monthly, total or balance | Silent high-order truncation (modulo). No error. |
| xref with several cards per account | The lowest card number wins (R2). |
| Input lines short or with CRLF | Padded or stripped (R3). |

## Preserved defects (fix later, deliberately)

- **D1**: the last account in TCATBALF is never rewritten, although its interest transaction is written.
- **D2**: interest is truncated, not rounded.
- **D3**: fees are not computed (empty `1400-COMPUTE-FEES`). Kept as an empty hook.
- **D4**: no size-error checks. Overflow wraps silently.
- **D5**: the group id is read from the field the sample data leaves blank. The open-error text says `DALY REJECTS FILE` for DISCGRP.

## Acceptance tests (→ intent AC#)

| Test | AC |
|---|---|
| `mvn -f modernized/cbact04c test` green; pom has JUnit 5 + Surefire only | 1 |
| `GoldenTest.stock`: TRANSACT, ACCTFILE and SYSOUT byte-equal to `legacy/golden/CBACT04C/`, exit 0 | 2, 4 |
| `GoldenTest.afterCbtrn02c`: inputs from `legacy/golden/CBTRN02C/`, same comparisons, exit 0 | 3, 4 |
| `InterestTest`: 1164.87 × 15.00 → 14.56; negative values; overflow modulo; a count of truncated amounts in scenario 3 equal to 48 | 5 |
| `DefectTest.lastAccountNotRewritten`: acct `00000000050` stays at 1945.87 with its cycle fields unchanged, while tran `2022071800000050` has amount `0000000187G` | 6 |
| `AbendTest`: no DEFAULT row, missing account, missing xref → exit 16 and the expected SYSOUT tail; a missing group → DEFAULT used | 7 |
| Golden tests run with PARM `2022071800` and clock `2022071800000000` injected | 8 |
