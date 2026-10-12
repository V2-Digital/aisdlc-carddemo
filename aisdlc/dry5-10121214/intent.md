# CR-1 Intent: INTCALC (`CBACT04C`) to Java 21

Run `dry5-10121214` · Stage: Plan · Target `modernized/cbact04c/`

## Problem / outcome

**Problem.** The nightly interest calculation (`app/jcl/INTCALC.jcl`, `app/cbl/CBACT04C.cbl`) only runs as COBOL on the mainframe. That blocks the move of batch workloads off the mainframe.

**Outcome.** A plain Java 21 program that can run in a container and does exactly what the COBOL does. For the same inputs it writes **byte-identical** interest transactions (`TRANSACT`) and an identical updated account master (`ACCTFILE`). This is shown on golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Legacy defects are kept as they are and flagged, not fixed.

## What the legacy code does today

Baseline check: `legacy/harness/run.sh CBACT04C` gave RC 0, 50 TRANSACT records, 50 ACCTFILE records and 152 SYSOUT lines. All four files are byte-identical to `legacy/golden/CBACT04C/`.

### Job and files

The job is `INTCALC.jcl` STEP15, run as `EXEC PGM=CBACT04C,PARM='2022071800'`. The PARM is a 10-character date/run id (`CBACT04C.cbl:176-178`).

| DD | Org / access (`.cbl:28-56`) | LRECL | Key | Use |
|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential, INPUT | 50 | acct(11)+type(2)+cat(4), cols 1-17 | driver: category balances (`CVTRA01Y`) |
| `XREFFILE` | KSDS, random, INPUT | 50 | card 1-16; **AIX** acct 26-36 | card number for the account (`CVACT03Y`) |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct 1-11 | read, then REWRITE (`CVACT01Y`) |
| `DISCGRP` | KSDS, random, INPUT | 50 | group(10)+type(2)+cat(4), cols 1-16 | interest rate `S9(4)V99` at cols 17-22 (`CVTRA02Y`) |
| `TRANSACT` | sequential, OUTPUT, new GDG | 350 | — | interest transactions (`CVTRA05Y`) |

`XREFFIL1` (the AIX path) is not opened by the program. The harness stores the alternate key on `XREFFILE` instead.

### Processing steps

1. **Open** all five files (`.cbl:182-186`). Any status other than `00` abends with RC 16 (`.cbl:234-323`, `628-632`).
2. **Read TCATBALF in key order** (`.cbl:325-348`). Status `10` means EOF; any other non-`00` status abends. Each record is DISPLAYed to SYSOUT (`.cbl:193`).
3. **On an account break** (`.cbl:194-206`), when the account id differs from `WS-LAST-ACCT-NUM`:
   - rewrite the previous account, except on the first record;
   - reset `WS-TOTAL-INT` to 0;
   - read `ACCTFILE` by account id (`.cbl:372-391`);
   - read `XREFFILE` by its alternate key, account id (`.cbl:393-413`).
   If either record is not found, the program DISPLAYs `ACCOUNT NOT FOUND: …` and then abends.
4. **Rate lookup** (`.cbl:210-213`, `415-460`). The key is `ACCT-GROUP-ID` + `TRANCAT-TYPE-CD` + `TRANCAT-CD`. On status `23` (not found) the program DISPLAYs `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retries with group `DEFAULT`. If `DEFAULT` is also missing, it abends.
5. **If the rate ≠ 0** (`.cbl:214-217`):
   - `WS-MONTHLY-INT = TRAN-CAT-BAL × DIS-INT-RATE / 1200` (`.cbl:464-465`). There is no `ROUNDED`, so the result is **truncated** to 2 decimals in `S9(9)V99`.
   - The monthly amount is added to `WS-TOTAL-INT`, and one transaction is written (step 6).
   - `1400-COMPUTE-FEES` is then performed, but it is empty (`.cbl:518-519`).
   If the rate is 0, no transaction is written.
6. **Transaction record** (`.cbl:473-515`):
   | Field | Value |
   |---|---|
   | `TRAN-ID` | PARM-DATE(10) + 6-digit running suffix, e.g. `2022071800000001` |
   | type / category | `01` / `0005` |
   | `TRAN-SOURCE` | `System` |
   | `TRAN-DESC` | `Int. for a/c ` + ACCT-ID |
   | `TRAN-AMT` | monthly interest |
   | merchant fields | id 0, name/city/zip blank |
   | `TRAN-CARD-NUM` | card number from XREF |
   | `TRAN-ORIG-TS`, `TRAN-PROC-TS` | `YYYY-MM-DD-HH.MM.SS.hh0000`, taken from `CURRENT-DATE`, **not** from PARM (`.cbl:613-626`) |
   The rest of `TRAN-DESC` and the FILLER (cols 331-350) are spaces, because the record is never initialised.
7. **Account update** (`.cbl:350-370`):
   - `ACCT-CURR-BAL += WS-TOTAL-INT`;
   - `ACCT-CURR-CYC-CREDIT = 0` and `ACCT-CURR-CYC-DEBIT = 0`;
   - REWRITE the record. Any status other than `00` abends.
   Every account that has TCATBAL records is updated this way, including accounts whose rates are all 0. The exception is the last account (finding F1).
8. **Close** all files and DISPLAY `END OF EXECUTION…`. GOBACK with RC 0.

Evidence from the posted-balance scenario (`golden/CBACT04C-after-CBTRN02C`):
- The DEFAULT rate for type `01`/cat `0001` is 15.00%. For type `03`/cat `0001` it is 0.00%. That gives 100 TCATBAL records, 50 transactions and 100 `MISSING` messages.
- Account 1: balance 1288.10 + interest 14.56 = **1302.66**, and both cycle fields become 0.
- I recomputed all 50 amounts from the inputs, and every one matches truncation. Rounding half-up would give a different result in **28 of 50**.

## Scope

**In**
- A Java 21 rewrite of CBACT04C in `modernized/cbact04c/` (Maven, JUnit 5 only), with the same five logical files.
- It reads and writes the canonical fixed-width text form used by `legacy/golden` and `app/data/ASCII`:
  - overpunch signs;
  - LF output;
  - tolerates CRLF and short input lines (such as the 36-byte `cardxref.txt`), padding them with spaces as the harness loader does.
- The run date PARM and an injectable clock, so the timestamps can be frozen to match golden.
- Equivalence tests against both golden scenarios, plus characterization tests that pin the findings below.

**Out**
- Fixing any legacy defect (F1-F5).
- Implementing fees (CR-2).
- VSAM/GDG data migration, the scheduler (CA7/Control-M), container build and deploy wiring.
- CBTRN02C. Its golden output is used only as input.

## Acceptance criteria

1. `mvn test` in `modernized/cbact04c/` passes on Java 21, with no dependencies other than JUnit 5 and Surefire.
2. With the stock inputs, PARM `2022071800` and clock `2022-07-18 00:00:00.00`, the output `TRANSACT` is byte-identical to `legacy/golden/CBACT04C/TRANSACT.txt` (50 records) and `ACCTFILE` to `…/ACCTFILE.txt` (50 records).
3. With `ACCTFILE` and `TCATBALF` taken from `legacy/golden/CBTRN02C/` and the other inputs from stock data, both outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/` (50 + 50 records).
4. Interest is truncated, not rounded. A test pins at least one case where truncation and rounding differ, including a negative balance.
5. The last-account defect (F1) is preserved: account `00000000050` in scenario 3 is unchanged, even though transaction `2022071800000050` charges it 18.77. A test pins this.
6. A missing group falls back to `DEFAULT`. A rate of 0 produces no transaction, but the account's cycle fields are still zeroed.
7. These conditions end with exit code 16 and no further processing, as in the COBOL: account not found, XREF not found, `DEFAULT` rate missing, and any unexpected I/O error.
8. Output encoding: overpunch signs (`{`/`A-I`/`}`/`J-R`), fixed widths, LF, space-filled unused bytes.
9. *(Pending Q1)* SYSOUT lines and RC are identical to golden `SYSOUT.txt` / `RC.txt` (152 and 302 lines, RC 0).

