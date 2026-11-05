# CR-1 Intent — INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation (`app/jcl/INTCALC.jcl`, step `STEP15`, program `CBACT04C`) only runs on the mainframe. That blocks moving batch work into containers.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that takes the same inputs and PARM and writes **byte-identical** `TRANSACT` and `ACCTFILE` outputs. This is shown on golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Legacy behaviour, defects included, stays as it is.

## What the legacy code does today

Evidence comes from running `legacy/harness/run.sh CBACT04C /tmp/out1`: RC 0, with 50 TRANSACT, 50 ACCTFILE and 152 SYSOUT lines, identical to `legacy/golden/CBACT04C/`.

### Files (JCL `INTCALC.jcl`; SELECTs at `CBACT04C.cbl:28-57`)

| DD | Org / access | LRECL | Key | Copybook | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential, INPUT | 50 | acct(11)+type(2)+cat(4), cols 1-17 | `CVTRA01Y` | Driver: category balances, read in key order |
| `XREFFILE` | KSDS, random, INPUT | 50 | card num(16). **AIX** acct id, cols 26-36 | `CVACT03Y` | Card number for the account (read via the alternate key) |
| `DISCGRP` | KSDS, random, INPUT | 50 | group(10)+type(2)+cat(4), cols 1-16 | `CVTRA02Y` | Interest rate `DIS-INT-RATE S9(4)V99`, cols 17-22 |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct id(11) | `CVACT01Y` | Read, then REWRITE balance and cycle fields |
| `TRANSACT` | Sequential, OUTPUT | 350 | – | `CVTRA05Y` | New interest transactions |

`XREFFIL1` (the AIX path) is not needed under the harness; see `legacy/README.md`.
PARM is `2022071800` (10 bytes, `PARM-DATE`, `:176-178`).

### Processing steps

1. Display `START OF EXECUTION OF PROGRAM CBACT04C`, then open the 5 files. Any status other than `00` abends (`:181-186`, `:234-323`).
2. Read `TCATBALF` sequentially (`:325-348`). For each record: count it and `DISPLAY` the 50-byte record (`:192-193`).
3. **Account break** when `TRANCAT-ACCT-ID` ≠ last account (`:194-206`). Unless this is the first record, `1050-UPDATE-ACCOUNT` writes back the *previous* account. Then:
   - reset the total interest
   - read `ACCTFILE` by key (`:372-391`)
   - read `XREFFILE` by the acct-id alternate key (`:393-413`)
   - a missing account or xref → `DISPLAY 'ACCOUNT NOT FOUND: '…`, then abend
4. **Rate lookup** (`:210-213`, `:415-460`): key = `ACCT-GROUP-ID` + `TRANCAT-TYPE-CD` + `TRANCAT-CD`.
   - On status `23` (not found), display `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and re-read with group `DEFAULT`.
   - Any other failure abends.
5. **If the rate ≠ 0** (`:214-217`):
   - `monthly = bal × rate / 1200`, **truncated** into `S9(9)V99` (no `ROUNDED`, `:464-465`)
   - add it to the account total (`:467`)
   - write one transaction (`:473-515`)
   - `1400-COMPUTE-FEES` is empty (`:518-520`)
6. **Transaction record** (`:474-498`):
   - `TRAN-ID` = PARM-DATE + 6-digit running suffix (starts at 1)
   - type `01`, category `0005`, source `System`
   - desc `Int. for a/c ` + ACCT-ID
   - amount = monthly interest
   - merchant id 0, merchant name/city/zip spaces
   - card = `XREF-CARD-NUM`
   - orig/proc timestamps `YYYY-MM-DD-HH.MM.SS.hh0000` from `CURRENT-DATE` (`:613-626`)
   - the rest of `TRAN-DESC` and the FILLER at cols 331-350 are spaces (the record is never initialised)
7. **Account update** (`1050`, `:350-370`): `ACCT-CURR-BAL += total`, `ACCT-CURR-CYC-CREDIT = 0`, `ACCT-CURR-CYC-DEBIT = 0`, then REWRITE.
8. At EOF, close all files, display `END OF EXECUTION OF PROGRAM CBACT04C`, and exit with RC 0 (`:224-232`).
9. Abend (`:628-632`): display `ABENDING PROGRAM`, call `CEE3ABD` (999). Under the harness this is **exit 16**.

### Observed on golden data

- **Truncation, not rounding:** acct 2 has balance 2339.97 at 15.00% = 29.2496. The output is `0000000292D` (29.24).
- **Balance update:** acct 1 has 1288.10 + 14.56 = 1302.66 (`00000013026F`), and its cycle fields are zeroed. Compare `golden/CBTRN02C/ACCTFILE.txt` with `golden/CBACT04C-after-CBTRN02C/ACCTFILE.txt`.
- The `DEFAULT` group gives type 01 / cat 0001 a rate of 15.00. Type 03 / cat 0001 has rate 0, so no transaction is written for it. That is why 100 TCATBAL records give 50 transactions.
- Stock scenario: every TCATBAL balance is 0 and the rate is non-zero, so CBACT04C writes 50 **zero-amount** transactions. ACCTFILE comes out identical to `acctdata.txt`.
- Every rate lookup falls back to `DEFAULT`. `ACCT-GROUP-ID` is blank, and the group code sits in `ACCT-ADDR-ZIP` (data quirk). SYSOUT therefore has 152 lines in the stock scenario and 302 in the after-CBTRN02C scenario.

## Scope

**In**
- Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only), runnable as a plain `java` command / in a container.
- Inputs and outputs use the harness's canonical text form: fixed-width lines, LF, overpunch zoned decimals. KSDS files are in key order.
- The same PARM (run date) and an injectable clock for timestamps.
- Reproduce every rule above, including the defects listed below.
- Equivalence tests against both golden scenarios, plus characterization tests for the defects.

**Out**
- Any behaviour fix: the last-account bug, the empty fee logic, the group-id data quirk.
- CBTRN02C, CICS screens, and real VSAM/EBCDIC binary I/O.
- Scheduler (CA7/Control-M) and JCL changes, and deploying to a container platform.

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbact04c/` passes on Java 21 with no dependencies beyond JUnit 5 and Surefire.
2. Scenario `CBACT04C`: inputs are the stock `app/data/ASCII` files, PARM `2022071800`, clock `2022-07-18 00:00:00.00`. `TRANSACT` is byte-identical to `legacy/golden/CBACT04C/TRANSACT.txt` (50 records) and `ACCTFILE` to `…/ACCTFILE.txt` (50 records).
3. Scenario `CBACT04C-after-CBTRN02C`: same as AC2, but with ACCTFILE/TCATBALF taken from `legacy/golden/CBTRN02C/`. Both outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. Interest is truncated toward zero to 2 decimals. A test pins acct 2 → 29.24 (not 29.25).
5. A missing disclosure group falls back to `DEFAULT`. A rate of 0 writes no transaction and adds no interest.
6. Characterization test: the last account in TCATBALF is **not** rewritten, although its transaction **is** written (acct `00000000050`, see Risk R1).
7. `TRAN-ID` = PARM date + zero-padded 6-digit sequence. Timestamps follow `YYYY-MM-DD-HH.MM.SS.hh0000`. Unused bytes are spaces.
8. Missing account, missing xref, missing `DEFAULT` group, or an unreadable file → non-zero exit (16) with `ABENDING PROGRAM` on output. No partial success is reported.
9. SYSOUT is byte-identical to golden `SYSOUT.txt` and RC `0` (see Q1).

