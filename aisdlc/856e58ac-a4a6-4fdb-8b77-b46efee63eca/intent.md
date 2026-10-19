# CR-1 Intent — INTCALC (`CBACT04C`) to Java 21

## Problem
The nightly interest calculation runs only as COBOL on the mainframe (`app/jcl/INTCALC.jcl` → `app/cbl/CBACT04C.cbl`). That ties a core card-servicing batch to mainframe capacity and skills. It also blocks the move of batch workloads into containers.

## Outcome
A plain Java 21 program (`modernized/cbact04c/`, Maven, JUnit 5 only) that can run in a container. For the same inputs it produces **byte-identical** `TRANSACT` (interest transactions) and `ACCTFILE` (updated account master). This is proven on golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Legacy behaviour, including its defects, is preserved and pinned by tests.

## What the legacy code does today

**Run:** `EXEC PGM=CBACT04C,PARM='2022071800'` (`INTCALC.jcl:22`). The PARM is a 10-char date used as the transaction-ID prefix (`CBACT04C.cbl:176-178`).

### Files
| DD | Org / access | LRECL | Key | Copybook | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential, INPUT | 50 | acct(11)+type(2)+cat(4), cols 1-17 | `CVTRA01Y` | driver: category balances, key order (`:28-32`, `:236`) |
| `XREFFILE` | KSDS, random, INPUT | 50 | card(16); **AIX** acct id cols 26-36 | `CVACT03Y` | card number for account (`:34-39`, `:394-395`) |
| `DISCGRP` | KSDS, random, INPUT | 50 | group(10)+type(2)+cat(4) | `CVTRA02Y` | interest rate `DIS-INT-RATE` S9(4)V99 (`:47-51`) |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct id(11) | `CVACT01Y` | read, then REWRITE (`:41-45`, `:291`) |
| `TRANSACT` | sequential, OUTPUT | 350 | — | `CVTRA05Y` | new interest transactions (`:53-56`, `:309`) |

`XREFFIL1` (AIX path) is in the JCL but the harness serves the alternate key from `XREFFILE` itself (`legacy/README.md`).

### Processing (main loop `:188-222`)
1. Read `TCATBALF` sequentially. For each record: count it, and `DISPLAY` the 50-byte record (`:192-193`).
2. **On account change** (`:194-206`): if this is not the first account, `1050-UPDATE-ACCOUNT` for the previous one. Then reset total interest to 0, read `ACCTFILE` by acct id (`:372-391`), and read `XREFFILE` by alternate key acct id (`:393-413`).
3. **Rate lookup** (`1200`, `:415-440`): key = `ACCT-GROUP-ID` + tran type + tran cat. Status `23` (not found) displays `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE`, then retries with group `DEFAULT` (`:443-460`). If `DEFAULT` is also missing, the program abends.
4. **If rate ≠ 0** (`:214-217`):
   - `WS-MONTHLY-INT = (TRAN-CAT-BAL × DIS-INT-RATE) / 1200`, into S9(9)V99. There is **no ROUNDED**, so the result is truncated (`:464-465`).
   - Add it to `WS-TOTAL-INT` (`:467`).
   - Write one transaction (`1300-B-WRITE-TX`, `:473-515`):
     - `TRAN-ID` = PARM-DATE(10) + 6-digit running suffix, e.g. `2022071800000001`
     - type `01`, cat `0005`, source `System`
     - desc `Int. for a/c <acct id 11>`, space-padded
     - amount = monthly interest; merchant id 0; merchant name/city/zip spaces
     - card = xref card number
     - orig/proc timestamp = current clock as `YYYY-MM-DD-HH.MM.SS.hh0000` (`:613-626`)
     - 20-byte filler = spaces (never initialised)
   - `1400-COMPUTE-FEES` runs, and it is empty (`:518-520`).
5. **`1050-UPDATE-ACCOUNT`** (`:350-370`): `ACCT-CURR-BAL += WS-TOTAL-INT`, cycle credit = 0, cycle debit = 0, then REWRITE. This also happens when the rate was 0, so cycle fields are zeroed even when no interest is charged.
6. **Errors**: any unexpected file status → `ERROR …` message + `FILE STATUS IS: NNNNxxxx` → `ABENDING PROGRAM` → `CEE3ABD` U0999 (harness exit **16**) (`:628-648`). This includes account or xref not found (`:374-375`, `:396-397`).
7. Close all files and display `END OF EXECUTION OF PROGRAM CBACT04C`. RC 0.

### Observed behaviour (harness run, this session)
- `legacy/harness/run.sh CBACT04C`: RC 0. `TRANSACT` (50 recs) and `ACCTFILE` (50) are identical to `legacy/golden/CBACT04C/`. All stock balances are 0, so all 50 transactions are zero-amount and ACCTFILE equals `acctdata.txt`.
- `golden/CBACT04C-after-CBTRN02C`: input `TCATBALF` has 100 records (50 accts × type/cat `01/0001` and `03/0001`). The `03` rate is 0, so only 50 transactions are written. All lookups fall back to `DEFAULT`, because `ACCT-GROUP-ID` is blank in the sample data.
- Truncation is confirmed. Account 2: 2339.97 × 15.00 / 1200 = 29.2496 → `0000000292D` (29.24, not 29.25).

