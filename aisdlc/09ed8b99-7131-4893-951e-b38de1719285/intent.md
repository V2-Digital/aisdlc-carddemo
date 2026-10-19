# CR-1 Intent: INTCALC (`CBACT04C`) to Java 21

## Problem
The nightly interest calculation (`INTCALC`) runs as COBOL on the mainframe. That ties a core batch step to
mainframe capacity and skills, and blocks the move of batch work into containers.

## Outcome
A plain Java 21 program in `modernized/cbact04c/` that does the same job and runs in a container. For the same
inputs it writes **byte-identical** `TRANSACT` (interest transactions) and `ACCTFILE` (updated account master),
proven on the golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Behaviour stays like-for-like: legacy
defects are kept, pinned by tests, and listed below for a separate decision.

## What the legacy code does today

**Job:** `app/jcl/INTCALC.jcl:22` runs `EXEC PGM=CBACT04C,PARM='2022071800'`. The PARM is the run date and
becomes the prefix of every transaction id.

**Files** (all fixed-width display records; layouts come from the copybooks)

| DD | Access (`CBACT04C.cbl`) | Record / key | Sample / golden |
|---|---|---|---|
| `TCATBALF` | KSDS, sequential INPUT (:28) | `CVTRA01Y`, 50 B. Key 1-17 = acct(11)+type(2)+cat(4). Balance at 18-28 is `S9(9)V99` | `tcatbal.txt` has 50 recs, all zero balances. `golden/CBTRN02C/TCATBALF.txt` has 100 |
| `XREFFILE` | KSDS, random by **alternate key** acct id (:34-38) | `CVACT03Y`, 50 B. Card number 1-16, acct id 26-36 | `cardxref.txt`, 50 recs (36-byte lines), one card per account |
| `DISCGRP` | KSDS, random INPUT (:47) | `CVTRA02Y`, 50 B. Key 1-16 = group(10)+type(2)+cat(4). Rate at 17-22 is `S9(4)V99` | `discgrp.txt`, 51 recs (incl. `DEFAULT` groups) |
| `ACCTFILE` | KSDS, random **I-O**, REWRITE (:41, :291) | `CVACT01Y`, 300 B. Key acct id 1-11. Curr bal 13-24, cyc credit 79-90, cyc debit 91-102, group id 113-122 | `acctdata.txt`, 50 recs |
| `TRANSACT` | Sequential OUTPUT, LRECL 350 (:53, :309) | `CVTRA05Y`, 350 B | New GDG generation |

**Processing steps** (line numbers refer to `app/cbl/CBACT04C.cbl`)
1. Open all five files. Any status other than `00` → abend (:234-323).
2. Read `TCATBALF` sequentially in key order, so the records arrive grouped by account (:188-190, :325). Each record is DISPLAYed to SYSOUT (:193).
3. **When the account changes** (:194):
   - If this is not the first account, rewrite the previous account (step 6).
   - Reset the total interest to 0 (:200).
   - Read `ACCTFILE` by acct id (:372) and `XREFFILE` by alt key acct id (:393). INVALID KEY displays `ACCOUNT NOT FOUND`, then the non-`00` status abends.
