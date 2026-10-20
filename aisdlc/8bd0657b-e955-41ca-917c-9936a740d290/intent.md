# CR-1 Intent — INTCALC (`CBACT04C`) to Java 21

## Problem
The nightly interest calculation runs only as COBOL on the mainframe (`app/jcl/INTCALC.jcl`, `app/cbl/CBACT04C.cbl`). That keeps the batch workload tied to the mainframe and blocks moving it to containers.

## Outcome
A plain Java 21 program in `modernized/cbact04c/` that a container can run. For the same inputs it produces byte-identical interest transactions (`TRANSACT`) and an updated account master (`ACCTFILE`), as proven on golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Legacy behaviour, defects included, is kept as-is. Any defects are pinned by tests and listed below for a separate decision.

## What the legacy code does today

### Job and files
`INTCALC.jcl:22` runs `EXEC PGM=CBACT04C,PARM='2022071800'`. The PARM is the business date (10 chars) and is used only to build transaction IDs.

| DD | Org / access (`CBACT04C.cbl`) | LRECL | Key | Layout | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential, INPUT (:28, :236) | 50 | acct(11)+type(2)+cat(4), off 0 len 17 | `CVTRA01Y` | driver: one balance per account/type/category |
| `XREFFILE` | KSDS random via **alt key** acct-id (:34, :395) | 50 | card(16); AIX acct off 25 len 11, NONUNIQUE (`XREFFILE.jcl:74-75`) | `CVACT03Y` | card number for the transaction |
| `ACCTFILE` | KSDS random, **I-O** (:41, :291) | 300 | acct(11) | `CVACT01Y` | read and then REWRITE |
| `DISCGRP` | KSDS random, INPUT (:47) | 50 | group(10)+type(2)+cat(4), len 16 | `CVTRA02Y` | interest rate `DIS-INT-RATE S9(4)V99` |
| `TRANSACT` | sequential OUTPUT (:53, :309) | 350 | — | `CVTRA05Y` | new interest transactions |
| `XREFFIL1` | AIX path | — | — | — | not opened by name. The harness serves it via the alt key |

### Processing (main loop `CBACT04C.cbl:188-222`)
1. Display `START OF EXECUTION OF PROGRAM CBACT04C`, then open the 5 files. Any open status other than `00` → abend (:234-323).
2. Read TCATBALF sequentially in key order (:326). Status `10` = EOF. Any other status besides `00` → abend.
3. For each record: increment the count and `DISPLAY` the raw 50-byte record (:193).
4. **Account control break** when the account ID changes (:194):
   - It is not the first record, so `1050-UPDATE-ACCOUNT` runs for the **previous** account (:196, :350).
   - Reset `WS-TOTAL-INT` to 0, then random-read ACCTFILE (:372) and XREFFILE by acct alt key (:393). Not found → `ACCOUNT NOT FOUND: …` and then abend.
5. Rate lookup (`1200`, :415): key = `ACCT-GROUP-ID` + type + cat. Status `23` → display `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE`, then re-read with group `DEFAULT` (:436-459). If DEFAULT is also missing → abend.
6. If rate ≠ 0 (:214):
   - **Interest** `WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200` into `S9(9)V99`, with **no ROUNDED** (truncates toward zero) and no SIZE ERROR (:464). The result is added to `WS-TOTAL-INT`.
   - **Write transaction** (:473-515), built as follows:
     - `TRAN-ID` = PARM-DATE + 6-digit run-wide counter
     - type `01`, cat `0005`, source `System`
     - desc `Int. for a/c ` + ACCT-ID, right-padded with spaces
     - amount = monthly interest; merchant id 0; name, city and zip spaces
     - card = `XREF-CARD-NUM`
     - ORIG-TS = PROC-TS = `YYYY-MM-DD-HH.MM.SS.hh0000` from `CURRENT-DATE` (:613-624)
     - trailing FILLER(20) = spaces (never initialised)
   - `1400-COMPUTE-FEES` is empty (:518-520).
7. **Account update** (`1050`, :350-370): `ACCT-CURR-BAL += WS-TOTAL-INT`, cycle credit = 0, cycle debit = 0, then REWRITE. Non-`00` → abend.
8. At EOF: close all 5 files, display `END OF EXECUTION OF PROGRAM CBACT04C`, RC 0.
9. Abend (`9999`, :628): `ABENDING PROGRAM`, then `CEE3ABD` with code 999. Harness: stderr `CEE3ABD: ABEND U0999`, exit 16. `9910` (:635) formats the file status as `FILE STATUS IS: NNNNxxxx`.

### Observed behaviour (harness run, PARM `2022071800`, clock frozen at `2022-07-18 00:00:00.00`)
- `run.sh CBACT04C` → RC 0. TRANSACT 50, ACCTFILE 50, SYSOUT 152. **Byte-identical to `legacy/golden/CBACT04C`.**
- Stock data has 50 accounts × 1 TCATBAL record each, and every balance is zero. That gives 50 zero-amount transactions, and ACCTFILE equals `acctdata.txt`.
- `CBACT04C-after-CBTRN02C` has 100 TCATBAL records (types `01` and `03` for every account), but only type `01`/cat `0001` has a non-zero DEFAULT rate (15.00%). Type `03` rate = 0, so no transaction is written for it, giving 50 transactions with non-zero amounts. 49 of 50 account records change.
- Every rate lookup misses on the first read and falls back to `DEFAULT` (see F3).

