# CR-1 Intent — Modernise INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation (job `INTCALC`, program `CBACT04C`) runs only as COBOL on the
mainframe. That blocks our move of batch workloads off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. Given the same inputs, it
produces byte-identical interest transactions (`TRANSACT`) and an updated account master (`ACCTFILE`). Proof is
an automated comparison against `legacy/golden/`. Legacy behaviour, defects included, is kept unchanged.

## What the legacy code does today

Evidence: I ran `legacy/harness/run.sh CBACT04C` on stock data, then again with
`INPUT_DIR=legacy/golden/CBTRN02C`. All 8 output files (TRANSACT, ACCTFILE, SYSOUT, RC × 2 scenarios) are
byte-identical to `legacy/golden/`.

### Job and files

`app/jcl/INTCALC.jcl:22` runs `EXEC PGM=CBACT04C,PARM='2022071800'`. The PARM is the run date, used only as a
transaction-ID prefix.

| DD | Copybook | Org / access | LRECL | Key | Use |
|---|---|---|---|---|---|
| `TCATBALF` | `CVTRA01Y` | KSDS, sequential, INPUT (`CBACT04C.cbl:28`) | 50 | acct 9(11) + type X(2) + cat 9(4) (cols 1-17) | Category balances: driver file |
| `XREFFILE` | `CVACT03Y` | KSDS, random, INPUT, read by **alt key** acct id (`:34`, `:394`) | 50 | card 16; AIX acct id at offset 25, length 11 | Card number for the transaction |
| `ACCTFILE` | `CVACT01Y` | KSDS, random, **I-O** (`:41`, `:291`) | 300 | acct id 9(11) | Read, then REWRITE balance |
| `DISCGRP` | `CVTRA02Y` | KSDS, random, INPUT (`:47`) | 50 | group X(10) + type X(2) + cat 9(4) | Interest rate `DIS-INT-RATE S9(4)V99` |
| `TRANSACT` | `CVTRA05Y` | Sequential, OUTPUT, new GDG (`:53`, JCL `:39-43`) | 350 | — | Generated interest transactions |

Byte columns that matter (1-based):
- TCATBAL: balance `S9(9)V99` at 18-28.
- ACCT: `CURR-BAL` 13-24, `CYC-CREDIT` 83-94, `CYC-DEBIT` 95-106, `GROUP-ID` 117-126.
- TRAN: `AMT` 133-143, card 263-278, `ORIG-TS` 279-304, `PROC-TS` 305-330.

All signed numbers are zoned decimal with an overpunched sign.

### Processing steps

1. Open all five files and abend if any status is not `00` (`:234-323`).
2. Read TCATBAL sequentially in key order (`:325`). Print each record to SYSOUT (`:193`) and count it (`:192`).
3. **Account break:** this runs when `TRANCAT-ACCT-ID ≠ WS-LAST-ACCT-NUM` (`:194`).
   - Unless this is the first record, rewrite the previous account (`:195-196`).
   - Reset the total interest (`:200`).
   - Read ACCTFILE by acct id (`:372`).
   - Read XREF by alt key acct id (`:393`).
   - If either record is missing, print `ACCOUNT NOT FOUND`, then abend (`:374-389`, `:396-411`).
4. **Rate lookup** uses the key (`ACCT-GROUP-ID`, type, cat) (`:210-213`, `:415`).
   - Status `23` prints `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE`, then retries with the
     group `DEFAULT` (`:436-438`, `:443`). If the retry fails, the program abends.
5. **If the rate ≠ 0** (`:214`):
   - Interest is `WS-MONTHLY-INT = (TRAN-CAT-BAL × DIS-INT-RATE) / 1200` (`:464`). The result is
     **truncated** to 2 dp, not rounded, because there is no `ROUNDED`. Golden data confirms this in 28 cases
     where truncating and rounding give different answers.
   - Add the interest to the account total (`:467`) and write one transaction (`:473-515`).
   - Then perform `1400-COMPUTE-FEES`, which is empty (`:518`).
   - **If the rate = 0:** write no transaction.
6. **Transaction record** (`:476-498`):
   - `TRAN-ID` = PARM date (10) + running 6-digit suffix starting at `000001`.
   - Type `01`, cat `0005`, source `System`.
   - Description `Int. for a/c <ACCT-ID>`, padded with spaces.
   - Amount = monthly interest. Merchant id 0; merchant name, city and zip blank.
   - Card number from XREF.
   - Both timestamps = `CURRENT-DATE` in `YYYY-MM-DD-HH.MM.SS.hh0000` format.
   - The trailing 20-byte FILLER is spaces, because it was never initialised.
7. **Account update** (`1050`, `:350-370`): `CURR-BAL += total interest`, `CYC-CREDIT = 0`, `CYC-DEBIT = 0`,
   then REWRITE.
8. At EOF, close all files (`:224-228`), print the end message and return RC 0. Any I/O error calls `CEE3ABD`
   (U0999). The harness stub turns that into RC 16 (`:628-632`).

### Observed on golden data

| Scenario | TCATBAL in | TRANSACT out | ACCTFILE records changed | SYSOUT lines |
|---|---|---|---|---|
| `CBACT04C` (stock) | 50 (all balance 0, type 01/cat 0001) | 50, all amount 0 | 0 (identical to `acctdata.txt`) | 152 |
| `CBACT04C-after-CBTRN02C` | 100 (50 × `01/0001` at 15.00%, 50 × `03/0001` at 0%) | 50 (`…000001`-`…000050`) | **49 of 50** | 302 |