4. **Look up the rate** in `DISCGRP` (:210-213, :415):
   - The key is `ACCT-GROUP-ID` + the TCATBAL type + category.
   - On status `23` the program displays `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and retries with group `DEFAULT` (:436-438).
   - If the `DEFAULT` record is also missing, the program abends (:443-459).
5. **If the rate ≠ 0** (:214):
   - Monthly interest = `(TRAN-CAT-BAL * DIS-INT-RATE) / 1200` into `S9(9)V99`. There is no `ROUNDED`, so the result is **truncated** (:464-465).
   - Add the monthly interest to the account total (:467).
   - Write one transaction (:473-500):
     - id = PARM-DATE(10) + a 6-digit run-wide counter (:474-479)
     - type `01`, cat `0005`, source `System`
     - desc `Int. for a/c ` + ACCT-ID
     - amount = monthly interest; merchant id 0; merchant name/city/zip spaces
     - card = the xref card number
     - orig/proc timestamp = `YYYY-MM-DD-HH.MM.SS.hh0000` from `CURRENT-DATE` (:613-624)
     - remaining bytes stay as uninitialised WORKING-STORAGE, which is spaces (README "Known quirks")
   - `1400-COMPUTE-FEES` is then performed. It is empty (:518-520).
6. **Account update** (:350-370): add the total interest to `ACCT-CURR-BAL`, zero both cycle fields, then REWRITE.
7. At EOF, close all files and display `END OF EXECUTION…`. The return code is 0.

**Errors:** every abend displays `ABENDING PROGRAM` and calls `CEE3ABD` (code 999, :628-632). The harness turns this into exit **16**.

**Observed run** (`legacy/harness/run.sh`, re-run today; all outputs `cmp`-identical to `legacy/golden/`):

| Scenario | TRANSACT | ACCTFILE | SYSOUT lines | RC |
|---|---|---|---|---|
| `CBACT04C` (stock data) | 50 recs, all amount `0000000000{` | identical to `acctdata.txt` | 152 | 0 |
| `CBACT04C-after-CBTRN02C` | 50 recs, non-zero, e.g. acct 1: 1164.87 × 15.00 / 1200 = 14.56 (`0000000145F`) | 49 of 50 recs changed | 302 | 0 |

- One transaction is written per account. Type `01`/cat `0001` has `DEFAULT` rate 15.00. Type `03`/cat `0001` has rate 0, so no transaction is written for it.
- The trailing bytes of each transaction record are **spaces**.

## Scope
**In**
- A Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only) that reproduces steps 1-7. It covers the PARM date, the frozen-clock timestamp, `DEFAULT` fallback, truncation, the transaction id counter, SYSOUT messages and abend paths.
- File I/O in the harness's canonical text format (one fixed-width record per line, LF, overpunch signs). KSDS files are read and written in key order.
- An equivalence test against both golden scenarios, plus characterization tests for the defects below.

**Out**
- Fixing any legacy behaviour, including the defects below and the empty `1400-COMPUTE-FEES` (that is CR-2).
- CBTRN02C, the online/CICS programs, JCL/scheduler changes, VSAM/EBCDIC binary I/O, and container build/deploy plumbing.

## Acceptance criteria
1. `mvn -f modernized/cbact04c test` passes on Java 21. The only dependency is JUnit 5.
2. Stock inputs, PARM `2022071800`, clock `2022071800000000`: Java `TRANSACT` and `ACCTFILE` are byte-identical to `legacy/golden/CBACT04C/`.
3. Inputs = stock data with `ACCTFILE`/`TCATBALF` taken from `legacy/golden/CBTRN02C/`, same PARM and clock: outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. The run date (PARM) and the clock are inputs. No real-clock dependency in tests.
5. Characterization tests pin each defect D1-D4 below with golden evidence where available.
6. Interest uses decimal arithmetic truncated to 2 dp (no floating point). Overpunch encode/decode covers `{A-I}J-R` for every field type used.
7. Missing account / xref / `DEFAULT` disclosure record ends the run non-zero with the legacy messages.
8. `legacy/harness/golden.sh --check` still passes (legacy untouched).

## Risks and findings
- **D1. Last account never rewritten (defect, preserve).**
  - Cause: the loop's `ELSE PERFORM 1050-UPDATE-ACCOUNT` (:219-220) is unreachable. After EOF, `PERFORM UNTIL END-OF-FILE = 'Y'` exits before the body runs again.
  - Evidence: `golden/CBACT04C-after-CBTRN02C`. TRANSACT charges acct `00000000050` 18.77 (`0000000187G`). Yet ACCTFILE rec 50 is byte-identical to `golden/CBTRN02C/ACCTFILE.txt`: balance 1945.87, cycle fields not zeroed. Accounts 1-49 are updated (49 of 50 recs differ).
  - Impact: the last account's interest is posted as a transaction but never applied to the balance.
- **D2. Disclosure group never matches (data/design, preserve).**
  - The group code `A000000000` sits in `ACCT-ADDR-ZIP` (103-112), and `ACCT-GROUP-ID` (113-122) is blank.
  - As a result, every lookup falls back to `DEFAULT`: 100 `DISCLOSURE GROUP RECORD MISSING` lines in the after-scenario SYSOUT. The account-specific rates are never used.
- **D3. Interest is truncated, not rounded** (no `ROUNDED`, :464). Example: acct 2, 2339.97 × 15 / 1200 = 29.2496 → 29.24. Negative balances truncate toward zero. Neither golden scenario has a negative balance, so Java must pin this case with unit tests.
- **D4. Silent overflow.** `WS-TRANID-SUFFIX PIC 9(6)` (:173) and the `S9(9)V99` interest fields have no `ON SIZE ERROR`. After 999,999 transactions the ids would wrap and repeat. This is unreachable at sample volume; pin it with a unit test only.
- **Cosmetic:** the DISCGRP open-failure message says `ERROR OPENING DALY REJECTS FILE` (:281), a copy-paste slip. Preserve it.
- **Fees:** `1400-COMPUTE-FEES` is a no-op (:518). No fees are charged today.
- **Ordering assumption:** the logic needs `TCATBALF` in key order (KSDS guarantees this). Java must sort or validate the input, because a text file carries no such guarantee. All sample/golden inputs are sorted.
- **Unspecified xref choice:** with multiple cards per account, the card picked by the alt-key read is the first duplicate. The sample has one card per account, so this case is untested.
- **Timestamp precision:** the timestamp is hundredths followed by literal `0000`, not microseconds.

## Questions for the approver
1. **SYSOUT and RC parity.** Should the Java program also reproduce `SYSOUT.txt` byte-for-byte, with RC 0 / 16 on abend?
   *Default: yes. It is cheap and makes ops logs comparable. Acceptance remains gated on TRANSACT and ACCTFILE.*
2. **ACCTFILE update style.** Should the program rewrite the account master in place, or write a new output file?
   *Default: read `ACCTFILE` and write the full updated file in key order to an output path. This is equivalent to the KSDS after REWRITE.*
3. **Clock source.** How should the program get the timestamp clock?
   *Default: an optional 16-digit `YYYYMMDDHHMMSSss` setting (same as `COB_CURRENT_DATE`), falling back to the system clock in UTC.*
