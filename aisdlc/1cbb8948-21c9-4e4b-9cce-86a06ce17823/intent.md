# CR-1 Intent: INTCALC (`CBACT04C`) to Java 21

## Problem
The nightly interest calculation (`app/jcl/INTCALC.jcl` → `app/cbl/CBACT04C.cbl`) runs only as COBOL on the
mainframe. That blocks the move of batch workloads to containers.

## Outcome
A plain Java 21 program in `modernized/cbact04c/` that runs in a container. Given the same inputs, it posts
the same interest: its interest transactions (`TRANSACT`) and updated account master (`ACCTFILE`) are
**byte-identical** to the COBOL program's. Known legacy defects stay as they are, are pinned by tests, and are
listed below for a separate decision.

## What the legacy code does today

### Files (JCL `INTCALC.jcl` STEP15, `PARM='2022071800'`)
| DD | Org / access | Rec len | Key | Use | Layout |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | 50 | acct-id(11)+type(2)+cat(4), off 0 len 17 | input, driver | `CVTRA01Y.cpy` |
| `XREFFILE` | KSDS, random by **alt key** acct-id (off 25 len 11) | 50 | card-num(16) | input | `CVACT03Y.cpy` |
| `ACCTFILE` | KSDS, random, `I-O` | 300 | acct-id(11) | read + REWRITE | `CVACT01Y.cpy` |
| `DISCGRP` | KSDS, random | 50 | group-id(10)+type(2)+cat(4) | input, interest rates | `CVTRA02Y.cpy` |
| `TRANSACT` | sequential, `OUTPUT` | 350 | n/a | new interest transactions | `CVTRA05Y.cpy` |

`XREFFIL1` (the AIX path) is not opened by the program. All numeric fields are zoned DISPLAY with a trailing
overpunch sign. There are no COMP or COMP-3 fields in the records.

### Processing (CBACT04C.cbl)
1. Opens all five files (`:182-186`). Any status other than `00` → abend (`9999`, RC 16 under the harness).
2. Reads TCATBALF sequentially in key order (`:325-348`). Status `10` sets EOF. For each record it
   DISPLAYs the 50-byte record to SYSOUT (`:193`).
3. **On an account break** (`TRANCAT-ACCT-ID ≠ WS-LAST-ACCT-NUM`, `:194`):
   - if this is not the first account, it rewrites the previous account (`1050`, `:196`)
   - resets `WS-TOTAL-INT` to 0 (`:200`)
   - reads ACCTFILE by acct-id (`1100`, `:372`) and XREFFILE by alt key acct-id (`1110`, `:393`). Not found →
     `ACCOUNT NOT FOUND: <id>` then abend.
4. Looks up the rate in DISCGRP by key `ACCT-GROUP-ID + TRANCAT-TYPE-CD + TRANCAT-CD` (`:210-213`, `:415`).
   If the key is missing (status `23`), it DISPLAYs `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP
   CODE` and retries with group `DEFAULT` (`:436-439`). If that read also fails → abend (`:443-459`).
5. **If `DIS-INT-RATE ≠ 0`** (`:214`):
   - **Monthly interest** = `TRAN-CAT-BAL × DIS-INT-RATE / 1200` into `S9(9)V99`, **truncated** (no `ROUNDED`)
     (`:464-465`). Evidence: 1164.87 × 15.00 / 1200 = 14.5609 → `0000000145F` = 14.56. Account 2 gives
     29.2496 → 29.24.
   - Adds the result to `WS-TOTAL-INT` (`:467`).
   - Writes one TRANSACT record (`1300-B`, `:473-515`):
     - `TRAN-ID` = PARM-DATE(10) + `WS-TRANID-SUFFIX` 9(6), a run-wide counter starting at 1
     - type `01`, category `0005`, source `System`
     - desc `Int. for a/c <ACCT-ID>`, space-padded
     - amount = monthly interest
     - merchant id 0, merchant name/city/zip spaces
     - card = `XREF-CARD-NUM`
     - `TRAN-ORIG-TS` and `TRAN-PROC-TS` = current clock as `YYYY-MM-DD-HH.MM.SS.hh0000`. This is the run clock,
       not the PARM date (`:619-631`).
     - FILLER is never set, so it is spaces (see README "Known quirks").
   - Performs `1400-COMPUTE-FEES`, which is empty (`:518-520`).
6. `1050-UPDATE-ACCOUNT` (`:350-370`) does `ACCT-CURR-BAL += WS-TOTAL-INT`, sets `ACCT-CURR-CYC-CREDIT` and
   `ACCT-CURR-CYC-DEBIT` to 0, then REWRITEs the record. Any other field is unchanged.
7. Closes all files and DISPLAYs `END OF EXECUTION OF PROGRAM CBACT04C`. RC 0 (`:224-232`).

### Observed behaviour (harness run, matches golden byte for byte)
| Scenario | TCATBAL in | TRANSACT out | ACCTFILE changed | SYSOUT | RC |
|---|---|---|---|---|---|
| `CBACT04C` (stock) | 50, all `01/0001`, balance 0 | 50, all amount 0 | 0 of 50 | 152 lines | 0 |
| `CBACT04C-after-CBTRN02C` | 100: `01/0001` (rate 15.00) and `03/0001` (DEFAULT rate 0 → no tran) | 50, non-zero | 49 of 50 | 302 lines | 0 |

