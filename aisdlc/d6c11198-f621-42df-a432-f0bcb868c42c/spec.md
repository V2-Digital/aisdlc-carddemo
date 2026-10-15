# CR-1 Spec: `CBACT04C` in Java 21 (behaviour-preserving)

Legacy behaviour, file inventory and findings F1-F10 are in [intent.md](intent.md). This spec only pins what the Java must reproduce byte for byte.

## Inputs / outputs

Canonical text form (`legacy/README.md` "Canonical text form"): one fixed-width record per line, LF. On read, strip a trailing `\r` and right-pad short lines with spaces to LRECL (F10). On write, emit exactly LRECL bytes + `\n`.

| DD | Copybook | Dir | Fields used (offset, len, PIC → Java) |
|---|---|---|---|
| TCATBALF | `app/cpy/CVTRA01Y.cpy` | in | acct-id 0,11 X → `String`; type 11,2; cat 13,4; `TRAN-CAT-BAL` 17,11 S9(9)V99 → `BigDecimal` scale 2 |
| XREFFILE | `app/cpy/CVACT03Y.cpy` | in | card-num 0,16; acct-id 25,11 (alt key) |
| ACCTFILE | `app/cpy/CVACT01Y.cpy` | in+out | acct-id 0,11; `CURR-BAL` 12,12 S9(10)V99; `CYC-CREDIT` 78,12 and `CYC-DEBIT` 90,12 S9(10)V99; `GROUP-ID` 112,10 X. All other bytes copied through unchanged |
| DISCGRP | `app/cpy/CVTRA02Y.cpy` | in | key 0,16 (group 10 + type 2 + cat 4); `DIS-INT-RATE` 16,6 S9(4)V99 |
| TRANSACT | `app/cpy/CVTRA05Y.cpy` | out | whole 350-byte record, see R6 |

Signed zoned fields: last byte is an overpunch (`{`/`A-I` = +0..9, `}`/`J-R` = −0..9). Encode zero and positives with `{A-I`, negatives with `}J-R`.

The program receives a 10-char PARM date and a `Clock`. CLI: `java -jar cbact04c.jar <PARM> <IN_DIR> <OUT_DIR>`. It reads `<IN_DIR>/<DD>.txt` and writes `<OUT_DIR>/ACCTFILE.txt` and `TRANSACT.txt`. SYSOUT goes to stdout and the exit code is the process status.

## Processing rules (in order)

- **R1 Load.** Load the KSDS inputs into sorted maps keyed by the primary key. XREF is also indexed by acct-id (alt key). On duplicates, the first in primary-key order wins. Print `START OF EXECUTION OF PROGRAM CBACT04C`.
- **R2 Drive.** Read TCATBALF in key order. Print each 50-byte record verbatim.
- **R3 Account break.** When the acct-id differs from the last one:
  - If this is not the first break, rewrite the previous account (R7).
  - Set total = 0.
  - Look up ACCTFILE by acct-id, then XREF by acct-id. A miss on either is an abend (E1, E2).
- **R4 Rate.** Look up key = `GROUP-ID` + type + cat.
  - On a miss, print `DISCLOSURE GROUP RECORD MISSING` then `TRY WITH DEFAULT GROUP CODE`, and retry with `"DEFAULT   "` (padded to 10) + type + cat.
  - A miss on the retry is an abend (E3).
- **R5 Interest.** Only if the rate ≠ 0:
  - `monthly = bal × rate / 1200`, computed exactly, then `setScale(2, DOWN)` (F2), then reduced modulo 10^9 keeping the sign (S9(9)V99, F6).
  - `total = (total + monthly)` mod 10^9.
  - Then R6. Fees do nothing (F5).
- **R6 TRANSACT record** (offset:len):
  - `0:16` PARM + suffix. The suffix is a 6-digit counter: `(n+1) mod 10^6`, zero-padded (F6).
  - `16:2` `01`; `18:4` `0005`; `22:10` `System` padded.
  - `32:100` `Int. for a/c ` + acct-id, space-padded (F7).
  - `132:11` monthly, overpunch.
  - `143:9` `000000000`; `152:110` spaces.
  - `262:16` XREF card-num.
  - `278:26` and `304:26` the clock as `yyyy-MM-dd-HH.mm.ss.hh0000`. hh is hundredths, truncated. Read the clock once per record (F8).
  - `330:20` spaces (F7).
