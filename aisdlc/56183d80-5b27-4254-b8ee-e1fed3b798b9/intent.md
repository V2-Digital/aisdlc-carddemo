# CR-1 Intent: INTCALC (`CBACT04C`) rewritten in Java 21

## Problem / outcome

**Problem.** The nightly interest calculation runs as COBOL on the mainframe (`app/jcl/INTCALC.jcl`). We want batch workloads off the mainframe, and this job is the first one to move.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that can run in a container. Given the same inputs, it produces byte-identical interest transactions (`TRANSACT`) and an updated account master (`ACCTFILE`). Legacy behaviour is kept as it is, defects included. Each defect is pinned by a test and flagged for a separate decision.

## What the legacy code does today

Job: `INTCALC.jcl` STEP15 runs `EXEC PGM=CBACT04C,PARM='2022071800'`. The PARM is the run date and becomes the prefix of every transaction ID.

### Files

| DD | Org / access | LRECL | Key | Use | Layout |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | 50 | acct(11)+type(2)+cat(4), cols 1-17 | input, the driver | `CVTRA01Y.cpy`; `TRAN-CAT-BAL` S9(9)V99 @18 |
| `XREFFILE` | KSDS, random by **AIX** acct-id @26(11) | 50 | card num @1(16) | input | `CVACT03Y.cpy` |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4) | input | `CVTRA02Y.cpy`; `DIS-INT-RATE` S9(4)V99 @17 |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct-id @1(11) | read + REWRITE | `CVACT01Y.cpy`; group id @113(10) |
| `TRANSACT` | sequential, NEW | 350 | — | output | `CVTRA05Y.cpy` |

Every numeric field is zoned DISPLAY with a trailing overpunch sign. Files are bound in `CBACT04C.cbl:28-58`, FDs are at `:61-92`.

### Processing steps

1. Open all five files (`CBACT04C.cbl:182-186`). ACCTFILE is opened I-O and TRANSACT OUTPUT. If any open fails, the program abends.
2. Read TCATBALF in key order. Each record is DISPLAYed to SYSOUT (`:190-193`).
3. **When the account changes** (`:194-206`):
   - Rewrite the previous account. This is skipped for the first record.
   - Reset `WS-TOTAL-INT` to 0.
   - Read ACCTFILE by account id (`:372`).
   - Read XREFFILE by the account-id alternate key to get the card number (`:393-395`).
4. Look up the rate in DISCGRP with key (`ACCT-GROUP-ID`, type, cat) (`:210-213`, `:415`).
   - On status `23`: DISPLAY two lines, then retry with group `'DEFAULT'` (`:417-419`, `:436-438`, `:443`).
   - If the DEFAULT read also misses, the program abends.
5. **Only if the rate ≠ 0** (`:214-217`):
   - `WS-MONTHLY-INT = (TRAN-CAT-BAL × DIS-INT-RATE) / 1200`. There is no `ROUNDED`, so the result is **truncated** to 2 decimals in an S9(9)V99 field (`:464-465`, `:168`).
   - Add the amount to `WS-TOTAL-INT`.
   - Write one transaction (`:473-500`):
     - `TRAN-ID` = PARM-DATE(10) + a running 6-digit suffix (`:476`, `:173`)
     - type `01`, cat `0005`, source `System`
     - desc `Int. for a/c <acct-id>`
     - amount = monthly interest
     - merchant id 0, merchant name/city/zip blank
     - card number from the xref record
     - `ORIG-TS` = `PROC-TS` = `CURRENT-DATE` in DB2 format, not the PARM date (`:613-624`)
   - Then perform `1400-COMPUTE-FEES`, which is empty (`:518-520`).
   - If the rate is 0, nothing is written for that category.
6. **Account update** (`1050`, `:350-356`):
   - `ACCT-CURR-BAL += WS-TOTAL-INT`
   - cycle credit and cycle debit set to 0
   - REWRITE the record
7. Close all files, DISPLAY end, `GOBACK` with RC 0 (`:224-232`).
8. **Errors:** any unexpected file status prints `FILE STATUS IS: NNNN…`, then `ABENDING PROGRAM`, then calls `CEE3ABD` (U0999). The harness stub exits with **16** (`:628-648`).

### Observed behaviour (harness runs)

The run of `legacy/harness/run.sh CBACT04C` is byte-identical to `golden/CBACT04C` on all four files.

| Scenario | TCATBAL in | TRANSACT out | ACCTFILE changed | SYSOUT / RC |
|---|---|---|---|---|
| `CBACT04C` (stock) | 50 | 50, all amount 0 | 0 of 50 (identical to `acctdata.txt`) | 152 / 0 |
| `CBACT04C-after-CBTRN02C` | 100 (50 × `01/0001`, 50 × `03/0001`) | 50, non-zero | 49 of 50 | 302 / 0 |