In both scenarios every account's `ACCT-GROUP-ID` is blank, so all 100 lookups fall back to `DEFAULT`.

## Scope
**In**
- A Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only) that reproduces steps 1–7, including SYSOUT
  text and the return code.
- File I/O in the harness's canonical text format: fixed-width lines, LF line endings, overpunch zoned
  decimals. Files are located by DD name.
- Clock injection, so timestamps can be frozen to match golden.
- Equivalence tests against both golden scenarios, plus characterization tests for the defects below.

**Out**
- Fixing any legacy defect, including the empty fees paragraph (CR-2).
- CBTRN02C, CICS/online programs, JCL and scheduler changes, VSAM/EBCDIC binary I/O, container image and
  deploy plumbing (later stages).

## Acceptance criteria
1. `mvn -q verify` in `modernized/cbact04c/` passes on Java 21, with JUnit 5 as the only test dependency.
2. With PARM `2022071800`, a clock frozen at `2022-07-18 00:00:00.00` and the stock inputs, the program
   writes `TRANSACT` (50 records) and `ACCTFILE` (50 records), each byte-identical to
   `legacy/golden/CBACT04C/`.
3. With ACCTFILE and TCATBALF taken from `legacy/golden/CBTRN02C/` and the other inputs from stock data,
   `TRANSACT` and `ACCTFILE` are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. In both scenarios, SYSOUT matches `SYSOUT.txt` (152 / 302 lines) and the exit code is 0. *(Default; see Q1.)*
5. Characterization test: the last account in TCATBAL order is **not** rewritten. Account `00000000050` stays
   at 1945.87 / cycle 1501.75 / -47.88, while its interest tran (18.77) is still written.
6. Characterization test: interest is truncated, not rounded. Interest on a negative balance is negative.
7. Characterization test: a category whose rate is 0 produces no transaction and adds no interest.
8. Characterization test: a missing DISCGRP key falls back to `DEFAULT` and prints both messages. A missing
   account, missing xref, or missing DEFAULT rate ends with RC 16 and `ABENDING PROGRAM`.
9. Running `legacy/harness/golden.sh --check` still passes, and nothing under `legacy/golden`, `legacy/harness`
   or `.github` is modified.

## Risks and findings
| # | Finding | Evidence | Handling |
|---|---|---|---|
| D1 | **Last account never updated.** After EOF the `PERFORM UNTIL` exits before the `ELSE PERFORM 1050` branch can run (`:188-222`), so the last account loses its interest and cycle reset. That interest tran is still posted, so TRANSACT and ACCTFILE disagree. | golden after-CBTRN02C: tran `2022071800000050` = 18.77, but the ACCTFILE row for acct 50 equals `golden/CBTRN02C/ACCTFILE.txt`. 49 of 50 rows change. | Preserve and pin (AC5). **Needs a separate fix decision; this is a money defect.** |
| D2 | `1400-COMPUTE-FEES` is empty. | `:518-520` | Preserve (CR-2). |
| D3 | Interest is truncated toward zero per category, so a fraction of a cent is lost on each one. | `:464` has no `ROUNDED`. Truncation is visible in the tran amounts. | Preserve and pin (AC6). Business to confirm the intent. |
| D4 | The tran-ID suffix is `9(6)` with no size check, so it would wrap after 999,999 trans and produce duplicate IDs. | `:173`, `:474` | Preserve. Unreachable at current volumes. |
| D5 | Timestamps come from the wall clock, not the PARM date. A rerun gives different TRANSACT bytes. The hundredths go in a field named "MIL". | `:619-631` | Preserve and inject the clock. |
| D6 | Sample data puts the group code in `ACCT-ADDR-ZIP` and leaves `ACCT-GROUP-ID` blank, so every lookup uses `DEFAULT`. | all 50 accts: cols 103-122 = `A000000000` + blanks | This is a data issue, not code. The account-specific rate path (the `00` read) is **not covered by golden** and is only unit tested. |
| D7 | The account break compares numeric `9(11)` with `X(11)`, and the first-time flag hides the first break. If one acct-id appears in non-adjacent records, it would be read and rewritten twice. | `:194`. KSDS key order prevents this. | Java must process TCATBAL in key order. |
| R1 | Overpunch sign handling and fixed-width padding are the main byte-identity risk. `-0` (`}`) vs `+0` (`{`) must follow COBOL rules: results are written as `{` for zero. | README quirks | Codec unit tests, plus the golden diff. |
| R2 | Truncation in `ACCT-CURR-BAL S9(10)V99` and `TRAN-AMT S9(9)V99` on overflow. | COBOL truncates high-order digits silently. | Replicate with BigDecimal plus explicit modulo; pin with a test. |

## Questions for the approver
1. **Should SYSOUT and the RC match too, or only TRANSACT and ACCTFILE?** *Default: match both.* It is cheap,
   and operations keep the same job log and abend semantics.
2. **What I/O format should the container use?** *Default: the harness's canonical text files, located by DD
   name (e.g. env `DD_ACCTFILE`), with ACCTFILE rewritten in place in key order.* VSAM/EBCDIC binary is out of
   scope.
3. **Who decides D1 and D3?** *Default: raise them as separate change requests for the Cards business owner.
   This CR preserves both.*
