# CR-1 Intent: INTCALC / `CBACT04C` to Java 21

Run `685b6296-ed6f-482f-b34e-e9432cf8407c` · Stage: Plan

## Problem / outcome

**Problem.** The nightly interest calculation (job `INTCALC`, program `CBACT04C`) only runs as COBOL on the mainframe. That blocks our move of batch work off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. Given the same inputs, it writes the same interest transactions and the same updated account master as the COBOL program, byte for byte, including the COBOL's known defects. Those defects are pinned by tests and listed below, so they can be fixed in a separate change.

## What the legacy code does today

Job: `app/jcl/INTCALC.jcl:22` `EXEC PGM=CBACT04C,PARM='2022071800'`. PARM-DATE is `X(10)` (`CBACT04C.cbl:178`).

### Files

| DD | Org / access | LRECL | Key | Copybook | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential (`:28-32`) | 50 | acct(11)+type(2)+cat(4), offset 0 len 17 (`TCATBALF.jcl:40`) | `CVTRA01Y` | input, read in key order |
| `XREFFILE` | KSDS, random (`:34-39`) | 50 | card num(16). AIX: acct id offset 25 len 11 (`XREFFILE.jcl:43,74`) | `CVACT03Y` | read **by acct id** (AIX) |
| `ACCTFILE` | KSDS, random, I-O (`:41-45`, `:291`) | 300 | acct id(11) (`ACCTFILE.jcl:40`) | `CVACT01Y` | read + REWRITE |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4), len 16 (`DISCGRP.jcl:40`) | `CVTRA02Y` | interest rate lookup |
| `TRANSACT` | sequential, OUTPUT (`:309`) | 350 | none | `CVTRA05Y` | new interest transactions |

Numbers are zoned decimal with overpunch signs. Money fields are `S9(n)V99`.

### Processing (`CBACT04C.cbl:188-222`)

1. Open all five files. Any status other than `00` displays an error, then abends: `CEE3ABD` U0999, which is exit code 16 in the harness (`:234-323`, `:628-632`).
2. Read `TCATBALF` sequentially. For each record: add 1 to the count and `DISPLAY` the raw 50-byte record (`:192-193`).
3. **When the account changes** (`TRANCAT-ACCT-ID ≠ WS-LAST-ACCT-NUM`, `:194`):
   - For every account after the first, rewrite the previous account first (`1050`, `:196`).
   - Reset the total interest to 0. Read `ACCTFILE` by account id (`:372-391`). Read `XREFFILE` by account id through the AIX (`:393-413`). Not found → displays `ACCOUNT NOT FOUND: <id>`, then abends.
