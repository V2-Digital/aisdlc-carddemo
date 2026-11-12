# CR-1 Intent: INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation (job `INTCALC`, program `CBACT04C`) only runs as COBOL on the mainframe. That blocks the move of batch work into containers.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` does the same job. For the same inputs it produces byte-identical interest transactions (`TRANSACT`) and an updated account master (`ACCTFILE`). Equivalence is proven on the golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Behaviour stays the same, including the known defects.

## What the legacy code does today

Job `app/jcl/INTCALC.jcl:22` runs `EXEC PGM=CBACT04C,PARM='2022071800'`. The PARM becomes `PARM-DATE X(10)` (`CBACT04C.cbl` LINKAGE, after `WS-COUNTERS`).

### Files

| DD | Copybook | Org / access | Key (offset, len) | LRECL | Use |
|---|---|---|---|---|---|
| `TCATBALF` | `CVTRA01Y` | KSDS, sequential | acct 11 + type 2 + cat 4 (0,17) | 50 | input, read in key order |
| `XREFFILE` | `CVACT03Y` | KSDS, random | card num (0,16). Read by **alt key** acct id (25,11) | 50 | input |
| `ACCTFILE` | `CVACT01Y` | KSDS, random, **I-O** | acct id (0,11) | 300 | read + REWRITE |
| `DISCGRP` | `CVTRA02Y` | KSDS, random | group 10 + type 2 + cat 4 (0,16) | 50 | input (interest rates) |
| `TRANSACT` | `CVTRA05Y` | sequential, OUTPUT | n/a | 350 | new interest transactions |

`XREFFIL1` (the AIX path) appears in the JCL. The harness serves the alternate key from `XREFFILE` itself.

### Processing (`CBACT04C.cbl`)

