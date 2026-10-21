# CR-1 Intent — INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation runs only as COBOL on the mainframe (`app/jcl/INTCALC.jcl` → `CBACT04C`). That blocks the move of batch work off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. For the same inputs it produces byte-identical interest transactions (`TRANSACT`) and an updated account master (`ACCTFILE`), including the legacy defects. Those defects are pinned by tests and listed below so they can be decided on separately.

## What the legacy code does today

Run: `legacy/harness/run.sh CBACT04C` gives RC 0, TRANSACT 50 recs, ACCTFILE 50 recs, SYSOUT 152 lines. **This matches `legacy/golden/CBACT04C` byte for byte.**

### Files (JCL `INTCALC.jcl:22-41`, SELECTs `CBACT04C.cbl:28-56`)

| DD | Org / access | Open | LRECL | Key | Layout |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | INPUT | 50 | acct(11)+type(2)+cat(4) | `CVTRA01Y`: balance `S9(9)V99` at col 18 |
| `XREFFILE` | KSDS, random by **alt key** acct id (col 26, len 11) | INPUT | 50 | card num(16) | `CVACT03Y` |
| `ACCTFILE` | KSDS, random | **I-O** (REWRITE) | 300 | acct id(11) | `CVACT01Y` |
| `DISCGRP` | KSDS, random | INPUT | 50 | group(10)+type(2)+cat(4) | `CVTRA02Y`: rate `S9(4)V99` at col 17 |
| `TRANSACT` | sequential, new | OUTPUT | 350 | – | `CVTRA05Y` |

PARM `'2022071800'` → `PARM-DATE X(10)` (`:176-178`). The length halfword is ignored. `XREFFIL1` (AIX path) is not referenced by the program.

### Processing steps

1. Open all five files. Any status other than `00` → DISPLAY message + `FILE STATUS IS: NNNNxxxx` → abend U999 (harness RC **16**) (`:234-323`, `:628-648`).
2. Read TCATBAL sequentially in key order (`:325-348`). DISPLAY each 50-byte record to SYSOUT (`:193`).
3. **Control break on account id** (`:194-206`). When the account changes: rewrite the *previous* account (skipped the first time), reset total interest to 0, read ACCTFILE by id (`:372-391`), then read XREF by acct-id alt key (`:393-413`). Not found → abend.
4. Look up the interest rate in DISCGRP by key (`ACCT-GROUP-ID`, `TRANCAT-TYPE-CD`, `TRANCAT-CD`) (`:210-213`). On status `23` it DISPLAYs `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and retries with group `DEFAULT`. If the retry also fails → abend (`:415-460`).
5. If the rate ≠ 0 (`:214`):
   - **Interest:** `WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200`, **truncated** to 2 dp (no `ROUNDED`) into `S9(9)V99` (`:464-465`). Added to the account total (`:467`).
   - **Write one transaction** (`:473-515`):
     - `TRAN-ID` = PARM-DATE + 6-digit running suffix (global, starts at 1, increments only for written txns)
     - type `01`, cat `0005`, source `System`, desc `Int. for a/c <ACCT-ID>`
     - amount = monthly interest, merchant id 0, merchant name/city/zip spaces
     - card = XREF card num
     - orig/proc timestamps = `YYYY-MM-DD-HH.MM.SS.hh0000` from `CURRENT-DATE` (`:613-626`)
   - Perform `1400-COMPUTE-FEES`, which is empty (`:518-520`).
6. **Account update** (`1050`, `:350-370`): `ACCT-CURR-BAL += total interest`, `ACCT-CURR-CYC-CREDIT = 0`, `ACCT-CURR-CYC-DEBIT = 0`, REWRITE. This applies to every account in TCATBAL, even with zero interest. Accounts not in TCATBAL are untouched.
7. Close all files and DISPLAY start/end banners (`:181`, `:230`). RC 0.

### Evidence from the golden scenarios

- **`CBACT04C` (stock):**
  - All 50 TCATBAL rows are `01/0001` with zero balance. All 50 accounts fall back to `DEFAULT` (rate 15.00).
  - This writes 50 zero-amount txns. ACCTFILE is identical to `acctdata.txt`.
- **`CBACT04C-after-CBTRN02C`:**
  - 100 TCATBAL rows: 50 × `01/0001` (rate 15.00) and 50 × `03/0001` (DEFAULT rate 0 → no txn).
  - This writes 50 txns, e.g. acct 1: `0000000145F` = 14.56.
  - All 50 amounts equal `bal × 15 / 1200` **truncated**. Rounding would change **28 of 50**. This was checked with a script against `CBTRN02C/TCATBALF.txt`.
  - 49 of 50 ACCTFILE records changed. Account 50 did not (see D1).

## Scope

**In**
- Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only). It reproduces steps 1-7 on the harness's canonical text datasets (fixed-width lines, LF, EBCDIC overpunch). Same DD names, PARM date, frozen-clock behaviour.
- Byte-identical `TRANSACT.txt` and `ACCTFILE.txt` for both CBACT04C golden scenarios. Matching `RC` (0, and 16 on abend). Matching `SYSOUT` lines, since they are cheap and help with operations.
- Characterization tests that pin every defect in the list below.

**Out**
- Fixing any legacy defect (D1-D5).
- Implementing fees (CR-2).
- CBTRN02C, real VSAM/EBCDIC binary I/O, JCL/scheduler changes, container image and deploy plumbing.

## Acceptance criteria

1. `mvn -f modernized/cbact04c test` passes on Java 21 with JUnit 5 + Surefire as the only dependencies.
2. On stock inputs with PARM `2022071800` and clock `2022-07-18 00:00:00.00`, the Java `TRANSACT.txt` and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C/`. Both files have 50 records.
3. On inputs `ACCTFILE` + `TCATBALF` from `legacy/golden/CBTRN02C/` (all other inputs stock), the outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. For both scenarios, SYSOUT lines and the exit code match golden `SYSOUT.txt` / `RC.txt`.
5. Interest is truncated, not rounded. A test covers a balance where the two differ, e.g. 3577.41 × 15 / 1200 → 44.71.
6. The last account in TCATBAL is **not** rewritten (D1), and a test asserts this.
7. A missing group falls back to `DEFAULT`. If `DEFAULT` is also missing, or an account or XREF record is missing, the run ends with exit code 16 and the legacy messages.
8. The timestamp source and the PARM date are injectable, so the run is deterministic, as with `COB_CURRENT_DATE` in the harness.
9. `legacy/harness/golden.sh --check` still passes, and nothing under `legacy/golden`, `legacy/harness` or `.github` is changed.