## Scope

**In scope**
- A Java 21 Maven project in `modernized/cbact04c/`, built with JUnit 5 only.
- It reads and writes the harness's canonical text format: fixed-width records, LF line endings, overpunch signs.
- Each dataset is bound by its DD name. PARM date and current timestamp are passed in as inputs.
- Same processing order and arithmetic as the COBOL (decimal, truncating), the same abend conditions and the same
  RC.
- Equivalence tests against both CBACT04C golden scenarios.
- Characterization tests that pin the legacy defects listed below.

**Out of scope**
- Fixing any legacy defect, including the last-account bug and the empty fees paragraph (fees are CR-2).
- `CBTRN02C`/POSTTRAN, CICS screens, other jobs, and the JCL/scheduler cut-over.
- Real VSAM/EBCDIC binary I/O, GDG cataloguing, and container image publishing (Deploy stage).

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbact04c/` passes on Java 21. The only dependencies are JUnit 5 and Surefire.
2. Scenario `CBACT04C`: with the stock inputs, PARM `2022071800` and clock `2022-07-18 00:00:00.00`, the Java
   `TRANSACT.txt` (50 records) and `ACCTFILE.txt` (50 records) are byte-identical to `legacy/golden/CBACT04C/`.
3. Scenario `CBACT04C-after-CBTRN02C`: with ACCTFILE and TCATBALF from `legacy/golden/CBTRN02C/` and the other
   inputs from stock, outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. The exit code matches `RC.txt` (0) for both scenarios.
5. Interest is truncated to 2 dp, not rounded. A test pins at least one case where truncating and rounding
   differ, taken from the golden data.
6. The disclosure-group fallback to `DEFAULT` works. A rate of 0 writes no transaction but still updates the
   account.
7. **Defect preserved:** the last account in TCATBAL key order is not rewritten. In scenario 3, account
   `00000000050` stays byte-identical to its input, even though transaction `2022071800000050` charges it 18.77.
   A characterization test pins this.
8. A missing account, missing XREF, or missing `DEFAULT` rate row ends with exit code 16 and no further
   processing, matching the COBOL abend.
9. Stock input files with CRLF line endings (`tcatbal.txt` etc.) are accepted, the same way the harness loads
   them. Output is always LF.

## Risks and findings

- **D1, last account never updated (defect, preserve).**
  - Cause: the loop `PERFORM UNTIL END-OF-FILE = 'Y'` (`:188`) re-tests its condition right after EOF is set
    inside the body (`:190`, `:340`), so the `ELSE PERFORM 1050-UPDATE-ACCOUNT` (`:219-220`) can never run.
  - Evidence: in `golden/CBACT04C-after-CBTRN02C`, account 50 still shows a balance of 1945.87
    (`00000019458G`) and cycle credit/debit of 1501.75 / 47.88. Its interest of 18.77 (`0000000187G`) was posted
    to TRANSACT but never added to the balance. The other 49 accounts were updated.
  - Business impact: transactions and balances drift apart by one account's interest every night.
- **D2, interest truncated, not rounded** (`:464`, no `ROUNDED`). This systematically under-charges by up to
  0.0099 per category. Preserve it. Product owners may want a decision.
- **D3, `1400-COMPUTE-FEES` is empty** (`:518-520`). No fees are ever charged. Preserve it; this is CR-2.
- **F1, group id is in the wrong field in the sample data.** `ACCT-GROUP-ID` (cols 117-126) is blank for all 50
  accounts, so every lookup falls back to `DEFAULT`. The Java code must still do the real lookup first, so that
  non-default groups work on real data.
- **F2, uninitialised `TRAN-RECORD`.** The FILLER (331-350) and the tail of the description are spaces under the
  default dialect, or `x'00'` under `-std=ibm`. The golden files use spaces, and spaces are the target.
- **F3, timestamp.** The COBOL takes the hundredths from `CURRENT-DATE` and pads with `0000`. The Java code needs
  an injectable clock so tests can be deterministic.
- **F4, edge cases not covered by golden data.**
  - Negative balances (no case in the goldens).
  - A suffix overflow past 999,999 transactions, which wraps silently: `PIC 9(6)`, no `ON SIZE ERROR`.
  - Accounts with several cards: the XREF alt key returns the first duplicate.
  
  These will be covered by unit tests that follow COBOL semantics, not by golden comparison.
- **F5, cosmetic.** A DISCGRP open failure prints `ERROR OPENING DALY REJECTS FILE` (`:281`). Preserve it.
- **R1, input order.** TCATBAL is a KSDS read in key order. If the Java program reads an unsorted text file, the
  results change. Mitigation: sort by the 17-byte key, which is what the KSDS load does.

## Questions for the approver

1. **Must SYSOUT also be byte-identical?** The CR names only TRANSACT and ACCTFILE.
   *Default: yes.* Also match `SYSOUT.txt` (152 / 302 lines) and RC, because it is cheap and makes operations
   parity easy to show.
2. **I/O contract for the container.**
   *Default:* the program is configured by DD name (one file path per DD), with the PARM date as an argument.
   It reads the canonical text format and writes the updated ACCTFILE as a full new file in key order, rather
   than updating in place. Running against real VSAM/EBCDIC is a later decision.
3. **D1 and D2 fixes.** *Default:* leave them out of CR-1 and raise separate CRs after the migration is accepted.
