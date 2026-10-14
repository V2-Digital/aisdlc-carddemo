# CR-1 Intent: INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** Nightly interest calculation (`INTCALC`) runs as COBOL on the mainframe. Batch work is moving off the mainframe, and this job is one of the blockers.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. For the same inputs it produces byte-identical interest transactions (`TRANSACT`) and an identical updated account master (`ACCTFILE`). Legacy behaviour is kept as it is, defects included. The defects are pinned by tests and listed below for a separate decision.

## What the legacy code does today

**Job:** `app/jcl/INTCALC.jcl:22` runs `EXEC PGM=CBACT04C,PARM='2022071800'`. PARM-DATE is `X(10)` (`CBACT04C.cbl:178`).

### Files

| DD | Org / access | LRECL | Key | Copybook | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential (`:28-32`) | 50 | 1-17 acct(11)+type(2)+cat(4) | `CVTRA01Y` | input, read in key order |
| `XREFFILE` | KSDS, random, via AIX (`:34-39`) | 50 | 1-16 card; AIX acct id 26-36 | `CVACT03Y` | input, card number for the account |
| `ACCTFILE` | KSDS, random, I-O (`:41-45`) | 300 | 1-11 acct id | `CVACT01Y` | read + REWRITE |
| `DISCGRP` | KSDS, random (`:47-51`) | 50 | 1-16 group(10)+type(2)+cat(4) | `CVTRA02Y` | input, interest rate `DIS-INT-RATE` S9(4)V99 at 17-22 |
| `TRANSACT` | sequential, OUTPUT (`:53-56`) | 350 | (none) | `CVTRA05Y` | output, new interest transactions (GDG +1 in JCL) |

The IDCAMS key definitions match: `TCATBALF.jcl:36-41`, `XREFFILE.jcl:39-44` / AIX `:72-77`, `ACCTFILE.jcl:36-41`, `DISCGRP.jcl:36-41`.

### Processing (`CBACT04C.cbl`)

