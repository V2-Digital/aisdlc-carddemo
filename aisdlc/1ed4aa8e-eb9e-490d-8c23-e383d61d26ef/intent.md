# CR-1 Intent: rewrite INTCALC (`CBACT04C`) in Java 21

## Problem / outcome

**Problem.** Interest on card balances is calculated every night by a COBOL batch job, `INTCALC` running `CBACT04C`. The job runs on the mainframe, which the bank is moving batch work off. As long as it stays COBOL, it ties the nightly cycle to mainframe capacity and skills.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. It does exactly what the COBOL job does today. For the same inputs it writes byte-identical interest transactions (`TRANSACT`) and an identical updated account master (`ACCTFILE`). This is shown on both golden scenarios. Known defects are kept as they are and listed for a separate decision.

## What the legacy code does today

Source: `app/cbl/CBACT04C.cbl` (CBL), run by `app/jcl/INTCALC.jcl` step `STEP15`, `PARM='2022071800'`.

### Files

| DD | Organisation / access | LRECL | Key | Use | Copybook |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential read | 50 | acct(11)+type(2)+cat(4), cols 1-17 | input, the driver | `CVTRA01Y` (CBL:28, 97) |
| `XREFFILE` | KSDS, random, by **alt key** acct id cols 26-36 | 50 | card num cols 1-16 | input | `CVACT03Y` (CBL:34-39, 102) |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct id cols 1-11 | read + REWRITE | `CVACT01Y` (CBL:41, 112, 291) |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4), cols 1-16 | input (interest rates) | `CVTRA02Y` (CBL:47, 107) |
| `TRANSACT` | sequential, OUTPUT (JCL `DISP=NEW`, RECFM=F) | 350 | none | output | `CVTRA05Y` (CBL:53, 117, 309) |

Numeric fields that matter:
- `TRAN-CAT-BAL` S9(9)V99, cols 18-28.
- `DIS-INT-RATE` S9(4)V99, cols 17-22.
- `ACCT-CURR-BAL` S9(10)V99, cols 13-24.
- `ACCT-CURR-CYC-CREDIT` and `-DEBIT`, cols 79-90 and 91-102.
- `ACCT-GROUP-ID` X(10), cols 113-122.
- `TRAN-AMT` S9(9)V99, cols 133-143.

All of these are zoned DISPLAY with a trailing overpunch sign. None are COMP-3.

### Processing steps

1. Display `START OF EXECUTION...` and open all five files. Any failed open abends (CBL:181-186, 234-323).
2. Read `TCATBALF` sequentially in key order. Each record is DISPLAYed to SYSOUT (CBL:190-193, 326).
3. **On an account break** (`TRANCAT-ACCT-ID` differs from the previous one, CBL:194):
   - If this is not the first account, rewrite the previous account (step 6).
   - Reset the running total to 0.
   - Read `ACCTFILE` by acct id (CBL:372-391).
   - Read `XREFFILE` by alt key acct id to get the card number (CBL:393-413).
4. **Rate lookup** with key `ACCT-GROUP-ID + TRANCAT-TYPE-CD + TRANCAT-CD` (CBL:210-213, 415-440). If the key is not found (status 23), DISPLAY two lines (`DISCLOSURE GROUP RECORD MISSING`, `TRY WITH DEFAULT GROUP CODE`) and re-read with group `DEFAULT` (CBL:436-460). Any other failure abends.
5. **If the rate is not 0** (CBL:214-217):
   - Calculate `monthly = bal * rate / 1200`. The result is **truncated** to 2 decimals: the COMPUTE has no ROUNDED and no ON SIZE ERROR (CBL:464-465).
   - Add `monthly` to the account total (CBL:467).
   - Write one transaction (CBL:473-515):
     - `TRAN-ID` = PARM date (10) + 6-digit running sequence, one sequence for the whole run (CBL:474-479).
     - Type `01`, category `0005`, source `System`.
     - Description `Int. for a/c <ACCT-ID>`.
     - Amount = `monthly`. Merchant id 0, merchant name/city/zip blank.
     - Card = xref card number.
     - `TRAN-ORIG-TS` and `TRAN-PROC-TS` = `FUNCTION CURRENT-DATE` formatted as `YYYY-MM-DD-HH.MM.SS.hh0000` (CBL:613-624). This is the clock, **not** the PARM.
   - Perform `1400-COMPUTE-FEES`, which is empty (CBL:518-520).
