# CR-1 Intent: INTCALC (`CBACT04C`) to Java 21

## Problem
The nightly interest calculation (`app/jcl/INTCALC.jcl`, step `STEP15`, program `CBACT04C`) runs only on the
mainframe. That blocks the move of batch workloads off the mainframe.

## Outcome
A plain Java 21 program in `modernized/cbact04c/` that runs in a container, reads the same five datasets and
writes a **byte-identical** interest transaction file (`TRANSACT`) and updated account master (`ACCTFILE`). It is a
like-for-like replacement, including the legacy defects, which stay pinned by tests until someone decides to fix them.

## What the legacy code does today

### Files (DD names from `INTCALC.jcl:27-42`)
| DD | Dataset | Access | Record | Key | Copybook |
|---|---|---|---|---|---|
| `TCATBALF` | category balances KSDS | INPUT, sequential (`CBACT04C.cbl:28-32`) | 50 | acct(11)+type(2)+cat(4), 17 @0 | `CVTRA01Y` |
| `XREFFILE` | card xref KSDS | INPUT, random by **alt key** acct-id @25 len 11 (`:34-39`) | 50 | card-num(16) | `CVACT03Y` |
| `ACCTFILE` | account master KSDS | **I-O**, random (`:41-45`) | 300 | acct-id(11) | `CVACT01Y` |
| `DISCGRP` | disclosure group (rates) KSDS | INPUT, random (`:47-51`) | 50 | group(10)+type(2)+cat(4), 16 @0 | `CVTRA02Y` |
| `TRANSACT` | new interest transactions | OUTPUT, sequential, RECFM=F (`INTCALC.jcl:38-42`) | 350 | n/a | `CVTRA05Y` |

`PARM='2022071800'` (10 chars) is used as the transaction-ID prefix (`:176-178`, `:476`).

### Processing
1. Open all files and abend on any status other than `00` (`:182-186`, `:234-323`).
2. Read `TCATBALF` sequentially in key order, so rows arrive grouped by account (`:325-348`). DISPLAY every record (`:193`).
3. **When the account changes** (`:194-206`): if this isn't the first account, rewrite the previous one (1050). Then reset
   the total interest, read `ACCTFILE` by acct-id (`:372-391`) and read `XREFFILE` by alt key acct-id (`:393-413`).
   An account or xref that isn't found displays `ACCOUNT NOT FOUND`, then abends (U999, RC 16).
