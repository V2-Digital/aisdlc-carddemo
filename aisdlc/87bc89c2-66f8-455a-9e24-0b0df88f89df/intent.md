# CR-1 Intent: Java 21 rewrite of INTCALC / `CBACT04C`

## Problem / outcome

**Problem.** The nightly interest calculation runs only as COBOL on the mainframe (`app/jcl/INTCALC.jcl`, program
`app/cbl/CBACT04C.cbl`). That stops us moving batch workloads off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. Given the same inputs, it
produces byte-identical interest transactions (`TRANSACT`) and an updated account master (`ACCTFILE`). This is
proven on golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Legacy behaviour is kept as it is, defects
included.

## What the legacy code does today

**Job.** `INTCALC.jcl` STEP15 runs `PGM=CBACT04C,PARM='2022071800'`. The PARM is a 10-character date prefix for
transaction IDs (`CBACT04C.cbl:176-178`). Its length is never checked.

### Files

All records are fixed-width DISPLAY fields with trailing overpunch signs. None are COMP or COMP-3.

| DD | Org / access | Open | LRECL | Key (0-based offset, length) | Copybook |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | INPUT | 50 | acct-id(0,11) + type(11,2) + cat(13,4) | `CVTRA01Y` |
| `XREFFILE` | KSDS, random | INPUT | 50 | card-num(0,16); **alt** acct-id(25,11) | `CVACT03Y` |
| `ACCTFILE` | KSDS, random | **I-O** | 300 | acct-id(0,11) | `CVACT01Y` |
| `DISCGRP` | KSDS, random | INPUT | 50 | group(0,10) + type(10,2) + cat(12,4) | `CVTRA02Y` |
| `TRANSACT` | sequential | OUTPUT | 350 | none (written in order) | `CVTRA05Y` |

The SELECTs are at `CBACT04C.cbl:28-55` and the FDs at `:61-92`. Key fields:
- TCATBAL bal `S9(09)V99` @17
- DISCGRP rate `S9(04)V99` @16
- ACCT curr-bal @12, cyc-credit @78, cyc-debit @90 (all `S9(10)V99`), group-id @112
- TRAN amt `S9(09)V99` @132

### Processing steps

1. Open all five files. Any status other than `00` → abend (`:234-323`).
2. Read TCATBAL sequentially in key order (`:325-348`) and DISPLAY each record to SYSOUT (`:193`).
3. **On an account break** (`:194-206`):
   - Rewrite the *previous* account (`1050`). This is skipped for the first account.
   - Reset the total interest to 0.
   - Random-read ACCTFILE by acct-id (`:372-391`).
   - Read XREFFILE by the **alternate key** acct-id to get a card number (`:393-413`).
   - If the account or the xref is missing, DISPLAY `ACCOUNT NOT FOUND`, then abend, because the status is not `00`.
4. **Rate lookup** (`:210-213`, `:415-460`). Read DISCGRP by (ACCT-GROUP-ID, type, cat).
   - Status `23`: DISPLAY `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and retry with group
     `DEFAULT`. If the retry is not `00` → abend.
   - Any status other than `00`/`23` → abend.
5. **If rate ≠ 0** (`:214-217`):
   - Monthly interest = `(TRAN-CAT-BAL * DIS-INT-RATE) / 1200` into `S9(09)V99`. It is **truncated, not rounded**,
     and there is no SIZE ERROR check (`:464-465`).
   - Add it to the account total (`:467`).
   - Write one transaction (step 6).
   - `PERFORM 1400-COMPUTE-FEES`, which is empty (`:518-520`).
6. **Interest transaction** (`:473-515`):
   - TRAN-ID = PARM-DATE(10) + run-wide counter `9(06)`.
   - type `01`, cat `0005`, source `System`.
   - desc `Int. for a/c ` + ACCT-ID.
   - amt = monthly interest.
   - merchant-id 0; name/city/zip spaces.
   - card = XREF-CARD-NUM.
   - ORIG-TS = PROC-TS = `YYYY-MM-DD-HH.MM.SS.hh0000` from `CURRENT-DATE` (`:613-626`).
   - FILLER 331-350 and the rest of the desc are never initialised, so they are spaces (see `legacy/README.md`).
7. **Account update** `1050` (`:350-370`): `CURR-BAL += total interest`, cycle credit = 0, cycle debit = 0, then
   REWRITE.
8. At EOF, close all files and DISPLAY the end banner. RC 0. On abend, `CEE3ABD` U0999 → exit 16 (`:628-632`).

### Observed behaviour (harness run, frozen clock `2022071800000000`)

- **Stock data:** `harness/run.sh CBACT04C` gives TRANSACT 50, ACCTFILE 50, SYSOUT 152, RC 0. All four files are
  byte-identical to `legacy/golden/CBACT04C/`. Every amount is 0, because all stock balances are 0.
- **After CBTRN02C:**
  - TCATBALF has 100 records, 50 accounts × types `01/0001` and `03/0001`.
  - Only `01/0001` has a non-zero rate (`DEFAULT 01 0001` = 15.00%), so there are 50 transactions.
  - Example: `2022071800000001`, a/c 1, amount `0000000145F` (+14.56).
- **Truncation:** recomputing the 50 amounts from `CBTRN02C/TCATBALF` gives 50/50 matches when truncated and only
  22/50 when rounded half-up.
- **Default fallback:** every lookup falls back to `DEFAULT`. This is because the group code `A000000000` is in the
  ZIP field and `ACCT-GROUP-ID` is blank (README quirk).

## Scope

**In**
- A Java 21 Maven project in `modernized/cbact04c/` (JUnit 5 only) that reproduces steps 1–8.
- It reads and writes the harness's canonical text format, with the same DD names.
- PARM date and a clock that can be injected, so outputs are deterministic.
- Characterization tests for each defect listed below.
- A golden equivalence test for both scenarios.

**Out**
- Fixing any defect.
- Fees (`1400` stays a no-op; that is CR-2).
- CBTRN02C.
- Real VSAM/EBCDIC binary I/O.
- JCL or scheduler changes.
- Container image and deploy plumbing (Deploy stage).

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbact04c/` passes with only JUnit 5 + Surefire as dependencies.
2. Scenario `CBACT04C` (stock inputs, PARM `2022071800`, clock `2022-07-18 00:00:00.00`): `TRANSACT.txt` and
   `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C/`.