## Risks and findings

### Legacy defects (preserve; separate decision)

| # | Defect | Evidence |
|---|---|---|
| **D1** | **The last account's interest is never written back.** The `ELSE PERFORM 1050` (`:219-220`) is unreachable, because `PERFORM UNTIL` is tested before the body and EOF is set inside it. The last account keeps its old balance and cycle fields, but its interest txn *is* written. | Golden after-CBTRN02C: txn `2022071800000050` charges acct 50 18.77 (`0000000187G`). Acct 50 is unchanged vs `CBTRN02C/ACCTFILE.txt` (bal `00000019458G` = 1945.87). |
| D2 | Interest is truncated, not rounded. This systematically under-charges by up to 0.01 per category. | 28/50 golden amounts would differ under ROUND_HALF_UP. |
| D3 | Cycle credit/debit are zeroed for every account in TCATBAL, even when no interest is charged. | `:353-354`. Visible in 49 changed records. |
| D4 | Negative category balances would produce negative interest (a credit). | Formula `:464-465`. No negative balances in the golden data, so this is untested. |
| D5 | Wrong error text: the DISCGRP open failure says `ERROR OPENING DALY REJECTS FILE`. | `:281` |
| – | `1400-COMPUTE-FEES` is empty. | `:518-520` (CR-2) |

### Data findings

- The group code sits in `ACCT-ADDR-ZIP` and `ACCT-GROUP-ID` is blank. Every lookup therefore uses `DEFAULT`, so the account-specific group paths (`A000000000`, `ZEROAPR`) are never exercised by golden. Unit tests must cover the hit path.

### Byte-exactness risks for the rewrite

- Overpunch encoding (`{`/`}`).
- TRAN-ID suffix behaviour.
- Spaces in the uninitialised tail of `TRAN-DESC` and in FILLER cols 331-350. The default dialect gives spaces.
- `S9(9)V99` overflow truncation, which is not reachable in the golden data.
- XREF alt-key duplicates: the first record wins. There are 0 duplicate account ids in `cardxref.txt`.
- The ACCTFILE output must stay in key order and include untouched records.

## Questions for the approver

1. **SYSOUT parity:** should the Java job reproduce the COBOL DISPLAY lines exactly, or only TRANSACT/ACCTFILE/RC? *Default: reproduce exactly (AC 4). It costs little and lets operators compare runs.*
2. **I/O format:** is it acceptable for the Java program to read and write the canonical fixed-width text datasets (the harness's unload format), not VSAM? *Default: yes. Conversion to the target storage belongs to the deploy/data-migration work.*
3. **D1-D5:** we confirm they are preserved in CR-1 and raised as follow-up CRs. D1 is a material financial defect: one account per run is not billed in the master. *Default: preserve now, and open a priority CR for D1 straight after cut-over.*
