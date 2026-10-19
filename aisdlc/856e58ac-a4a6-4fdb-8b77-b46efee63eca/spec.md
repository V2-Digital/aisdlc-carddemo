# CR-1 Spec — `CBACT04C` in Java 21 (behaviour-preserving)

Legacy behaviour, file table, and defects: [intent.md](intent.md). This spec fixes the Java contract. Q1–Q3 are taken at their defaults: SYSOUT and RC are byte-identical too, the I/O is canonical text, and D1 gets its own CR later.

## Invocation
`java -jar cbact04c.jar <PARM-DATE> <IN_DIR> <OUT_DIR>`
- PARM-DATE is the first 10 chars of the argument, right-padded with spaces.
- The clock is injected as `java.time.Clock` (default `Clock.systemUTC()`). The CLI accepts the override `CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss`, the same 16-digit form the harness uses.
- Console output goes to stdout. The exit code is the program RC: `0`, or `16` on abend.

## Inputs and outputs
Each file is `<DD>.txt`: one fixed-width record per line, LF endings. On read, strip `\r` and right-pad each line with spaces to LRECL (`cardxref.txt` has 36-byte lines). Zoned decimals use overpunch on the last byte: `{A-I` = +0..9, `}J-R` = −0..9. Plain unsigned digits also read as positive.

| DD | Copybook | Fields used (1-based col, len, PIC → Java) |
|---|---|---|
| `TCATBALF` in | `app/cpy/CVTRA01Y.cpy` | acct 1,11 9(11)→`long`; type 12,2 X; cat 14,4 9(4); bal 18,11 S9(9)V99→`BigDecimal` |
| `XREFFILE` in | `app/cpy/CVACT03Y.cpy` | card 1,16 X; acct 26,11 9(11). Indexed by acct; the **first record in file order** wins |
| `DISCGRP` in | `app/cpy/CVTRA02Y.cpy` | key 1,16 (group 10 + type 2 + cat 4); rate 17,6 S9(4)V99 |
| `ACCTFILE` in/out | `app/cpy/CVACT01Y.cpy` | id 1,11; curr-bal 13,12 S9(10)V99; cyc-credit 84,12; cyc-debit 96,12; group 118,10 X. All other bytes pass through untouched |
| `TRANSACT` out | `app/cpy/CVTRA05Y.cpy` | full 350-byte record, built as in Rule 4 |

ACCTFILE is written back to `OUT_DIR/ACCTFILE.txt` as all records in ascending key order, with rewritten records replaced in place.

## Processing rules (in order)
1. Print `START OF EXECUTION OF PROGRAM CBACT04C`. Load the inputs.
2. For each TCATBALF record, in file (key) order, print the raw 50-byte record.
3. **Account change.** If the acct differs from the previous one:
   - If this is not the first account, rewrite the previous account (Rule 6).
   - Set `total = 0`.
   - Look up ACCTFILE by acct. If it is missing, print `ACCOUNT NOT FOUND: <acct11>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`, then abend.
   - Look up XREF by acct. If it is missing, print `ACCOUNT NOT FOUND: <acct11>`, `ERROR READING XREF FILE`, `FILE STATUS IS: NNNN0023`, then abend.
4. **Rate.** Build the key from `ACCT-GROUP-ID` (raw 10 bytes) + type + cat.
   - If the key is missing, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`. Retry with `DEFAULT   `.
   - If `DEFAULT` is also missing, print `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, then abend.
5. **If rate ≠ 0:**
   - `monthly = (bal × rate) / 1200`, truncated toward zero to 2 dp (`RoundingMode.DOWN`). Keep only the low 9 integer digits (S9(9)V99, no size error).
   - `total = trunc9(total + monthly)`.
   - `suffix = (suffix + 1) mod 1_000_000`.
   - Write the TRANSACT record:
     - `TRAN-ID` = PARM(10) + suffix(6, zero-padded)
     - `01`, `0005`, `System    `
     - desc = `Int. for a/c ` + acct(11), padded with spaces to 100
     - amount = monthly as S9(9)V99 overpunch
     - merchant id = `000000000`; name, city, zip = spaces
     - card = xref card
     - orig-ts = proc-ts = `yyyy-MM-dd-HH.mm.ss.hh0000` from the clock (UTC, hh = hundredths)
     - 20 spaces
   - The fee step is a no-op (D2).
6. **Rewrite** (runs only from Rule 3): `curr-bal = trunc10(curr-bal + total)`, `cyc-credit = 0`, `cyc-debit = 0`. The positive zero is written as `00000000000{`.
7. At EOF, do **not** rewrite the last account (D1). Write the outputs, then print `END OF EXECUTION OF PROGRAM CBACT04C`. Exit 0.
8. **Abend:** print `ABENDING PROGRAM` and exit 16. Do not print the END message. Outputs after an abend are not compared.

Numeric output: zoned, zero-padded, with overpunch sign on the last digit. Zero and positive values use `{A-I`. Negative non-zero values use `J-R`, or `}` when the last digit is 0.

## Edge cases
| Case | Handling |
|---|---|
| Rate = 0 | No transaction and no suffix increment. The account is still rewritten with cycles zeroed (unless it is the last account) |
| Negative balance, rate ≠ 0 | Negative interest, truncated toward zero (e.g. −0.009 → 0.00, written as `{`) |
| Group found (non-DEFAULT) | Uses that rate and prints no MISSING lines. Not in the golden data (D3), so a unit test covers it |
| Several TCATBALF rows for one acct | Interest accumulates in `total`. The xref lookup happens once per account |
| Overflow of monthly, total, balance, or suffix | High-order digits are dropped silently (D5) |
| CRLF or short input lines | Normalised on read (R3) |

## Preserved defects (fix later, deliberately)
- **D1** The last account in TCATBALF is never rewritten.
- **D2** `1400-COMPUTE-FEES` is empty.
- **D3** Lookups key on `ACCT-GROUP-ID`, which is blank in the sample data, so every lookup falls back to `DEFAULT`.
- **D4** The DISCGRP open failure text reads `ERROR OPENING DALY REJECTS FILE`. In Java this is unreachable, because a missing `DISCGRP.txt` is reported with this text and RC 16.
- **D5** Overflow and the suffix wrap give no error.

## Acceptance tests (→ intent AC)
| Test | AC |
|---|---|
| `mvn -q verify` on JDK 21, deps limited to JUnit 5 + Surefire | 1 |
| `GoldenTest.stock`: stock inputs, PARM `2022071800`, clock 2022-07-18T00:00Z → TRANSACT, ACCTFILE, SYSOUT, RC equal `legacy/golden/CBACT04C/` | 2, 9 |
| `GoldenTest.afterCbtrn02c`: ACCTFILE and TCATBALF from `golden/CBTRN02C` → equal `golden/CBACT04C-after-CBTRN02C/` | 3, 9 |
| `InterestTest`: 2339.97 × 15.00 → 29.24; negative-balance truncation; trunc9 overflow | 4, 5-D5 |
| `DefectTest.lastAccountNotRewritten`: account 50 gets tx `…000050` = 18.77, and its ACCTFILE record is unchanged | 5 |
| `RulesTest.zeroRate`: no transaction; cycle fields zeroed | 6 |
| `RulesTest`: group hit; DEFAULT fallback; missing DEFAULT, account, or xref → RC 16 with no END line | 7 |
| `RecordTest`: TRAN-ID sequence and wrap at 999999; desc, filler, and timestamp layout; overpunch round-trip | 8 |