- **R7 Rewrite.** `CURR-BAL = (CURR-BAL + total)` mod 10^10, sign kept. `CYC-CREDIT = CYC-DEBIT = 0` (encoded `00000000000{`). Do this even when total = 0 (F4).
- **R8 EOF.** Stop the loop **without rewriting the last account** (F1). Write ACCTFILE in primary-key order, including accounts not in TCATBAL, unchanged. Print `END OF EXECUTION OF PROGRAM CBACT04C`. Exit 0.

## Error paths (abend = exit 16)

Each abend prints the lines below to stdout, then `ABENDING PROGRAM`, then `CEE3ABD: ABEND U0999` to stderr. On abend no output files are guaranteed: tests assert only SYSOUT and the exit code.

| # | Cause | stdout before `ABENDING PROGRAM` |
|---|---|---|
| E1 | acct not in ACCTFILE | `ACCOUNT NOT FOUND: <acct>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023` |
| E2 | acct not in XREF | `ACCOUNT NOT FOUND: <acct>`, `ERROR READING XREF FILE`, `FILE STATUS IS: NNNN0023` |
| E3 | no DEFAULT row | the 2 R4 lines, `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023` |
| E4 | input file missing | `ERROR OPENING …` message for that DD (`CBACT04C.cbl:245-320`), `FILE STATUS IS: NNNN0035` |

`legacy/tests/cbact04c-edge.sh` confirms the exact E1-E3 text against the COBOL. Where it disagrees with this table, the COBOL wins.

## Edge cases

- Rate = 0: no TRANSACT record and no suffix increment. The account is still rewritten (R7).
- Negative balance × positive rate gives negative interest, written with a `}J-R` sign (F9). Truncation is toward zero: −0.0125 → −0.01. A result of exactly 0 is written as `{`; the COBOL test confirms this.
- A group-specific DISCGRP row is used without the warning lines (AC8).
- Overflow wraps silently, modulo, sign kept (F6).

## Preserved defects (fix later, deliberately)

- **F1** The last account's interest is never added to its balance and its cycle fields are not cleared.
- **F2** Interest truncates instead of rounding.
- **F3** The rate lookup uses a blank `ACCT-GROUP-ID`, so every lookup falls back to DEFAULT and logs a warning.
- **F4** Cycle credit/debit are zeroed for every account touched, even with no interest.
- **F5** Fees paragraph is empty.
- **F6** Amount, balance and TRAN-ID suffix overflow silently.
- **F8** Each record reads its own wall-clock timestamp, so a run is not one consistent timestamp.

## Acceptance tests

| AC | Test |
|---|---|
| 1 | `mvn -f modernized/cbact04c test` green. The POM has only junit-jupiter + surefire |
| 2, 4 | `GoldenTest.stock`: stock data, PARM `2022071800`, fixed clock. TRANSACT, ACCTFILE and SYSOUT byte-equal `legacy/golden/CBACT04C/`, exit 0 |
| 3, 4 | `GoldenTest.afterCbtrn02c`: ACCTFILE and TCATBALF from `legacy/golden/CBTRN02C/`, else stock. Equal to `legacy/golden/CBACT04C-after-CBTRN02C/` |
| 5 | `InterestTest`: 2339.97 @ 15.00 → 29.24 (`0000000292D`). Also covers negative values and modulo wrap |
| 6 | `DefectTest.lastAccountNotRewritten`: acct 50 row equals the input, and TRANSACT #50 amount = `0000000187G` |
| 7 | `AbendTest` E1, E2, E3: exit 16 + the SYSOUT lines above |
| 8 | `RateLookupTest`: synthetic group row used, no warning lines |
| 9 | `OverpunchTest`: every digit 0-9 × sign round-trips, including `{` and `}` |
