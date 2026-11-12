# CR-1 Intent — INTCALC (`CBACT04C`) to Java 21

## Problem
The nightly interest calculation (`app/jcl/INTCALC.jcl`, step `STEP15`, program `app/cbl/CBACT04C.cbl`) runs only on the mainframe. That blocks moving batch workloads into containers.

## Outcome
A plain Java 21 program in `modernized/cbact04c/` runs in a container. For the same inputs it produces the same interest transactions and the same updated account master as the COBOL job, byte for byte, including the job's known defects. Those defects are pinned by tests and flagged for a separate decision.

## What the legacy code does today

### Files (DD names from `INTCALC.jcl:27-43`)
| DD | Org / access | LRECL | Key | Mode | Copybook |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | 50 | acct(11)+type(2)+cat(4), cols 1-17 | INPUT | `CVTRA01Y` |
| `XREFFILE` | KSDS, random by **AIX** acct id (cols 26-36, NONUNIQUE) | 50 | card num, cols 1-16 | INPUT | `CVACT03Y` |
| `ACCTFILE` | KSDS, random | 300 | acct id, cols 1-11 | **I-O** (REWRITE) | `CVACT01Y` |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4), cols 1-16 | INPUT | `CVTRA02Y` |
| `TRANSACT` | sequential, new GDG `SYSTRAN(+1)` | 350 | — | OUTPUT | `CVTRA05Y` |

`PARM='2022071800'` (10 characters) is the transaction-id prefix (`CBACT04C.cbl:176-178`).

Key fields: `TRAN-CAT-BAL` S9(9)V99 at cols 18-28. `DIS-INT-RATE` S9(4)V99 at cols 17-22. `ACCT-CURR-BAL` cols 13-24, `ACCT-CURR-CYC-CREDIT` 79-90 and `ACCT-CURR-CYC-DEBIT` 91-102 (all S9(10)V99). `ACCT-GROUP-ID` cols 113-122.

