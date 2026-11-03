# CR-1 Intent — Modernise INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation (`INTCALC`) runs only as COBOL on the mainframe. That blocks the
plan to move batch workloads into containers, and it ties a money-moving job to a shrinking skill set.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that, from the same input files and run date,
produces **byte-identical** interest transactions (`TRANSACT`) and an identical updated account master
(`ACCTFILE`). This is proven on the golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Behaviour is
like-for-like, including known defects, which are pinned by tests and raised for a separate decision.

## What the legacy code does today

Job `app/jcl/INTCALC.jcl:22` runs `EXEC PGM=CBACT04C,PARM='2022071800'`. The PARM is a 10-character run date
(`PARM-DATE`, `CBACT04C.cbl:178`).

### Files

| DD | Org / access | LRECL | Key | Copybook | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential read | 50 | acct(11)+type(2)+cat(4), cols 1-17 | `CVTRA01Y` | driver: balance per account/category (`cbl:28-32`) |
| `XREFFILE` | KSDS, random by **alt key** acct-id (cols 26-36) | 50 | card num (cols 1-16) | `CVACT03Y` | card number for the account (`cbl:34-39`, `394-395`) |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct id (cols 1-11) | `CVACT01Y` | read + REWRITE (`cbl:41-45`, `291`) |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4), cols 1-16 | `CVTRA02Y` | interest rate `DIS-INT-RATE` S9(4)V99, cols 17-22 (`cbl:47-51`) |
| `TRANSACT` | sequential, OUTPUT | 350 | none | `CVTRA05Y` | new interest transactions (`cbl:53-56`, `309`) |

Numbers are zoned decimal with EBCDIC overpunch signs. Harness I/O is one fixed-width record per LF-terminated
line, and KSDS files are unloaded in key order.

### Processing steps

1. Open all five files. Any open status other than `00` displays an error and abends with U0999 (exit **16**)
   (`cbl:234-323`, `628-632`).
2. Read `TCATBALF` sequentially and `DISPLAY` each 50-byte record (`cbl:190-193`).
3. **On an account break** (`TRANCAT-ACCT-ID ≠ WS-LAST-ACCT-NUM`, `cbl:194`):
   - Rewrite the previous account, except on the first record (`cbl:195-199`).
   - Reset the accumulated interest to 0 (`cbl:200`).
   - Read the account (`1100`, `cbl:372-391`). If it is not found, display `ACCOUNT NOT FOUND:` and abend.
   - Read the xref by account id (`1110`, `cbl:393-413`). If it is not found, display the message and abend.
4. **Rate lookup** (`1200`, `cbl:415-440`): the key is `ACCT-GROUP-ID` + type + category (`cbl:210-212`).
   - Status `23` displays `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and retries with
     group `DEFAULT` (`1200-A`, `cbl:443-460`). If `DEFAULT` is also missing, the program abends.
5. **If the rate ≠ 0** (`cbl:214`):
   - Monthly interest = `TRAN-CAT-BAL × DIS-INT-RATE / 1200` into S9(9)V99. There is **no ROUNDED**, so the
     result is truncated toward zero (`cbl:464-465`).
   - Add the monthly interest to the account total (`cbl:467`).
   - Write one transaction (`1300-B`, `cbl:473-515`):
     - `TRAN-ID` = PARM-DATE + 6-digit running counter starting at 000001 (`cbl:474-480`).
     - Type `01`, category `0005`, source `System` (`cbl:482-484`).
     - Description `Int. for a/c ` + 11-digit ACCT-ID (`cbl:485-489`).
     - Amount = monthly interest. Merchant id is 0; merchant name, city and zip are spaces.
     - Card number comes from the xref.
     - `TRAN-ORIG-TS` = `TRAN-PROC-TS` = current timestamp `YYYY-MM-DD-HH.MM.SS.hh0000` (`cbl:613-626`).
     - The rest of the description and the 20-byte FILLER stay spaces (`TRAN-RECORD` is never initialised).
   - `1400-COMPUTE-FEES` runs and does nothing (`cbl:518-520`).
   - If the rate is 0, no transaction is written and nothing is added.
6. **Account update** (`1050`, `cbl:350-370`): add the total interest to `ACCT-CURR-BAL`, zero
   `ACCT-CURR-CYC-CREDIT` and `-DEBIT`, then REWRITE. A failed rewrite abends.
7. At EOF, close all files and display start/end banners. RC is 0.

### Observed behaviour (harness, `COB_CURRENT_DATE=2022071800000000`)

- **Stock data:** RC 0, SYSOUT 152 lines, 50 TRANSACT records, all with amount 0. All 50 tcatbal rows are
  type/cat `01/0001`, every balance is 0, and the `DEFAULT` rate is 15.00%. ACCTFILE is unchanged. This matches
  `golden/CBACT04C`.
- **After CBTRN02C:** 100 tcatbal rows (50 × `01/0001`, 50 × `03/0001`). Type `03` has `DEFAULT` rate 0.00, so
  it produces no transaction. Result: 50 TRANSACT records and 49 of 50 accounts changed. This matches
  `golden/CBACT04C-after-CBTRN02C`.
- **Truncation, checked by hand on account 1:** the balance is 1164.87 (`0000011648G`) and the rate 15.00.
  1164.87 × 15 / 1200 = 14.560875, and the output is `0000000145F` = **14.56**.
- **No group-specific rate is ever used.** In every lookup `ACCT-GROUP-ID` is blank (`A000000000` sits in
  `ACCT-ADDR-ZIP`), so all rates come from the `DEFAULT` group.

## Scope

**In:**
- A Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only) that reproduces steps 1-7. It reads and writes
  the harness's canonical text datasets, and takes the run date (PARM) and the clock as inputs.
- Byte-identical `TRANSACT.txt` and `ACCTFILE.txt` on both golden scenarios.
- Characterization tests for every defect and quirk listed below.
- Abend paths that stop with exit code 16.

**Out:**
- Fixing any legacy defect, including the last-account write-back and the fees logic (CR-2).
- CBTRN02C, JCL/scheduler changes, and VSAM/DB2 replacement.
- Container image and deployment wiring (Deploy stage).
- Performance tuning.

## Acceptance criteria

1. `mvn -q test` passes in `modernized/cbact04c/` on Java 21 with no dependencies beyond JUnit 5 and Surefire.
2. Scenario `CBACT04C` (stock inputs, PARM `2022071800`, clock `2022-07-18 00:00:00.00`): the Java
   `TRANSACT.txt` (50 records) and `ACCTFILE.txt` (50 records) are byte-identical to `legacy/golden/CBACT04C/`.
3. Scenario `CBACT04C-after-CBTRN02C` (ACCTFILE + TCATBALF from `golden/CBTRN02C`): `TRANSACT.txt` and
   `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. Interest is truncated toward zero, not rounded. A test pins 1164.87 @ 15.00 → 14.56. Negative balances
   follow COBOL truncation and sign rules, The sign of a zero result (`{` or `}`) must be confirmed by a harness run in Design.
