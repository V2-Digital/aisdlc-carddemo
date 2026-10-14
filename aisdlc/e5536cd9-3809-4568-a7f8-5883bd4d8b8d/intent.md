# CR-1 Intent: INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation (job `INTCALC`, program `CBACT04C`) runs only on the mainframe. That blocks moving batch work into containers.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that a container can run. For the same inputs and run date it writes **byte-identical** `TRANSACT` (interest transactions) and `ACCTFILE` (updated account master) files. This is proven on golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Legacy behaviour is kept exactly, defects included.

## What the legacy code does today

**Job** `app/jcl/INTCALC.jcl`: `EXEC PGM=CBACT04C,PARM='2022071800'` (10-character run date, used as the `TRAN-ID` prefix).

### Files

| DD | Org / access | Rec len | Key | Copybook | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | 50 | acct(11)+type(2)+cat(4), offset 0 len 17 | `CVTRA01Y` | input, read in key order |
| `XREFFILE` | KSDS, random | 50 | card(16). AIX acct-id offset 25 len 11 | `CVACT03Y` | input, read **by acct-id** (alternate key) |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct-id(11) | `CVACT01Y` | read + REWRITE |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4) | `CVTRA02Y` | interest-rate lookup |
| `TRANSACT` | sequential, OUTPUT | 350 | none | `CVTRA05Y` | new interest transactions |

Key money fields (zoned, overpunch sign):

| Field | PIC |
|---|---|
| `TRAN-CAT-BAL` | `S9(9)V99` |
| `DIS-INT-RATE` | `S9(4)V99` (annual %) |
| `ACCT-CURR-BAL`, `ACCT-CURR-CYC-CREDIT/DEBIT` | `S9(10)V99` |
| `TRAN-AMT`, `WS-MONTHLY-INT`, `WS-TOTAL-INT` | `S9(9)V99` |

### Processing steps (`app/cbl/CBACT04C.cbl`)

1. **Open files** (182-186). TCATBALF/XREF/DISCGRP are opened INPUT, ACCTFILE I-O, TRANSACT OUTPUT. Any status other than `00` → abend.
2. **Read loop** (188-222). Read TCATBALF sequentially. Each record is counted and `DISPLAY`ed to SYSOUT (192-193).
3. **Account break** (194-206). When the account id changes, the previous account is rewritten (`1050`), except on the first record. Then `WS-TOTAL-INT` is reset, the account is read by key (`1100`, 372) and the xref is read by acct-id (`1110`, 393). A missing account or xref → `ACCOUNT NOT FOUND` → abend.
4. **Rate lookup** (`1200`, 415-440). The key is `ACCT-GROUP-ID` + type + cat. Status `23` → displays `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE`, then retries with group `DEFAULT` (437-438). If `DEFAULT` is also missing → abend (443-459).
5. **Interest** (214-217, `1300` 462-468). Only if `DIS-INT-RATE ≠ 0`:
   - `WS-MONTHLY-INT = TRAN-CAT-BAL * DIS-INT-RATE / 1200`. This is **truncated** to 2 dp, with no `ROUNDED` and no `ON SIZE ERROR`.
   - The result is added to `WS-TOTAL-INT` and a transaction is written.
   - Then `1400-COMPUTE-FEES` runs, which is empty (518-520).
6. **Write transaction** (`1300-B`, 473-515):

   | Field | Value |
   |---|---|
   | `TRAN-ID` | PARM-DATE + 6-digit running suffix |
   | type / cat | `01` / `0005` |
   | source | `System` |
   | desc | `Int. for a/c <ACCT-ID>` |
   | amount | monthly interest |
   | merchant id | 0 |
   | merchant name / city / zip | spaces |
   | card | `XREF-CARD-NUM` |
   | orig / proc timestamps | both `CURRENT-DATE` as `YYYY-MM-DD-HH.MM.SS.hh0000` (613-626) |

   `TRAN-RECORD` is never initialised, so unset bytes stay spaces.
7. **Update account** (`1050`, 350-370):
   - `ACCT-CURR-BAL += WS-TOTAL-INT`.
   - Cycle credit and cycle debit are set to 0.
   - The record is REWRITten.
   - This also runs for accounts whose total interest is zero.
8. **Close and finish** (224-232). All files are closed, then `END OF EXECUTION`, RC 0. On abend: `ABENDING PROGRAM` + `CEE3ABD` U0999 (628-632). The harness maps this to exit **16**.

### Observed behaviour (harness, run today)

Both scenarios re-ran with RC 0. `TRANSACT`, `ACCTFILE` and `SYSOUT` are all byte-identical to `legacy/golden/`.

| Scenario | TCATBAL in | TRANSACT out | ACCTFILE records changed |
|---|---|---|---|
| `CBACT04C` (stock data) | 50 (all `01/0001`, all balances 0) | 50, all amount 0 | 0 of 50 |
| `CBACT04C-after-CBTRN02C` | 100 (50 × `01/0001` + 50 × `03/0001`) | 50 (`03/0001` has DEFAULT rate 0 → no transaction) | **49 of 50** |

Every lookup falls back to `DEFAULT`: `ACCT-GROUP-ID` is blank in the sample data. Rate for `DEFAULT 01 0001` = 15.00%.

