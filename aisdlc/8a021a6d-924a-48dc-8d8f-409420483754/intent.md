# Intent — CR-1: INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation runs only as COBOL on the mainframe (`app/jcl/INTCALC.jcl` → `app/cbl/CBACT04C.cbl`). That ties batch workloads to the platform we are leaving.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. Given the same inputs, it produces byte-identical interest transactions (`TRANSACT`) and an updated account master (`ACCTFILE`). Proof is both golden scenarios. It is a like-for-like port: legacy defects stay in and are pinned by tests.

## What the legacy code does today

### Job and files

`INTCALC.jcl` STEP15: `EXEC PGM=CBACT04C,PARM='2022071800'` (JCL:22). The PARM is the run date and becomes the transaction-ID prefix.

| DD | Org / access | LRECL | Key | Copybook | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential read | 50 | acct(11)+type(2)+cat(4), cols 1-17 | `CVTRA01Y` | Driver: category balance `S9(9)V99` @18 (cbl:28-32, 326) |
| `XREFFILE` | KSDS, random by **alt key** acct id @26 len 11 | 50 | card num(16) | `CVACT03Y` | Account → card number (cbl:34-39, 394-395) |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct id(11) | `CVACT01Y` | Read, then REWRITE (cbl:41-45, 291, 356) |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4) | `CVTRA02Y` | Interest rate `S9(4)V99` @17 (cbl:47-51, 416) |
| `TRANSACT` | Sequential, OUTPUT | 350 | – | `CVTRA05Y` | New interest transactions (cbl:53-56, 309, 500) |

`XREFFIL1` (the AIX path) is not opened by the program. The harness serves the alternate key from `XREFFILE`.

### Processing (cbl:188-222)
1. Read `TCATBALF` in key order, so records arrive grouped by account. For each record: DISPLAY the record (cbl:193) and add 1 to the record count.
2. **When the account changes** (cbl:194-206): if this is not the first account, rewrite the previous one (`1050`). Then reset `WS-TOTAL-INT` to 0, read the account by id (`1100`), and read the xref by account id (`1110`).
3. **Rate lookup** (`1200`, cbl:210-213, 415-460): the key is `ACCT-GROUP-ID` + type + cat. On status `23`, DISPLAY `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and retry with group `DEFAULT`. If the `DEFAULT` record is also missing, abend.
4. **If rate ≠ 0** (cbl:214-217):
   - Monthly interest = `TRAN-CAT-BAL × DIS-INT-RATE / 1200`, **truncated** to 2 dp (no `ROUNDED`) into `S9(9)V99` (cbl:464-465).
   - Add it to the account total (cbl:467).
   - Write one transaction (`1300-B`, cbl:473-515):
     - `TRAN-ID` = PARM-DATE(10) + 6-digit suffix, running 000001… over the whole run.
     - Type `01`, cat `0005`, source `System`.
     - Description `Int. for a/c <acct-id>`.
     - Amount = monthly interest; merchant id 0; merchant name, city and zip are spaces.
     - Card number from the xref.
     - Orig and proc timestamps are `YYYY-MM-DD-HH.MM.SS.hh0000` from `CURRENT-DATE` (cbl:613-624).
   - Then `1400-COMPUTE-FEES`, which is empty (cbl:518-520).
5. **Account update** (`1050`, cbl:350-370): `ACCT-CURR-BAL += total interest`, cycle credit and cycle debit set to 0, REWRITE. This happens even when the total is 0.
6. Close the files and DISPLAY the end message. RC 0.
7. **Errors:** any unexpected file status triggers DISPLAY `FILE STATUS IS: NNNNxxxx`, `ABENDING PROGRAM` and `CEE3ABD` U999. The harness reports this as RC 16 (cbl:628-648). A missing account or xref also abends, after an `ACCOUNT NOT FOUND:` line.

### Observed behaviour (harness run + golden)
- Stock data: 50 TCATBAL rows, all type 01/0001 with balance 0. Result: 50 zero-amount transactions. `ACCTFILE` is byte-identical to `acctdata.txt`. SYSOUT has 152 lines, RC 0. My run of `legacy/harness/run.sh CBACT04C` matches `golden/CBACT04C` byte for byte.
- After `CBTRN02C`: 100 TCATBAL rows (50 × `01/0001`, 50 × `03/0001`). Only `01/0001` has a non-zero DEFAULT rate (15.00%), so 50 transactions are written. Example: acct 1, 1164.87 × 15 / 1200 = 14.560875 → `0000000145F` (14.56). Balance goes from 1288.10 to 1302.66. SYSOUT has 302 lines, RC 0.
- Truncation is visible: acct 2, 2339.97 × 15 / 1200 = 29.2496 → **29.24**. Rounding would give 29.25.
- All 50 sample accounts have `ACCT-GROUP-ID` blank. `A000000000` sits in `ACCT-ADDR-ZIP` (cols 103-112). Every lookup therefore goes through the DEFAULT fallback, and the `A000000000` rows in `discgrp.txt` are never used.

## Scope

**In**
- A Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only) that reproduces the steps above.
- It reads and writes the canonical fixed-width text form used by the harness and `app/data/ASCII` (one record per line, LF, overpunch signs).
- An injectable run date (PARM) and clock.
- Equivalence tests against both golden scenarios.
- Characterization tests for the defects listed below.

**Out**
- Fixing any legacy defect.
- Implementing fees (`1400`; that is CR-2).
- `CBTRN02C`.
- VSAM/EBCDIC binary I/O.
- Scheduler, JCL and container deployment changes.
- Changing `legacy/`.

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbact04c/` passes on Java 21 with only JUnit 5 and Surefire as dependencies.
2. Scenario `CBACT04C`: stock inputs, PARM `2022071800`, clock `2022-07-18 00:00:00.00`. `TRANSACT` (50 records × 350) and `ACCTFILE` (50 × 300) are byte-identical to `legacy/golden/CBACT04C/`.
3. Scenario `CBACT04C-after-CBTRN02C`: `ACCTFILE` and `TCATBALF` from `legacy/golden/CBTRN02C/`, other inputs stock. Both outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. Exit code 0 on success. Non-zero (16, as in the harness) where COBOL abends: missing account, missing xref, missing DEFAULT rate row.
5. Interest is truncated toward zero to 2 dp, computed in exact decimal (no floating point). A test pins 29.24 for acct 2.
6. Characterization test: the last account in `TCATBALF` is **not** rewritten. Acct `00000000050` stays byte-identical to its input while its 18.77 transaction is still written.
7. Characterization test: a zero rate writes no transaction, but the account is still rewritten with cycle credit and debit zeroed.
8. Transaction ID = PARM date + 6-digit sequence across the whole run. Bytes outside the populated fields are spaces, as in the default-dialect golden.
9. SYSOUT lines match `golden/*/SYSOUT.txt` (see Q1).