## Risks and findings

Legacy defects (all to be **preserved**; separate decisions):
- **F1. The last account is never rewritten.** The `ELSE PERFORM 1050-UPDATE-ACCOUNT` at `.cbl:219-220` is unreachable. After EOF the loop test (`.cbl:188`) exits before the body runs again. Evidence: account 50 in `golden/CBACT04C-after-CBTRN02C/ACCTFILE.txt` is byte-identical to `golden/CBTRN02C/ACCTFILE.txt`:
  - its balance stays at `00000019458G` (1945.87);
  - its cycle credit/debit stay at 1501.75 / -47.88;
  - yet its interest transaction was written.
  So 49 of 50 accounts change. On stock data the defect is hidden because all interest is 0.
- **F2. Interest is truncated, not rounded** (`.cbl:464`). 28 of 50 amounts are 1 cent lower than they would be with rounding, and truncation goes toward zero on negative balances.
- **F3. The account group sits in the wrong field in the data.** In `acctdata.txt`, `A000000000` is in `ACCT-ADDR-ZIP` (cols 103-112) and `ACCT-GROUP-ID` is blank. Every lookup misses and uses `DEFAULT`, so group-specific rates are never applied.
- **F4. The timestamp comes from the wall clock, not the PARM date** (`.cbl:614`). Re-running for a past date stamps today's date, while `TRAN-ID` uses the PARM.
- **F5. Arithmetic has no overflow guard.** There is no `ON SIZE ERROR` on the COMPUTE or the ADD, so a value that overflows its PIC silently loses its high-order digits. The PARM length is not validated either. Java needs explicit handling that copies the COBOL truncation; this is not exercised by golden data.
- **F6. Hard-coded values and an empty paragraph.** Type `01` / category `0005` are hard-coded. `1400-COMPUTE-FEES` is empty (CR-2).

Migration risks:
- **Decimal semantics.** Use `BigDecimal` scaled to 2 with `RoundingMode.DOWN`, and copy the COBOL intermediate precision. Signed zero is written as `{` (golden contains `0000000000{`).
- **ACCTFILE is updated in place (I-O).** The Java program must write the full file in key order, including accounts that have no TCATBAL record, which pass through untouched.
- **XREF alternate key.** If an account had several cards, COBOL returns the first one in alternate-key order. The sample data has no duplicates, so this is untested.
- **Input order.** Account breaks depend on TCATBALF being read in key order. Java must sort by the 17-byte key and not trust the file order.

## Questions for the approver

- **Q1. Should SYSOUT and RC also match golden** (record DISPLAYs, `MISSING` messages, `ABENDING PROGRAM` / RC 16)? **Default: yes.** It is cheap, golden already defines it, and it keeps operator runbooks valid.
- **Q2. What I/O contract should the container use?** **Default:** one directory of canonical `<DD>.txt` files in, and `TRANSACT.txt` plus the updated `ACCTFILE.txt` written to an output directory, with no in-place overwrite. This mirrors `run.sh`. Choosing the real storage (DB or object store) is out of scope.
- **Q3. Where does the clock come from?** **Default:** system UTC, overridable through `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`) to match the harness. F4 stays as it is.
