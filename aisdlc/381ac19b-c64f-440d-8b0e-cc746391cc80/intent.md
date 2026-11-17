# CR-1 Intent — INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation runs only as COBOL on the mainframe (`app/jcl/INTCALC.jcl` → `app/cbl/CBACT04C.cbl`). That blocks the move of batch work off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that can run in a container. Given the same inputs, it produces byte-identical interest transactions (`TRANSACT`) and an updated account master (`ACCTFILE`). Legacy behaviour is kept exactly, defects included. Each defect is pinned by a test and flagged for a separate decision.

## What the legacy code does today

### Job and files

The job runs `EXEC PGM=CBACT04C,PARM='2022071800'` (INTCALC.jcl:22). The PARM is a 10-character date prefix used for transaction ids.

| DD | Access | Org / LRECL | Key | Layout |
|---|---|---|---|---|
| `TCATBALF` | input, sequential (cbl:28-32, 236) | KSDS / 50 | acct(11)+type(2)+cat(4), cols 1-17 | `CVTRA01Y`: `TRAN-CAT-BAL` S9(9)V99 at cols 18-28 |
| `XREFFILE` | input, random by **alternate key** acct-id (cbl:34-39, 394-395) | KSDS / 50 | card(16). AIX acct(11) at col 26 | `CVACT03Y`: card number cols 1-16 |
| `DISCGRP` | input, random (cbl:47-51, 272) | KSDS / 50 | group(10)+type(2)+cat(4) | `CVTRA02Y`: `DIS-INT-RATE` S9(4)V99 at cols 17-22 |
| `ACCTFILE` | I-O, random read + REWRITE (cbl:41-45, 291) | KSDS / 300 | acct(11) | `CVACT01Y` |
| `TRANSACT` | output, new (cbl:53-56, 309; JCL `DISP=NEW`) | sequential / 350 | — | `CVTRA05Y` |

`XREFFIL1` (the AIX path) is not opened by the program. The harness serves the alternate key directly.

### Processing steps

1. Open all five files. Any status other than `00` displays an error and abends (cbl:182-186, 234-323).
2. Read `TCATBALF` sequentially in key order, so the records arrive grouped by account. For each record: count it and `DISPLAY` the 50-byte record (cbl:190-193).
3. **Account break** (cbl:194-206): when the account id changes, rewrite the previous account (from the second break onwards). Then reset `WS-TOTAL-INT=0`, read `ACCTFILE` by acct id (cbl:372) and read `XREFFILE` by alternate acct id (cbl:393).
4. **Rate lookup** (cbl:210-213, 415-460): key = `ACCT-GROUP-ID` + tran type + tran cat.
   - On status `23`: display `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` (cbl:418-419), then re-read with group `DEFAULT` (cbl:437-438). If that also fails, the program abends (cbl:446-458).
5. If the rate is not 0 (cbl:214):
   - **Interest** = `TRAN-CAT-BAL * DIS-INT-RATE / 1200` into `S9(9)V99`, **truncated, not rounded** (cbl:464-465). It is added to `WS-TOTAL-INT` (cbl:467).
   - **Write one transaction** (cbl:473-515):

     | Field | Value |
     |---|---|
     | `TRAN-ID` | PARM-DATE + 6-digit running suffix (cbl:474-480) |
     | type / cat | `01` / `0005` |
     | source | `System` |
     | desc | `Int. for a/c ` + ACCT-ID (cbl:485-489) |
     | amount | the interest |
     | merchant id | 0 |
     | merchant name / city / zip | spaces |
     | card | `XREF-CARD-NUM` |
     | orig/proc timestamps | `CURRENT-DATE` as `YYYY-MM-DD-HH.MM.SS.hh0000` (cbl:613-626) |
     | 20-byte filler | spaces (never initialised; see README) |

   - Run `1400-COMPUTE-FEES`. It is empty (cbl:518-520).
6. **Account update** `1050` (cbl:350-370): `ACCT-CURR-BAL += WS-TOTAL-INT`, set cycle credit and cycle debit to 0, then REWRITE.
7. Close all files, display the END line, return RC 0 (cbl:224-232).

Abends (cbl:628-632) call `CEE3ABD` with code 999. The harness turns this into exit code 16.

### Observed behaviour (harness run, this session)

- `legacy/harness/run.sh CBACT04C`: all 4 outputs are byte-identical to `legacy/golden/CBACT04C/`.
- **Stock data.** `tcatbal.txt` has 50 records, all type 01 / cat 0001, all with balance 0.
  - Output: 50 transactions with amount `0000000000{`, 152 SYSOUT lines, RC 0.
  - The rate is 1.50 ≠ 0, so the transactions are still written.
  - `ACCTFILE` comes out identical to `acctdata.txt`.
- **`CBACT04C-after-CBTRN02C`.** Input is 100 TCATBAL records: 50 of type 01/0001 and 50 of type 03/0001.
  - Output: 50 transactions (ids `2022071800000001`…`…050`), 302 SYSOUT lines, 100× default-group fallback, RC 0.
  - Only the type 01 records produce transactions. `DEFAULT/03/0001` has rate 0, so it is skipped.
  - Worked example, account 1: 1164.87 × 15.00 / 1200 = 14.560875, truncated to **14.56** (`0000000145F`). The balance goes from 1288.10 to **1302.66** (`00000013026F`), and the cycle fields are zeroed.

