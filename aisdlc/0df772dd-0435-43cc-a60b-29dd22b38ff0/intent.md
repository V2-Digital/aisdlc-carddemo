# CR-1 Intent — INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation (`app/jcl/INTCALC.jcl`, step `STEP15`, program `CBACT04C`) runs only on the mainframe. That stops the bank from moving batch workloads off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. For the same inputs it produces byte-identical interest transactions (`TRANSACT`) and an identical updated account master (`ACCTFILE`). Legacy behaviour, defects included, is kept exactly, so the cut-over changes nothing for the business.

## What the legacy code does today

Source: `app/cbl/CBACT04C.cbl` (652 lines). Run under the harness on 2026-10-18, both golden scenarios reproduce byte-for-byte (`CBACT04C`: 50 TRANSACT, 50 ACCTFILE, 152 SYSOUT lines, RC 0; `CBACT04C-after-CBTRN02C`: 50 / 50 / 302, RC 0).

### Files

| DD | Org / access | LRECL | Key | Use | Copybook |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential, INPUT | 50 | acct(11)+type(2)+cat(4), cols 1-17 | driver: category balances | `CVTRA01Y` (`TRAN-CAT-BAL` S9(9)V99) |
| `XREFFILE` | KSDS, random, INPUT | 50 | card(16); **AIX acct-id cols 26-36** | card number for the account | `CVACT03Y` |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct-id(11) | read + REWRITE | `CVACT01Y` |
| `DISCGRP` | KSDS, random, INPUT | 50 | group(10)+type(2)+cat(4) | interest rate `DIS-INT-RATE` S9(4)V99 (annual %) | `CVTRA02Y` |
| `TRANSACT` | sequential, OUTPUT | 350 | – | new interest transactions | `CVTRA05Y` |
| PARM | `'2022071800'` | 10 | – | `PARM-DATE`, transaction-ID prefix | `CBACT04C.cbl:176-178` |

Select/FD definitions: `CBACT04C.cbl:28-92`. `XREFFIL1` (the AIX path in the JCL) is never opened by name. The alternate key comes from `ALTERNATE RECORD KEY` (`:38`).

### Processing steps

1. Open all five files. If any status is not `00`: message, then abend (`:234-323`).
2. Read `TCATBALF` sequentially in key order, so balances arrive grouped by account. Each record is `DISPLAY`ed and counted (`:188-193`, `:325-348`).
3. **When the account changes** (`:194-206`):
   - if this is not the first account, rewrite the previous one (step 6);
   - reset `WS-TOTAL-INT` to 0;
   - read `ACCTFILE` by acct-id (`:372-391`);
   - read `XREFFILE` by the alternate key acct-id (`:393-413`) to get `XREF-CARD-NUM`.
   - Not found → `ACCOUNT NOT FOUND`, then abend.
4. **Rate lookup** for each balance record (`:210-213`, `:415-460`):
   - key = `ACCT-GROUP-ID` + type + category;
   - status `23` (not found) → print `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE`, then retry with group `DEFAULT`;
   - `DEFAULT` missing, or any other status → abend.
5. **Interest**, only if rate ≠ 0 (`:214-217`, `:462-470`):
   - `monthly = (TRAN-CAT-BAL × rate) / 1200`. `COMPUTE` has no `ROUNDED`, so the result is truncated to 2 decimals in `S9(9)V99`, and there is no `ON SIZE ERROR`;
   - added to `WS-TOTAL-INT`;
   - one transaction written (`:473-514`):
     - `TRAN-ID` = PARM-DATE + 6-digit running suffix (`:474-480`);
     - type `01`, category `0005`, source `System`, desc `Int. for a/c <acct-id>`;
     - amount = monthly interest; merchant id 0; merchant name/city/zip spaces;
     - card = `XREF-CARD-NUM`;
     - orig and proc timestamps = `CURRENT-DATE` formatted `YYYY-MM-DD-HH.MM.SS.hh0000` (`:613-626`);
     - the 20-byte FILLER and the tail of `TRAN-DESC` are never initialised. They come out as spaces.
   - `1400-COMPUTE-FEES` is then performed. It is empty (`:518-519`).
6. **Account update** (`:350-370`):
   - `ACCT-CURR-BAL += WS-TOTAL-INT`;
   - `ACCT-CURR-CYC-CREDIT = 0` and `ACCT-CURR-CYC-DEBIT = 0`;
   - REWRITE the whole 300-byte record. All other fields pass through unchanged.
7. Close all files; print `END OF EXECUTION OF PROGRAM CBACT04C`; RC 0 (`:224-232`).
8. Abend (`:628-633`): prints `ABENDING PROGRAM` and calls `CEE3ABD` with 999. The harness turns this into exit 16. File status is printed as `FILE STATUS IS: NNNN` + 4 digits (`:635-652`).

### Business rules, checked against the golden data

- Account 1, category `01/0001`:
  - balance 1164.87 (`0000011648G`) at DEFAULT rate 15.00% → 14.560875 → **14.56** (`0000000145F`).
  - The `03/0001` balance (-70.77) has DEFAULT rate 0, so no transaction is written.
  - New balance 1288.10 + 14.56 = **1302.66** (`00000013026F`); cycle credit/debit become `00000000000{`.
- In the sample data, `ACCT-GROUP-ID` is blank for every account (cols 112-121; the `A000000000` sits in ZIP). So every lookup falls back to `DEFAULT`: 100 `MISSING` messages in the after-CBTRN02C SYSOUT.
- 100 balance records (50 type `01`, 50 type `03`) → 50 transactions. Only type 01/cat 0001 has a non-zero DEFAULT rate.
- Interest is written even when it is 0.00. The stock scenario writes 50 zero-amount transactions because every stock balance is 0.

