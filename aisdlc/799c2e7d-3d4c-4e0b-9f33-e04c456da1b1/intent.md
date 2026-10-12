# CR-1 Intent — INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation runs only as COBOL on the mainframe (`app/jcl/INTCALC.jcl` → `CBACT04C`). That ties a core card batch job to mainframe capacity, skills and scheduling, and holds back the move of batch work off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. Given the same inputs, it writes the same interest transactions and updated account master as the COBOL, byte for byte. It reproduces the legacy behaviour exactly, defects included. Defects are pinned by tests and listed for a separate decision.

## What the legacy code does today

All line refs are `app/cbl/CBACT04C.cbl` unless noted.

### Invocation
- `//STEP15 EXEC PGM=CBACT04C,PARM='2022071800'` (`app/jcl/INTCALC.jcl`). PARM is the 10-char `PARM-DATE` (:178). It is used only as the transaction-id prefix.
- Timestamps come from `FUNCTION CURRENT-DATE`, not from PARM (:613-626). The harness freezes the clock with `COB_CURRENT_DATE=2022071800000000`.

### Files

Positions are 1-based. Text form is fixed width with overpunch signs.

| DD | Copybook | Org / access | LRECL | Key | Use |
|---|---|---|---|---|---|
| `TCATBALF` | CVTRA01Y | KSDS, sequential (:28) | 50 | 1-17: acct 1-11, type 12-13, cat 14-17 | input, driver |
| `XREFFILE` | CVACT03Y | KSDS, random (:34) | 50 | card 1-16; **AIX acct 26-36** (used) | input |
| `ACCTFILE` | CVACT01Y | KSDS, random, **I-O** (:41) | 300 | acct 1-11 | read + REWRITE |
| `DISCGRP` | CVTRA02Y | KSDS, random (:47) | 50 | group 1-10, type 11-12, cat 13-16 | input |
| `TRANSACT` | CVTRA05Y | sequential, OUTPUT (:53) | 350 | — | new interest trans |

Fields that matter:
- **TCATBAL:** `TRAN-CAT-BAL` S9(9)V99, cols 18-28.
- **DISCGRP:** `DIS-INT-RATE` S9(4)V99, cols 17-22.
- **ACCT:**
  - `CURR-BAL` S9(10)V99, cols 13-24
  - `CURR-CYC-CREDIT` 79-90 and `CURR-CYC-DEBIT` 91-102, both S9(10)V99
  - `ADDR-ZIP` 103-112
  - `GROUP-ID` 113-122
- **TRAN:** id 1-16, type 17-18, cat 19-22, source 23-32, desc 33-132, amt 133-143 (S9(9)V99), merchant id 144-152, name 153-202, city 203-252, zip 253-262, card 263-278, orig-ts 279-304, proc-ts 305-330, filler 331-350.

### Processing steps
1. Display `START OF EXECUTION OF PROGRAM CBACT04C`. Open all five files. Any status other than `00` → error text, `9910` status display, `9999` abend (:234-323).
2. Read `TCATBALF` in key order (:325). Status `10` = EOF; any other non-`00` status abends. For each record (:188-222):
   1. Count it and `DISPLAY` the 50-byte record (:193).
   2. When the account id changes (:194):
      - unless this is the first record, run `1050-UPDATE-ACCOUNT` for the previous account (:196);
      - reset the total interest;
      - random-read `ACCTFILE` (:373) and read `XREFFILE` by the AIX acct id (:394).
      - Either read failing (status `23`) shows `ACCOUNT NOT FOUND: …` and then abends.
   3. Look up `DISCGRP` with key (`ACCT-GROUP-ID`, type, cat) (:415).
      - On `23`: display `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE`, then retry with group `DEFAULT` (:436-460). If `DEFAULT` also misses → abend.
   4. If the rate ≠ 0 (:214):
      - `WS-MONTHLY-INT = TRAN-CAT-BAL * DIS-INT-RATE / 1200` (:464). Result is S9(9)V99 with **no ROUNDED**, so it is truncated.
      - Add it to `WS-TOTAL-INT`.
      - Write one transaction (:473-515).
      - Perform `1400-COMPUTE-FEES`, which is empty (:518).
3. Transaction written by `1300-B-WRITE-TX`:

   | Field | Value |
   |---|---|
   | id | `PARM-DATE` + 6-digit running suffix (:474-480) |
   | type / cat | `01` / `0005` |
   | source | `System` |
   | desc | `Int. for a/c ` + ACCT-ID, rest of field spaces |
   | amt | monthly interest |
   | merchant id | 0 |
   | merchant name / city / zip | spaces |
   | card | `XREF-CARD-NUM` |
   | both timestamps | `YYYY-MM-DD-HH.MM.SS.hh0000` |
   | filler | spaces (never initialised; GnuCOBOL default dialect) |

4. `1050-UPDATE-ACCOUNT` (:350-371):
   - `CURR-BAL += total interest`
   - cycle credit = 0 and cycle debit = 0
   - REWRITE the record; a status other than `00` abends.
5. Close all files (status checked), display `END OF EXECUTION…`, `GOBACK` with RC 0.
6. Abend (:628-633) calls `CEE3ABD` with 999. The harness stub exits **16**.

### Observed behaviour (harness, run today)
- `run.sh CBACT04C` matches `legacy/golden/CBACT04C` byte for byte: TRANSACT 50, ACCTFILE 50, SYSOUT 152, RC 0.
- **Stock data:**
  - every TCATBAL balance is 0 → 50 zero-amount transactions;
  - ACCTFILE is unchanged vs `acctdata.txt`;
  - all 50 group lookups miss (`ACCT-GROUP-ID` is blank; the `A000000000` code sits in ZIP) → `DEFAULT`.
