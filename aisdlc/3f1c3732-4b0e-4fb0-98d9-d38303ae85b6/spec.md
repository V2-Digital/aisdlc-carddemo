# CR-1 Spec — `CBACT04C` in Java 21

Behaviour-preserving rewrite. Legacy behaviour and defects are described in [intent.md](intent.md) (D1–D6, R1–R3).
This spec fixes the contract the Java code must meet. Approver defaults Q1–Q3 are taken as **yes**.

## Interface

`java -jar cbact04c.jar --parm 2022071800 --in <DIR> --out <DIR> [--now YYYYMMDDHHMMSSss]`

- `--parm`: same as JCL `PARM`. Its first 10 chars are `PARM-DATE`; pad with spaces if shorter.
- `--now`: frozen clock in the harness `COB_CURRENT_DATE` 16-digit form. Default: system clock in UTC.
- stdout = SYSOUT (DISPLAY lines). stderr = `CEE3ABD: ABEND U0999` on abend. Exit code = RC (0 or 16).
- I/O is bytes (ISO-8859-1), one record per line, LF. On load, drop `\r` and right-pad short lines with spaces
  to LRECL, as `harness/run.sh` does (`cardxref.txt` lines are 36 bytes).

## Files

| DD (`<DIR>/<DD>.txt`) | Copybook | LRECL | In/out | Fields used (1-based offset, length, PIC → Java) |
|---|---|---|---|---|
| `TCATBALF` | `app/cpy/CVTRA01Y.cpy` | 50 | in, sorted by key 1–17 | acct `1,11 9(11)`→String; type `12,2 X`; cat `14,4 9(4)`; bal `18,11 S9(9)V99`→BigDecimal |
| `ACCTFILE` | `app/cpy/CVACT01Y.cpy` | 300 | in → out | id `1,11`; curr-bal `13,12 S9(10)V99`; cyc-credit `79,12`; cyc-debit `91,12` (both S9(10)V99); group-id `113,10 X` |
| `XREFFILE` | `app/cpy/CVACT03Y.cpy` | 50 | in | card `1,16 X`; acct `26,11 9(11)` (alt key) |
| `DISCGRP` | `app/cpy/CVTRA02Y.cpy` | 50 | in | key `1,16` = group(10)+type(2)+cat(4); rate `17,6 S9(4)V99` |
| `TRANSACT` | `app/cpy/CVTRA05Y.cpy` | 350 | out, write order | all fields (see rule 5) |

The output `ACCTFILE.txt` holds **every** input account in key order. Rewritten records are replaced in place.
All other bytes, including FILLER, are copied through unchanged.

**Zoned decimal codec:** the last byte carries the sign and digit: `{`/`A`–`I` = +0..9, `}`/`J`–`R` = −0..9.
Reading `}` gives 0. On write, a value < 0 uses `}`/`J`–`R`, and a value ≥ 0 uses `{`/`A`–`I`.
Unsigned `9(n)` fields are written as plain digits.

## Processing rules (in order)

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`. Load all inputs. If a file is missing, print the legacy
   open message (`ERROR OPENING …`, DISCGRP prints `ERROR OPENING DALY REJECTS FILE` (D5)), then
   `FILE STATUS IS: NNNN0035`, then abend.
2. Loop over TCATBALF records in key order. Print each record verbatim (50 bytes) to stdout.
3. On an account change: if a previous account exists, rewrite it (rule 6). Then set total = 0 and look up
   the account by id. If it is missing, print `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE`,
   `FILE STATUS IS: NNNN0023`, then abend. Next, look up the xref by acct id, taking the first match in
   card-number order (R3). If it is missing, print `ACCOUNT NOT FOUND: <id>` (D5), `ERROR READING XREF FILE`,
   `FILE STATUS IS: NNNN0023`, then abend.
4. Rate: look up the key `ACCT-GROUP-ID + type + cat`. If it misses, print `DISCLOSURE GROUP RECORD MISSING`
   and `TRY WITH DEFAULT GROUP CODE`, then retry with group `DEFAULT` padded to 10. If that also misses, print
   `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, then abend.