## Scope
**In**
- Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only) reproducing steps 1-9.
- Input: the canonical fixed-width text datasets (the harness/golden format, LF; tolerate CRLF on input as the harness does). Output: the same format.
- Byte-identical `TRANSACT.txt` and `ACCTFILE.txt`. Also `SYSOUT` and the RC (see Q1).
- PARM date as an argument, plus an injectable clock for deterministic timestamps.
- Golden-equivalence tests on both scenarios, plus characterization tests for F1-F6.

**Out**
- Fixing any defect below. Implementing fees (CR-2).
- VSAM/EBCDIC binary I/O, JCL/scheduler changes, CBTRN02C, deployment wiring.

## Acceptance criteria
1. `mvn -q verify` in `modernized/cbact04c/` passes on Java 21 with JUnit 5 as the only dependency.
2. Run with the stock inputs, PARM `2022071800` and clock `2022-07-18T00:00:00.00`, the program produces `TRANSACT.txt` and `ACCTFILE.txt` byte-identical to `legacy/golden/CBACT04C/`.
3. The same holds with ACCTFILE and TCATBALF taken from `legacy/golden/CBTRN02C/`: outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. In both scenarios, the stdout display lines and the exit code match `SYSOUT.txt` / `RC.txt` (0), subject to Q1.
5. Interest truncates toward zero to 2 decimal places. A test pins account `00000000002`: balance 2339.97 × 15 / 1200 = 29.249625 → **29.24** (`0000000292D`).
6. The last account in TCATBALF is **not** rewritten (F1). A test pins account `00000000050`: unchanged vs input, even though transaction `2022071800000050` exists.
7. Group miss → DEFAULT fallback with the two display lines. Missing DEFAULT → abend path (RC 16, `ABENDING PROGRAM`).
8. No transaction is written for rate = 0 records, but the account is still rewritten (cycle fields zeroed) when it is not the last one.
9. Overpunch sign encoding/decoding round-trips all 20 sign characters. Negative amounts are written with `}`/`J`-`R`.
10. A missing account or XREF record → abend path, RC 16.

## Risks and findings
| # | Finding | Evidence | Treatment |
|---|---|---|---|
| F1 | **Last account never rewritten.** After EOF the loop exits before its `ELSE PERFORM 1050` (:219-220) can run, so the last account's interest goes into TRANSACT but never into ACCTFILE, and its cycle fields are not zeroed. | `golden/CBACT04C-after-CBTRN02C`: tran `…0050` charges 18.77 (`0000000187G`), but acct `00000000050` is byte-identical to `golden/CBTRN02C/ACCTFILE.txt` (bal `00000019458G`). Only 49 of 50 records change. | Preserve and pin (AC6). **Customer-impacting**: interest is billed but not posted to the balance. |
| F2 | **Interest truncated, not rounded** (no `ROUNDED`, :464). | In the after-scenario, 28 of 50 transactions would be 1 cent higher with half-up rounding, e.g. acct 2: 29.249625 → 29.24. | Preserve (AC5). Use `BigDecimal` with `RoundingMode.DOWN`. Never use `double`. |
| F3 | **Disclosure group always misses**: the key uses `ACCT-GROUP-ID` (:210), which is blank in the sample data (the group code sits in `ACCT-ADDR-ZIP`). Every account falls back to `DEFAULT`, and the `A000000000` and `ZEROAPR` rates (17 rows each in `discgrp.txt`) are never used. | Every SYSOUT record is followed by `DISCLOSURE GROUP RECORD MISSING`. | Data-quality issue, not code. Preserve the lookup order exactly. |
| F4 | `1400-COMPUTE-FEES` is empty (:518). | Source. | Keep as a no-op hook (CR-2). |
| F5 | **Silent overflow**: no `ON SIZE ERROR` on the COMPUTE (:464, `S9(9)V99`) or on the ADD to `ACCT-CURR-BAL` (:352, `S9(10)V99`). COBOL drops the high-order digits. | Source. Not reachable with the sample data. | Reproduce the truncation modulo 10^9 / 10^10 and pin it with a unit test. |
| F6 | `PARM-LENGTH` is ignored, and `TRAN-ID` always takes 10 PARM bytes (:476). The transaction counter is run-wide, so 1M+ transactions would wrap the 6-digit suffix. | Source. | Preserve. Pad a short PARM with spaces. |
| F7 | XREF alt key is NONUNIQUE. With multiple cards per account, COBOL returns the first duplicate in insertion order. | `XREFFILE.jcl:74-75`. Sample data has 0 duplicates. | Take the first match in file order. Not tested by golden. |
| R1 | Timestamps come from the wall clock (hundredths, then `0000`). The golden files depend on the frozen clock. | README "Determinism". | Make the clock injectable, defaulting to the system clock. |
| R2 | Uninitialised fields (end of TRAN-DESC, FILLER 331-350) are spaces only under the default GnuCOBOL dialect. On z/OS they could be `x'00'`. | README "Known quirks". | Match golden (spaces). Flag this for mainframe parallel-run comparison. |
| R3 | The rewrite reads and writes text unloads, not VSAM. Operations needs an unload/load step, or a later CR for a real store. | Scope. | Called out for Deploy. |

## Questions for the approver
1. **Must SYSOUT (display lines) and RC also match the golden files?** The CR only names TRANSACT and ACCTFILE. *Default: yes. Match both, since it costs little and helps operators.*
2. **F1 (last account not posted) is a real billing defect.** *Default: preserve and pin it in CR-1 as the CR instructs, and raise a separate CR to fix it in both the COBOL and Java versions.*
3. **I/O format.** *Default: canonical fixed-width text, as in `legacy/golden/`. Paths are passed per DD name on the command line, with an `INPUT_DIR`-style directory convention.*