### Processing (control break on account id)
1. Open all five files. Any status other than `00` → DISPLAY the error and file status, then abend U0999 (`:234-323`, `:628-632`).
2. Read TCATBALF sequentially. DISPLAY each 50-byte record to SYSOUT (`:190-193`).
3. **When the account changes** (`:194-206`): rewrite the previous account (except on the first record), reset the account total to 0, read ACCTFILE by account id, and read XREFFILE by the AIX account id. Either one not found → DISPLAY `ACCOUNT NOT FOUND: …`, status 23 → abend (`:372-413`).
4. **Rate lookup** (`:210-213`, `:415-460`): read DISCGRP with key (`ACCT-GROUP-ID`, type, cat). On status `23`, DISPLAY `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and re-read with group `DEFAULT`. If `DEFAULT` is also missing → abend.
5. **If the rate ≠ 0** (`:214-217`): monthly interest = `TRAN-CAT-BAL × DIS-INT-RATE / 1200`, **truncated** to 2 decimals (no `ROUNDED`, no `ON SIZE ERROR`) (`:464-465`). Add it to the account total (`:467`), then write a transaction (step 6). Then PERFORM `1400-COMPUTE-FEES`, which is empty (`:518-520`).
6. **Interest transaction** (`:473-515`):
   - `TRAN-ID` = PARM-DATE + 6-digit running suffix (starts at 000001, one counter for the whole run)
   - type `01`, cat `0005`, source `System`
   - desc `Int. for a/c ` + `ACCT-ID`, padded with spaces
   - amount = monthly interest
   - merchant id 0, merchant name/city/zip spaces
   - card = `XREF-CARD-NUM`
   - orig and proc timestamps = `YYYY-MM-DD-HH.MM.SS.hh0000` from CURRENT-DATE (`:613-626`)
   - FILLER cols 331-350 = spaces (never initialised)
7. **Account update** (`:350-370`): `ACCT-CURR-BAL += total`, cycle credit = 0, cycle debit = 0, REWRITE.
8. At EOF, close all files, DISPLAY start/end banners, RC 0.

### Observed behaviour (`legacy/harness/run.sh CBACT04C`, matches golden byte for byte)
- Stock data: 50 TCATBAL records, 50 TRANSACT records (all amount 0, because every stock balance is 0), ACCTFILE unchanged, SYSOUT 152 lines, RC 0.
- `CBACT04C-after-CBTRN02C`: 100 TCATBAL records (50 accounts × types 01/0001 and 03/0001). Only `DEFAULT/01/0001` has a non-zero rate (15.00%), so the run writes 50 transactions. 49 of the 50 accounts change. SYSOUT is 302 lines, RC 0.
- Rounding check (my script): all 50 amounts equal the truncated `bal×rate/1200`. Half-up rounding would differ in **28 of 50**.

## Scope
**In:** a Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only) that reproduces steps 1-8. Inputs and outputs use the harness's canonical fixed-width text format (`app/data/ASCII` / `legacy/golden`). It must support PARM and a frozen clock, and match SYSOUT and the return code. Characterization tests cover the defects listed below. There is also an equivalence test against both golden scenarios.

**Out:**
- Fixing any legacy defect.
- Fees logic (`1400-COMPUTE-FEES`, CR-2).
- CBTRN02C.
- Real VSAM/EBCDIC binary I/O, JCL/scheduler changes, container image and deploy plumbing.

## Acceptance criteria
1. `mvn -f modernized/cbact04c verify` passes using only JUnit 5 and Surefire.
2. Scenario `CBACT04C` (stock inputs, PARM `2022071800`, clock `2022-07-18 00:00:00.00`): Java `TRANSACT.txt` (50 records) and `ACCTFILE.txt` (50) are byte-identical to `legacy/golden/CBACT04C/`.
3. Scenario `CBACT04C-after-CBTRN02C` (ACCTFILE and TCATBALF from `golden/CBTRN02C`): `TRANSACT.txt` (50) and `ACCTFILE.txt` (50) are byte-identical to the golden files.
4. `SYSOUT.txt` (152 / 302 lines) and the return code (`0`) also match for both scenarios.
5. Interest is truncated toward zero to cents. A test pins a case where truncation and rounding differ (golden: account 2, balance 2339.97 × 15.00 / 1200 = 29.249625 → `0000000292D` = 29.24; half-up would give 29.25).
6. The last-account defect is pinned: the last account's interest transaction is written, but its ACCTFILE record is not updated (golden account `00000000050`).
7. The disclosure-group fallback to `DEFAULT` is reproduced, including the two SYSOUT lines. A missing `DEFAULT` row, a missing account or a missing xref gives the abend path: error DISPLAYs, then `ABENDING PROGRAM`, and a non-zero exit (16, matching the harness CEE3ABD stub).
8. Accounts with no TCATBAL record and categories with a rate of 0 cause no write and no transaction.

## Risks and findings
- **D1 — Last account never rewritten (defect, keep it).** The `ELSE PERFORM 1050-UPDATE-ACCOUNT` at `:219-220` can't be reached: the loop `UNTIL END-OF-FILE='Y'` (`:188`) exits as soon as the read sets EOF. Evidence: golden TRANSACT `2022071800000050` charges 18.77 to account 50, but in ACCTFILE account 50 is byte-identical to the CBTRN02C input (balance `00000019458G`, cycle fields not zeroed).
- **D2 — Interest truncates, doesn't round** (`:464`). This undercharges by up to 0.01 per category per month. Keep it.
- **D3 — Disclosure-group key comes from `ACCT-GROUP-ID`, which is blank in all sample data.** The group code sits in `ACCT-ADDR-ZIP` instead, so every lookup falls back to `DEFAULT` (100 "MISSING" messages in scenario 3). The group-specific rates in `discgrp.txt` (`A000000000`, `ZEROAPR`) are never exercised by the golden data. Keep it, and unit-test the direct-hit path.
- **D4 — Silent overflow.** There is no `ON SIZE ERROR` on the interest COMPUTE or the balance ADD. High-order digits would be truncated. The Java code must mimic this modulo behaviour, not throw.
- **D5 — The transaction-id suffix is `9(6)`.** It wraps to 000000 after 999,999 transactions in a run, which would give duplicate ids. `PARM-LENGTH` is never checked. Keep it.
- **D6 — Every account that has TCATBAL rows gets its cycle credit and debit zeroed**, even when its interest is 0 (stock run: all fields are already 0, so this is invisible). Accounts with no TCATBAL rows are untouched.
- **D7 — `1400-COMPUTE-FEES` is an empty stub** (CR-2).
- **Format risks:** sign overpunch must be EBCDIC style (`{`, `A-I`, `}`, `J-R`). `cardxref.txt` lines are 36 bytes (pad to 50 on read). `tcatbal`, `trancatg` and `trantype` have CRLF line endings. All inputs are already in key order. The XREF AIX is NONUNIQUE: with duplicates, the first record wins (the sample data has none).
- **Error-message quirk:** a DISCGRP open failure prints `ERROR OPENING DALY REJECTS FILE` (`:281`, copy-paste error). Keep it.

## Questions for the approver
1. **I/O format.** The Java program will read and write the canonical fixed-width text files (one record per line, LF), not VSAM. ACCTFILE is rewritten as a whole file in key order. *Default: yes. VSAM/EBCDIC conversion stays with the migration platform.*
2. **SYSOUT and RC parity.** The CR only requires TRANSACT and ACCTFILE to match. *Default: also match SYSOUT and RC (it costs little and helps the ops runbooks).*
3. **Abend exit code.** *Default: exit 16 with `ABENDING PROGRAM`, matching the harness stub. The mainframe's U0999 has no container equivalent.*