- **`CBACT04C-after-CBTRN02C`:**
  - 100 TCATBAL records (50 accounts × types `01` and `03`);
  - `DEFAULT/03/0001` has rate 0, so only the 50 type-01 rows produce transactions (`2022071800000001`…`…000050`);
  - DEFAULT 01/0001 = 15.00%;
  - SYSOUT has 302 lines (100 record displays + 200 lookup-miss lines + start/end).
- **Truncation verified:** all 50 amounts equal bal × 15 / 1200 truncated; only 22 would match half-up rounding. Example: acct 2, 2339.97 → 29.249625 → **29.24**.

## Scope

**In:**
- Java 21 / Maven / JUnit 5 rewrite in `modernized/cbact04c/`.
- It reads and writes the canonical text form of the five datasets (same format as `legacy/golden`).
- It takes PARM-date and the clock as inputs (a clock override, for determinism).
- The same SYSOUT lines and exit codes (0, abend 16).
- Characterization tests for each defect below.
- An equivalence test against both golden scenarios.

**Out:**
- Fixing any defect.
- Fees logic (`1400`, CR-2).
- `CBTRN02C`.
- Real VSAM/EBCDIC binary I/O, JCL/scheduler changes, container image/deploy plumbing (Deploy stage).
- Performance tuning.

## Acceptance criteria

1. `mvn -f modernized/cbact04c test` passes on Java 21 with JUnit 5 as the only dependency.
2. With PARM `2022071800` and clock `2022-07-18 00:00:00.00`, on `app/data/ASCII` inputs, the program writes `TRANSACT.txt` and `ACCTFILE.txt` byte-identical to `legacy/golden/CBACT04C/`.
3. With `ACCTFILE`/`TCATBALF` from `legacy/golden/CBTRN02C/` and the other inputs from the sample data, outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/` (50 + 50 records).
4. SYSOUT and exit code match the golden `SYSOUT.txt` (152 / 302 lines) and `RC.txt` (`0`). *(Proposed default; see Q1.)*
5. Interest is `bal × rate / 1200` **truncated** to 2 dp (pinned with acct 2 → 29.24). Exact decimal arithmetic is used, never binary floating point.
6. Characterization tests pin each defect (D1-D4) to the legacy behaviour.
7. A missing account, xref, or DEFAULT disclosure group, or an I/O error, stops the run with the legacy message and exit 16.
8. Zoned overpunch signs round-trip for positive, negative and zero values (`{`, `A`-`I`, `}`, `J`-`R`).
9. `legacy/harness/golden.sh --check` still passes. Nothing under `legacy/golden`, `legacy/harness` or `.github` changes.

## Risks and findings

| # | Finding | Evidence | Treatment |
|---|---|---|---|
| D1 | **Last account never updated.** The `ELSE PERFORM 1050` (:220) is unreachable: after EOF the loop condition (:188) is tested before the body runs again. The last account's interest is charged as a transaction but never added to its balance, and its cycle fields are not zeroed. | after-CBTRN02C: `TRANSACT` record `…000050` charges acct 50 18.77 (`0000000187G`). Acct 50 in `ACCTFILE` is byte-identical to `golden/CBTRN02C/ACCTFILE.txt` (bal `00000019458G`). 49 of 50 records changed. | Preserve and pin; flag. |
| D2 | **Truncation, not rounding,** of monthly interest (:464, no `ROUNDED`). Customers are under-charged by up to 0.01 per category per month. | 50/50 amounts match truncation; only 22 match rounding. | Preserve and pin; flag. |
| D3 | **Group-id lookup always misses on the sample data:** `ACCT-GROUP-ID` (113-122) is blank and the code sits in ZIP. Every account gets `DEFAULT` rates. Probably a data-load issue. | 50 / 100 `DISCLOSURE GROUP RECORD MISSING` lines in SYSOUT. | Preserve the lookup logic exactly; flag the data issue. |
| D4 | **Silent overflow:** `WS-TRANID-SUFFIX` 9(6) and the S9(9)V99 interest fields have no `ON SIZE ERROR`, so they wrap or truncate high-order digits. The tran id repeats after 999,999 rows. | :173, :464, :474 (code reading; not reachable with sample data). | Mirror COBOL truncation; unit-test it. |
| F1 | Misleading message: a DISCGRP open failure prints `ERROR OPENING DALY REJECTS FILE`. | :281 | Preserve text. |
| F2 | Timestamps use the wall clock, not PARM. Reruns are not reproducible without a clock override. | :613-626; README Determinism | Injectable clock; default is the system clock. |
| F3 | Uninitialised `TRAN-RECORD` tail and filler are spaces only under the default dialect (`-std=ibm` gives `x'00'`). | `legacy/README.md` quirks | Match golden (spaces). |
| R1 | Real mainframe input is EBCDIC VSAM, not text. Byte-identity is proven only on the canonical text form. | Harness design | Out of scope; conversion is handled at the platform boundary. |
| R2 | The golden data exercises only positive balances, the `DEFAULT` path and one non-zero rate (15%). | Data above | Add unit tests: negative balance, group hit, zero rate, empty TCATBAL. |

## Questions for the approver

1. **Must SYSOUT and the exit code match the legacy exactly?** The CR names only TRANSACT and ACCTFILE. *Default: yes.* It costs little and keeps operator runbooks valid.
2. **Defect follow-ups:** D1 (last account not updated) and D2 (truncation) affect customer balances. Should they be raised as separate CRs? *Default: yes, one CR each; no change here.*
