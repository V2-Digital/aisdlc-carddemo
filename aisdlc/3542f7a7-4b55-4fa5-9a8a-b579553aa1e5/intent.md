# Intent: CR-1, rewrite INTCALC (`CBACT04C`) in Java 21

## Problem / outcome

**Problem.** The nightly interest calculation (job `INTCALC`, COBOL program `CBACT04C`) runs only on the
mainframe. That blocks the move of batch workloads off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. For the same inputs
it produces byte-identical interest transactions (`TRANSACT`) and an updated account master (`ACCTFILE`).
This is proven on both golden scenarios. Legacy behaviour, defects included, is kept as it is and pinned by
tests.

## What the legacy code does today

Run under the harness (`legacy/harness/run.sh CBACT04C`): RC 0. TRANSACT 50, ACCTFILE 50 and SYSOUT 152
records, all **byte-identical** to `legacy/golden/CBACT04C/`.

### Files (JCL `app/jcl/INTCALC.jcl:22-42`, PARM `2022071800`)

| DD | Org / access | Mode | LRECL | Key | Copybook |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | INPUT | 50 | acct(11)+type(2)+cat(4), cols 1-17 | `CVTRA01Y` |
| `XREFFILE` | KSDS, random via **alt key** acct id (cols 26-36) | INPUT | 50 | card num, cols 1-16 | `CVACT03Y` |
| `ACCTFILE` | KSDS, random | **I-O** (REWRITE) | 300 | acct id, cols 1-11 | `CVACT01Y` |
| `DISCGRP` | KSDS, random | INPUT | 50 | group(10)+type(2)+cat(4), cols 1-16 | `CVTRA02Y` |
| `TRANSACT` | sequential, new | OUTPUT | 350 | n/a | `CVTRA05Y` |

Key numeric fields are zoned decimal with a trailing overpunch sign:
- `TRAN-CAT-BAL` S9(9)V99, cols 18-28.
- `DIS-INT-RATE` S9(4)V99, cols 17-22.
- `ACCT-CURR-BAL` S9(10)V99, cols 13-24.
- `ACCT-CURR-CYC-CREDIT` and `-DEBIT`, cols 79-90 and 91-102.
- `TRAN-AMT` S9(9)V99, cols 133-143.

### Processing (`app/cbl/CBACT04C.cbl`)

1. Open all five files. Any status other than `00` → abend (`:234-323`).
2. Read `TCATBALF` sequentially in key order, which groups the records by account. DISPLAY every record
   (`:188-193`).
3. **On an account break** (`:194-206`):
   - Write the previous account back (step 6), unless this is the first record.
   - Reset the running total to 0.
   - Read `ACCTFILE` by account id and `XREFFILE` by alternate key account id, which gives the card number.
   - If either record is missing → `ACCOUNT NOT FOUND` + abend.
4. **Rate lookup** (`:210-213`, `:415-460`): key = `ACCT-GROUP-ID` + type + category.
   - On status `23`: display `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE`, then retry
     with group `DEFAULT`.
   - If the `DEFAULT` lookup also misses → abend.
5. **If rate ≠ 0** (`:214-217`):
   - `monthly = bal × rate / 1200`, **truncated** to 2 dp (no `ROUNDED`) (`:464-465`).
   - Add it to the account total.
   - Write a transaction (`:473-515`):
     - `TRAN-ID` = PARM date (10) + 6-digit running suffix.
     - Type `01`, category `0005`, source `System`.
     - Description `Int. for a/c <acct-id>`.
     - Amount = monthly. Merchant id 0, merchant name/city/zip blank.
     - Card = xref card.
     - Orig and proc timestamps = current time in `YYYY-MM-DD-HH.MM.SS.mm0000` format (`:613-626`).
   - Then perform `1400-COMPUTE-FEES`, which is empty (`:518-520`).
   - If the rate is 0, no transaction is written.
6. **Account update** (`:350-370`):
   - Balance += total interest.
   - Cycle credit and debit are set to 0.
   - REWRITE the record.
7. Close all files, DISPLAY `END OF EXECUTION…`, RC 0 (`:224-232`).
8. **Abend path** (`:628-648`): DISPLAY `ABENDING PROGRAM` and `CEE3ABD` U0999. The harness stub returns
   exit status 16.

### Golden evidence

- `CBACT04C` (stock data): 50 TCATBAL rows, all type 01 / category 0001 with balance 0. The `DEFAULT`
  01/0001 rate is 1.50%. Result: 50 transactions of `0000000000{`, and ACCTFILE equals `acctdata.txt`.
- `CBACT04C-after-CBTRN02C`: 100 TCATBAL rows.
  - 50 rows are 01/0001 (rate 1.50) → 50 transactions totalling **1,279.16**.
  - 50 rows are 03/0001 (rate 0.00) → no transactions.
  - I recomputed all 50 amounts: **50 of 50 match truncation**. Half-up rounding would get 28 of the 50 wrong.

## Scope

**In**
- Java 21 Maven project `modernized/cbact04c/`, JUnit 5 only.
- It reads and writes the canonical text form used by the harness and golden files: fixed width, one record
  per line, LF, overpunch signs.
- PARM date and current timestamp are inputs. The timestamp is injectable for determinism.
- The same SYSOUT lines and exit codes: 0 on success, 16 on abend.
- An equivalence test against both golden scenarios, plus characterization tests for the findings below.

