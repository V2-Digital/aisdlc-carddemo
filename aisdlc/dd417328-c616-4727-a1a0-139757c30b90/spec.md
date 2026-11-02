# CR-1 Spec: `CBACT04C` in Java 21

This is a like-for-like rewrite. Legacy behaviour, file roles and defects are described in [intent.md](intent.md) and are not repeated here. **Every rule below, defects included, is reproduced exactly.**

## Interface

`java -jar cbact04c.jar --parm 2022071800 --in <dir> --out <dir> [--current-date YYYYMMDDHHMMSSss]`

- **Input datasets.** `<in>/<DD>.txt` for `TCATBALF`, `XREFFILE`, `ACCTFILE` and `DISCGRP`. These use the harness canonical form (`legacy/README.md`).
- **Outputs.** `<out>/TRANSACT.txt` and `<out>/ACCTFILE.txt`. ACCTFILE is I-O in COBOL: here it is read from `<in>` and written to `<out>`, so the input is never mutated. DISPLAY lines go to stdout (which is SYSOUT). The process exit code is the RC.
- **Clock.** `--current-date` has the same 16-digit form as `COB_CURRENT_DATE`. If it is absent, the program reads the system clock in UTC once per transaction, as COBOL does (AC8).
- **Reading input.** CR is stripped. Each line is right-padded with spaces to its LRECL: `cardxref` lines are 36 bytes.

## Records (only the fields this program touches; offsets are 1-based)

| File / copybook | Field | Cols | PIC | Java |
|---|---|---|---|---|
| TCATBALF `app/cpy/CVTRA01Y.cpy` (50) | key acct+type+cat | 1-17 | 9(11) X(2) 9(4) | `String` |
| | `TRAN-CAT-BAL` | 18-28 | S9(9)V99 | `BigDecimal` scale 2 |
| XREFFILE `app/cpy/CVACT03Y.cpy` (50) | `XREF-CARD-NUM` | 1-16 | X(16) | `String` |
| | `XREF-ACCT-ID` (AIX) | 26-36 | 9(11) | `String` |
| ACCTFILE `app/cpy/CVACT01Y.cpy` (300) | `ACCT-ID` (key) | 1-11 | 9(11) | `String` |
| | `ACCT-CURR-BAL` | 13-24 | S9(10)V99 | `BigDecimal` |
| | `ACCT-CURR-CYC-CREDIT` / `-DEBIT` | 79-90 / 91-102 | S9(10)V99 | `BigDecimal` |
| | `ACCT-GROUP-ID` | 113-122 | X(10) | `String` |
| DISCGRP `app/cpy/CVTRA02Y.cpy` (50) | key group+type+cat | 1-16 | X(10) X(2) 9(4) | `String` |
| | `DIS-INT-RATE` | 17-22 | S9(4)V99 | `BigDecimal` |
| TRANSACT `app/cpy/CVTRA05Y.cpy` (350) | the whole record is written | 1-350 | see below | `String` |

The bytes of an account record that the program does not touch are copied through unchanged.

**Zoned codec.** The sign is overpunched on the last digit: `{A-I` mean +0..9 and `}J-R` mean −0..9. When a value is written, a zero result always gets `{`, never `}`. When a value is stored into a field, its high-order digits are dropped (the value is taken mod 10^digits), as COBOL does when there is no `ON SIZE ERROR`.

