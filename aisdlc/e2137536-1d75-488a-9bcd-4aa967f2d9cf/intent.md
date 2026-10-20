# CR-1 Intent: INTCALC (`CBACT04C`) → Java 21

Run `e2137536-1d75-488a-9bcd-4aa967f2d9cf` · Stage: Plan · Baseline `0cdf1ab`

## Problem / outcome

**Problem.** The nightly interest calculation (`app/jcl/INTCALC.jcl`, COBOL `CBACT04C`) only runs on the mainframe.
That blocks moving batch work off it.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. For the same inputs it
writes **byte-identical** interest transactions (`TRANSACT`) and the same updated account master (`ACCTFILE`) as the
COBOL. Proof is the golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Legacy behaviour, defects included,
is kept exactly as it is.

## What the legacy code does today

Evidence: I ran `legacy/harness/run.sh CBACT04C /tmp/run1`. It gave RC 0, TRANSACT 50, ACCTFILE 50 and SYSOUT 152
records, the same counts as `legacy/golden/CBACT04C`.

### Files (JCL `INTCALC.jcl:22-43`, SELECTs `CBACT04C.cbl:28-56`)

| DD | Org / access | Mode | LRECL | Key | Copybook |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | INPUT | 50 | acct-id 9(11) + type X(2) + cat 9(4) (offset 0, len 17) | `CVTRA01Y` |
| `XREFFILE` | KSDS, random | INPUT | 50 | card-num X(16). AIX acct-id 9(11) at offset 25 is used | `CVACT03Y` |
| `ACCTFILE` | KSDS, random | **I-O** (REWRITE) | 300 | acct-id 9(11) | `CVACT01Y` |
| `DISCGRP` | KSDS, random | INPUT | 50 | group X(10) + type X(2) + cat 9(4) (len 16) | `CVTRA02Y` |
| `TRANSACT` | Sequential, new | OUTPUT | 350 | — (write order) | `CVTRA05Y` |
| `PARM` | `PARM='2022071800'` | — | 10 | — | `CBACT04C.cbl:176-178` |

`XREFFIL1` (AIX path) is not opened by the program. The harness serves the AIX from `XREFFILE` itself.

### Processing steps

1. Open all five files. Any status other than `00` → display, then abend (`:234-323`).
2. Read `TCATBALF` sequentially in key order (`:325-348`). DISPLAY each 50-byte record (`:193`).
3. **Account break** (`:194-206`): when the acct-id changes:
   - Rewrite the *previous* account (`1050`), except on the first record.
   - Reset the interest total.
   - Read `ACCTFILE` by acct-id (`:372-391`).
   - Read `XREFFILE` by the alternate key acct-id (`:393-413`). This gives the first card for the account.
4. **Rate lookup** (`:210-213, 415-460`): key = `ACCT-GROUP-ID` + TCATBAL type + cat.
   - Status `23` → DISPLAY `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE`, then retry with
     group `DEFAULT` and the same type/cat.
   - A missing `DEFAULT` row → abend.
5. If `DIS-INT-RATE ≠ 0` (`:214-217`):
   - `WS-MONTHLY-INT = (TRAN-CAT-BAL × DIS-INT-RATE) / 1200`, **truncated** to 2 dp. There is no `ROUNDED`
     (`:464-465`).
   - Add it to `WS-TOTAL-INT`.
   - Write one transaction (`1300-B-WRITE-TX`, `:473-515`).
   - Perform `1400-COMPUTE-FEES`, which is empty (`:518-520`).
6. Transaction record (`:474-498`):
   - `TRAN-ID` = PARM date (10) + 6-digit run-wide counter `WS-TRANID-SUFFIX`.
   - Type `01`, cat `0005`, source `System`.
   - Desc = `Int. for a/c ` + `ACCT-ID`, padded with spaces.
   - Amt = monthly interest. Merchant id 0, merchant name/city/zip spaces.
   - Card = `XREF-CARD-NUM`.
   - Orig/proc TS = `YYYY-MM-DD-HH.MM.SS.hh0000`, from `CURRENT-DATE` (hundredths + `0000`, `:613-626`).
   - Trailing FILLER (20) = spaces (uninitialised WS, see README).
