# CR-1 Intent — Modernise INTCALC (`CBACT04C`) to Java 21

Run `5c27f7ac-7a36-4166-a830-3749ca91113d` · Stage: Plan

## Problem / outcome

**Problem.** Nightly interest is calculated by a COBOL batch on the mainframe (`app/jcl/INTCALC.jcl` → `CBACT04C`).
Batch is moving off the mainframe, and this job can't run in a container as it is.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that, given the same inputs and run date, produces
**byte-identical** interest transactions (`TRANSACT`) and an updated account master (`ACCTFILE`) to the COBOL. That
includes the COBOL's known defects. We prove it on both golden scenarios, and characterization tests pin the defects.

## What the legacy code does today

Evidence: I ran `legacy/harness/run.sh CBACT04C`. RC 0, 50 TRANSACT, 50 ACCTFILE, 152 SYSOUT lines, all
byte-identical to `legacy/golden/CBACT04C/`. Run time 0.4 s.

### Files (JCL `INTCALC.jcl:22-42`, SELECTs `CBACT04C.cbl:28-56`)

| DD | Org / access | LRECL | Key | Copybook | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential, INPUT | 50 | acct(11)+type(2)+cat(4), cols 1-17 | `CVTRA01Y` | driver: one balance per acct/type/category |
| `XREFFILE` | KSDS, random, INPUT | 50 | card(16). Read by **alt key** acct-id, cols 26-36 | `CVACT03Y` | card number for the transaction |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct-id(11) | `CVACT01Y` | read, then REWRITE balance |
| `DISCGRP` | KSDS, random, INPUT | 50 | group(10)+type(2)+cat(4) | `CVTRA02Y` | interest rate `DIS-INT-RATE` S9(4)V99 |
| `TRANSACT` | sequential, OUTPUT (new GDG) | 350 | — | `CVTRA05Y` | generated interest transactions |

PARM: `'2022071800'`, a 10-char run date (`PARM-DATE`, `:176-178`). The harness freezes the clock at `2022071800000000`.

### Processing steps

1. Open all 5 files. Any status ≠ `00` → message + abend (`:234-323`).
2. Read TCATBALF sequentially and DISPLAY each 50-byte record to SYSOUT (`:190-193`).
3. **Account break** when `TRANCAT-ACCT-ID ≠ WS-LAST-ACCT-NUM` (`:194-206`): if this isn't the first account,
   rewrite the previous one (`1050`). Then reset `WS-TOTAL-INT`, read ACCTFILE by acct-id (`1100`), and read
   XREFFILE by alt key acct-id (`1110`).
