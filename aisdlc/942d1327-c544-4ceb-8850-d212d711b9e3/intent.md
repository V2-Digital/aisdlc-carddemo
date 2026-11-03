# CR-1 Intent: INTCALC (`CBACT04C`) to Java 21

## Problem
The nightly interest calculation (`app/jcl/INTCALC.jcl`, step `STEP15`, program `CBACT04C`) only runs on the mainframe. That blocks moving the card batch workload into containers.

## Outcome
A plain Java 21 program in `modernized/cbact04c/` that runs in a container, takes the same inputs, and writes the same interest transactions and updated account master **byte for byte**, including the legacy defects. The defects are pinned by tests and listed below for a separate decision.

## What the legacy code does today

### Files (`INTCALC.jcl:22-41`, `CBACT04C.cbl:28-56`)
| DD | Dataset | Org / access | LRECL | Key | Use |
|---|---|---|---|---|---|
| TCATBALF | TCATBALF.VSAM.KSDS | KSDS, sequential | 50 (`CVTRA01Y`) | acct(11)+type(2)+cat(4), off 0 len 17 (`TCATBALF.jcl`) | input, driver |
| XREFFILE (+XREFFIL1 AIX path) | CARDXREF.VSAM.KSDS | KSDS, random by **alt key** | 50 (`CVACT03Y`) | card(16); AIX acct id off 25 len 11 (`XREFFILE.jcl:72-91`) | input |
| DISCGRP | DISCGRP.VSAM.KSDS | KSDS, random | 50 (`CVTRA02Y`) | group(10)+type(2)+cat(4), len 16 | input (rates) |
| ACCTFILE | ACCTDATA.VSAM.KSDS | KSDS, random, **I-O** | 300 (`CVACT01Y`) | acct id, len 11 | read + REWRITE |
| TRANSACT | SYSTRAN(+1) GDG | sequential, OUTPUT | 350 (`CVTRA05Y`) | n/a | new interest transactions |

PARM: `'2022071800'` → `PARM-DATE X(10)` (`INTCALC.jcl:22`, `CBACT04C.cbl:176-178`).

### Processing steps
1. Display the start banner, then open all five files. Any status other than `00` → abend (`:181-186`, `:234-323`).
2. Read TCATBALF sequentially in key order (`:325-348`). Each record is DISPLAYed to SYSOUT (`:193`).
3. **Control break on account id** (`:194-206`). When the account changes and this is not the first record, rewrite the previous account (step 6). Then reset the total interest, read ACCTFILE by account id (`:372-391`), and read XREFFILE by its alternate key, account id (`:393-413`). A miss on either one → abend.
4. **Rate lookup** (`:210-213`, `:415-460`). Key = `ACCT-GROUP-ID` + tran type + category. On status `23`, DISPLAY `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and retry with group `DEFAULT`. A miss on the retry → abend.
5. **If the rate ≠ 0** (`:214-217`):
   - `monthly = bal × rate / 1200` into `S9(09)V99`. No `ROUNDED`, so the result is **truncated** (`:464-465`). Evidence: 1164.87 × 15.00 / 1200 = 14.5609 → `0000000145F` = 14.56 (golden after-CBTRN02C, tran 1).
   - Add it to the account total (`:467`).
   - Write one transaction (`:473-515`):
     - ID = PARM-DATE + 6-digit suffix that runs across the whole job.
     - type `01`, category `0005`, source `System`, desc `Int. for a/c <ACCT-ID>`.
     - amount = monthly; merchant id 0; merchant name/city/zip spaces.
     - card = `XREF-CARD-NUM`.
     - orig and proc timestamps = `CURRENT-DATE` in the form `YYYY-MM-DD-HH.MM.SS.hh0000` (`:613-626`).
   - `1400-COMPUTE-FEES` runs next. It is empty (`:518-520`).
6. **Account update** (`1050`, `:350-370`): `ACCT-CURR-BAL += total`, cycle credit = 0, cycle debit = 0, then REWRITE. A failed REWRITE → abend.
7. Close the files and display the end banner. RC 0 (`:224-232`).
8. **Abend path**: DISPLAY `ABENDING PROGRAM`, then `CEE3ABD` U0999. The harness turns this into exit **16** (`:628-632`). File status is formatted by `9910` (`:635-648`).

### Observed behaviour (harness run)
`legacy/harness/run.sh CBACT04C /tmp/cb4out` → RC 0, TRANSACT 50, ACCTFILE 50, SYSOUT 152. This is identical to `legacy/golden/CBACT04C`.

| Scenario | TCATBAL rows | Rows with rate ≠ 0 | TRANSACT | ACCTFILE records changed |
|---|---|---|---|---|
| `CBACT04C` (stock) | 50 (all type 01/0001, bal 0) | 50 | 50, all amount 0 | 0 (identical to `acctdata.txt`) |
| `CBACT04C-after-CBTRN02C` | 100 (50 × 01/0001, 50 × 03/0001) | 50 (type 03 rate = 0) | 50, non-zero | **49 of 50** |

The disclosure lookup misses on all 100 rows of the after-CBTRN02C scenario, because `ACCT-GROUP-ID` is blank. Every rate comes from `DEFAULT`.

## Scope
**In**
- Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only).
- Reads the same canonical text datasets as the harness: fixed width, EBCDIC overpunch, LF.
- Writes `TRANSACT` and `ACCTFILE` in the same unload form.
- Takes the PARM date and a run timestamp as inputs, so runs are deterministic.
- Same exit codes (0, or 16 on abend).
- Characterization tests for the defects.

**Out**
- Fixing any defect.
- CBTRN02C.
- Fee logic (CR-2).
- VSAM, GDG or JCL changes.
- Scheduler (CA7/Control-M) changes.
- Container image and deploy pipeline (Deploy stage).
- EBCDIC binary datasets.

## Acceptance criteria
1. `mvn test` in `modernized/cbact04c/` passes on Java 21, with JUnit 5 + Surefire as the only dependencies.
2. On the `CBACT04C` scenario inputs, PARM `2022071800` and timestamp `2022-07-18-00.00.00.000000`, `TRANSACT.txt` and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C/` (50 + 50 records).
3. Same check for `CBACT04C-after-CBTRN02C`, with ACCTFILE/TCATBALF from `legacy/golden/CBTRN02C/` and the other inputs from stock: byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. Exit code is 0 on both scenarios, matching `RC.txt`.
5. Interest is truncated, not rounded. There is a unit test for a positive and a negative balance.
6. The defects below are pinned by named tests: the last account is not rewritten, the empty fees step, and the disclosure fallback to `DEFAULT`.
7. A missing account, xref, or `DEFAULT` disclosure record → exit 16 and no partial-success RC.
8. Byte columns of the 350-byte tran record that the program never sets (desc tail, FILLER 331-350) are spaces.