6. **Account update** (CBL:350-370): `ACCT-CURR-BAL += total`, set cycle credit and cycle debit to 0, then REWRITE.
7. At EOF, close the files, display `END OF EXECUTION...` and return RC 0 (CBL:224-232). Any I/O error displays `FILE STATUS IS: NNNNxxxx` and `ABENDING PROGRAM`, then calls `CEE3ABD`. Under the harness that exits with 16 (CBL:628-648).

### Observed behaviour (harness, frozen clock `2022-07-18 00:00:00.00`)

`legacy/harness/run.sh CBACT04C /tmp/out04` gave RC=0 and output byte-identical to `legacy/golden/CBACT04C`:

| Scenario | TCATBAL in | TRANSACT out | ACCTFILE changed | SYSOUT |
|---|---|---|---|---|
| `CBACT04C` (stock) | 50 | 50, all amount 0 | 0 of 50 (identical to `acctdata.txt`) | 152 lines |
| `CBACT04C-after-CBTRN02C` | 100 (cats 01/0001 and 03/0001 per account) | 50 (one per account, from 01/0001 at 15.00%; 03/0001 has 0% so no txn) | 49 of 50 | 302 lines |

Worked example, account 1:
- Category balance 1164.87 × 15.00 / 1200 = 14.560875, written as `0000000145F` = **14.56** (truncated).
- New balance 1288.10 + 14.56 = **1302.66** (`00000013026F`).
- Cycle credit/debit go from 1164.87 / -70.77 to 0.

Every rate lookup in the sample data falls back to `DEFAULT`, because `ACCT-GROUP-ID` is blank (README "Sample data"). SYSOUT therefore has 100 `MISSING` lines in the second scenario.

## Scope

**In**
- Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only).
- It reads and writes the harness's canonical text dataset form: one fixed-width record per line, LF, overpunch signs, KSDS in key order, CRLF tolerated on input.
- It takes the PARM date as an argument and an injectable clock for the timestamps.
- Exact COBOL decimal semantics: truncation, overpunch, and high-order truncation on overflow.
- An equivalence test against both golden scenarios.
- Characterization tests that pin the defects below.
- Run instructions for a container.

**Out**
- Fixing any legacy defect or implementing fees (CR-2).
- CBTRN02C, CICS screens, other jobs.
- Real VSAM/EBCDIC binary I/O, a scheduler or JCL change, and building or publishing a container image. A Dockerfile/run note is enough.

## Acceptance criteria

1. `mvn -f modernized/cbact04c test` passes on Java 21 with no dependencies other than JUnit 5 and Surefire.
2. For scenario `CBACT04C`, running on the stock inputs from `app/data/ASCII` with PARM `2022071800` and clock `2022-07-18T00:00:00.00Z` produces `TRANSACT.txt` and `ACCTFILE.txt` byte-identical to `legacy/golden/CBACT04C/`.
3. For scenario `CBACT04C-after-CBTRN02C`, running with `ACCTFILE` and `TCATBALF` taken from `legacy/golden/CBTRN02C/` produces byte-identical `TRANSACT.txt` and `ACCTFILE.txt`.
4. Exit code is 0 on success, which matches `RC.txt`. A missing account, xref or DEFAULT rate, or any I/O failure, gives exit 16 after the same `ABENDING PROGRAM` message.
5. Interest is truncated, not rounded. A unit test pins 1164.87 × 15.00 / 1200 = 14.56.
6. Characterization test: the **last account's** interest is written to TRANSACT, but its ACCTFILE record is unchanged (account `00000000050`, txn `2022071800000050`, amount 18.77).
7. A category with rate 0 writes no transaction and adds nothing. An account whose categories are all 0% still gets its cycle fields zeroed (if it is not the last account).
8. Rate fallback: an unknown group/type/cat key uses `DEFAULT` with the same type/cat.
9. Transaction ids are `PARM + 6-digit sequence` across the whole run, and timestamps come from the clock, not the PARM.
10. `legacy/golden/` and `legacy/harness/` are unchanged, and `golden.sh --check` still passes.