1. **Open:** all five files are opened (`0000`–`0400`). Any status other than `00` displays an error and abends with U0999 (harness RC 16, `9999-ABEND-PROGRAM` :629-632).
2. **Main loop** (:188-222): each `TCATBALF` record is read in key order (:326) and DISPLAYed in full to SYSOUT (:193).
3. **Account break** (:194-206): when the account id changes, the previous account is written back (`1050`), except on the first record. Then `WS-TOTAL-INT` is reset, the account is read by key (:373), and the xref is read by acct id (:394). A miss on either read abends.
4. **Rate lookup** (`1200` :416-440): DISCGRP is read with key (`ACCT-GROUP-ID`, `TRANCAT-TYPE-CD`, `TRANCAT-CD`) (:210-212).
   - Status `23` (not found) displays `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then re-reads with group `DEFAULT` (:437, :444). If `DEFAULT` is also missing, the program abends.
   - Any other status that is not `00` abends.
5. **Interest** (`1300` :464-467): runs only if `DIS-INT-RATE ≠ 0` (:214). `WS-MONTHLY-INT S9(9)V99 = (TRAN-CAT-BAL × DIS-INT-RATE) / 1200`. The result is **truncated**: no ROUNDED clause. It is added to `WS-TOTAL-INT`.
6. **Write transaction** (`1300-B` :474-498), one per category with a non-zero rate, **even when the amount is 0**:
   - `TRAN-ID` = PARM-DATE (10) + `WS-TRANID-SUFFIX 9(6)` (:474-476). The suffix starts at 000001.
   - type `01`, category `0005`, source `System`.
   - desc = `Int. for a/c ` + ACCT-ID, followed by spaces.
   - amount = monthly interest. Merchant id 0. Merchant name, city and zip are spaces.
   - card = `XREF-CARD-NUM`.
   - orig and proc timestamps = `YYYY-MM-DD-HH.MM.SS.hh0000` from `CURRENT-DATE` (:613-622).
   - The trailing 20-byte FILLER is never set, so it stays spaces.
7. **Fees** (`1400` :518): empty paragraph. No effect.
8. **Account update** (`1050` :352-356): `ACCT-CURR-BAL += WS-TOTAL-INT`. Cycle credit and cycle debit are set to 0. The record is REWRITten. Accounts that earned no interest are still rewritten (cycle fields zeroed).
9. **Close** all files and display `END OF EXECUTION OF PROGRAM CBACT04C` (:230). RC 0.

### Observed behaviour (harness run, PARM `2022071800`, clock frozen 2022-07-18 00:00)

- **Stock data:** 50 TCATBAL rows, all type `01` cat `0001` with balance 0. Result: 50 transactions of 0.00, ACCTFILE identical to `acctdata.txt`, 152 SYSOUT lines, RC 0. This matches `legacy/golden/CBACT04C` byte for byte.
- **After CBTRN02C:** 100 TCATBAL rows: 50 of type `01/0001` (DEFAULT rate 1.50%) and 50 of type `03/0001` (rate 0, so no transaction). Result: 50 transactions, for example account 1: 14.56 (`0000000145F`).
- Every lookup misses and falls back to `DEFAULT`, because `ACCT-GROUP-ID` is blank in the sample data (100 `MISSING` lines in SYSOUT).

## Scope

**In**
- A Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only) that implements steps 1–9.
- It reads and writes the harness canonical text form: fixed width, LF line endings, overpunch signs.
- It produces TRANSACT, the updated ACCTFILE, SYSOUT and an exit code.
- Equivalence tests against both golden scenarios.
- Characterization tests that pin the defects below.

**Out**
- Fixing any defect.
- `1400-COMPUTE-FEES` (that is CR-2).
- CBTRN02C.
- Real VSAM or EBCDIC binary I/O.
- JCL or scheduler changes, container image, deployment.

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbact04c/` passes on Java 21 with no dependencies beyond JUnit 5 and Surefire.
2. Run on the stock inputs with PARM `2022071800` and clock 2022-07-18 00:00:00.00, the program's `TRANSACT.txt` and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C/`.
3. Run on `legacy/golden/CBTRN02C/{ACCTFILE,TCATBALF}.txt` plus the stock XREF and DISCGRP files, outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/` (50 + 50 records).
4. SYSOUT is byte-identical to golden `SYSOUT.txt` (152 / 302 lines), and the exit code is 0 in both scenarios (see Q2).
5. The run date (PARM) and the clock are inputs. Nothing reads the wall clock without that injection point.
6. Characterization tests pin:
   - (a) the last account is not rewritten (F1);
   - (b) interest is truncated, not rounded (F2);
   - (c) zero-amount transactions are written when the rate is non-zero (F3);
   - (d) missing group falls back to `DEFAULT`, and if `DEFAULT` is missing the program abends.
7. Fatal conditions exit with code 16 after printing `ABENDING PROGRAM`, like the harness's U0999: missing account, missing xref, missing DEFAULT rate, bad input.
8. `legacy/harness/golden.sh --check` still passes. Nothing under `legacy/golden/`, `legacy/harness/` or `.github/` changes.

## Risks and findings

| # | Finding | Evidence | Treatment |
|---|---|---|---|
| F1 | **The last account's interest is never written back.** After EOF, `PERFORM UNTIL` exits before the `ELSE PERFORM 1050` branch (:189, :219-220) can run. | `golden/CBACT04C-after-CBTRN02C`: TRANSACT charges acct 50 18.77 (`0000000187G`), but ACCTFILE row 50 is identical to `golden/CBTRN02C/ACCTFILE.txt` (balance 1945.87, cycle fields not zeroed). Only 49 of 50 rows change. | Preserve. Pin with a test. Flag. |
| F2 | **Interest is truncated toward zero, not rounded.** | Recomputed all 50 amounts as `bal × 1.50 / 1200`: all 50 match truncation, and 28 would differ by 1 cent under half-up rounding (e.g. acct 48: 1512.77 → 18.909625 → `18.90`). | Preserve (Java `RoundingMode.DOWN`). Flag. |
| F3 | A zero-amount transaction is written whenever the rate is non-zero, even if the balance is 0. | Stock run: 50 transactions all `0000000000{`. | Preserve. |
| F4 | The group lookup uses `ACCT-GROUP-ID`, which is blank in the sample data. The group code sits in `ACCT-ADDR-ZIP`. Every lookup falls back to `DEFAULT`. | 100 `MISSING` lines in SYSOUT; `legacy/README.md`. | Preserve. This is a data issue, raised for information. |
| F5 | Copy-paste error: a DISCGRP open failure reports `ERROR OPENING DALY REJECTS FILE`. | `CBACT04C.cbl:281` | Preserve the message text. |
| F6 | No overflow guard: `S9(9)V99` interest, `S9(10)V99` balance, `9(6)` tran-id suffix. COBOL silently drops the high-order digits. | No `ON SIZE ERROR` in `1300`/`1050`. | Mirror COBOL truncation (mod 10^n). Not exercised by the golden data. |
| F7 | Timestamps use hundredths plus `0000`, not real microseconds. | :613-622 | Reproduce the format. Inject the clock. |

**Delivery risks**
- Overpunch sign encoding, including `-0` written as `}`.
- Trailing-space preservation, and reading `cardxref.txt` (36-byte lines) and the CRLF inputs the way the harness loader does.
- Ordering: TCATBAL must be processed in key order, and ACCTFILE output in key order.

## Questions for the approver

1. **I/O contract for the container:** should the program read and write the canonical text form (the same as `app/data/ASCII` and the golden files), with one path per DD and ACCTFILE written to a new output file instead of updated in place? *Default: yes. Paths are given by `DD_<NAME>` environment variables, mirroring the harness.*
2. **SYSOUT and RC parity:** the CR names only TRANSACT and ACCTFILE. *Default: also match SYSOUT byte for byte and use RC 0/16, since this is cheap and helps operations.*
3. **Defects F1/F2:** confirm that both are preserved in this CR and raised as separate change requests. F1 under-posts interest every night on the last account in the file. *Default: preserve, and log follow-up CRs.*