## Scope

**In**
- Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only):
  - reads the four inputs and writes `TRANSACT` and `ACCTFILE` in the harness's canonical text format (fixed width, LF, overpunch signs);
  - takes the PARM date and the run timestamp as inputs.
- An equivalence test against both golden scenarios.
- Characterization tests that pin each defect below.
- A short run note: how to invoke it and how to feed it the files.

**Out**
- Fixing any legacy defect (separate decision, see Risks).
- CR-2 fee logic (`1400-COMPUTE-FEES`).
- `CBTRN02C`.
- Reading or writing native VSAM/EBCDIC. Conversion stays with the existing unload/load tooling.
- Scheduler (CA7/Control-M) changes, container image and deployment wiring. Deploy stage handles those.

## Acceptance criteria

1. Given the `CBACT04C` stock inputs and PARM `2022071800`, with the clock fixed at `2022-07-18 00:00:00.00`: Java `TRANSACT` and `ACCTFILE` are byte-identical to `legacy/golden/CBACT04C/` (50 + 50 records).
2. Given `ACCTFILE` and `TCATBALF` from `legacy/golden/CBTRN02C/` plus the stock XREF/DISCGRP inputs: outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/` (50 + 50).
3. The last account in `TCATBALF` order is **not** rewritten. In scenario 2, account `00000000050` stays identical to `golden/CBTRN02C/ACCTFILE.txt`, while its transaction of 18.77 (`0000000187G`) is still written. Pinned by a test.
4. Interest truncates rather than rounds. A test with a balance where the two differ (for example 0.40 at 15% → 0.005 → `0.00`, where rounding would give 0.01) pins truncation. A test with a negative balance pins that negative interest is computed and written.
5. A missing group falls back to `DEFAULT`. If `DEFAULT` is also missing, the run ends with exit code 16.
6. Rate 0 → no transaction, but the account is still rewritten with its cycle fields zeroed (unless it is the last account, AC 3).
7. Transaction IDs are `PARM-DATE` + a 6-digit suffix starting at `000001`, numbered across the whole run. Uninitialised bytes come out as spaces.
8. A missing account or XREF record, or any I/O error, gives exit 16 with no partial-success RC 0. Normal completion gives exit 0.
9. `mvn test` passes on Java 21 using only JUnit 5 + Surefire. `legacy/harness/golden.sh --check` still passes.
10. SYSOUT lines and RC also match the golden files for both scenarios (default for Q1).

## Risks and findings

Legacy defects. Preserve them all in CR-1; each needs a separate decision.

| # | Finding | Evidence | Impact |
|---|---|---|---|
| D1 | **Last account never updated.** After EOF the loop test (`:188`) ends the loop before the `ELSE PERFORM 1050-UPDATE-ACCOUNT` (`:219-220`) can run. | Scenario 2: acct 50 is charged 18.77 in TRANSACT, but its ACCTFILE balance stays 1945.87 (`00000019458G`) and cycle 1501.75 / -47.88 are not zeroed. 49 of 50 records change. | Transactions and the master disagree every night for one account. |
| D2 | Interest is truncated, not rounded (no `ROUNDED`, `:464-465`). | COBOL semantics. Amounts in the golden data are consistent with it. | Systematic under-charge of up to 0.01 per category. |
| D3 | A negative (credit) balance at a non-zero rate produces negative interest. No sign guard. | `:464-467` | Not triggered by the sample data: type 03 credits have rate 0. |
| D4 | Zero-amount transactions are written whenever the rate is non-zero. | Stock golden: 50 × `0000000000{` | Noise in the transaction history. |
| D5 | `ACCT-GROUP-ID` is blank in the sample data, so every lookup uses `DEFAULT` (100 SYSOUT warnings). | acctdata cols 112-121 | Data issue rather than code, but it hides group-specific pricing. |
| D6 | No overflow protection: `S9(9)V99` interest and `S9(10)V99` balance would silently drop high-order digits. The 6-digit suffix wraps after 999,999 transactions, giving duplicate IDs. | `:168`, `:173`, `:352` | Edge case. Java must mimic it to stay like-for-like. |
| D7 | The XREF alternate key is non-unique. Only the first card for the account is used. | `:38`, `:394-395` | No duplicates in the sample data (0 found). |
| D8 | The DISCGRP open-error message says `ERROR OPENING DALY REJECTS FILE`. | `:281` | Cosmetic. Keep the text. |
| D9 | `PARM-LENGTH` is ignored. A short or missing PARM yields junk IDs. | `:176-178`, `:476` | Java should require exactly 10 characters, or mimic. See Q3. |

Migration risks:
- Decimal arithmetic must be `BigDecimal` with the COBOL truncation and digit limits. `double` is not acceptable.
- Overpunch sign encoding has to match exactly: `-0` is written as `}`.
- Timestamps come from the clock. Inject the clock so runs are reproducible.

## Questions for the approver

1. **SYSOUT/RC parity.** The CR requires byte-identity only for TRANSACT and ACCTFILE. *Default: also match SYSOUT and RC against golden.* Operators keep the same log lines, and the cost is low.
2. **I/O format.** *Default: the Java job reads and writes the canonical fixed-width text files, the same format the harness unloads. File paths are bound by DD-name environment variables or arguments.* Native VSAM/EBCDIC conversion stays outside this CR.
3. **Invalid PARM** (D9). *Default: if the PARM is not exactly 10 characters, exit 16 with a message.* This is the only intentional deviation proposed, and the golden runs never exercise it. Reply "mimic" to keep the legacy behaviour.
4. **Defects D1-D4.** *Default: preserve in CR-1 and raise D1 as a separate change request* (it has the highest business impact).
