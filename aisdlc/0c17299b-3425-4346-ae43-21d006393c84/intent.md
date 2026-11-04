# CR-1 Intent — Modernise INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation (`INTCALC`) only runs as COBOL on the mainframe. That keeps a
batch workload on the platform we are leaving, and only COBOL specialists can change it.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. Given the same input
files it writes the same interest transactions and the same updated account master, byte for byte. The
golden scenarios are the proof. Behaviour stays the same, defects included. Defects are pinned by tests and
listed below for a separate decision.

## What the legacy code does today

Job `app/jcl/INTCALC.jcl:22`: `EXEC PGM=CBACT04C,PARM='2022071800'`. The PARM (10 chars) becomes the
transaction-id prefix (`CBACT04C.cbl:176-178`).

### Files

| DD | Org / access | LRECL | Key | Copybook | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential, INPUT | 50 | acct(11)+type(2)+cat(4), off 0 len 17 | `CVTRA01Y` | driver: balance per account/category (`:28-32`) |
| `XREFFILE` | KSDS, random, INPUT | 50 | card(16); **alt key** acct-id off 25 len 11 | `CVACT03Y` | card number for the account (`:34-39`) |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct-id(11) | `CVACT01Y` | read, then REWRITE (`:41-45`, `:291`) |
| `DISCGRP` | KSDS, random, INPUT | 50 | group(10)+type(2)+cat(4) | `CVTRA02Y` | interest rate `DIS-INT-RATE S9(4)V99` (`:47-51`) |
| `TRANSACT` | sequential, OUTPUT | 350 | — | `CVTRA05Y` | new interest transactions (`:53-56`, `:309`) |

`XREFFIL1` (AIX path) is in the JCL but the program never names it. The harness serves the alt key directly.

### Processing steps

1. DISPLAY start banner. Open the 5 files. If an open status is not `00`, show the error and file status,
   then abend (`:181-186`, `:234-323`).
2. Read `TCATBALF` in key order. For each record (`:188-222`):
   - Count it and DISPLAY the raw 50-byte record (`:192-193`).
   - **Account break** (`acct-id ≠ last`, `:194-206`): if this is not the first account, rewrite the previous
     account (step 4). Then set total interest to 0, read `ACCTFILE` by acct-id (`:372-391`), and read
     `XREFFILE` by the alt key acct-id (`:393-413`). Not found means abend.
   - **Rate lookup** (`:210-213`, `:415-460`): key = `ACCT-GROUP-ID` + type + cat. On status `23` it DISPLAYs
     `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and retries with group `DEFAULT`. If
     `DEFAULT` is also missing, it abends.
   - **If rate ≠ 0** (`:214-217`): `monthly = (TRAN-CAT-BAL × rate) / 1200` into `S9(9)V99`, **truncated,
     not rounded** (`:464-465`). Add it to the account total (`:467`), write one transaction (step 3), then
     PERFORM `1400-COMPUTE-FEES`, which is empty (`:518-520`).
3. **Write transaction** (`:473-515`):
   - `TRAN-ID` = PARM-DATE + 6-digit running suffix starting at 000001.
   - Type `01`, cat `0005`, source `System`.
   - Desc `Int. for a/c ` + 11-digit acct-id, padded with spaces.
   - Amount = monthly interest. Merchant id 0, name/city/zip spaces. Card number from xref.
   - Orig and proc timestamps = `YYYY-MM-DD-HH.MM.SS.hh0000` from `CURRENT-DATE` (`:613-626`).
   - The 20-byte FILLER stays spaces (never initialised).
4. **Update account** (`:350-370`): `ACCT-CURR-BAL += total interest`, `CURR-CYC-CREDIT = 0`,
   `CURR-CYC-DEBIT = 0`, then REWRITE. A status other than `00` means abend.
5. At EOF, close all files. DISPLAY end banner. RC 0 (`:224-232`).
6. **Abend** (`:628-648`): DISPLAY `ABENDING PROGRAM`, then `CALL 'CEE3ABD'` with code 999. The harness stub
   prints `CEE3ABD: ABEND U0999` and exits **16**.

### Observed behaviour (harness run, PARM `2022071800`, clock frozen 2022-07-18)

- `legacy/harness/run.sh CBACT04C`: TRANSACT 50, ACCTFILE 50, SYSOUT 152, RC 0. **All byte-identical to
  `legacy/golden/CBACT04C`.**
- Stock `tcatbal.txt` has 50 records, all type 01 / cat 0001 with balance 0. Result: 50 transactions of
  amount 0, and ACCTFILE identical to `acctdata.txt`.
- `golden/CBACT04C-after-CBTRN02C`: 100 TCATBAL records (50 × `01/0001`, rate 15.00; 50 × `03/0001`, rate 0).
  It writes 50 transactions with ids `2022071800000001`…`…050`. SYSOUT has 302 lines (100 × "MISSING" pairs).
- I checked all 50 amounts in that scenario: each equals `trunc(bal × 15 / 1200)`. Rounding would match only
  22 of them, so truncation is confirmed.

## Scope

**In**
- A Java 21 rewrite of CBACT04C in `modernized/cbact04c/` (Maven, JUnit 5, no frameworks).
- It reads and writes the harness's canonical text form (fixed width, LF, overpunch zoned decimals) for the
  DDs `TCATBALF`, `XREFFILE`, `ACCTFILE`, `DISCGRP` → `TRANSACT`, `ACCTFILE`.
- The PARM date and an injectable clock, so the timestamps can be reproduced.
- Equivalence tests against both golden scenarios.
- Characterization tests for the defects and edge rules below.

**Out**
- Fixing any legacy defect.
- Implementing fees (`1400-COMPUTE-FEES`, CR-2).
- CBTRN02C.
- JCL, scheduler (CA7/Control-M) and deployment changes.
- VSAM/EBCDIC binary I/O.
- Changes to `legacy/` or the golden files.

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbact04c/` passes on Java 21. The only dependencies are JUnit 5 and
   Surefire.
