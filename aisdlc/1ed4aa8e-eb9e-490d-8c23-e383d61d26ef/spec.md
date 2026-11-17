# CR-1 Spec: `CBACT04C` in Java 21 (behaviour-preserving)

Legacy behaviour, files and defects: [intent.md](intent.md). This spec fixes **how** the Java code must reproduce it.
Approver defaults are taken: SYSOUT is matched line for line (secondary bar), I/O is canonical text, and D1 is preserved.

## Inputs / outputs

Each record is one line, fixed width. On read: strip a trailing `\r`, then right-pad with spaces to LRECL (so `cardxref.txt` at 36 bytes becomes 50). On write: exactly LRECL bytes followed by `\n`.

| DD | Copybook | LRECL | Java access |
|---|---|---|---|
| `TCATBALF` | `app/cpy/CVTRA01Y.cpy` | 50 | read in file order (already key order) |
| `XREFFILE` | `app/cpy/CVACT03Y.cpy` | 50 | map by acct id, cols 26-36; **first** match wins |
| `ACCTFILE` | `app/cpy/CVACT01Y.cpy` | 300 | `LinkedHashMap` by cols 1-11, written back in key order |
| `DISCGRP` | `app/cpy/CVTRA02Y.cpy` | 50 | map by cols 1-16 |
| `TRANSACT` | `app/cpy/CVTRA05Y.cpy` | 350 | written in write order |
| SYSOUT | n/a | var | stdout, one DISPLAY per line, trailing spaces kept |

Fields the program reads or writes (1-based cols; all signed fields are zoned with a trailing overpunch):

| Field | Cols | PIC | Java |
|---|---|---|---|
| `TRANCAT-ACCT-ID` / `-TYPE-CD` / `-CD` | 1-11 / 12-13 / 14-17 | X(11) 9(11), X(2), 9(4) | `String` (raw bytes) |
| `TRAN-CAT-BAL` | 18-28 | S9(9)V99 | `BigDecimal` scale 2 |
| `XREF-CARD-NUM` | 1-16 | X(16) | `String` |
| `DIS-INT-RATE` | 17-22 | S9(4)V99 | `BigDecimal` scale 2 |
| `ACCT-CURR-BAL` | 13-24 | S9(10)V99 | `BigDecimal`, re-encoded |
| `ACCT-CURR-CYC-CREDIT` / `-DEBIT` | 79-90 / 91-102 | S9(10)V99 | set to `00000000000{` |
| `ACCT-GROUP-ID` | 113-122 | X(10) | `String` (blank in sample data) |
| TRANSACT record | 1-350 | layout in `CVTRA05Y` | built from a 350-space buffer |

Every other byte of the ACCTFILE record is copied through unchanged.