## Scope

**In scope**
- Java 21 Maven project `modernized/cbact04c/`, using JUnit 5 only.
- It reads and writes the harness's canonical text form: fixed width, LF line endings, overpunch signs. It tolerates CRLF line endings and short lines on input.
- The PARM date is an argument. The "current timestamp" can be injected so runs are deterministic.
- Outputs: `TRANSACT`, the full `ACCTFILE` in key order, SYSOUT lines, and the return code.
- Equivalence tests against both golden scenarios. Characterization tests for each defect listed below.

**Out of scope**
- Fixing any defect, including the last-account bug.
- Implementing fees (`1400`; that is CR-2).
- VSAM/EBCDIC binary I/O.
- JCL or scheduler changes, CBTRN02C, and container image or deploy plumbing (later stages).

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbact04c/` passes on Java 21. The only dependency is JUnit 5.
2. Scenario `CBACT04C`: inputs `app/data/ASCII`, PARM `2022071800`, timestamp `2022-07-18 00:00:00.00`. `TRANSACT` and `ACCTFILE` are byte-identical to `legacy/golden/CBACT04C/`.
3. Scenario `CBACT04C-after-CBTRN02C`: inputs are `ACCTFILE`/`TCATBALF` from `legacy/golden/CBTRN02C/`, everything else stock. `TRANSACT` and `ACCTFILE` are byte-identical to golden.
4. For both scenarios, SYSOUT lines match golden `SYSOUT.txt` and the return code is 0.
5. Interest is truncated, not rounded. A test pins 1164.87 × 15.00 → 14.56.
6. Defect D1 (last account not rewritten) is pinned by a test. Account `00000000050` is unchanged in scenario 3 even though transaction `2022071800000050` charges it 18.77.
7. Rate 0 writes no transaction, and a missing group falls back to `DEFAULT`. A missing `DEFAULT` row, account or xref gives an abend equivalent: a non-zero exit code (16) plus the same error DISPLAY text.
8. The program runs as `java -jar` with configurable input/output file locations and no mainframe dependencies.

## Risks and findings

| # | Finding | Evidence | Treatment |
|---|---|---|---|
| D1 | **Last account's interest is never written back.** The `ELSE PERFORM 1050` at EOF is dead code, because the loop test runs before the body. | cbl:188-221. Scenario 3 account 50: balance stays `00000019458G` and cycle credit/debit stay 1501.75 / -47.88, byte-identical to `golden/CBTRN02C/ACCTFILE.txt`. Meanwhile `TRANSACT` record 50 charges `0000000187G` (18.77). 49 of 50 accounts change. | Preserve and pin. **Flag for a separate fix CR** (customer ledger and balance disagree). |
| D2 | **Group lookup uses `ACCT-GROUP-ID`, which is blank in all sample accounts** (the `A000000000` code sits in `ACCT-ADDR-ZIP`). Every lookup falls back to `DEFAULT`. | 100× `DISCLOSURE GROUP RECORD MISSING` in scenario 3 SYSOUT | Preserve. This is a data issue, worth noting to the business. |
| D3 | Interest is **truncated** toward zero (`COMPUTE` without `ROUNDED`). | cbl:464; account 1: 14.560875 → 14.56 | Preserve (use BigDecimal with RoundingMode.DOWN). |
| D4 | `1400-COMPUTE-FEES` is empty, so no fees are charged. | cbl:518-520 | Preserve (CR-2). |
| D5 | Timestamps come from the wall clock, not the PARM date. Re-runs on another day give different `TRAN-ORIG/PROC-TS`. | cbl:613-626 | Preserve. The clock is injectable for tests. |
| D6 | Silent overflow. The transaction suffix is `9(6)` and wraps after 999,999. `ADD` to `ACCT-CURR-BAL` has no `ON SIZE ERROR`. | cbl:173, 352, 474 | Preserve (modulo / high-order truncation). Unreachable at current volumes. |
| D7 | Wrong error text: a DISCGRP open failure says `ERROR OPENING DALY REJECTS FILE`. | cbl:281 | Preserve text. |
| D8 | A zero-amount transaction is written whenever the rate is non-zero and the balance is 0. | stock scenario: 50× `0000000000{` | Preserve. |
| R1 | Neither golden scenario has a negative category balance, a positive-rate non-DEFAULT group, or several cards per account. Equivalence there rests on unit tests, not golden data. | `cut` over golden inputs: 0 negative amounts, 0 duplicate xref acct ids | Cover with targeted unit tests (negative truncation, overpunch `}`/`J`-`R`). |
| R2 | If an account has several cards, the xref read takes the first alternate-key duplicate in BDB order. | cbl:394 | Java takes the first in file (card-key) order. Document it as an assumption. |

## Questions for the approver

1. **Should SYSOUT be part of the equivalence bar?** The CR only names `TRANSACT`/`ACCTFILE`. *Default: yes.* The goldens exist, the cost is low, and the log is an ops interface.
2. **How should the Java job get the "current" timestamp?** *Default:* the system clock, with an optional override (`CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss`), which mirrors the harness.
3. **D1 follow-up.** *Default:* open a separate CR to fix the last-account write-back after cut-over, and reconcile any accounts already affected in production.