2. Scenario `CBACT04C`: on the stock inputs with PARM `2022071800` and clock `2022071800000000`, the Java
   `TRANSACT.txt` and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C/` (50 + 50 records).
3. Scenario `CBACT04C-after-CBTRN02C`: with `ACCTFILE`/`TCATBALF` taken from `legacy/golden/CBTRN02C/`, both
   outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. On success the exit code is 0, matching `RC.txt`.
5. Interest is `trunc((bal × rate) / 1200)` to 2 decimals, truncated toward zero. A unit test covers a
   fractional case and a negative balance.
6. A missing group key falls back to `DEFAULT`. A missing `DEFAULT` record, account or xref makes the program
   exit with 16, as the legacy abend does.
7. A characterization test pins **D1**: the last account's interest is not written back to ACCTFILE.
8. `legacy/harness/golden.sh --check` still passes (the legacy side is untouched).

## Risks and findings

**Defects (preserve; separate decision needed)**
- **D1 — Last account is never updated.** After EOF the loop exits before its `ELSE PERFORM
  1050-UPDATE-ACCOUNT` (`:188-189`, `:219-220`) can run. Evidence:
  - In `after-CBTRN02C`, account `00000000050` is charged 18.77 (`0000000187G`, tran `2022071800000050`).
  - Yet its ACCTFILE record is identical to `golden/CBTRN02C/ACCTFILE.txt`: balance still 1945.87, cycle
    fields not zeroed.
  - Only 49 of 50 records change.
  - The defect is invisible on stock data because all interest there is zero.
- **D2 — Truncation, not rounding** (`:464`, no `ROUNDED`). On real data 28 of 50 amounts lose a fraction of
  a cent compared with rounding.
- **D3 — Silent overflow.** `WS-MONTHLY-INT`/`WS-TOTAL-INT` are `S9(9)V99`, and the account balance is
  `S9(10)V99`. There is no `ON SIZE ERROR`, so high-order digits are dropped silently. The golden data does
  not exercise this. Java must truncate the same way (a test pins it).
- **D4 — Cycle fields are zeroed whether or not interest is charged**, including when every rate is 0
  (`:353-354`). This is a business-rule question rather than a crash.
- **D5 — Wrong error text.** The DISCGRP open error says `ERROR OPENING DALY REJECTS FILE` (`:281`).
  Cosmetic.
- `TRAN-ID` suffix `9(6)` wraps after 999 999 transactions. This is out of reach on current volumes.

**Data findings**
- `ACCT-GROUP-ID` is blank in all sample accounts: the group code sits in `ACCT-ADDR-ZIP`. As a result every
  lookup falls back to `DEFAULT`, and the real group rates are never exercised by the golden scenarios.

**Migration risks**
- Overpunch sign encoding.
- Fixed-width padding.
- CRLF in `tcatbal.txt`: strip it on read as the harness does.
- `cardxref.txt` lines are 36 bytes and must be padded to 50.
- Timestamp hundredths come from the clock, so the clock must be injectable.
- Uninitialised fields must be written as spaces.
- The rewrite assumes the account-break logic receives TCATBAL in key order. Java must sort or require
  sorted input, the way a KSDS delivers it.

## Questions for the approver

1. **SYSOUT parity.** The CR requires TRANSACT and ACCTFILE. Should the Java DISPLAY log also match
   `SYSOUT.txt` byte for byte? *Default: yes. It is cheap, and it is good evidence for operations.*
2. **I/O contract.** *Default: an input directory and an output directory of canonical `<DD>.txt` files (the
   harness format), plus the PARM date as an argument. A clock override variable (`CARDDEMO_CURRENT_DATE`)
   makes runs reproducible.* VSAM/EBCDIC conversion stays with the data-migration workstream.
3. **D1–D4.** These are preserved in CR-1 as instructed. *Default: open a follow-up CR for D1 (a financial
   misstatement on the last account each night). D2/D3 need a business rule decision.*
