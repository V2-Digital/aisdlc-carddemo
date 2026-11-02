# CR-1 Intent: INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation (job `INTCALC`, COBOL program `CBACT04C`) runs only on the mainframe. That blocks our move of batch work off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. Given the same inputs, it writes the same interest transactions (`TRANSACT`) and the same updated account master (`ACCTFILE`), byte for byte. It also keeps today's behaviour exactly, defects included, and pins each defect with a test so that fixing it can be decided separately.

## What the legacy code does today

Source: `app/cbl/CBACT04C.cbl` (CBL), run by `app/jcl/INTCALC.jcl` step `STEP15`, `PARM='2022071800'`.

### Files

| DD | Copybook | LRECL | Org / access | Key | Use |
|---|---|---|---|---|---|
| `TCATBALF` | `CVTRA01Y` | 50 | KSDS, sequential | acct-id 9(11) + type X(2) + cat 9(4), cols 1-17 | input, the driver (CBL:26-30, 236) |
| `XREFFILE` | `CVACT03Y` | 50 | KSDS, random | card 1-16; **AIX acct-id 26-36** | input, read by AIX (CBL:32-38, 394-395) |
| `ACCTFILE` | `CVACT01Y` | 300 | KSDS, random | acct-id 1-11 | **I-O**, read + REWRITE (CBL:291, 356, 373) |
| `DISCGRP` | `CVTRA02Y` | 50 | KSDS, random | group X(10) + type X(2) + cat 9(4), cols 1-16 | input (CBL:416, 444) |
| `TRANSACT` | `CVTRA05Y` | 350 | sequential, F | none | output, new (CBL:309, 500) |

Every numeric field is zoned DISPLAY with a trailing overpunch sign. There is no COMP or COMP-3 in any record. Key amount fields: `TRAN-CAT-BAL` S9(9)V99 at cols 18-28, `DIS-INT-RATE` S9(4)V99 at 17-22, `ACCT-CURR-BAL` S9(10)V99 at 13-24, `ACCT-CURR-CYC-CREDIT` at 79-90, `ACCT-CURR-CYC-DEBIT` at 91-102, `ACCT-GROUP-ID` X(10) at 113-122, `TRAN-AMT` S9(9)V99 at 133-143.

### Processing steps

1. The program opens all five files. If any open does not return status `00`, it abends (CBL:236-325).
2. It reads `TCATBALF` sequentially in key order and DISPLAYs each record to SYSOUT (CBL:188-193).
3. **When the account changes** (control break on acct-id, CBL:194):
   - If this is not the first account, it rewrites the previous account (step 5).
   - It sets total interest to 0.
   - It reads `ACCTFILE` by acct-id, then `XREFFILE` by the acct-id alternate key to get the card number (CBL:373, 394).
4. **For each category balance:**
   - It looks up `DISCGRP` with key (`ACCT-GROUP-ID`, type, cat) (CBL:210-213).
   - On status `23` it prints `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE`, then retries with group `DEFAULT` (CBL:436-437). If the `DEFAULT` record is also missing, it abends.
   - If the rate is not 0 (CBL:214):
     - Monthly interest = `TRAN-CAT-BAL * DIS-INT-RATE / 1200`, stored in S9(9)V99. **The result is truncated, not rounded** (CBL:464-465, no `ROUNDED`).
     - It adds the interest to the account total and writes one transaction (CBL:474-500):
       - `TRAN-ID` = PARM date (10) + 6-digit counter (counts across the whole run).
       - Type `01`, cat `0005`, source `System`.
       - Desc = `Int. for a/c ` + acct-id. The rest of the field stays spaces.
       - Amount = the monthly interest. Merchant id = 0, merchant name/city/zip = spaces.
       - Card number from the xref.
       - Orig and proc timestamps = `CURRENT-DATE` as `YYYY-MM-DD-HH.MM.SS.hh0000`.
       - FILLER cols 331-350 = spaces, because the record is never initialised.
   - It then PERFORMs `1400-COMPUTE-FEES`, which is empty (CBL:518).
5. **Account update** (CBL:350-356): `ACCT-CURR-BAL += total`. Cycle credit and cycle debit are set to 0. Then `REWRITE`.
6. At EOF it closes the files, prints `END OF EXECUTION...`, and finishes with RC 0.
7. Any I/O error prints `FILE STATUS IS: NNNNxxxx` and calls `CEE3ABD` (abend U0999). The harness maps this to RC 16 (CBL:632).

### Observed (harness, frozen clock 2022-07-18)

- `run.sh CBACT04C` reproduces `legacy/golden/CBACT04C` exactly: TRANSACT 50, ACCTFILE 50, SYSOUT 152 lines, RC 0.
- Stock data: 50 TCATBAL records, all balances 0. That gives 50 transactions with amount `0000000000{`, and ACCTFILE is unchanged.
- `CBACT04C-after-CBTRN02C`:
  - Input is 100 TCATBAL records: 50 accounts × (`01/0001` positive, `03/0001` negative).
  - Every `ACCT-GROUP-ID` is blank, so all 100 lookups fall back to `DEFAULT`. The rate for `01/0001` is 15.00%; for `03/0001` it is 0.00%, so no transaction is written.
  - Output: 50 transactions with non-zero amounts. One example is account 1: 1288.10 → 1302.66 (+14.56).