## Risks and findings

| # | Finding | Evidence | Handling |
|---|---|---|---|
| D1 | **Last account never written back.** After EOF the `ELSE PERFORM 1050` branch (cbl:219-220) is unreachable, because `PERFORM UNTIL` tests before each pass. The last account's interest is lost, and its cycle fields are not reset. | `golden/CBACT04C-after-CBTRN02C`: TRAN `2022071800000050` charges acct 50 18.77 (`0000000187G`). In ACCTFILE, acct 50 is still identical to `golden/CBTRN02C` (balance `00000019458G`). Only 49 of 50 accounts change. | Preserve and pin (AC6). Flag for a fix CR. |
| D2 | **Group id read from the wrong place in the data.** `ACCT-GROUP-ID` is blank. The group code was loaded into `ACCT-ADDR-ZIP`, so group-specific rates are never applied. | cols 103-112 = `A000000000` and cols 113-122 = blank in all 50 rows of `acctdata.txt`; SYSOUT shows 100 × `DISCLOSURE GROUP RECORD MISSING` | Data defect, not code. Preserve. Flag. |
| D3 | Fees are never computed: `1400-COMPUTE-FEES` is empty. | cbl:518-520 | Out of scope (CR-2). |
| D4 | Silent overflow: there is no `ON SIZE ERROR` on the COMPUTE or ADD, so high-order digits are dropped. The 6-digit TRAN-ID suffix also wraps after 999,999. | cbl:464-467, 474 | Mirror the COBOL truncation. Not reachable with the golden data. |
| D5 | The cycle credit/debit reset assumes this job runs once per cycle. Running it twice adds interest twice. | cbl:352-354 | Operational behaviour. Unchanged. |
| R1 | **Byte-exactness depends on the harness conventions:** overpunch signs (`-fsign=EBCDIC`), uninitialised WORKING-STORAGE = spaces (the tail of `TRAN-DESC` and FILLER 331-350), and timestamp hundredths followed by `0000`. | `legacy/README.md` "Known quirks" | Encode these explicitly in the Java code and test them. |
| R2 | The rate lookup uses the `ACCT-GROUP-ID` of the account last read, and xref uses the first card for the account. | cbl:210, 394 | Replicate. No duplicate account ids in `cardxref.txt` (checked). |
| R3 | The account is read before the previous one is rewritten only in the sense that the rewrite uses the buffered `ACCOUNT-RECORD`. Ordering matters for D1. | cbl:196, 203 | Keep the same control flow. |

## Questions for the approver

1. **SYSOUT parity.** The CR names only TRANSACT and ACCTFILE. Should the Java program also reproduce the DISPLAY log line for line? **Default: yes.** It is cheap, it is checked against golden, and it keeps ops runbooks valid.
2. **File format.** The Java program will work on the canonical fixed-width text files, not VSAM. Loading and unloading to the target datastore is a deploy concern. **Default: accept.**
3. **D1 and D2.** Should each be raised as a separate CR after this migration? **Default: yes. D1 is a revenue-impacting bug.** On real data, every night the last account in key order gets no interest posted.