## Risks and findings

- **R1 – Defect: last account never rewritten** (`:188-221`). The `ELSE PERFORM 1050-UPDATE-ACCOUNT` at `:219-220` cannot run. `PERFORM UNTIL` tests `END-OF-FILE` before the body, so after EOF the loop exits.
  - Evidence: `golden/CBACT04C-after-CBTRN02C/TRANSACT.txt` record `2022071800000050` charges acct 50 18.77 (`0000000187G`).
  - Yet acct 50 in `ACCTFILE.txt` is byte-identical to `golden/CBTRN02C/ACCTFILE.txt`: only 49 of 50 records change.
  - Impact: the customer is billed a transaction, but the balance is not updated and the cycle totals are not reset. **Preserve it and flag it.**
- **R2 – Fees not implemented:** `1400-COMPUTE-FEES` is an empty paragraph (`:518-520`). Preserve it (CR-2 target).
- **R3 – Zero-amount transactions** are written whenever the rate ≠ 0, even if the balance is 0 (stock scenario: 50 of 50). Preserve.
- **R4 – Data quirk:** the group id is in the wrong field in `acctdata.txt`, so every lookup uses `DEFAULT`. The tests only exercise the fallback path. The direct-hit path is covered by a unit test only.
- **R5 – Numeric edge cases** have no golden coverage:
  - `TRAN-ID` suffix `9(6)` wraps after 999,999 transactions.
  - Fields overflow without `ON SIZE ERROR` (COBOL drops high-order digits).
  - Interest on negative (credit) balances comes out negative, with `J`-`R`/`}` signs.
  - Java must mimic COBOL truncation semantics, so it should use `BigDecimal` with `RoundingMode.DOWN` and modulo field width.
- **R6 – Dialect dependency:** the unset bytes of TRAN-RECORD are spaces under the GnuCOBOL default dialect but `x'00'` with `-std=ibm` / possibly real z/OS. The golden files define spaces.
- **R7 – Timestamps** come from the wall clock (hundredths of a second plus `0000`). Byte-identity needs an injectable clock. In production, every transaction in a run gets its own timestamp.
- **R8 – Error paths** (abend 999 → RC 16) are not exercised by any golden scenario. Equivalence there rests on the code reading, not on evidence.

## Questions for the approver

1. **Is SYSOUT/RC parity required?** The CR names only TRANSACT and ACCTFILE. *Default: yes.* Reproduce SYSOUT byte-for-byte and the RC (0, or 16 on abend), because operators and monitoring may grep it. It is cheap, since the goldens exist.
2. **File interface:** the Java reads and writes the canonical text datasets (one file per DD, path from an env var / arg named after the DD). It does not use VSAM. ACCTFILE is rewritten as a full new file in key order. *Default: yes.* Real dataset transfer and conversion belong to Deploy plumbing.
3. **R1 follow-up:** should a separate CR fix the last-account write-back? *Default: raise it as a new CR after this migration. Do not fix it here.*
