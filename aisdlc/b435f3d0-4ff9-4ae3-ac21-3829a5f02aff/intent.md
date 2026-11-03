# CR-1 Intent: INTCALC (`CBACT04C`) to Java 21

## Problem
The nightly interest calculation runs only as COBOL on the mainframe (`app/jcl/INTCALC.jcl` → `CBACT04C`). That ties a core card-servicing job to the platform we are leaving. The job can't run in a container, and we can't test it in a normal CI pipeline.

## Outcome
The same job runs as a plain Java 21 program in `modernized/cbact04c/`. For the same inputs it produces the **same bytes** as the COBOL: the interest transactions (`TRANSACT`) and the updated account master (`ACCTFILE`). Legacy defects are kept as they are, pinned by tests, and listed for a separate decision.

## What the legacy code does today
Evidence base: I ran `legacy/harness/run.sh CBACT04C` on stock data and with `INPUT_DIR=legacy/golden/CBTRN02C`. All 8 output files (TRANSACT, ACCTFILE, SYSOUT, RC × 2 scenarios) match `legacy/golden/` byte for byte.

### Files (`CBACT04C.cbl:28-56`, `INTCALC.jcl`)
| DD | Org / access | LRECL | Key | Use | Layout |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | 50 | acct(11)+type(2)+cat(4), 1-17 | input, the driver | `CVTRA01Y` |
| `XREFFILE` | KSDS, random by **alt key** acct-id (26-36, non-unique) | 50 | card num 1-16 | input | `CVACT03Y` |
| `ACCTFILE` | KSDS, random, `I-O` | 300 | acct id 1-11 | read + REWRITE | `CVACT01Y` |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4), 1-16 | input (rates) | `CVTRA02Y` |
| `TRANSACT` | sequential, `OUTPUT` | 350 | n/a | new interest trans | `CVTRA05Y` |

`XREFFIL1` (the AIX path) is not referenced by the program. PARM is `2022071800` (10 chars, `PARM-DATE`, `:176-178`).

### Processing
1. Open all five files. Any status other than `00` → DISPLAY, then abend U999 (`:234-323`, `:628-632`).
2. Read TCATBAL sequentially in key order. Status `10` = EOF (`:325-348`). Each record counts and is DISPLAYed to SYSOUT (`:192-193`).
3. **Account break** (`:194-206`): when the acct id differs from the last one:
   - If this is not the first account, REWRITE the *previous* account (step 6).
   - Reset `WS-TOTAL-INT` to 0.
   - READ ACCTFILE by acct id (`:372-391`) and XREF by alternate key acct id (`:393-413`). Not found → DISPLAY `ACCOUNT NOT FOUND`, then abend, because the status is not `00`.
4. **Rate lookup** (`:210-213`, `:415-460`): key = `ACCT-GROUP-ID` + type + category.
   - Status `23` → DISPLAY `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE`, then retry with group `DEFAULT`. A miss on `DEFAULT` too → abend.
5. **Interest** (`:214-217`, `:462-470`): only if `DIS-INT-RATE ≠ 0`.
   - `WS-MONTHLY-INT = TRAN-CAT-BAL × DIS-INT-RATE / 1200` (no `ROUNDED`, so it **truncates** to 2 dp). Added to `WS-TOTAL-INT`.
   - Example: acct 1 cat 01/0001 is 1164.87 × 15.00 / 1200 = 14.560875 → `0000000145F` (14.56).
   - Then the empty `1400-COMPUTE-FEES` runs (`:518-520`).
6. **Write transaction** (`:473-515`), one per non-zero-rate TCATBAL row:

   | Field | Value |
   |---|---|
   | `TRAN-ID` | PARM-DATE + 6-digit running suffix |
   | type | `01` |
   | category | `0005` |
   | source | `System` |
   | `TRAN-DESC` | `Int. for a/c ` + ACCT-ID |
   | `TRAN-AMT` | monthly interest |
   | merchant id | 0 |
   | merchant name / city / zip | spaces |
   | card | `XREF-CARD-NUM` |
   | `TRAN-ORIG-TS` / `TRAN-PROC-TS` | `YYYY-MM-DD-HH.MM.SS.hh0000` from `CURRENT-DATE` (`:613-626`) |

   `TRAN-RECORD` is never initialised, so the DESC tail and the 20-byte FILLER are spaces.
7. **Account update** (`:350-370`): `ACCT-CURR-BAL += WS-TOTAL-INT`. `CURR-CYC-CREDIT` and `CURR-CYC-DEBIT` are set to 0. Then REWRITE.
8. Close all files. DISPLAY end message, GOBACK with RC 0 (`:224-232`).

### Observed data
- **Stock run:**
  - Input: 50 TCATBAL rows, all type/cat `01/0001`, all balances zero.
  - Output: 50 zero-amount transactions. ACCTFILE is identical to `acctdata.txt`. SYSOUT is 152 lines.
- **After-CBTRN02C run:**
  - Input: 100 TCATBAL rows (50 × `01/0001`, 50 × `03/0001`).
  - DEFAULT rates: `01/0001` = 15.00%, `03/0001` = 0.00%. So only the 50 type-01 rows produce transactions (`2022071800000001`..`…050`).
  - 100 `DISCLOSURE GROUP RECORD MISSING` lines, because every lookup falls back to DEFAULT (see R3).
  - 49 of 50 accounts are rewritten. Example, acct 1: 1288.10 + 14.56 = 1302.66 (`00000013026F`), cycle fields `…{`.
  - SYSOUT is 302 lines.