7. `1050-UPDATE-ACCOUNT` (`:350-370`):
   - `ACCT-CURR-BAL += WS-TOTAL-INT`.
   - Cycle credit and cycle debit := 0.
   - REWRITE the record. All other bytes are unchanged.
8. Close all files (`:224-228`), DISPLAY end message, `GOBACK` with RC 0.
9. Abend path (`:628-648`): DISPLAY `ABENDING PROGRAM`, then `CEE3ABD` with 999. The harness turns this into exit 16.

### Business rules confirmed from data

- **Truncation, not rounding.** In `CBACT04C-after-CBTRN02C`, all 50 amounts equal `trunc(bal×15/1200, 2)`.
  Half-up rounding matches only 22. The other 28 records tell the two apart.
- **Only non-zero rates produce a transaction.** Post-posting TCATBAL has 100 rows (type `01` and `03` for each
  account). `DEFAULT/03/0001` has rate 0, so only the 50 type-01 rows produce transactions. SYSOUT has 302 lines:
  1 + 100 × 3 + 1.
- **Every account goes through DEFAULT.** `ACCT-GROUP-ID` is blank in the sample data (README quirks).
- **Stock data:** all balances are 0, so the run writes 50 zero-amount transactions. `ACCTFILE` comes out
  byte-identical to `acctdata.txt`.

## Scope

**In**
- A Java 21 Maven project at `modernized/cbact04c/` (JUnit 5 only) that does steps 1–9.
- It reads and writes the harness's canonical text form: fixed-width lines, LF, overpunch signs. Input files are
  the same `<DD>.txt` files.
- PARM date and "current date" are inputs. The clock can be frozen, the same way as `COB_CURRENT_DATE`.
- Byte-identical `TRANSACT.txt` and `ACCTFILE.txt` (and `SYSOUT.txt`/`RC.txt`, see Q2) on both golden scenarios.
- Characterization tests for each defect listed below.