## Processing rules (reproduce exactly)

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`.
2. For each TCATBALF record: print the 50-byte record as is.
3. Account break (acct id ≠ last; last starts as spaces): if this is not the first account, rewrite the held account (rule 7). Then set `total = 0`, look up the account (missing → abend A1) and the xref (missing → abend A2).
4. Rate key = `ACCT-GROUP-ID(10) + TYPE-CD(2) + CAT-CD(4)`. If it misses, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with `DEFAULT` padded to 10. If that also misses → abend A3.
5. If `rate ≠ 0`: `monthly = trunc2(bal × rate / 1200)`. trunc2 = exact quotient, `RoundingMode.DOWN` (toward zero) to scale 2. Then apply high-order truncation to S9(9)V99: keep `|v| mod 10^9`, sign kept. Then `total = fit9(total + monthly)`.
6. Write a TRANSACT record (positions from `CVTRA05Y`):
   - `TRAN-ID` = PARM(10) + `seq` as 6 digits. `seq` is incremented first, is run-wide, and wraps mod 10^6 (D5).
   - `TYPE-CD` = `01`. `CAT-CD` = `0005`. `SOURCE` = `System` padded to 10.
   - `DESC` = `Int. for a/c ` + acct id, space-padded to 100.
   - `AMT` = encode S9(9)V99 of `monthly`. `MERCHANT-ID` = `000000000`. Merchant name, city and zip are spaces.
   - `CARD-NUM` = xref card.
   - `ORIG-TS` = `PROC-TS` = clock as `yyyy-MM-dd-HH.mm.ss.` + hundredths(2) + `0000`. The clock is read once per record.
   - `FILLER` (331-350) = spaces.
7. Account rewrite: `ACCT-CURR-BAL = fit10(bal + total)`, both cycle fields = +0, then replace the record in the map.
8. At EOF: do **not** rewrite the held account (D1). Write TRANSACT and ACCTFILE, print `END OF EXECUTION OF PROGRAM CBACT04C`, exit 0.

**Zoned encode/decode.** The last byte carries the digit and sign: `{`/`A`-`I` = +0..9, `}`/`J`-`R` = -0..9. Decoding a plain digit in the last byte is treated as positive. Encoding uses `{`/`A`-`I` for v ≥ 0 and `}`/`J`-`R` for v < 0. A result of zero is written as `{` (+0). The design assumes this: GnuCOBOL does not keep −0 on store. This is verified by the differential test (plan T5).

**Abends.** Print the lines below, write the outputs as they stand (ACCTFILE with the rewrites done so far, TRANSACT partial), print `CEE3ABD: ABEND U0999` to stderr, and exit 16. Nothing is printed after the abend.

| Id | SYSOUT lines |
|---|---|
| A1 account | `ACCOUNT NOT FOUND: <acct>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM` |
| A2 xref | `ACCOUNT NOT FOUND: <acct>`, `ERROR READING XREF FILE`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM` |
| A3 default rate | the 2 MISSING lines, then `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM` |
| Open / read / write I/O error | the paragraph's `ERROR …` text (CBL:234-323, e.g. D7 `ERROR OPENING DALY REJECTS FILE` for DISCGRP), `FILE STATUS IS: NNNN<status>`, `ABENDING PROGRAM` |

Missing or unreadable input files map to open errors. Their status is `0035`. The status for an unwritable output file is not pinned.

## Edge cases

- Rate 0 (e.g. 03/0001 in after-CBTRN02C): no txn, no seq increment, total unchanged. The account is still rewritten with its cycle fields zeroed.
- Negative balance: monthly is negative and truncated toward zero (e.g. −10.00 × 15 / 1200 = −0.125 → −0.12, `0000000001K`). Tiny negatives become `{`.
- Overflow (D4): `monthly`, `total` and `ACCT-CURR-BAL` silently drop high-order digits and never error.
- Empty TCATBALF: only the START and END lines, empty TRANSACT, ACCTFILE unchanged, exit 0.
- CRLF or short lines on any input: normalised as above.

## Preserved defects (fix later, deliberately)

- D1: the last account is never rewritten, so its interest is lost and its cycle fields are not reset.
- D2: interest is truncated, not rounded.
- D3: no fees (`1400-COMPUTE-FEES` is a no-op).
- D4: no size-error handling, so amounts overflow silently.
- D5: the 6-digit transaction sequence wraps at 10^6.
- D6: timestamps come from the wall clock, not the PARM, with hundredths precision only.
- D7: the DISCGRP open error says `DALY REJECTS FILE`.

## Acceptance tests (→ intent AC)

| AC | Test |
|---|---|
| 1 | `mvn -f modernized/cbact04c test` green. The pom has only JUnit 5 and Surefire. |
| 2, 3 | `GoldenEquivalenceTest`: both scenarios, byte-compare TRANSACT and ACCTFILE, and SYSOUT plus exit code vs `RC.txt` |
| 4 | `AbendTest`: A1, A2, A3 and a missing input file → exit 16 and the exact SYSOUT tail |
| 5 | `InterestTest.truncates`: 1164.87 × 15.00 / 1200 = 14.56 |
| 6 | `DefectCharacterizationTest.lastAccountNotRewritten`: acct 50 unchanged, txn `2022071800000050` = 18.77 |
| 7 | `InterestTest.zeroRate`, plus all-zero-rate account (not last) gets its cycle fields zeroed |
| 8 | `InterestTest.defaultFallback` and `directHit` (synthetic DISCGRP with a real group, no MISSING lines) |
| 9 | `InterestTest.tranIdAndClock`: ids run across accounts, timestamps follow the injected clock and not the PARM |
| 4/D4, R2 | `ZonedTest`: overpunch round trip, −0, overflow truncation, negative truncation |
| 10 | CI `golden.sh --check`, and `git diff --stat baseline -- legacy/golden legacy/harness` is empty |