## Scope
**In**
- Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only) that reproduces steps 1–8.
- Inputs and outputs use the harness's canonical text form (fixed width, LF, overpunch zoned decimals).
- Run date from the PARM argument. Timestamp from an injectable clock, frozen for tests.
- Equivalence tests against `legacy/golden/CBACT04C` and `legacy/golden/CBACT04C-after-CBTRN02C`.
- Characterization tests pinning the defects below.

**Out**
- Fixing any legacy defect, including R1 and the fee calculation (CR-2).
- Real VSAM/EBCDIC I/O, JCL/scheduler changes, CBTRN02C, container image or deploy wiring (Deploy stage).

## Acceptance criteria
1. `mvn -f modernized/cbact04c test` passes on Java 21 using only JUnit 5 + Surefire.
2. With the inputs of scenario `CBACT04C` (stock `app/data/ASCII`), PARM `2022071800` and clock `2022-07-18 00:00:00.00`, the output `TRANSACT.txt` (50 × 350 bytes) and `ACCTFILE.txt` (50 × 300 bytes) are byte-identical to `legacy/golden/CBACT04C/`.
3. Same for scenario `CBACT04C-after-CBTRN02C`: ACCTFILE and TCATBALF are taken from `legacy/golden/CBTRN02C/`, the rest from stock data. Outputs must byte-match `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. SYSOUT lines and exit code match the golden `SYSOUT.txt` / `RC.txt` in both scenarios (default for Q2).
5. Interest truncates (never rounds) to 2 dp. A test pins a case where rounding would differ (14.560875 → 14.56).
6. A characterization test pins R1: the last account in TCATBAL is not rewritten, but its interest transaction is still written.
7. Missing account, missing XREF, or a miss on the DEFAULT disclosure group → non-zero exit, matching the COBOL abend path (RC 16 under the harness).
8. `legacy/golden/` and `legacy/harness/` are unchanged, and `golden.sh --check` still passes.

## Risks and findings
- **R1, defect: last account never updated.** `ELSE PERFORM 1050-UPDATE-ACCOUNT` (`:219-220`) is unreachable. After EOF the `UNTIL` test (`:188`) exits before the body runs again. Evidence:
  - Golden after-CBTRN02C TRANSACT charges acct `00000000050` 18.77 (`0000000187G`).
  - Its ACCTFILE row is unchanged from `golden/CBTRN02C`: balance 1945.87 (`00000019458G`), cycle credit/debit 1501.75 / -47.88.
  - The customer is billed a transaction that never reaches the balance. **Preserve it, pin it, raise a separate CR.**
- **R2, defect: fees not implemented.** `1400-COMPUTE-FEES` is an empty paragraph (`:518-520`). Preserve it (CR-2).
- **R3, data defect: disclosure groups always fall back to DEFAULT.** The `A000000000` group code is in `ACCT-ADDR-ZIP` (cols 103-112) and `ACCT-GROUP-ID` is blank. So every lookup misses and uses `DEFAULT` (100/100 lookups in after-CBTRN02C). The code is fine; the sample data is wrong. Keep the lookup and fallback logic exactly as written.
- **R4, rounding/sign.** COBOL truncates toward zero, so Java must use `BigDecimal` with `RoundingMode.DOWN`, never double. Sample data has no negative type-01 balances, so negative interest is only covered by a unit test, not by golden. Overpunch encoding of the result (including `}` for -0) must match the copybook PICs.
- **R5, overflow edges, not exercised:**
  - The 6-digit tran-id suffix wraps after 999,999 transactions. MOVE to `PIC 9(06)` truncates high digits, so IDs would repeat.
  - `ACCT-CURR-BAL` is S9(10)V99; ADD without `ON SIZE ERROR` truncates high-order digits.
  - Java should reproduce both truncations, not throw.
- **R6, timestamp.** Milliseconds come from hundredths: `.hh0000`, not real microseconds. The clock must be injectable for byte-identity (the harness freezes `COB_CURRENT_DATE`).
- **R7, alternate key.** The XREF AIX is `NONUNIQUEKEY`. With several cards per account, COBOL returns the first by AIX order. Sample data has no duplicates (0 of 50), so Java should take the first matching record in file order and document this.
- **R8, cosmetic.** The DISCGRP open-error message says `ERROR OPENING DALY REJECTS FILE` (`:281`), a copy-paste slip. Preserve the text.
- **R9, uninitialised fields.** The DESC tail and FILLER are spaces under the default dialect. On a real mainframe (`-std=ibm`) they could be `x'00'`. The golden output, and therefore Java, uses spaces.

## Questions for the approver
- **Q1. I/O format.** Java reads and writes the canonical fixed-width text files (one `<DD>.txt` per dataset, as the harness does), not VSAM. Real dataset access will be handled when the job is deployed. **Default: yes.**
- **Q2. SYSOUT/RC parity.** The CR requires TRANSACT and ACCTFILE. Should SYSOUT text and exit codes also match? Ops runbooks may grep these messages. **Default: yes, match golden SYSOUT and RC too.** It costs little.
- **Q3. R1 follow-up.** Fixing R1 changes balances. **Default: preserve in CR-1 and raise a separate CR owned by Cards Finance.**
