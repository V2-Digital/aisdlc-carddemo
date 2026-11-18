# CR-1 Intent — INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation (`app/jcl/INTCALC.jcl`, step `STEP15`, `PGM=CBACT04C`) runs only as COBOL on the mainframe. That blocks moving batch work into containers.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that, given the same input files and run date, writes **byte-identical** interest transactions (`TRANSACT`) and updated account master (`ACCTFILE`) to the COBOL program. Legacy defects are kept as they are and pinned by tests. Fixing them is a separate decision.

## What the legacy code does today

Verified by running `legacy/harness/run.sh CBACT04C` on stock data and with `INPUT_DIR=legacy/golden/CBTRN02C`. Both runs match `legacy/golden/` byte for byte (TRANSACT, ACCTFILE, SYSOUT, RC).

### Files (`CBACT04C.cbl:28-56`, `INTCALC.jcl`)

| DD | Org / access | LRECL | Key | Use | Copybook |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | 50 | acct(11)+type(2)+cat(4), offset 0, length 17 | input, driver | `CVTRA01Y` |
| `XREFFILE` | KSDS, random by **alt key** acct id (offset 25, length 11) | 50 | card num(16) | input | `CVACT03Y` |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4) | input, rates | `CVTRA02Y` |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct id(11) | read + REWRITE | `CVACT01Y` |
| `TRANSACT` | sequential, OUTPUT (new GDG) | 350 | – | interest trans | `CVTRA05Y` |
| PARM | `'2022071800'` | 10 | – | prefix for tran ids | `:176-178` |

`XREFFIL1` (AIX path) is not referenced by the program. The harness serves the alternate key directly.

### Processing steps

1. Open all five files. Any status other than `00` → message, `FILE STATUS IS: NNNNxxxx`, `ABENDING PROGRAM`, CEE3ABD U999 (harness RC 16) (`:234-323`, `:628-648`).
2. Read TCATBALF sequentially in key order. Each record is `DISPLAY`ed to SYSOUT (`:190-193`, `:325-348`).
3. **Account break** (`:194-206`): when the acct id changes, the previous account is rewritten (`1050`), except on the first record. Then `WS-TOTAL-INT` is set to 0, the account is read by id (`1100`, `:372-391`) and the xref is read by acct id (`1110`, `:393-413`). Not found → `ACCOUNT NOT FOUND: …` then abend.
4. **Rate lookup** (`1200`, `:415-440`): key = `ACCT-GROUP-ID` + tran type + tran cat. Status `23` → `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE`, then retry with group `DEFAULT` (`1200-A`, `:443-460`). The DEFAULT retry missing → abend.
5. **Interest** (`1300`, `:462-470`), only if `DIS-INT-RATE ≠ 0` (`:214`): `WS-MONTHLY-INT = TRAN-CAT-BAL × DIS-INT-RATE / 1200`, **truncated** to 2 dp (no `ROUNDED`), into `S9(09)V99`. It is added to `WS-TOTAL-INT`.
6. **Write interest transaction** (`1300-B`, `:473-515`):
   - `TRAN-ID` = PARM date(10) + suffix `9(06)` (run-wide counter from 1)
   - type `01`, cat `0005`, source `System`
   - desc `Int. for a/c ` + `ACCT-ID` (rest spaces)
   - amount = monthly interest
   - merchant id 0; merchant name/city/zip spaces
   - card = `XREF-CARD-NUM`
   - orig/proc ts = `YYYY-MM-DD-HH.MM.SS.hh0000` from `CURRENT-DATE` (`:613-626`)
   - FILLER 20 = spaces
7. `1400-COMPUTE-FEES` is an empty paragraph (`:518-520`).
8. **Account update** (`1050`, `:350-370`): `ACCT-CURR-BAL += WS-TOTAL-INT`, cycle credit and debit set to 0, then REWRITE. This applies even when total interest is 0.
9. At EOF, close all files and end with RC 0 (`:224-232`).

### Business rules seen in the data

- `ACCT-GROUP-ID` (cols 102-111) is blank in all accounts. The group code `A000000000` sits in `ACCT-ADDR-ZIP`. As a result, **every lookup falls back to `DEFAULT`**. SYSOUT shows the MISSING message for each TCATBAL record.
- `DEFAULT` rates: type 01/cat 0001 = 15.00%, 01/0002-0004 = 25.00%, 02 and 03 = 0, 04-06 = 15.00%, 07/0001 = 0.
- Stock TCATBAL: 50 records, all `01/0001`, all balances 0. That gives 50 transactions of 0.00, and ACCTFILE is unchanged vs `acctdata.txt`, because the cycle fields are already 0 there.
- After CBTRN02C: 100 records (50 × `01/0001`, 50 × `03/0001` at 0% rate). That gives 50 transactions with non-zero amounts. Example: account 2, balance 2339.97 × 15 / 1200 = 29.249625 → **29.24** (`0000000292D`). I checked all 50 amounts against `trunc(bal×rate/1200)`.

## Scope