1. Displays `START OF EXECUTION…`, then opens all 5 files. Any open status other than `00` abends the program (`:181-186`, `:234-323`).
2. Reads TCATBALF sequentially (`:325-348`). Each record is DISPLAYed to SYSOUT (`:193`).
3. **Account break** (`:194-206`): when the account id changes, the program rewrites the previous account (except on the first record), resets total interest to 0, reads ACCTFILE by id (`:372-391`), and reads XREFFILE by the AIX acct id (`:393-413`). A missing account or xref displays `ACCOUNT NOT FOUND: …` and then **abends** (status 23 ≠ 00).
4. **Rate lookup** (`:210-213`, `:415-460`): looks up DISCGRP with the key `ACCT-GROUP-ID` + type + cat. On status 23 it displays `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and retries with group `DEFAULT`. If the default is missing too, the program abends.
5. **Interest** (`:214-217`, `:462-470`): only when the rate ≠ 0. `WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200`, **truncated** to 2 dp (no ROUNDED) into S9(9)V99, then added to `WS-TOTAL-INT`. It then performs `1400-COMPUTE-FEES`, which is empty (`:518-520`).
6. **Interest transaction** (`:473-515`), one per non-zero-rate category:
   - `TRAN-ID` = PARM-DATE + 6-digit running suffix
   - type `01`, cat `0005`, source `System`
   - desc `Int. for a/c <acct-id>`, the rest spaces
   - amount = monthly interest
   - merchant id 0, merchant name/city/zip spaces
   - card number from the xref
   - ORIG-TS and PROC-TS = `YYYY-MM-DD-HH.MM.SS.hh0000` from `CURRENT-DATE` (`:613-626`)
   - The 20-byte FILLER at 331-350 is never set, so it stays spaces (README "Known quirks").
7. **Account update** (`1050`, `:350-370`): `ACCT-CURR-BAL += WS-TOTAL-INT`, cycle credit and cycle debit set to 0, then REWRITE.
8. Closes all files, displays `END OF EXECUTION…`, and returns RC 0 (`:224-232`). Any error goes through `9910` (status display) and `9999` (`CEE3ABD` U999, harness exit 16) (`:628-648`).

### Observed (harness run, today)

- `legacy/harness/run.sh CBACT04C`: RC 0. TRANSACT 50, ACCTFILE 50 and SYSOUT 152 records, all **byte-identical** to `legacy/golden/CBACT04C/`.
- Stock TCATBAL has every balance at zero. The 50 transactions all have amount `0000000000{`, and ACCTFILE equals `app/data/ASCII/acctdata.txt` unchanged.
- In `golden/CBACT04C-after-CBTRN02C`, TCATBALF has 100 records: 50 with type/cat `01/0001` and 50 with `03/0001`.
  - `DEFAULT 01/0001` has a rate of 15.00%, which gives the 50 transactions.
  - `DEFAULT 03/0001` has a rate of 0, so those records get no transaction.
  - I recomputed all 50 amounts as `trunc(bal × 15 / 1200)` and every one matched. 48 of them needed truncation.
  - SYSOUT has 302 lines = 2 + 100 × 3.
- Every lookup falls back to `DEFAULT`, because `ACCT-GROUP-ID` (113-122) is blank in the sample data.

## Scope

**In**
- A Java 21 + JUnit 5 Maven project in `modernized/cbact04c/` that reproduces steps 1-8. That covers the PARM date, fixed-width record I/O in the canonical text format (overpunch signs), truncating decimal arithmetic, the DEFAULT fallback, the frozen clock (`COB_CURRENT_DATE`-equivalent), the abend paths and RC, and the SYSOUT lines.
- Golden equivalence tests for `CBACT04C` and `CBACT04C-after-CBTRN02C`.
- Characterization tests for the defects listed below.
- A container-runnable entry point.

**Out**
- Fixing any legacy defect.
- Implementing fees (CR-2).
- CBTRN02C.
- VSAM/BDB access, real EBCDIC binary datasets, GDG handling.
- JCL/scheduler changes and production cut-over.

## Acceptance criteria

1. `mvn -f modernized/cbact04c verify` passes on Java 21, with JUnit 5 as the only test dependency.
2. Scenario `CBACT04C`, stock inputs, PARM `2022071800`, clock `2022-07-18 00:00:00.00`: the Java `TRANSACT.txt` and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C/`, at 50 and 50 records.
3. Scenario `CBACT04C-after-CBTRN02C`, with ACCTFILE/TCATBALF from `golden/CBTRN02C`: the same byte-identical result against `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. On both scenarios, SYSOUT is identical to the golden `SYSOUT.txt` (152 / 302 lines) and the exit code is 0.
5. Interest is truncated, not rounded. There is a unit test with a balance that has a non-zero third decimal, plus a negative balance that truncates toward zero.
6. A category with rate 0 produces no transaction and adds nothing to the account.
7. The last-account defect is preserved and pinned by a test: account `00000000050` in scenario 3 is unchanged, while its interest transaction (`0000000187G`) is still written.
8. A missing account, missing xref, or missing DEFAULT rate ends with the legacy messages and a non-zero exit code (16, matching the harness's CEE3ABD).
9. The program reads inputs and writes outputs by DD name (a directory or env-var mapping) in the same text format as `legacy/golden`, so its outputs can be diffed directly.

## Risks and findings

- **D1, last account never rewritten (defect, preserve).** After EOF the `PERFORM UNTIL` (`:188`) exits before the `ELSE PERFORM 1050` (`:219-220`) can run. Evidence in `golden/CBACT04C-after-CBTRN02C`:
  - Account 50 still shows balance `00000019458G` and its old cycle credit/debit, even though TRANSACT charges it 18.77.
  - Accounts 1-49 are updated. Example: account 1 goes from `00000012881{` to `00000013026F` (+145.06), with cycle fields set to 0.
- **D2, fees not implemented.** `1400-COMPUTE-FEES` is empty (`:518-520`). It is out of scope (CR-2).
- **D3, disclosure group taken from an empty field.** `ACCT-GROUP-ID` is blank in all 50 accounts, and the group-like value `A000000000` sits in `ACCT-ADDR-ZIP`. Every lookup therefore hits status 23 and uses DEFAULT, and SYSOUT carries 2 warning lines per category record. This is a data or copybook issue: preserve it.
- **D4, truncation instead of rounding.** Interest is truncated to the cent (`:464-465`, no `ROUNDED`). 48 of the 50 non-zero amounts lost fractions of a cent. Preserve.
- **D5, no overflow guard.** The COMPUTE and ADD have no `ON SIZE ERROR`. High-order digits would be silently truncated: 9 integer digits for the transaction amount, 10 for the balance. Not reachable with the sample data. Java must mimic the COBOL truncation, not throw.
- **D6, not idempotent.** TCATBAL balances are never reset. Running the job twice charges interest twice and creates duplicate TRAN-IDs (the suffix restarts at 1 every run, and 6 digits wrap after 999,999).
- **D7, cosmetic.** The DISCGRP open-error message says `ERROR OPENING DALY REJECTS FILE` (`:281`). `WS-RECORD-COUNT` is counted but never printed.
- **D8, timestamp precision.** Timestamps carry only hundredths (`hh0000` microseconds), and the clock is read again for every transaction. Java must use a single injectable clock to stay deterministic.
- **Risk, sign and arithmetic fidelity.** Byte-equality depends on correct EBCDIC overpunch encode/decode (including `}` for -0) and on exact BigDecimal truncation. Both are covered by AC 2-5.
- **Risk, xref with several cards per account.** The AIX read returns the first card for the account. In the sample data each account has 1 xref, so the Java version should take the first in file (card-key) order. That is unverified for duplicates.

## Questions for the approver

1. **I/O format.** Should the Java program read and write the canonical fixed-width text files (as in `legacy/golden`) rather than VSAM/EBCDIC datasets? *Default: yes, text files mapped by DD name. Converting real datasets is a separate migration concern.*
2. **SYSOUT parity.** The CR only requires TRANSACT and ACCTFILE. *Default: also match SYSOUT and RC exactly, because it is cheap and the golden copies exist.*
3. **Defects D1-D7.** *Default: preserve all of them, pin them with tests, and raise D1 and D3 as follow-up CRs (D1 misstates balances for one account every night).*