3. Scenario `CBACT04C-after-CBTRN02C` (ACCTFILE + TCATBALF from `legacy/golden/CBTRN02C/`, other inputs stock):
   `TRANSACT.txt` and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. Exit code is 0 for both scenarios, matching `RC.txt`.
5. Interest is truncated to 2 decimals toward zero. A test pins one case where rounding would differ.
6. A characterization test pins the last-account defect: the last account in TCATBAL order is **not** rewritten,
   even though its interest transaction is written.
7. A test confirms the DISCGRP `DEFAULT` fallback. A missing account, xref or `DEFAULT` rate ends with a non-zero
   exit (legacy: 16) and no partial "success".
8. A test confirms that rate = 0 categories write no transaction but still count toward the account break and
   rewrite.
9. `legacy/harness/golden.sh --check` still passes. Nothing under `legacy/golden/`, `legacy/harness/` or `.github/`
   is changed.

## Risks and findings

| # | Finding | Evidence | Treatment |
|---|---|---|---|
| D1 | **Last account never rewritten.** The `ELSE PERFORM 1050` branch at `:219-220` is unreachable. After EOF, `PERFORM UNTIL` exits before the body runs again. | Golden after-CBTRN02C: txn `2022071800000050` charges a/c 50 +18.77, but a/c 50 in `ACCTFILE` is byte-identical to `CBTRN02C/ACCTFILE` (balance `00000019458G`, cycle fields not zeroed). 49 of the other 49 accounts changed. | Preserve and pin (AC6). Flag for a separate CR. |
| D2 | Interest is truncated, not rounded. `COMPUTE` has no `ROUNDED`. | 50/50 truncation match vs 22/50 rounding (above). | Preserve (AC5). Business to confirm the intended policy. |
| D3 | No overflow check. A result over `S9(09)V99` silently loses high-order digits. The TRAN-ID counter `9(06)` wraps after 999,999 transactions. | `:464-465`, `:173`, `:474` | Preserve the modular behaviour and document it. Not hit by golden data. |
| D4 | Group-ID data defect: the group code is in `ACCT-ADDR-ZIP` and `ACCT-GROUP-ID` is blank, so every account uses `DEFAULT` rates. | 50/50 `DISCLOSURE GROUP RECORD MISSING` in SYSOUT. | This is a data issue, not code. Flag it. |
| D5 | `1400-COMPUTE-FEES` is empty. | `:518-520` | Keep as an empty hook (CR-2). |
| D6 | Misleading message: the DISCGRP open error says `ERROR OPENING DALY REJECTS FILE`. | `:281` | Preserve the text if SYSOUT parity is in scope. |
| D7 | Credit (negative) balances would generate negative "interest". There is no sign check. | `:464` (no guard). 0 negative amounts in the golden data. | Preserve and pin with a unit test. |
| R1 | Uninitialised `TRAN-RECORD` bytes are spaces under the default dialect but `x'00'` under `-std=ibm` / a real mainframe. | `legacy/README.md` | Match golden (spaces). Note the difference for mainframe parallel runs. |
| R2 | Overpunch sign encoding and zoned decimal arithmetic must be exact. Use `BigDecimal`, never `double`. | README sign quirk | Design concern. |
| R3 | The timestamp comes from the wall clock, with hundredths precision. | `:613-626` | Use an injectable clock. Tests use the frozen golden clock. |
| R4 | The XREF lookup on the alternate key returns the first card for each account. The stock data has no duplicate acct-ids (checked), so ordering for duplicates is untested. | `cardxref.txt` cols 26-36 have no duplicates | Use the first match in primary-key order, matching BDB's duplicate order. Note it as untested. |

## Questions for the approver

1. **Should SYSOUT and RC also be byte-identical, beyond `TRANSACT` + `ACCTFILE`?** Default: **yes**. Match
   `SYSOUT.txt` and `RC.txt` for both scenarios too. Operations runbooks and log scrapers may depend on those
   messages. It costs little.
2. **Where should the defect follow-ups (D1, D2, D4, D7) go?** Default: raise them as separate CRs after this
   migration. D1 is the most material: the last account each night keeps stale cycle totals and misses its
   interest posting.