- In the second scenario, only the `01/0001` rows produce transactions. DEFAULT `01/0001` is 15.00%. DEFAULT `03/0001` is 0%, so those rows are skipped.
- Every lookup misses and falls back to DEFAULT: 100 × `DISCLOSURE GROUP RECORD MISSING`.
- All 50 amounts equal truncate(bal × 15 / 1200). 48 of them had a fractional part beyond 2 decimals, which confirms truncation rather than rounding.

## Scope

**In**
- Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only). Its logic matches `CBACT04C`, steps 1-8 above.
- Reads and writes the harness canonical text format (fixed-width, LF, overpunch). DD paths are configurable. The run date is a PARM argument and the clock is injectable.
- Exact zoned-decimal / overpunch encoding and decoding, with COBOL truncation semantics.
- An equivalence test against both golden scenarios.
- Characterization tests for each defect below.

**Out**
- Fixing any defect, including the last-account bug and the empty fees paragraph (fees are CR-2).
- `CBTRN02C`, the online CICS programs, and real VSAM/EBCDIC binary I/O.
- Changes to JCL or scheduling, and the container image / deploy.

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbact04c/` passes on Java 21. The only dependencies are JUnit 5 and Surefire.
2. Inputs from `app/data/ASCII/` and run date `2022071800` with clock `2022-07-18 00:00:00.00`: the Java `TRANSACT` and `ACCTFILE` outputs are **byte-identical** to `legacy/golden/CBACT04C/` (50 + 50 records).
3. ACCTFILE and TCATBALF from `legacy/golden/CBTRN02C/`, other inputs as in AC2: outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/` (50 + 50).
4. The last account processed is **not** rewritten. Account `00000000050` in AC3's output equals its input record, while its transaction `2022071800000050` (18.77) is still written.
5. Interest is truncated, not rounded. A unit test covers a balance whose bal × rate / 1200 has a third decimal ≥ 5.
6. A zero-rate category writes no transaction and adds nothing to the account. A missing group falls back to `DEFAULT`.
7. Transactions keep the uninitialised-storage form: the `TRAN-DESC` tail and the 20-byte FILLER are spaces.
8. On a fatal I/O condition (account not found, xref not found, DEFAULT group missing) the program stops with a non-zero exit code (16) and does not exit 0.
9. Stretch, see Q1: SYSOUT text and RC match the golden `SYSOUT.txt` / `RC.txt`.

## Risks and findings

| # | Finding | Evidence | Treatment |
|---|---|---|---|
| D1 | **The last account's interest is never written back.** After EOF the `PERFORM UNTIL` test exits before the `ELSE PERFORM 1050` branch can run. | `CBACT04C.cbl:188-222`. `golden/CBACT04C-after-CBTRN02C`: account 50 charged 18.77 but its ACCTFILE record is unchanged vs `golden/CBTRN02C` (49 of 50 differ) | Preserve, pin (AC4), flag |
| D2 | Interest is truncated, not rounded, and the field is S9(9)V99. Very large products would also lose high-order digits silently. | `:464-465`, `:168`; 48 of 50 golden amounts | Preserve (AC5) |
| D3 | `1400-COMPUTE-FEES` is empty: no fees are ever charged. | `:518-520` | Preserve; CR-2 |
| D4 | Sample data puts the group code in `ACCT-ADDR-ZIP`, and `ACCT-GROUP-ID` is blank. Every lookup falls back to DEFAULT. | `legacy/README.md`; 100 "MISSING" lines in SYSOUT | Data issue, preserve |
| D5 | Timestamps come from the system clock, not the PARM date, so reruns differ. | `:613-624` | Inject the clock |
| D6 | The `TRAN-ID` suffix is 9(6). Past 999,999 transactions in one run it wraps and would duplicate IDs. | `:173`, `:474` | Preserve; note |
| D7 | Copy-paste message: a DISCGRP open failure prints "ERROR OPENING DALY REJECTS FILE". | `:281` | Preserve text |
| D8 | Account-not-found and xref-not-found DISPLAY a message and then abend. There is no skip path. | `:374-390`, `:396-412` | Preserve (AC8) |
| R1 | Overpunch/sign and fixed-width handling is where byte mismatches would come from. | README "Known quirks" | Golden diff in CI |
| R2 | The XREF AIX lookup returns the first card for an account. The sample has no duplicate account ids, so this path is not exercised by the golden data. | `cardxref.txt` (0 dupes) | Use first-in-key-order; note |

## Questions for the approver

1. **SYSOUT parity.** The CR only requires `TRANSACT` and `ACCTFILE`. Should the Java job also reproduce the COBOL SYSOUT lines and RC?
   - *Default: yes.* It costs little and it gives operations the same log.
2. **Abend code.** The mainframe abends with U0999 and the harness reports exit 16.
   - *Default: exit 16, with the same `ABENDING PROGRAM` message.*
