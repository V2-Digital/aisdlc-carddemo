# CR-1 Spec: `CBACT04C` in Java 21 (behaviour-preserving)

Legacy behaviour, file inventory and defects are in [intent.md](intent.md). This spec covers only what the Java code must reproduce byte for byte.

## Invocation

`java -jar cbact04c.jar <PARM-DATE>`. PARM is 10 chars, right-padded with spaces if it is shorter and truncated if it is longer.
- DD paths are environment variables `DD_TCATBALF`, `DD_XREFFILE`, `DD_DISCGRP`, `DD_ACCTFILE`, `DD_TRANSACT`. Each points to a canonical text file (fixed width, LF, overpunch; `legacy/README.md`). Same names as the harness.
- `ACCTFILE` is read in full and then rewritten in key order (the unloaded I-O image). `TRANSACT` is created new.
- Clock: `java.time.Clock` is injected. The default is the system clock; tests use a fixed `2022-07-18T00:00:00`.
- SYSOUT goes to stdout. Exit code: 0, or 16 on abend (intent Q1/Q2 defaults).

## Fields read, written or reinterpreted

| File (copybook) | Field | Offset (0-based) / len | PIC | Java |
|---|---|---|---|---|
| TCATBALF ([CVTRA01Y](../../app/cpy/CVTRA01Y.cpy)), 50 | acct-id / type / cat | 0/11, 11/2, 13/4 | 9(11) X(2) 9(4) | `String` |
| | `TRAN-CAT-BAL` | 17/11 | S9(9)V99 | `BigDecimal` scale 2 |
| XREFFILE ([CVACT03Y](../../app/cpy/CVACT03Y.cpy)), 50 | card-num / acct-id (AIX) | 0/16, 25/11 | X(16) 9(11) | `String` |
| DISCGRP ([CVTRA02Y](../../app/cpy/CVTRA02Y.cpy)), 50 | key group+type+cat | 0/16 | X(10) X(2) 9(4) | `String` |
| | `DIS-INT-RATE` | 16/6 | S9(4)V99 | `BigDecimal` scale 2 |
| ACCTFILE ([CVACT01Y](../../app/cpy/CVACT01Y.cpy)), 300 | `ACCT-ID` | 0/11 | 9(11) | `String` |
| | `ACCT-CURR-BAL` | 12/12 | S9(10)V99 | `BigDecimal` |
| | `ACCT-CURR-CYC-CREDIT` / `-DEBIT` | 76/12, 88/12 | S9(10)V99 | set to `+0` |
| | `ACCT-GROUP-ID` | 110/10 | X(10) | `String` |
| TRANSACT ([CVTRA05Y](../../app/cpy/CVTRA05Y.cpy)), 350 | all fields, written | see the copybook | | `String` builder |

All other ACCTFILE bytes are copied through unchanged. The account file is kept as a raw 300-byte line, and only the three numeric fields above are patched.

**Zoned encoding.** Sign overpunch on the last digit: `{`/`A`-`I` positive, `}`/`J`-`R` negative. Zero is always written as `{`, and a negative result is written with `}`/`J`-`R`. Unsigned `9(n)` fields are written as plain digits. On store, values are truncated to the field's precision, both the extra decimals and the high-order digits (modulo 10^n, as COBOL MOVE does without SIZE ERROR).

## Processing rules (in order)

1. Open: if an input is missing or unreadable, or the output is not writable, print the matching `ERROR OPENING …` line (DISCGRP prints `ERROR OPENING DALY REJECTS FILE`, D7), then the status line, then `ABENDING PROGRAM`, then exit 16.
2. Print `START OF EXECUTION OF PROGRAM CBACT04C`.
3. For each TCATBALF record in key order, print the raw 50-byte record. Then:
   - If the acct-id differs from the last one: if this is not the first record, rewrite the previous account (rule 6). Then set `total = 0`, read the account (if missing: `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`, abend), and read the xref by AIX, taking the first card in primary-key order (if missing: `ACCOUNT NOT FOUND: <id>`, `ERROR READING XREF FILE`, status 0023, abend).