## Scope

**In**
- A Java 21 Maven project in `modernized/cbact04c/` (JUnit 5 only). It reads and writes the canonical fixed-width text format used by `legacy/golden/` and `app/data/ASCII/`.
- Same DD inputs, run-date PARM, and injectable clock (golden runs use the frozen date `2022-07-18 00:00:00.00`).
- Same RC (0 / 16 on abend conditions).
- Equivalence tests against both golden scenarios.
- Characterization tests that pin each defect listed below.

**Out**
- Fixing any legacy defect.
- Fees (`1400`, which is CR-2).
- Changes to `CBTRN02C`.
- VSAM/EBCDIC binary I/O.
- Containerising, scheduling and deployment (later stages).

## Acceptance criteria

1. `mvn test` in `modernized/cbact04c/` passes on Java 21, with only JUnit 5 + Surefire as dependencies.
2. Stock inputs + PARM `2022071800` + frozen clock: `TRANSACT.txt` and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C/` (50 + 50 records).
3. Inputs from `legacy/golden/CBTRN02C/` (ACCTFILE, TCATBALF) + stock XREF/DISCGRP: `TRANSACT.txt` and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. Interest is truncated toward zero, not rounded. A test pins a case where the two differ (28 of 50 golden amounts would change under HALF_UP).
5. The last account in TCATBAL is **not** rewritten (defect D1). This is pinned by a test on account `00000000050`.
6. Missing disclosure group → `DEFAULT` fallback. Rate 0 → no transaction and no suffix increment, but the account is still rewritten with cycle fields zeroed.
7. Missing account, missing xref, or missing `DEFAULT` row → exit code 16 and no further processing.
8. `TRAN-ID` = PARM date + zero-padded 6-digit counter across the whole run. Bytes 331-350 and the `TRAN-DESC` tail are spaces.
9. SYSOUT matches the golden `SYSOUT.txt` (see Q1).

## Risks and findings

| # | Finding | Evidence | Handling |
|---|---|---|---|
| D1 | **The last account is never updated.** After EOF, the `PERFORM UNTIL` test exits before the `ELSE PERFORM 1050` branch (188-221) can run. The last account's interest is posted as a transaction, but its balance is not updated and its cycle fields are not cleared. | Golden `after-CBTRN02C`: tran `2022071800000050` charges acct 50 interest of 18.77 (`0000000187G`). Acct 50 in ACCTFILE is identical to the CBTRN02C input (balance `00000019458G`). Only 49 of 50 records change. | Preserve + pin. Flag for a separate CR. |
| D2 | **Interest is truncated, not rounded**, so customers are under-charged by up to 0.01 per category per month. | No `ROUNDED` at 464-465. Python check: all 50 golden amounts = truncation; 28 would differ under rounding. | Preserve + pin. |
| D3 | **Overflow is silent.** There is no `ON SIZE ERROR`. `WS-TRANID-SUFFIX PIC 9(6)` wraps after 999,999 transactions, which would create duplicate TRAN-IDs. Interest larger than `S9(9)V99` would be truncated on the high-order side. | 168, 173, 474 | Preserve with modulo/truncation semantics. Not reachable on the sample data. |
| D4 | **Disclosure groups never match on sample data.** The group code sits in `ACCT-ADDR-ZIP` and `ACCT-GROUP-ID` is blank, so every lookup uses DEFAULT. | 100 × `DISCLOSURE GROUP RECORD MISSING` in golden SYSOUT. `legacy/README.md` | Data issue, not a code issue. Flag it. |
| D5 | `1400-COMPUTE-FEES` is a no-op. | 518-520 | Out of scope (CR-2). |
| D6 | The DISCGRP open-error message says `DALY REJECTS FILE` (a copy-paste slip). | 281 | Preserve the text. |
| R1 | **Hidden coupling to sorting and grouping.** Correctness depends on TCATBAL being read in key order, so that all of an account's categories are contiguous. | 28-32, 194 | Read input in key order (it is unloaded in key order). |
| R2 | **Timestamps.** The hundredths-of-a-second value comes from the clock, and the last 4 digits are always `0000`. | 621-622 | Clock must be injectable for the tests. |
| R3 | **Exact money handling.** Overpunch signs and fixed-point arithmetic must be exact. | — | Use `BigDecimal`/`long` with explicit truncation. No floating point. |
| R4 | **Two input-format quirks.** `cardxref.txt` lines are 36 bytes, short of the 50-byte record. `tcatbal.txt` has CRLF line endings. The JCL's `XREFFIL1` AIX path DD is never referenced by the program. | `legacy/README.md`; INTCALC.jcl:31-32 | Reader strips CR and pads short lines to the record length. |

## Questions for the approver

1. **Must SYSOUT also be byte-identical?** The CR names only TRANSACT and ACCTFILE. *Default: yes.* It is cheap, and it shows the same lookups happen (including the DEFAULT fallback lines). Operations may rely on it.
2. **Abend exit code.** On the mainframe an abend is U0999; the harness reports 16. *Default: Java exits 16, and stderr reads `ABENDING PROGRAM`.*
3. **D1 and D2 change customer balances.** Should they be raised as separate CRs now? *Default: yes. Log them as follow-ups. Do not fix them in CR-1.*