4. **Rate lookup** (`1200`, `:415-440`): key = `ACCT-GROUP-ID` + type + category.
   - Status `23` (not found): displays `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retries with group `DEFAULT` (`1200-A`, `:443-460`).
   - Default also missing → abend. Any other status → abend.
5. **If the rate ≠ 0** (`:214`):
   - Monthly interest = `(TRAN-CAT-BAL × DIS-INT-RATE) / 1200`, stored in `S9(09)V99`. That is **truncated toward zero, not rounded** (`:464-465`).
   - Add it to the account's total (`:467`).
   - Write one transaction (`1300-B`, `:473-515`):
     - `TRAN-ID` = PARM-DATE(10) + a 6-digit running suffix (`:474-480`). The suffix is not reset per account.
     - Type `01`, category `0005`, source `System`.
     - Description `Int. for a/c <ACCT-ID>`. The rest of the 100 bytes stay spaces.
     - Amount = monthly interest. Merchant id 0. Merchant name, city and zip are spaces.
     - Card number = `XREF-CARD-NUM`.
     - Original and processed timestamps = `YYYY-MM-DD-HH.MM.SS.hh0000`, from `CURRENT-DATE` (`:613-626`).
     - The 20-byte FILLER is never set, so it stays spaces.
   - Run `1400-COMPUTE-FEES`, which is empty (`:518-520`).
6. **Account update** (`1050`, `:350-370`): `ACCT-CURR-BAL += total interest`. `ACCT-CURR-CYC-CREDIT` and `ACCT-CURR-CYC-DEBIT` are set to 0. Then REWRITE. This runs even when the interest is 0.
7. At end of file: close all files, display `END OF EXECUTION OF PROGRAM CBACT04C`, RC 0.

Accounts with no `TCATBALF` records are not touched.

### Observed behaviour (harness, run this stage)

`legacy/harness/run.sh CBACT04C /tmp/out04` gives RC 0, `TRANSACT` 50, `ACCTFILE` 50 and `SYSOUT` 152 records. All four files are byte-identical to `legacy/golden/CBACT04C/`.

| Scenario | TCATBALF in | TRANSACT | ACCTFILE changed | SYSOUT |
|---|---|---|---|---|
| `CBACT04C` (stock) | 50, all type `01`/cat `0001`, balance 0 | 50 txns, all `0000000000{` | 0 of 50 (same as `acctdata.txt`) | 152 = 1 + 50×3 + 1 |
| `CBACT04C-after-CBTRN02C` | 100 (50 × `01/0001`, 50 × `03/0001`) | 50 txns: `01/0001` at 1.50%. `03/0001` has rate 0, so no txn | 49 of 50 | 302 = 1 + 100×3 + 1 |

`ACCT-GROUP-ID` is blank in every sample account, so every lookup falls back to `DEFAULT`.

## Scope

**In**
- Java 21 Maven project `modernized/cbact04c/`, JUnit 5 only, no frameworks.
- Same processing, business rules, arithmetic and defects as above.
- Inputs `TCATBALF`, `XREFFILE`, `ACCTFILE`, `DISCGRP` and PARM date. Outputs `TRANSACT` and the updated `ACCTFILE`.
- Same SYSOUT lines and return codes: 0 on success, 16 on abend paths.
- Clock can be injected, so timestamps are deterministic.
- Equivalence tests on both golden scenarios, plus characterization tests for each finding below.

**Out**
- Fixing any legacy defect (F1-F5).
- Implementing fees (`1400`, which is CR-2).
- CBTRN02C, other jobs, JCL or scheduler changes.
- Real VSAM/EBCDIC I/O.
- Deploying the container.

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbact04c/` passes on Java 21, with JUnit 5 as the only test dependency.
2. Scenario `CBACT04C` (stock inputs, PARM `2022071800`, clock `2022-07-18 00:00:00.00`): `TRANSACT` and `ACCTFILE` are byte-identical to `legacy/golden/CBACT04C/`. That means 50 + 50 records, fixed width 350 / 300, LF line endings.
3. Scenario `CBACT04C-after-CBTRN02C` (`ACCTFILE` and `TCATBALF` from `legacy/golden/CBTRN02C/`, other inputs stock): `TRANSACT` and `ACCTFILE` are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. For both scenarios, SYSOUT and RC match the golden `SYSOUT.txt` (152 / 302 lines) and `RC.txt` (`0`).
5. A characterization test pins F1: the last account in `TCATBALF` is not rewritten. Account `00000000050` stays `00000019458G` and its cycle fields are not zeroed, even though transaction `2022071800000050` is written.
6. A characterization test pins truncation: balance 2339.97 × 1.50 / 1200 = 29.249625 gives `0000000292D` (29.24), not 29.25. 28 of the 50 golden amounts depend on this.
7. Tests cover the default-group fallback and the 3 SYSOUT lines per record. Rate 0 writes no transaction but still rewrites the account (cycle fields zeroed, balance unchanged).
8. A missing account, missing xref or missing `DEFAULT` group gives the legacy messages and RC 16.
9. Overpunch encode/decode round-trips every sign character (`{ A-I } J-R`), including negative balances (negative interest truncates toward zero).
10. `legacy/harness/golden.sh --check` still passes. Nothing under `legacy/golden/`, `legacy/harness/` or `.github/` is changed.

## Risks and findings

| # | Finding | Evidence | Treatment |
|---|---|---|---|
| **F1** | **Last account never written back.** After EOF, the `UNTIL` test ends the loop before the `ELSE PERFORM 1050` at `:219-220` can run. The last account's interest is never added to its balance, and its cycle fields are not zeroed. | `golden/CBACT04C-after-CBTRN02C`: txn `2022071800000050` charges acct 50 interest of 18.77 (`0000000187G`). Its ACCTFILE record is byte-identical to `golden/CBTRN02C/ACCTFILE.txt` (balance `00000019458G`). The other 49 accounts change. The defect is invisible on stock data. | Preserve and pin (AC5). Flag for a fix decision. **Money impact:** the interest transaction is posted but the balance does not include it. |
| F2 | Interest is truncated, not rounded (`COMPUTE` without `ROUNDED`, `:464`). | 28 of the 50 after-scenario amounts would be 1 cent higher with half-up rounding. All 28 golden values match truncation. | Preserve (AC6). Java must use exact decimal arithmetic (`BigDecimal`, `RoundingMode.DOWN`), never `double`. |
| F3 | `1400-COMPUTE-FEES` is empty. | `:518-520` | Preserve. This is CR-2. |
| F4 | The `TRAN-ID` suffix is `9(06)` and `ADD` has no `ON SIZE ERROR` (`:173`, `:474`). After 999,999 txns it wraps to `000000`, which gives duplicate ids. | code | Preserve the wrap. Not reachable with the sample data. |
| F5 | The DISCGRP open-failure message says `ERROR OPENING DALY REJECTS FILE` (copy-paste). | `:281` | Preserve the text. |
| F6 | Interest is posted as type `01` / category `0005`. Fees and interest share no distinct type. | `:482-483` | Preserve. Business owner to confirm separately. |
| R1 | **I/O format.** Containers have no VSAM. The equivalence proof uses the harness's canonical text form: fixed width, LF, overpunch, KSDS unloaded in key order. | `legacy/README.md` §Outputs | See Q1. |
| R2 | Timestamps come from the wall clock. The hundredths go into the `MIL` digits and are followed by `0000`. | `:613-626` | Inject the clock. The default is the system clock in UTC. |
| R3 | The xref AIX in the sample data has unique account ids (0 duplicates in `cardxref.txt`). Behaviour with several cards per account (first by AIX order) is untested by golden. | `cut -c26-36 cardxref.txt \| uniq -d` → 0 | Take the first match in file order. Note it in the design. |
| R4 | Uninitialised `TRAN-RECORD` bytes are spaces only under the default GnuCOBOL dialect. Under `-std=ibm` they are `x'00'`. | README §Known quirks | Match the golden output, which has spaces. |

## Questions for the approver

1. **Q1, I/O format for the Java job:** the canonical fixed-width text files (as in `legacy/golden/`), one file per DD, with paths passed by DD name.
   *Default: yes.* `ACCTFILE` is read and rewritten as a whole file in key order. Real VSAM/EBCDIC access is a later platform decision.
2. **Q2, F1 (last-account write-back):** this CR keeps the defect. Should a follow-up CR fix it? The fix would add the last account's interest to its balance, which changes the outputs against golden.
   *Default: yes, raise it as a separate CR after this migration is accepted.*