4. Rate lookup with key `ACCT-GROUP-ID`+type+cat. On a miss, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with `DEFAULT` padded to 10. If that also misses: `ERROR READING DEFAULT DISCLOSURE GROUP`, status 0023, abend.
5. If `rate ≠ 0` (negative rates included):
   - `monthly = (bal × rate) / 1200`, computed with exact intermediates, then `setScale(2, DOWN)` (truncation toward zero), then truncated to 9 integer digits.
   - `total += monthly`, stored as S9(9)V99 with the same truncation.
   - `suffix = (suffix + 1) mod 1,000,000` (D6).
   - Write the TRANSACT record: `PARM(10)+suffix(6 digits)`, `01`, `0005`, `System` padded to 10, desc `Int. for a/c ` + ACCT-ID padded to 100 with spaces, amount S9(9)V99, merchant id `000000000`, name/city/zip spaces (50/50/10), card number, ORIG-TS and PROC-TS = `yyyy-MM-dd-HH.mm.ss.hh0000` (hh = hundredths, truncated), and 20 spaces of FILLER.
   - Fees: no-op (D3).
6. Account rewrite: `bal = bal + total` (S9(10)V99, high-order truncation), cycle credit and debit set to 0, record replaced in the in-memory master.
7. At EOF, do **not** rewrite the current account (D1). Write the ACCTFILE image, close, print `END OF EXECUTION OF PROGRAM CBACT04C`, exit 0.
8. Abends: ACCTFILE is still written, with the rewrites done so far (a KSDS REWRITE takes effect immediately), and TRANSACT holds the records written so far. *(Golden data has no abend scenario, so this is best effort.)*

## Edge cases

| Case | Handling |
|---|---|
| Empty TCATBALF | Writes no transactions, ACCTFILE is unchanged, RC 0 |
| Rate = 0 | No transaction and no change to the total. The account is still rewritten (cycle fields zeroed) unless it is the last one |
| Negative balance or rate | Signed arithmetic, truncation toward zero |
| CRLF in inputs | Strip a trailing `\r` before parsing (README: tcatbal has CRLF). Short lines are padded with spaces to LRECL |
| Duplicate xref acct-id | First card in card-number order (R2) |

## Preserved defects (fix later, deliberately)

- D1: the last account is never rewritten.
- D2: truncation instead of rounding; silent high-order overflow.
- D3: the fees paragraph is empty.
- D4: the blank group id always falls back to DEFAULT (data).
- D5: timestamps use the clock, not the PARM date (the clock is injectable).
- D6: the TRAN-ID suffix wraps at 1,000,000.
- D7: wrong message text on DISCGRP open error.
- D8: a missing account or xref abends instead of skipping.

## Acceptance tests

| AC | Test |
|---|---|
| 1 | `mvn -q verify` (JUnit 5 + Surefire only) |
| 2 | `GoldenEquivalenceTest.stock`: inputs from `app/data/ASCII`; TRANSACT, ACCTFILE, SYSOUT and RC byte-equal to `legacy/golden/CBACT04C` |
| 3 | `GoldenEquivalenceTest.afterCbtrn02c`: ACCTFILE and TCATBALF from `legacy/golden/CBTRN02C`; compared with `legacy/golden/CBACT04C-after-CBTRN02C` |
| 4 | `DefectTest.lastAccountNotRewritten`: account 50 out = in; tx `2022071800000050` amount `0000000187G` |
| 5 | `InterestTest.truncatesNotRounds`: e.g. 100.00 × 15.99 / 1200 = 1.3325 → `1.33`; 1.00 × 15 / 1200 = 0.0125 → `0.01`; a negative case truncates toward zero |
| 6 | `DefectTest.zeroRateSkipped` and `defaultFallback` (SYSOUT lines are present) |
| 7 | `TransactLayoutTest`: cols 57-132 (desc tail) and 331-350 are spaces; record length is 350 |
| 8 | `AbendTest`: missing account, missing xref, missing DEFAULT → exit 16, `ABENDING PROGRAM` |
| 9 | Covered by the SYSOUT/RC asserts in AC2/AC3 |
| — | `ZonedDecimalTest`: round trip of every overpunch char; `-0` encodes as `{` |