## Risks and findings
| # | Finding | Evidence | Treatment |
|---|---|---|---|
| D1 | **The last account's interest is never written back.** The `ELSE PERFORM 1050` at `:219-220` is unreachable: after EOF the `UNTIL` test exits before the body runs. | Golden after-CBTRN02C: tran `2022071800000050` charges acct `00000000050` 18.77. That ACCTFILE record is unchanged from `golden/CBTRN02C` (bal `00000019458G`, cycle fields not zeroed). 49/50 records change. | Preserve, pin, flag |
| D2 | `1400-COMPUTE-FEES` is empty. | `:518-520` | Preserve (CR-2) |
| D3 | Every disclosure lookup falls back to `DEFAULT`. The sample data puts the group code in `ACCT-ADDR-ZIP` and leaves `ACCT-GROUP-ID` blank. | README "Sample data"; 100 × `DISCLOSURE GROUP RECORD MISSING` in after-CBTRN02C SYSOUT | Preserve. Data issue, flag |
| D4 | The interest is truncated, not rounded, so customers are under-charged by up to 0.01 per category per month. | `:464-465`, tran 1 above | Preserve, flag |
| D5 | The DISCGRP open error message says `ERROR OPENING DALY REJECTS FILE` (copy-paste). | `:281` | Preserve text |
| D6 | The transaction ID suffix is `9(06)` with no SIZE ERROR check. It wraps silently after 999,999 transactions per run, which would create duplicate IDs. | `:173`, `:474` | Preserve, flag |
| D7 | The TCATBAL rows of an account are rated with the account record read at the control break. The rewrite uses whatever is in `ACCOUNT-RECORD`. | `:194-213`, `:356` | Replicate exactly |
| R1 | Decimal semantics: zoned overpunch, implied decimals, truncation toward zero, and high-order truncation on `MOVE`. | Copybook PICs | `BigDecimal` / `long` with explicit scale; codec unit tests |
| R2 | Uninitialised `TRAN-RECORD` = spaces only under the default dialect (`x'00'` with `-std=ibm`). | README "Known quirks" | Match the golden output (spaces) |
| R3 | Timestamp source. COBOL uses the wall clock with hundredths. | `:613-626` | Inject the timestamp; default to the system clock |
| R4 | Input quirks: `cardxref.txt` is 36-byte lines, and some files have CRLF. | README | Reader pads to LRECL and strips CR, like `cdfiles` |

## Questions for the approver
1. **SYSOUT parity.** Should the Java program also reproduce `SYSOUT.txt` line for line (152 / 302 lines)? *Default: yes, write the same DISPLAY lines to stdout and test them against golden. It costs little and helps operations compare runs.*
2. **D1, D4, D6 follow-up.** Raise these as separate CRs after the migration? *Default: yes. This CR preserves them.*