## Processing rules (in order)

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`. Load the inputs into maps keyed as shown in the table above. TCATBALF is iterated **in key order**, as the KSDS returns it, not in line order. The XREF lookup by acct-id returns the first record in card-number order.
2. For each TCATBALF record: print the 50-byte record as it is.
3. **Control break.** If the acct-id differs from the last one: rewrite the previous account (rule 6), unless this is the first account. Then set total = 0 and look up the account and the xref (see Errors).
4. **Rate lookup** with key (`ACCT-GROUP-ID`, type, cat). If it is missing, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then look up `DEFAULT` padded to 10 characters with the same type and cat.
5. If rate ≠ 0:
   - `int = trunc(bal × rate / 1200)` to scale 2, rounding toward zero (`RoundingMode.DOWN`), then mod 10^11.
   - `total += int`, also truncated to S9(9)V99.
   - Increment the counter, mod 10^6, and write the TRANSACT record:
     `TRAN-ID` = PARM(10) + counter(6) · `01` · `0005` · `System` + 4 spaces · `Int. for a/c ` + acct-id, space-padded to 100 · amount S9(9)V99 · `000000000` · 50+50+10 spaces · card · ts · ts · 20 spaces.
   - `ts` = `YYYY-MM-DD-HH.MM.SS.hh0000`.
6. **Account rewrite.** `CURR-BAL += total` (mod 10^12). Cycle credit and cycle debit are set to `+0`.
7. At EOF, **no rewrite** (D1). Write ACCTFILE in key order, then print `END OF EXECUTION OF PROGRAM CBACT04C`, then exit 0.

## Errors (rule 3/4 lookups, I/O)

Each line below is printed in this order: the error lines, then `FILE STATUS IS: NNNN00` + status, then `ABENDING PROGRAM`. After that, stderr gets `CEE3ABD: ABEND U0999` and the program **exits 16**.

| Case | Error lines | Status |
|---|---|---|
| Account missing | `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE` | 23 |
| Xref missing | `ACCOUNT NOT FOUND: <id>`, `ERROR READING XREF FILE` | 23 |
| DEFAULT missing | `ERROR READING DEFAULT DISCLOSURE GROUP` | 23 |
| Input file missing or unreadable | `ERROR OPENING <...>` as in the CBL | 35 |

On abend, TRANSACT keeps the records already written. ACCTFILE is written with the rewrites done so far, so it matches the KSDS state at the moment of the abend.

## Edge cases

| Case | Handling |
|---|---|
| Rate = 0 (e.g. `03/0001`) | No transaction, no fee call. The account is still rewritten at the next break. |
| Balance 0, rate ≠ 0 | A zero-amount transaction is written (F1). |
| Negative balance, rate ≠ 0 | Negative interest is truncated toward zero, e.g. −10.07 × 15 / 1200 = −0.125875 → `-0.12` (`0000000001K`). |
| Several categories per account | Interest is summed into one rewrite. One transaction is written per category. |
| An account whose only records have rate 0 | It is rewritten with +0, so its cycle fields are zeroed (unless it is the last account). |
| Empty TCATBALF | No transactions. ACCTFILE is copied unchanged. RC 0. |

## Preserved defects (fix later, deliberately)

- **D1.** The last account in TCATBALF is never rewritten. Its interest is posted to TRANSACT but not added to the balance, and its cycle fields are not reset.
- **D2.** Interest is truncated, not rounded.
- **D3.** The lookup uses `ACCT-GROUP-ID`, which is blank in the data, so every account gets the `DEFAULT` rate.
- **D4.** No fees are charged (`1400-COMPUTE-FEES` is empty).
- **F1.** Zero-amount interest transactions are written.
- **F2.** The 6-digit tran-id counter wraps, and interest or balance overflow silently drops the high-order digits.
- **F3.** Timestamps have only hundredths of a second.

## Acceptance tests (mapped to the intent's ACs)

| AC | Test |
|---|---|
| 1 | `mvn -q verify`. The pom has JUnit 5 + Surefire only. |
| 2, 4 | `GoldenEquivalenceTest.stock`: stock inputs, `--current-date 2022071800000000`. Byte-compares TRANSACT, ACCTFILE, SYSOUT and RC against `legacy/golden/CBACT04C/`. |
| 3, 4 | `GoldenEquivalenceTest.afterCbtrn02c`: the same, with TCATBALF and ACCTFILE taken from `golden/CBTRN02C`. |
| 5 | `InterestTest`: truncation of positive amounts (28 golden cases where half-up rounding would differ), the negative case above, and the codec round-trip. |
| 6 | `DefectCharacterizationTest.lastAccountNotRewritten` (D1), plus one test for each of F1 and F2. |
| 7 | `ErrorPathTest`: group fallback, and missing account / xref / DEFAULT → RC 16, the exact SYSOUT lines, and no further records. |
| 8 | All tests inject `--current-date`. One test checks the clock default. |
| R1 | `legacy/tests/cbact04c-diff.sh`: runs the COBOL and the Java on crafted edge inputs and diffs the outputs. |