4. **Rate lookup** (`:210-213`, `:415-460`): key = `ACCT-GROUP-ID` + tran type + tran cat. On status `23` it displays
   `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and retries with group `DEFAULT`. If the
   `DEFAULT` key is also missing, it abends.
5. **If rate ≠ 0** (`:214-217`): monthly interest = `TRAN-CAT-BAL × DIS-INT-RATE / 1200` into `S9(9)V99`, **truncated**
   (no `ROUNDED`) (`:464-465`). Add it to the account total (`:467`), then write one transaction (1300-B, `:473-515`):
   - `TRAN-ID` = PARM-DATE(10) + 6-digit running suffix across the whole run (`:474-479`)
   - type `01`, cat `0005`, source `System`, desc `Int. for a/c <acct-id 11>` padded with spaces (`:482-488`)
   - amount = monthly interest; merchant id 0; merchant name/city/zip spaces; card = `XREF-CARD-NUM` (`:490-495`)
   - `TRAN-ORIG-TS` = `TRAN-PROC-TS` = `YYYY-MM-DD-HH.MM.SS.hh0000` from `CURRENT-DATE`, **not** from PARM (`:613-626`)
   - last 20 bytes FILLER: spaces (uninitialised working storage, default dialect)
   - Then perform `1400-COMPUTE-FEES`, which is empty (`:518-520`).
6. **1050-UPDATE-ACCOUNT** (`:350-370`): `ACCT-CURR-BAL += total interest`, cycle credit = 0, cycle debit = 0,
   REWRITE. All other 300-byte fields are carried through unchanged.
7. At EOF, close all files (`:224-228`), then exit with RC 0.

### Observed behaviour (harness, `legacy/harness/run.sh CBACT04C`)
- The stock run is byte-identical to `legacy/golden/CBACT04C`: 50 TRANSACT, 50 ACCTFILE, 152 SYSOUT lines, RC 0. All
  stock balances are 0, so it writes 50 zero-amount transactions and ACCTFILE equals `acctdata.txt`.
- `CBACT04C-after-CBTRN02C`: 100 TCATBAL rows (50 × `01/0001`, 50 × `03/0001`). Every account misses its group
  (blank `ACCT-GROUP-ID`) and falls back to `DEFAULT`, so 100 MISSING messages appear. `DEFAULT 03/0001` has rate 0, so
  only the 50 `01/0001` rows produce transactions. Worked example: account 1, 1164.87 × 15.00 / 1200 = 14.5608 →
  `0000000145F` (14.56). Balance goes 1288.10 → 1302.66 (`00000013026F`).

## Scope
**In:** Java 21 rewrite of `CBACT04C` in `modernized/cbact04c/` (Maven, JUnit 5). It reads and writes the canonical
fixed-width text format used by `legacy/golden/` and `app/data/ASCII/`, accepts PARM-date and an injectable clock, and
reproduces the TRANSACT and ACCTFILE bytes. Characterization tests cover every quirk listed below.
**Out:** fixing any legacy defect, implementing fees (`1400`, which is CR-2), CBTRN02C, VSAM/EBCDIC binary I/O, JCL
and scheduler changes, and running in production.

## Acceptance criteria
1. `mvn test` passes in `modernized/cbact04c/` with Java 21 and only JUnit 5 + Surefire as dependencies.
2. Run on `app/data/ASCII` inputs, PARM `2022071800` and clock `2022-07-18 00:00:00.00`, the output `TRANSACT.txt` and
   `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C/` (50 + 50 records).
3. Run on ACCTFILE and TCATBALF from `legacy/golden/CBTRN02C/` (other inputs stock), the outputs are byte-identical
   to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. A test pins the last-account defect: account `00000000050` gets transaction `2022071800000050` (18.77) but its
   ACCTFILE record is unchanged.
5. Tests pin: truncation, not rounding, for positive and negative balances; DEFAULT-group fallback; no transaction
   when rate = 0 while the account is still rewritten with cycle fields zeroed; the transaction-ID suffix runs across
   accounts; timestamps come from the clock, not PARM.
6. A missing account, missing xref, or missing DEFAULT rate fails the run with a non-zero exit status (legacy: RC 16)
   and writes no partial-success signal.
7. The existing check `legacy/harness/golden.sh --check` still passes, because legacy is unchanged.

## Risks and findings
- **D1 – Last account never rewritten (confirmed defect).** The `ELSE PERFORM 1050` at `:219-220` can't be reached:
  the `PERFORM UNTIL` tests `END-OF-FILE` before each pass, so the loop ends as soon as `1000` sets it to `Y`.
  Evidence: `golden/CBACT04C-after-CBTRN02C/TRANSACT.txt` charges account 50 interest of 18.77 (`0000000187G`), but
  its ACCTFILE record is byte-identical to `golden/CBTRN02C/ACCTFILE.txt` (49 of 50 records differ). **Customer
  impact:** the interest transaction is posted but the balance isn't updated, and the cycle totals aren't reset for
  the highest-numbered account each night. **Preserve.**
- **D2 – Interest truncated, not rounded** (`:464`). For negative (credit) balances, truncation goes toward zero. The
  golden data doesn't exercise this (0 negative amounts, because negative rows have rate 0). It will be pinned by a
  unit test built from COBOL semantics. Preserve.
- **D3 – Category balances never reset.** `TCATBALF` is opened INPUT, so the same balance accrues interest again on the
  next run unless another job clears it. This is a business finding, not a code bug. Out of scope.
- **D4 – Group lookup always misses on sample data.** `ACCT-GROUP-ID` is blank and the group code sits in
  `ACCT-ADDR-ZIP` (see `legacy/README.md`). This is a data issue, and only the DEFAULT rates are exercised.
- **D5 – `1400-COMPUTE-FEES` is empty.** No fees are charged (CR-2).
- **D6 – Minor:** the 6-digit transaction suffix silently wraps after 999,999 transactions. Timestamps use the wall
  clock, not the business date in PARM. The PARM length isn't validated.
- **Risk – COBOL numeric semantics.** Overpunch signs (`{`/`}`/`A-R`), implied decimals, and intermediate precision
  of `× / 1200` before truncation. Mitigation: use `BigDecimal` with explicit truncation, plus tests on the golden data.
- **Risk – Key ordering.** Legacy relies on KSDS key order. All sample files are already sorted by key (checked). The
  rewrite must process TCATBAL in key order and write ACCTFILE in key order.

## Questions for the approver
1. **When an abend condition occurs (account, xref or DEFAULT rate missing), what does Java leave on disk?** Legacy
   leaves the ACCTFILE rewrites done so far and a partial TRANSACT. *Default:* exit 16 and write no output files
   (atomic). The golden scenarios never hit this path, so byte-identity isn't affected.
2. **Should SYSOUT match too?** The CR only names TRANSACT and ACCTFILE. *Default:* reproduce the same DISPLAY lines
   on stdout and assert them against `SYSOUT.txt` as a bonus check, but they aren't part of acceptance.
