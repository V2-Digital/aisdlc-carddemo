# CR-1 Spec: CBACT04C in Java 21 (behaviour-preserving)

Legacy behaviour, file table, steps and defects are in [intent.md](intent.md). This spec fixes the **contract** the Java must meet. Approver defaults Q1-Q3 are assumed: SYSOUT is in the contract, ACCTFILE is written to a new output file, and no defects are fixed.

## Invocation

`java -jar cbact04c.jar <PARM> <IN_DIR> <OUT_DIR>`
- `PARM`: 10 chars (e.g. `2022071800`). It is used as-is as the TRAN-ID prefix.
- Inputs: `<IN_DIR>/{TCATBALF,XREFFILE,ACCTFILE,DISCGRP}.txt`.
- Outputs: `<OUT_DIR>/{TRANSACT,ACCTFILE}.txt`. SYSOUT goes to stdout and the abend line to stderr, as in the harness.
- Clock: env `CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss`, same as the harness. If it is unset, the system clock in UTC is used. Tests inject a `java.time.Clock`.

## Inputs / outputs (canonical text: LF, fixed width, overpunch)

| DD | Copybook | LRECL | Read as |
|---|---|---|---|
| TCATBALF | `app/cpy/CVTRA01Y.cpy` | 50 | sorted by cols 1-17 (KSDS order) |
| XREFFILE | `app/cpy/CVACT03Y.cpy` | 50 | map acct(26-36) → card(1-16); lowest card number wins |
| ACCTFILE | `app/cpy/CVACT01Y.cpy` | 300 | `LinkedHashMap` by cols 1-11, written out in key order |
| DISCGRP | `app/cpy/CVTRA02Y.cpy` | 50 | map key cols 1-16 → rate |
| TRANSACT | `app/cpy/CVTRA05Y.cpy` | 350 | appended in write order |

The reader accepts CRLF and short lines. It strips `\r` and right-pads lines to LRECL with spaces. Records are kept as `byte[]`/`String` and only the fields below are patched, so every other byte passes through unchanged.

| Field | Cols (1-based) | PIC | Java |
|---|---|---|---|
| TRANCAT-ACCT-ID / TYPE-CD / CAT-CD | 1-11 / 12-13 / 14-17 | 9(11) / X(2) / 9(4) | String |
| TRAN-CAT-BAL | 18-28 | S9(9)V99 | BigDecimal, scale 2 |
| DIS-INT-RATE | 17-22 | S9(4)V99 | BigDecimal, scale 2 |
| ACCT-CURR-BAL | 13-24 | S9(10)V99 | BigDecimal (written back) |
| ACCT-CURR-CYC-CREDIT / DEBIT | 79-90 / 91-102 | S9(10)V99 | written as zero, `00000000000{` |
| ACCT-GROUP-ID | 113-122 | X(10) | String (blank in sample) |
| XREF-CARD-NUM | 1-16 | X(16) | String |

**TRANSACT record** (350 bytes, all fields written):

| Field | Value |
|---|---|
| ID (1-16) | PARM + counter `%06d` |
| type (17-18) | `01` |
| cat (19-22) | `0005` |
| source (23-32) | `System` padded to 10 |
| desc (33-132) | `Int. for a/c ` + acct-id, space-padded to 100 |
| amt (133-143) | S9(9)V99 overpunch |
| merchant id (144-152) | `000000000` |
| name/city/zip (153-262) | spaces |
| card (263-278) | xref card |
| orig/proc TS (279-304 / 305-330) | `yyyy-MM-dd-HH.mm.ss.hh0000` |
| FILLER (331-350) | spaces |

**Overpunch codec.** The last digit carries the sign: `{A-I` = +0..9, `}J-R` = -0..9. Encoding always uses `{`/`}` for a zero last digit and treats zero as positive. Decoding also accepts a plain digit as positive.