4. **Rate lookup** `1200` (`:210-213`, `:415-460`): key = `ACCT-GROUP-ID` + tran type + category. On status `23`,
   DISPLAY `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and retry with group `DEFAULT`.
   If the `DEFAULT` read also fails → abend.
5. If the rate ≠ 0 (`:214-217`):
   - **Interest** `1300` (`:464-467`): `WS-MONTHLY-INT = TRAN-CAT-BAL * DIS-INT-RATE / 1200`, PIC S9(9)V99,
     **truncated** (no `ROUNDED`). The result is added to `WS-TOTAL-INT`.
   - **Write tran** `1300-B` (`:473-515`):
     - `TRAN-ID` = PARM-DATE + 6-digit counter. The counter runs across the whole run, not per account.
     - type `01`, cat `0005`, source `System`.
     - desc `Int. for a/c ` + ACCT-ID. The rest of the 100 bytes is spaces.
     - amt = monthly interest. Merchant id 0, merchant name/city/zip spaces. Card = `XREF-CARD-NUM`.
     - orig-ts and proc-ts = `CURRENT-DATE` formatted `YYYY-MM-DD-HH.MM.SS.hh0000` (`:613-626`).
     - FILLER (cols 331-350) is never initialised, so it's spaces.
   - **Fees** `1400` (`:518-520`): empty paragraph, does nothing.
6. **Account update** `1050` (`:350-370`): `ACCT-CURR-BAL += WS-TOTAL-INT`, cycle credit and debit set to 0, then
   REWRITE. No `ON SIZE ERROR`, so an overflow would truncate silently.
7. At EOF, close the files, DISPLAY `END OF EXECUTION…`, GOBACK with RC 0 (`:224-232`).
8. Abend path `9999` (`:628-632`): `ABENDING PROGRAM`, `CEE3ABD` U999. The harness stub exits with **RC 16**.
   File status is displayed as `FILE STATUS IS: NNNN00xx` (`:635-648`).

### Business rules, verified against golden data

- **Truncation, not rounding:** I recomputed all 50 amounts in `CBACT04C-after-CBTRN02C/TRANSACT.txt` as
  `trunc(bal × 15 / 1200)`. All 50 match, and 48 of them needed truncation.
- **Default group:** every lookup falls back to `DEFAULT`. `ACCT-GROUP-ID` is blank in the sample data (the
  `A000000000` code sits in `ACCT-ADDR-ZIP`). DEFAULT rates: 01/0001 = 15.00%. 03/0001 = 0%, so that category
  writes no transaction.
- In the after-CBTRN02C scenario, TCATBALF has 100 records (50 accts × {01/0001, 03/0001}) → 50 transactions.
  49 accounts have balance = old + interest with cycle credit/debit = 0, and all other bytes are unchanged.
- **A zero rate still updates the account:** the rewrite happens on every account break, whether or not any
  interest was written. 03/0001 has a 0% rate, so it writes no transaction, but its account is still rewritten.
- Stock run: all 50 TCATBAL balances are 0 at a 15% rate, giving 50 transactions of amount `0000000000{`.
  `ACCTFILE` comes out byte-identical to `acctdata.txt`, because cycle fields are already 0 and only 49 rewrites happen.

## Scope

**In**
- A Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only), runnable as a CLI or in a container.
- Inputs and outputs use the harness's canonical text format: fixed-width lines, LF, overpunch zoned decimals.
  ACCTFILE is written in key order.
- Run date (PARM) and clock can be injected, so outputs are deterministic.
- All business rules and defects above, preserved exactly.
- An equivalence test against both golden scenarios, plus characterization tests for the defects.
- SYSOUT and RC parity on the golden scenarios (see Q2).

**Out**
- Fixing any defect (separate CR). Fee computation (`1400`, CR-2).
- VSAM/EBCDIC binary I/O, JCL/scheduler (CA7/Control-M) changes, CBTRN02C, deployment plumbing.

## Acceptance criteria

1. `mvn -f modernized/cbact04c test` passes on Java 21, with JUnit 5 + Surefire as the only dependencies.
2. Scenario `CBACT04C` (stock inputs from `app/data/ASCII`, PARM `2022071800`, clock `2022-07-18 00:00:00.00`):
   `TRANSACT.txt` (50 records) and `ACCTFILE.txt` (50 records) are byte-identical to `legacy/golden/CBACT04C/`.
3. Scenario `CBACT04C-after-CBTRN02C`: inputs are ACCTFILE and TCATBALF from `legacy/golden/CBTRN02C/`, everything
   else stock. `TRANSACT.txt` and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. On both scenarios, SYSOUT is byte-identical (152 / 302 lines) and the exit code is 0.
5. Interest is truncated toward zero to 2 decimals. A test pins a case where rounding would differ.
6. A characterization test pins the **last-account defect**: account `00000000050` is unchanged in ACCTFILE,
   while TRANSACT still charges it 18.77.
7. A test pins that a missing group falls back to `DEFAULT` with the two SYSOUT lines. A missing DEFAULT → abend.
8. A test pins that a zero rate writes no transaction but the account's cycle fields are still zeroed.
9. A test pins that a missing account or xref → `ACCOUNT NOT FOUND: …`, `ERROR READING …`, `ABENDING PROGRAM`, exit 16.
10. `legacy/harness/golden.sh --check` still passes (legacy is untouched).

## Risks and findings

| # | Finding | Evidence | Treatment |
|---|---|---|---|
| D1 | **Last account never rewritten.** The `ELSE PERFORM 1050` at `:219-220` is unreachable: `PERFORM UNTIL` tests EOF before the body, so the final account's interest and cycle reset are lost. | `after-CBTRN02C`: acct 50 is charged 18.77 (`0000000187G`), yet ACCTFILE is identical to the input (balance `00000019458G`). The other 49 records change. | Preserve + pin (AC6) |
| D2 | **Group id looks misplaced in the data.** `ACCT-GROUP-ID` is blank and the code sits in `ACCT-ADDR-ZIP`, so all accounts get DEFAULT rates. | 150 `DISCLOSURE GROUP RECORD MISSING` lines across the golden runs | Data issue, preserve |
| D3 | **Fees are not implemented.** `1400-COMPUTE-FEES` is empty. | `:518-520` | Out of scope (CR-2) |
| D4 | **Abend leaves partial output.** ACCTFILE rewrites and TRANSACT writes made before an abend are not rolled back. | Control flow `:356`, `:500`, `:628` | Preserve |
| D5 | **Wrong message.** The DISCGRP open error says `ERROR OPENING DALY REJECTS FILE`. | `:281` | Cosmetic, preserve |
| D6 | **Silent overflow.** No size-error handling on the interest or balance arithmetic. | `:352`, `:464` | Preserve COBOL truncation semantics |
| D7 | **Ambiguous xref.** The alt-key read returns the first card when an account has several. | `:394-395`. Sample has 0 duplicate acct ids. | Preserve: first by alt-key order |

Risks
- **Decimal semantics.** We must use `BigDecimal` with truncation that matches COBOL intermediates, and keep
  overpunch encode/decode exact, including `-0` (`}`). Golden data contains negative balances (TCATBAL 03/0001).
- **Uninitialised storage.** Fields the COBOL never sets stay spaces under the default dialect. Java must emit spaces.
- **Coverage is thin.** Golden data only exercises the DEFAULT group, rates 15% and 0%, and a single XREF per
  account. Synthetic tests (AC5–9) cover the rest.
- **Clock.** The timestamp takes hundredths from the clock and appends `0000`. The clock must be injectable.

## Questions for the approver

1. **Defects D1–D7:** confirm they are preserved, not fixed, in this CR. **Default: preserve.** Raise D1 (lost
   interest write-back on the last account) as its own CR. It's a financial-impact defect.
2. **SYSOUT/RC parity:** the CR asks only for TRANSACT/ACCTFILE. **Default: also match SYSOUT and exit codes**
   (0 / 16). It's cheap, and operations log-scraping may rely on it.
3. **I/O format:** **Default: the canonical fixed-width text format** (same as `app/data/ASCII` and the golden
   outputs). Converting from/to VSAM is a platform concern, outside this CR.