## Risks and findings

| # | Finding | Evidence | Treatment |
|---|---|---|---|
| D1 | **Last account never rewritten.** The `ELSE PERFORM 1050-UPDATE-ACCOUNT` at EOF cannot run, because the `UNTIL` is tested before the body. The last account's interest is posted to TRANSACT but not to its balance, and its cycle fields are not reset. | CBL:188-222. In golden after-CBTRN02C, acct 50 is identical to `golden/CBTRN02C/ACCTFILE.txt` (balance 1945.87) while txn `…0050` charges 18.77. 49 of 50 records changed. | Preserve and pin (AC6). Flag for a fix CR. **Financial impact: interest is lost on one account every night.** |
| D2 | Interest is truncated, not rounded. | CBL:464-465, acct 1 example above | Preserve (AC5). Business to confirm the intended rounding. |
| D3 | `1400-COMPUTE-FEES` is empty, so no fees are ever charged. | CBL:518-520 | Preserve. This is CR-2. |
| D4 | No `ON SIZE ERROR`: `WS-MONTHLY-INT` and `ACCT-CURR-BAL` silently lose high-order digits on overflow. | CBL:464-467, 352 | Preserve the COBOL truncation and pin it with a unit test. This is not reachable with sample data. |
| D5 | The 6-digit transaction sequence would wrap or truncate after 999,999 interest txns in one run. | CBL:173, 474-479 | Preserve. Note only. |
| D6 | Timestamps use the wall clock, not the business date PARM, so a rerun of a past date gets today's timestamps. Only hundredths precision (`hh0000`). | CBL:613-624 | Preserve. The clock is injectable for tests. |
| D7 | Misleading error text: the DISCGRP open failure says `ERROR OPENING DALY REJECTS FILE`. | CBL:281 | Preserve the text. Cosmetic. |
| D8 | Sample data gap: `ACCT-GROUP-ID` is blank, so every lookup hits `DEFAULT`, and the non-DEFAULT path is never exercised by golden data. | README, SYSOUT 100× `MISSING` | Cover the direct-hit and ZEROAPR paths with synthetic unit tests. |
| R1 | Byte-identity traps: overpunch signs (`-0` is `}`), CRLF in `tcatbal.txt`, 36-byte `cardxref.txt` lines padded to 50, trailing spaces, and uninitialised `TRAN-DESC` tail and FILLER being spaces. | README "Known quirks" | Design must handle each one explicitly. |
| R2 | Negative category balances (credit interest) are allowed by the code but do not occur in golden data (0 negative amounts). | `cut -c143` of golden TRANSACT | Unit test with COBOL truncation toward zero. |

## Questions for the approver

1. **Is SYSOUT part of the equivalence bar?** The CR names only TRANSACT and ACCTFILE. *Default:* also match `SYSOUT.txt` line for line (it is cheap and helps operators), but treat it as a secondary test.
2. **I/O format.** *Default:* Java reads and writes the canonical text unloads used by the harness and golden, with DD-style paths passed as arguments or env vars. Mapping to real VSAM/database storage is a later platform decision.
3. **D1 (last-account bug).** *Default:* preserve it in this CR as instructed, and raise a separate fix CR, because it affects customer balances nightly.