## Processing rules (must match exactly)

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`. Load all four inputs. If a file is missing or unreadable, abend (R8) with the COBOL open-error text for that DD: `ERROR OPENING TRANSACTION CATEGORY BALANCE` (per `CBACT04C.cbl:234-323`), `ERROR OPENING CROSS REF FILE`, `ERROR OPENING DALY REJECTS FILE` (D4, kept), and `ERROR OPENING ACCOUNT MASTER FILE`.
2. For each TCATBAL record in key order, print the 50-byte record.
3. When the account ID changes:
   - If this is not the first account, rewrite the previous one (rule 6).
   - Set total = 0.
   - Look up the account. If it is missing, print `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023` and abend.
   - Look up the xref. If it is missing, print the same lines but with `ERROR READING XREF FILE`, then abend.
4. Look up the rate with key `GROUP-ID + type + cat`. If it misses, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with `DEFAULT   ` (padded to 10). If that also misses, print `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023` and abend.
5. If rate ≠ 0:
   - `monthly = (bal × rate).divide(1200, 2, DOWN)`, then keep only the low 9 integer digits (S9(9)V99 high-order truncation, no SIZE ERROR).
   - `total += monthly`.
   - Increment the counter (mod 10^6, no overflow guard) and write the transaction. The timestamp is read from the clock for each write.
6. **Rewrite account:**
   - `bal = bal + total`, keeping only the low 10 integer digits.
   - Set credit and debit to 0.
   - Replace the record in the map.
7. At EOF, **do not rewrite the last account (D1)**. Write TRANSACT and ACCTFILE, print `END OF EXECUTION OF PROGRAM CBACT04C`, and exit with code 0.
8. **Abend (R8):** print `ABENDING PROGRAM` to stdout and `CEE3ABD: ABEND U0999` to stderr, then exit with 16. Output files are not written. (Under COBOL, partial output in a new GDG is discarded on abend.)

## Edge cases

| Case | Handling |
|---|---|
| Empty TCATBALF | Only the start/end lines are printed, TRANSACT has 0 records, ACCTFILE is unchanged, RC 0 |
| Rate = 0 (e.g. DEFAULT `03/0001`) | No transaction and no counter increment. The account is still rewritten (cycle fields zeroed) |
| Rate ≠ 0, balance 0 | A zero-amount transaction is written (D4) |
| Negative balance | Truncation is toward zero: -10.01×15/1200 = -0.1251 → `-0.12`, encoded `00000000001K` |
| Duplicate acct in xref | Lowest card number is used (AIX first-in-primary-key order) |
| Account appears only in TCATBAL tail | This is D1: transaction written, master not updated |

## Preserved defects (pinned by characterization tests)
- **D1:** the last account in TCATBAL order is never rewritten.
- **D2:** the rate key uses the blank `ACCT-GROUP-ID`, so every lookup falls back to `DEFAULT`.
- **D3:** interest is truncated, not rounded.
- **D4:**
  - the DISCGRP open error text says `DALY REJECTS FILE`
  - zero-amount transactions are written
  - the 6-digit counter wraps without a guard
  - fees are not computed

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -q verify` passes on Java 21, JUnit 5 + Surefire only | 1 |
| `GoldenEquivalenceTest.stock`: TRANSACT, ACCTFILE and SYSOUT equal `legacy/golden/CBACT04C/*` byte-for-byte, RC 0 | 2, 4, 9 |
| `GoldenEquivalenceTest.afterCbtrn02c`: same check against `legacy/golden/CBACT04C-after-CBTRN02C/*` | 3, 4, 9 |
| `AbendTest`: missing account, missing xref, missing DEFAULT and missing input file each give RC 16 and the messages above; SYSOUT matches the COBOL output from `legacy/tests/cbact04c-abends.sh` | 4 |
| `InterestTest`: 1164.87×15 → 14.56, -10.01×15 → -0.12, a case where truncation ≠ HALF_UP, and rewrite with cycle fields zeroed | 5 |
| `TranIdTest`: IDs are sequential across accounts, zero-amount rows count, rate-0 rows don't | 6 |
| `DefaultFallbackTest`: a miss uses the DEFAULT rate for the same type/cat | 7 |
| `CharacterizationTest`: D1 (last account unchanged while its txn exists), D2 (group `A000000000` rates unused), D3 (truncation) | 8 |
| `OverpunchTest`: round-trips all 20 sign characters and zero encodes as `{` | 2, 3 |