## Scope

**In scope**
- Java 21 rewrite of `CBACT04C` in `modernized/cbact04c/` (Maven, JUnit 5 only).
- It reads and writes the harness's canonical text datasets (fixed-width, LF, overpunch).
- It writes `TRANSACT` and the updated `ACCTFILE`, plus SYSOUT lines and the exit code.
- An equivalence test against both golden scenarios.
- Characterization tests for each defect below.

**Out of scope**
- Fixing any legacy defect. The fee logic (`1400-COMPUTE-FEES`, CR-2).
- `CBTRN02C`. Real VSAM/EBCDIC binary I/O. Scheduler (CA7/Control-M) changes. Container image and deploy plumbing.
- Changes to `legacy/`, `app/`, or `.github/`.

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbact04c/` passes on Java 21 with no dependencies other than JUnit 5.
2. On the `CBACT04C` scenario inputs with PARM `2022071800` and the clock frozen at `2022-07-18 00:00:00.00`, `TRANSACT.txt` and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C/`.
3. The same holds for `CBACT04C-after-CBTRN02C`. Its inputs are `TCATBALF` and `ACCTFILE` from `golden/CBTRN02C`, and the rest is stock data.
4. SYSOUT lines and the exit code match `SYSOUT.txt` / `RC.txt` for both scenarios (see Q1).
5. Interest is truncated toward zero to 2 decimals. A unit test shows this, including a negative balance with a non-zero rate. That case does not occur in the golden data.
6. A characterization test shows that the last account in `TCATBALF` gets its interest transaction but is **not** rewritten. Its balance and cycle fields come out unchanged.
7. If a disclosure group is missing, the program falls back to `DEFAULT`. If an account, xref or `DEFAULT` record is missing, it stops with a non-zero exit code (16, like the CEE3ABD stub) and writes no further records.
8. The run date (PARM) and the "current timestamp" are inputs, not wall-clock reads, so tests are deterministic. In production the timestamp defaults to the real clock.

## Risks and findings

| # | Finding | Evidence | Treatment |
|---|---|---|---|
| D1 | **The last account is never rewritten.** The loop's `ELSE PERFORM 1050-UPDATE-ACCOUNT` (CBL:220) cannot run, because `PERFORM UNTIL` (CBL:188) tests EOF before the body. Its interest is posted to TRANSACT but never added to the balance, and its cycle credit/debit are not reset. | `golden/CBACT04C-after-CBTRN02C`: tran `2022071800000050` charges acct 50 18.77 (`0000000187G`). Acct 50 in ACCTFILE is byte-identical to `golden/CBTRN02C/ACCTFILE.txt`. Only 49 of 50 accounts change. | Preserve + pin (AC6). Flag. |
| D2 | **Interest is truncated, not rounded** (no `ROUNDED`, CBL:464). | Recomputed all 50 golden amounts: every one equals the truncated value. Rounding half-up would change 28 of the 50. | Preserve (AC5). Flag for finance. |
| D3 | **Every account is priced at the `DEFAULT` rate.** `ACCT-GROUP-ID` (cols 113-122) is blank in the sample data, and the group code `A000000000` sits in `ACCT-ADDR-ZIP`. | SYSOUT: 100× `DISCLOSURE GROUP RECORD MISSING` in after-CBTRN02C. | Data issue, not code. Preserve the lookup as written. |
| D4 | `1400-COMPUTE-FEES` is empty, so no fees are charged. | CBL:518 | Out of scope (CR-2). |
| F1 | A zero-amount transaction is written whenever the rate ≠ 0, even if the balance is 0. | Stock scenario: 50 transactions, all `0000000000{`. | Preserve. |
| F2 | `WS-TRANID-SUFFIX` is 9(6). Past 999,999 interest transactions in one run it wraps to 000000, which gives duplicate `TRAN-ID`s. Interest larger than S9(9)V99 loses its high-order digits silently. | CBL:173, 168 | Preserve COBOL truncation semantics (mod 10^n). Not reachable with current volumes. |
| F3 | The timestamp has only hundredths of a second (`hh0000`). Under an unfrozen clock, runs differ in cols 279-330. | CBL:614; `legacy/README.md` | Make the clock injectable (AC8). |
| R1 | **Golden coverage is thin.** It has one rate (15.00%), one category per account with interest, no negative interest, and no real disclosure group hits. The equivalence tests alone will not prove these paths. | Analysis above | Add unit/characterization tests for these paths. |
| R2 | The output text format (overpunch, trailing spaces, LF) is the harness's canonical form, not mainframe EBCDIC. | `legacy/README.md` | It is fine for this CR. Real file transfer and conversion belong to Deploy. |

## Questions for the approver

1. **Should SYSOUT and the exit code be byte-identical too?** The CR names only TRANSACT and ACCTFILE. *Default: yes, match them as well. It is cheap and makes ops logs comparable.*
2. **D1 and D2 are money defects.** The last account each night misses its interest, and interest is truncated. Should they become a follow-up CR? *Default: yes, preserve both here and raise a separate CR for D1 (and an analysis of D2 with Finance).*