**In**
- A Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only) reproducing steps 1-9.
- I/O in the harness's canonical text form: fixed-width, LF, overpunch signs. The inputs are the TCATBALF, XREFFILE, DISCGRP and ACCTFILE `.txt` unloads. The outputs are `TRANSACT.txt` and `ACCTFILE.txt`, with ACCTFILE written in full in key order.
- Run date (PARM) and clock are injectable, so the frozen `2022-07-18-00.00.00.000000` can be reproduced.
- The same SYSOUT lines and exit code (0 normal, 16 on abend).
- Characterization tests for every defect listed below.
- An equivalence test against both golden scenarios.

**Out**
- Fixing any legacy defect, including the last-account bug and the group-id mapping.
- Implementing fees (CR-2).
- VSAM/BDB file access, JCL/scheduler changes, the CBTRN02C rewrite, containerisation/deploy plumbing.

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbact04c/` passes on Java 21, with JUnit 5 + Surefire as the only dependencies.
2. Scenario `CBACT04C` (stock inputs, PARM `2022071800`, clock `2022071800000000`): `TRANSACT.txt` (50 records) and `ACCTFILE.txt` (50 records) are byte-identical to `legacy/golden/CBACT04C/`.
3. Scenario `CBACT04C-after-CBTRN02C` (ACCTFILE + TCATBALF from `legacy/golden/CBTRN02C/`): `TRANSACT.txt` and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. For both scenarios, SYSOUT matches `SYSOUT.txt` (152 / 302 lines) and the exit code matches `RC.txt` (0).
5. Interest is truncated, not rounded: a balance of 2339.97 at 15% gives 29.24. Negative balances truncate toward zero, as COBOL does.
6. A zero rate writes no transaction and adds nothing, but the account is still rewritten with cycle credit/debit = 0.
7. A characterization test pins the last-account defect: the last account in TCATBALF gets its transaction but its ACCTFILE record is unchanged.
8. A characterization test pins the fallback: a missing group goes to `DEFAULT`. A missing DEFAULT row, account or xref → abend message + exit 16.
9. Tran ids are `PARM(10)` + a 6-digit counter that runs across all accounts.
10. `legacy/golden/` and `legacy/harness/` are unchanged, and `golden.sh --check` still passes.

## Risks and findings

| # | Finding | Evidence | Handling |
|---|---|---|---|
| D1 | **Last account never rewritten.** The `ELSE PERFORM 1050` at `:219-220` can't run: after EOF is set inside the body, `UNTIL END-OF-FILE='Y'` exits before another pass. | `golden/CBACT04C-after-CBTRN02C`: tran `2022071800000050` charges acct 50 18.77 (`0000000187G`), yet acct 50's record is identical to `golden/CBTRN02C/ACCTFILE.txt` (bal `00000019458G`, cycle fields not zeroed). `diff` shows only 49/50 accounts changed. | Preserve and pin (AC7). Flag for a fix decision. |
| D2 | **Disclosure group never matches.** `ACCT-GROUP-ID` is blank. The real code is in `ACCT-ADDR-ZIP` (data issue, or the layout and data disagree). | `acctdata.txt` cols 92-101 = `A000000000`, cols 102-111 blank. Each SYSOUT record is followed by `DISCLOSURE GROUP RECORD MISSING`. | Preserve. Flag. |
| D3 | Interest is truncated, not rounded (no `ROUNDED`, `:464-465`). | acct 2: 29.249625 → 29.24. acct 3: 32.385875 → 32.38. | Preserve. Business should confirm. |
| D4 | `1400-COMPUTE-FEES` is empty (`:518-520`). | Source. | Preserve. CR-2. |
| D5 | Wrong error text for DISCGRP open failure: `ERROR OPENING DALY REJECTS FILE` (`:281`). | Source. | Preserve the text. |
| D6 | The account is rewritten (cycle fields zeroed) even when all its rates are 0. | `:350-356`. Stock run. | Preserve. |
| D7 | The tran-id suffix is `9(06)`. Beyond 999,999 interest trans per run it wraps and creates duplicate ids. | `:173`, `:474`. | Preserve (wrap modulo 10^6). Pin with a unit test. |
| D8 | Uninitialised `TRAN-RECORD`: the desc tail and FILLER are spaces only under the default dialect. Under `-std=ibm` they would be `x'00'`. | `legacy/README.md` "Known quirks". | Match the golden (spaces). |
| D9 | An account with several cards: the xref read by alt key returns the first duplicate. Stock data has 50 xrefs and no duplicate acct ids. | `cardxref.txt`. | Return the first in file/primary-key order. Not exercised by golden. |
| R1 | Overpunch sign and decimal handling is the main risk to byte identity. | README "Signs". | Use one shared codec with exhaustive tests. Use `BigDecimal` throughout. |
| R2 | Timestamp hundredths come from the clock. | `:613-626`. | Inject the clock and default it to the frozen value in tests. |

## Questions for the approver

1. **Should SYSOUT and the exit code also be part of the equivalence contract, or only TRANSACT/ACCTFILE?** *Default:* include them. The golden exists, it costs little, and ops monitoring may parse SYSOUT.
2. **I/O format: is the canonical fixed-width text (the harness unload format) acceptable as the Java program's file interface for now?** *Default:* yes. EBCDIC/VSAM conversion stays with the platform migration.
3. **D1-D3 fix decisions:** these are out of scope for CR-1. *Default:* raise them as separate CRs after cut-over. D1 under-posts interest on the last account every night, so it should go first.