5. If rate ≠ 0 (−0 counts as 0):
   - `interest = trunc2(bal × rate / 1200)`. Use the exact BigDecimal value, `RoundingMode.DOWN`, scale 2.
     Then reduce mod 10^9 on the integer part (D3, D4).
   - Add the interest to the total, mod 10^9 (`S9(9)V99`).
   - Increment the counter, which starts at 0 per run. Write one TRANSACT record:
     id = PARM-DATE + counter as `%06d`, type `01`, cat `0005`, source `System` padded to 10, desc
     `Int. for a/c ` + ACCT-ID padded to 100, amt = interest, merchant id `000000000`, name/city/zip spaces,
     card = xref card, orig-ts = proc-ts = `YYYY-MM-DD-HH.MM.SS.hh0000` from `--now`, then 20 spaces.
   - Fees: none (the legacy `1400` paragraph is empty).
6. Rewrite (`1050`): `curr-bal = (curr-bal + total) mod 10^10`, cyc-credit = 0, cyc-debit = 0 (written `{`).
   This runs even when the total is 0 or the rate was 0 (D2).
7. At EOF: **do not rewrite the last account** (D1). Write the outputs, print
   `END OF EXECUTION OF PROGRAM CBACT04C`, exit 0.
8. Abend: print `ABENDING PROGRAM` to stdout and `CEE3ABD: ABEND U0999` to stderr, exit 16, write no
   output files. The harness exports nothing on abend either.

## Edge cases

| Case | Handling |
|---|---|
| Negative balance | Truncate toward zero: −100.00 × 15 / 1200 = −1.25; −0.01 → −0.000125 → 0. Sign of a zero result: see the plan, risk 1 |
| Rate 0 for type/cat | No transaction and no counter increment. The account is still rewritten (D2) |
| Rate ≠ 0, balance 0 | A transaction with amount `0000000000{` is written (stock golden) |
| Group-specific hit | No MISSING lines are printed. That rate is used (D6 path) |
| Empty TCATBALF | No transactions and no rewrites. Prints START and END only. RC 0 |
| One account only | It is never rewritten (D1) |
| Account without TCATBAL | Passed through untouched |

## Preserved defects (fix later, deliberately)

- **D1** The last account's interest is never posted to ACCTFILE, and its cycle fields are not zeroed.
- **D2** Cycle credit/debit are zeroed even when the interest is 0.
- **D3** Interest is truncated, not rounded.
- **D4** Overflow silently drops high-order digits (mod 10^9 / 10^10).
- **D5** Misleading messages: `DALY REJECTS FILE` on a DISCGRP open error, and `ACCOUNT NOT FOUND` on an xref miss.

## Acceptance tests (→ intent AC#)

| Test | AC |
|---|---|
| `mvn -q verify` with only JUnit 5 + Surefire | 1 |
| `GoldenEquivalenceTest.stock`: stock `app/data/ASCII` inputs → TRANSACT, ACCTFILE, SYSOUT equal `legacy/golden/CBACT04C/` byte for byte; RC 0 | 2, 7, 9 |
| `GoldenEquivalenceTest.afterCbtrn02c`: ACCTFILE and TCATBALF from `golden/CBTRN02C` → equal `golden/CBACT04C-after-CBTRN02C/` | 3, 9 |
| `InterestTest`: 1164.87×15 → 14.56; −100.00×15 → −1.25 (`0000000012N`); fractional remainder truncated; overflow wraps | 4 |
| `RulesTest`: rate 0 → no transaction; rate ≠ 0 with bal 0 → transaction; counter spans accounts; ts format | 5, 7 |
| `AbendTest`: DEFAULT fallback; missing DEFAULT, account or xref → RC 16, exact SYSOUT lines, no outputs | 6 |
| `CharacterizationTest`: D1 last account unchanged; D2 zero-interest account has its cycle fields zeroed | 8 |
| `legacy/tests/cbact04c-edge.sh`: crafted inputs run through both COBOL and Java, outputs diffed | 4, 5, 6 (differential proof) |