**Out**
- Fixing any legacy defect, including implementing fees (CR-2).
- `CBTRN02C`, CICS/online programs, JCL/scheduler changes, container image or deploy pipeline.
- Real VSAM/EBCDIC binary I/O.
- Changes to `legacy/golden/`, `legacy/harness/` or `.github/`.

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbact04c/` passes on Java 21, with JUnit 5 as the only dependency.
2. Scenario `CBACT04C`: running on `app/data/ASCII` inputs with PARM `2022071800` and clock `2022071800000000`
   gives `TRANSACT.txt` (50 records) and `ACCTFILE.txt` (50 records) byte-identical to `legacy/golden/CBACT04C/`.
3. Scenario `CBACT04C-after-CBTRN02C`: running with `ACCTFILE`/`TCATBALF` from `legacy/golden/CBTRN02C/` gives
   files byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. `SYSOUT.txt` and `RC.txt` match golden on both scenarios (152 / 302 lines, RC `0`). This depends on Q2.
5. Interest is truncated toward zero to 2 dp. A test pins a balance where truncation and half-up give different
   results.
6. A test pins defect D1: the last account's interest is not written back to `ACCTFILE`.
7. Tests pin these rules:
   - DEFAULT-group fallback.
   - A zero rate writes no transaction.
   - Transaction ids run across accounts (`<PARM><000001..>`).
   - The rewrite changes only `ACCT-CURR-BAL` and the cycle credit/debit fields.
8. The run abends with exit 16 when a lookup fails: account missing, xref missing, `DEFAULT` row missing, or a
   file error.
9. Signed fields round-trip in EBCDIC overpunch (`{`/`A-I`/`}`/`J-R`). Negative balances are covered by a test.

## Risks and findings

**Defects (keep and pin, for a separate decision)**

| # | Finding | Evidence |
|---|---|---|
| D1 | **The last account is never rewritten.** After EOF the `PERFORM UNTIL` exits before the `ELSE PERFORM 1050` branch can run (`:188-222`). The last account's interest is posted as a transaction but not added to its balance, and its cycle fields are not zeroed. | In `after-CBTRN02C`, `TRANSACT` id `2022071800000050` charges acct `00000000050` 18.77 (`0000000187G`). ACCTFILE line 50 is byte-identical to `golden/CBTRN02C/ACCTFILE.txt` line 50. 49 of 50 records change. |
| D2 | `1400-COMPUTE-FEES` is empty. No fees are ever charged. | `:518-520` (CR-2) |
| D3 | Interest is truncated, not rounded. That systematically under-charges by up to 0.01 per category per month. | 28 of 50 golden amounts differ from half-up |
| D4 | The account master's group code is in `ACCT-ADDR-ZIP` and `ACCT-GROUP-ID` is blank. Every lookup misses and uses `DEFAULT`, so the group-specific rates in `DISCGRP` (`A000000000…`) are never used. This is a data defect. | SYSOUT prints `DISCLOSURE GROUP RECORD MISSING` for all 50 (stock) and all 100 (post-posting) lookups |
| D5 | `INVALID KEY` on the account/xref reads displays `ACCOUNT NOT FOUND`, then the status check abends anyway. A missing account stops the whole batch instead of skipping it. | `:372-413` |
| D6 | `WS-TRANID-SUFFIX` is `9(6)` and has no overflow check. More than 999,999 interest transactions in one run would wrap and produce duplicate ids. | `:173, 474` |
| D7 | `ADD`/`COMPUTE` have no `ON SIZE ERROR`, so high-order digits are silently truncated on overflow (balance `S9(10)V99`, interest `S9(9)V99`). | `:352, 464` |
| D8 | Wrong message text: a DISCGRP open failure displays `ERROR OPENING DALY REJECTS FILE`. | `:281` |
| D9 | An account is rewritten (cycle fields zeroed) even when all its rates are zero. Accounts with no TCATBAL rows are never touched. | step 7 logic |

**Migration risks**
- **Decimal arithmetic.** COBOL does fixed-point with an intermediate result. Java must use `BigDecimal` with
  explicit truncation (`RoundingMode.DOWN`), never `double`.
- **Key order.** TCATBALF is read in KSDS key order and ACCTFILE is unloaded in key order. If an input text file
  is not sorted, Java must sort it by key the same way.
- **Uninitialised bytes.** `TRAN-RECORD` is never initialised. Golden shows spaces (default dialect). Under
  `-std=ibm` it would be `x'00'`. Java emits spaces to match golden.
- **Carried-over state.** `TRAN-DESC` and FILLER are not cleared between writes. The fixed-length description
  overwrites the same bytes each time, so this has no visible effect today. Keep it in mind.
- **Clock.** The timestamp uses only hundredths of a second and pads with `0000`. Determinism needs an
  injectable clock.
- **Input quirks.** CRLF input files (`tcatbal.txt`) and short `cardxref.txt` lines (36 bytes) must be loaded the
  way the harness loads them: strip CR, pad to LRECL.

## Questions for the approver

1. **I/O format.** Should the Java program read and write the canonical text files (`<DD>.txt`, as in `golden/`)
   instead of VSAM/EBCDIC?
   *Default: yes.* Datasets are passed as paths named by DD. Each KSDS is loaded into an ordered in-memory map,
   and ACCTFILE is written back in key order.
2. **SYSOUT/RC parity.** The CR asks for byte-identical `TRANSACT` and `ACCTFILE` only.
   *Default: also match `SYSOUT.txt` and the RC (0 / 16 on abend).* It is cheap and gives operators the same log.
3. **Defect D1.** Keep it in this CR, as the CR requires. Should a follow-up CR be raised to fix D1 and D4,
   since they mean real money?
   *Default: yes, raise CR-3 after this one merges.*