## Scope
**In**
- Java 21 rewrite of `CBACT04C` in `modernized/cbact04c/` (plain Maven, JUnit 5 + Surefire).
- The program reads and writes the harness's canonical text format: fixed-width, LF, overpunch zoned decimals, one file per DD name, KSDS in key order.
- PARM date as an argument; clock injectable (default system clock, UTC).
- Every legacy rule above, including defects and the empty fee step.
- Golden-equivalence tests for both scenarios, plus characterization tests for the defects and edge rules.

**Out**
- Fixing any legacy defect (separate decision; fees are CR-2).
- Real VSAM/EBCDIC binary I/O, JCL/scheduler changes, container image and deploy plumbing.
- `CBTRN02C` and other programs.

## Acceptance criteria
1. `mvn -q verify` in `modernized/cbact04c/` passes on Java 21, using no dependencies beyond JUnit 5 and Surefire.
2. On stock inputs, with PARM `2022071800` and clock `2022-07-18 00:00:00.00` UTC, the output `TRANSACT.txt` and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C/`.
3. With `ACCTFILE`/`TCATBALF` from `legacy/golden/CBTRN02C/` and the other inputs from stock, the outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. Interest is truncated toward zero to 2 decimals. A test pins account 2 → 29.24.
5. Characterization test: the **last account in `TCATBALF` is not written back**. Account `00000000050` gets transaction `2022071800000050` (18.77) in `TRANSACT`, but its `ACCTFILE` record is unchanged (balance 1945.87, cycle 1501.75 / -47.88).
6. A zero rate writes no transaction, but the account is still rewritten with its cycle credit and debit zeroed.
7. A missing group falls back to `DEFAULT`. If `DEFAULT` is missing, or an account or xref is not found, the program exits with code 16 and writes no partial success message.
8. `TRAN-ID` = PARM date + 6-digit sequence across the run. Description, filler and timestamps match the layout in the "What the legacy code does" section.
9. Console output and exit code match `SYSOUT.txt`/`RC.txt` for both scenarios (see Q1).

## Risks and findings
- **D1 Last-account write-back (defect, confirmed).** The `ELSE PERFORM 1050-UPDATE-ACCOUNT` (`:219-220`) is unreachable: after EOF, the `PERFORM UNTIL` test exits before the body runs again. Evidence: in `golden/CBACT04C-after-CBTRN02C/ACCTFILE.txt`, 49 of 50 records differ from `golden/CBTRN02C/ACCTFILE.txt`. Account 50 is unchanged although its 18.77 interest transaction was written. Business impact: the last account each night is never charged, and its cycle totals are never reset. Preserve it.
- **D2 Fees never computed.** `1400-COMPUTE-FEES` is empty (`:518-520`). Preserve it (CR-2).
- **D3 Group id in the wrong field (data).** The sample data carries `A000000000` in `ACCT-ADDR-ZIP` and leaves `ACCT-GROUP-ID` blank, so every lookup misses and uses `DEFAULT`. The golden data does not exercise the non-default path. Unit tests must cover it.
- **D4 Wrong error text.** The DISCGRP open failure says `ERROR OPENING DALY REJECTS FILE` (`:281`). Preserve it.
- **D5 Silent overflow.** Arithmetic and the 6-digit tran-ID suffix have no `ON SIZE ERROR`, so high-order digits are truncated (suffix wraps after 999999; `ACCT-CURR-BAL` is S9(10)V99). This is not reachable with the golden data. Mirror COBOL truncation and pin it with unit tests.
- **R1 Timestamps come from the clock, not the PARM.** The outputs only reproduce with a frozen clock (the harness uses `COB_CURRENT_DATE=2022071800000000`). The Java version needs an injectable clock. Hundredths of a second go in position 21-22, followed by literal `0000`.
- **R2 Uninitialised storage.** The `TRAN-DESC` tail and the filler are spaces under the default dialect (`x'00'` under `-std=ibm`). The golden files use spaces.
- **R3 Input text quirks.** `cardxref.txt` lines are 36 bytes (they must be padded to 50), and `tcatbal.txt`/`trancatg.txt`/`trantype.txt` use CRLF. The Java reader must normalise these the same way the harness loader does.
- **R4 Negative balances.** Interest is computed on signed balances, so a credit balance with a non-zero rate would produce negative interest. In the golden data, all type-`03` balances are negative, but their rate is 0.

## Questions for the approver
1. **Should SYSOUT and the exit code be byte-identical too?** The CR names only TRANSACT and ACCTFILE. *Default: yes.* It is cheap, and operations runbooks and monitoring key on these messages and on RC 16.
2. **What should the I/O format be?** *Default: the canonical text files used by `legacy/golden`* (one file per DD in a directory, ACCTFILE rewritten in key order). Real VSAM or database storage would be a later CR.
3. **Should defect D1 be raised as its own CR now?** The last account per run is never charged interest, which is lost revenue. *Default: yes, raise it as a separate CR after this migration is accepted.*