5. Zero rate means no transaction and no change to the interest total. A test pins type `03` in the
   after-CBTRN02C scenario.
6. A missing group rate falls back to `DEFAULT`. If `DEFAULT` is also missing, or the account or xref is not
   found, the program exits with code 16 and writes no further output.
7. **Defect pinned:** the last account in TCATBALF is never rewritten. Account `00000000050` keeps balance
   1945.87 (`00000019458G`) and its cycle fields, although its interest transaction (18.77) is written.
8. Uninitialised fields (description tail, cols 331-350) are spaces. Timestamps use the injected clock in the
   format `YYYY-MM-DD-HH.MM.SS.hh0000`.
9. SYSOUT matches the golden `SYSOUT.txt` line for line, and RC is 0 on success (see Q1).

## Risks and findings

| # | Finding | Evidence | Treatment |
|---|---|---|---|
| D1 | **Last account's interest never posted** to ACCTFILE. The `ELSE PERFORM 1050` at EOF is unreachable because `PERFORM UNTIL` tests before the body. | `cbl:188-222`. In `golden/CBACT04C-after-CBTRN02C`, acct 50 is charged 18.77 in TRANSACT but its ACCTFILE row equals `golden/CBTRN02C/ACCTFILE.txt`. 49/50 rows changed. | Preserve and pin (AC7). Raise a separate fix CR. |
| D2 | `1400-COMPUTE-FEES` is empty, so no fees are ever charged. | `cbl:518-520` | Preserve (CR-2). |
| D3 | `ACCT-GROUP-ID` is blank in sample data, so every lookup misses and uses `DEFAULT` with SYSOUT noise. | 100 × `DISCLOSURE GROUP RECORD MISSING` in golden SYSOUT. | Preserve. Data-quality issue, not code. |
| D4 | Wrong error text: a DISCGRP open failure says `ERROR OPENING DALY REJECTS FILE`. | `cbl:281` | Preserve the text. |
| D5 | Truncation, not rounding, so fractions of a cent always go to the cardholder. | `cbl:464` and the account 1 check | Preserve and pin (AC4). Business decision later. |
| D6 | No size check on `ADD` to `ACCT-CURR-BAL` S9(10)V99 or on the interest S9(9)V99. COBOL silently drops high-order digits. | `cbl:352`, `464` | Mimic truncation and unit-test it. Unreachable with real data. |
| D7 | Accounts with balance 0 still get a zero-amount transaction (50 in stock run). Cycle credit/debit are zeroed for every account in TCATBALF, even with no interest. | `golden/CBACT04C/TRANSACT.txt` | Preserve. |
| R1 | Golden data has **no negative type-01 balances**, so negative-sign arithmetic is covered only by unit tests. | sign histogram of `golden/CBTRN02C/TCATBALF.txt` | Use decimal-exact `BigDecimal`/`long` arithmetic and add explicit tests. |
| R2 | Group-specific rates and the `DEFAULT`-missing abend are never exercised by golden data. | D3 | Add synthetic tests. Optionally run extra harness scenarios via `INPUT_DIR`. |
| R3 | The xref lookup by alternate key returns the first card for the account. The sample data has no duplicate account ids. | `cut -c26-36 cardxref.txt \| uniq -d` is empty | Use the first match in file order and document it. |

## Questions for the approver

1. **SYSOUT and RC parity:** should the Java program also reproduce SYSOUT (152 / 302 lines) exactly, or only
   TRANSACT and ACCTFILE? *Default: reproduce SYSOUT and RC too. The cost is low and it helps operations
   continuity.*
2. **I/O format:** should the Java program read and write the harness's canonical fixed-width text datasets, one
   file per DD, configured by directory/env? It would not touch VSAM directly. *Default: yes. Mapping to the
   real target storage belongs to the Deploy stage.*
3. **Run date and clock:** PARM date as a CLI argument, and the timestamp clock injectable, defaulting to the
   system UTC clock. *Default: yes, with an override equivalent to `COB_CURRENT_DATE` for tests.*