**Out**
- Fixing any legacy defect (see below).
- Implementing fees (CR-2).
- `CBTRN02C`, the online CICS programs, JCL or scheduler changes.
- VSAM/EBCDIC binary I/O.
- Container image and deployment wiring. Those belong to the Deploy stage.

## Acceptance criteria

1. `mvn -B verify` in `modernized/cbact04c/` passes on Java 21 with no dependencies besides JUnit 5 and
   Surefire.
2. Inputs `app/data/ASCII/` (stock), PARM `2022071800` and clock `2022-07-18 00:00:00.00`:
   - `TRANSACT` is byte-identical to `legacy/golden/CBACT04C/TRANSACT.txt` (50 records).
   - `ACCTFILE` is byte-identical to `legacy/golden/CBACT04C/ACCTFILE.txt` (50 records).
3. The same, with the ACCTFILE and TCATBALF inputs taken from `legacy/golden/CBTRN02C/`:
   - `TRANSACT` is byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/TRANSACT.txt`.
   - `ACCTFILE` is byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/ACCTFILE.txt`.
4. SYSOUT matches the golden `SYSOUT.txt` and the exit code matches `RC.txt` (0) for both scenarios.
5. Characterization tests pin each finding F1-F4 below.
6. The abend paths return exit code 16 after printing `ABENDING PROGRAM`, with no partial-success RC 0. The
   tests cover a missing account, a missing xref, and a missing `DEFAULT` disclosure row.
7. Input handling matches the harness loader:
   - Short lines are padded to LRECL. `cardxref.txt` is 36 bytes and must be padded to 50.
   - CRLF is stripped.
8. The rewrite uses exact decimal arithmetic (`BigDecimal`/`long` cents), never `double`.
9. `legacy/harness/golden.sh --check` still passes. No files under `legacy/golden/`, `legacy/harness/` or
   `.github/` are changed.

## Risks and findings

**Defects. Preserve them and pin them with tests. Each needs its own decision.**

- **F1: The last account's interest is never written back.**
  - Cause: the `ELSE PERFORM 1050-UPDATE-ACCOUNT` at `:219-220` is unreachable, because `PERFORM UNTIL` tests
    EOF before the body runs.
  - Evidence: in `golden/CBACT04C-after-CBTRN02C`, transaction `2022071800000050` charges account
    `00000000050` interest of 18.77. Yet that account's ACCTFILE record is byte-identical to
    `golden/CBTRN02C/ACCTFILE.txt`: balance 1945.87, cycle fields not zeroed. 49 of 50 accounts are updated.
- **F2: Interest is truncated, not rounded** (`:464`, no `ROUNDED`). Confirmed on 50 of 50 golden amounts;
  half-up would differ on 28.
- **F3: Every account falls back to the `DEFAULT` disclosure group.**
  - `ACCT-GROUP-ID` (cols 113-122) is blank in all sample accounts. The group code `A000000000` sits in
    `ACCT-ADDR-ZIP`. This is a data defect, but the program depends on it.
  - SYSOUT carries two warning lines per TCATBAL row: 100 in the stock run, 200 after posting.
- **F4: `1400-COMPUTE-FEES` is empty** (`:518-520`). No fees are charged (CR-2).

**Smaller findings. Preserve them; noted for the backlog.**
- Overflow is silent. There is no `ON SIZE ERROR`: the totals (S9(9)V99) and the 6-digit `TRAN-ID` suffix
  truncate high-order digits. The suffix wraps after 999,999 transactions.
- `TCATBALF` is opened INPUT, so category balances are not cleared after interest is charged. A re-run
  charges interest again, on top of the balance already raised by the first run's write-back.
- The xref lookup by alternate key returns the first card for the account (no duplicates in the sample).
- `0200-DISCGRP-OPEN` prints the wrong message, `ERROR OPENING DALY REJECTS FILE` (`:281`).
- `TRAN-DESC` tail and the trailing 20-byte FILLER are spaces only because GnuCOBOL's default dialect
  initialises WORKING-STORAGE that way. On z/OS, or with `-std=ibm`, they could be `x'00'`. Golden uses
  spaces.

**Migration risks**
- Overpunch encoding/decoding, especially `-0` (`}`).
- Break logic depends on TCATBAL being in key order. The Java version must read in key order, i.e. sorted
  by the 17-byte key, because KSDS unloads come out that way.
- Timestamp hundredths come from the clock. Tests must freeze it, as the harness does with
  `COB_CURRENT_DATE`.

## Questions for the approver

1. **SYSOUT parity.** The CR asks only for TRANSACT and ACCTFILE. Should the Java SYSOUT and exit code also
   be byte-identical? *Default: yes.* It is cheap, the golden files exist, and ops runbooks may grep these
   messages.
2. **I/O format in the container.** The canonical text form is LF fixed-width with overpunch, the same as
   `app/data/ASCII` and golden. The Java program reads and writes files named by DD, for example
   `--TCATBALF=path`. *Default: yes.* EBCDIC/VSAM conversion stays upstream and is out of scope.
3. **F1-F4.** Should each be raised as a separate change request after this migration? *Default: yes.* F1
   (lost interest write-back on the last account) is the most material and should be prioritised.
