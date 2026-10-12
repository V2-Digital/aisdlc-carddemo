# CR-3 Intent: POSTTRAN / CBTRN02C in Java 21

Run `5f79bb0e-9a5e-496a-81da-a281c9ce534a` · Stage: Plan · Source: `app/cbl/CBTRN02C.cbl` (731 lines), `app/jcl/POSTTRAN.jcl`

## Problem / outcome

**Problem.** Daily card transaction posting runs as a COBOL batch program on the mainframe (`POSTTRAN` step
`STEP15`). It is the only path by which the day's card activity reaches the transaction master, account balances
and category balances. Keeping it on COBOL ties a core nightly job to mainframe skills and runtime.

**Outcome.** A plain Java 21 program in `modernized/cbtrn02c/` that runs in a container and does exactly what
CBTRN02C does today. Given the same input files it produces byte-identical `TRANFILE`, `DALYREJS`, `ACCTFILE` and
`TCATBALF` and the same return code. Known legacy defects are kept, pinned by tests and listed for a later CR.

## What the legacy code does today

### Files (JCL `POSTTRAN.jcl:23-43`, SELECTs `CBTRN02C.cbl:29-61`)

| DD | Role | Org / open mode | LRECL | Key | Layout |
|---|---|---|---|---|---|
| `DALYTRAN` | day's transactions | sequential, `INPUT` (:238) | 350 | none | `CVTRA06Y` |
| `XREFFILE` | card → account | KSDS random, `INPUT` (:275) | 50 | card num 1-16 | `CVACT03Y` (acct id cols 26-36) |
| `ACCTFILE` | account master | KSDS random, `I-O` (:311) | 300 | acct id 1-11 | `CVACT01Y` |
| `TCATBALF` | category balances | KSDS random, `I-O` (:329) | 50 | acct 1-11 + type 12-13 + cat 14-17 | `CVTRA01Y` |
| `TRANFILE` | transaction master | KSDS random, **`OUTPUT`** (:256) | 350 | tran id 1-16 | `CVTRA05Y` |
| `DALYREJS` | rejects | sequential, `OUTPUT` (:293) | 430 | none | 350-byte input record + 80-byte trailer (4-digit code + 76-byte text) (:176-182) |

Money fields are zoned decimal with overpunch signs: amount `S9(9)V99`, account balances `S9(10)V99`.

### Processing steps

1. Open all six files. Any status other than `00` → `ABENDING PROGRAM`, CEE3ABD U999 (harness exit **16**) (:236-343, :707-711).
2. Read `DALYTRAN` sequentially until EOF (status `10`) (:202-219, :345-369). For each record, count it and clear the reason (:206-209).
3. **Validate** (:370-422). Only the first failing *lookup* stops validation:
   - Card not in `XREFFILE` → **100** `INVALID CARD NUMBER FOUND` (:380-392). Account lookup skipped.
   - Account (from xref) not in `ACCTFILE` → **101** `ACCOUNT RECORD NOT FOUND` (:396-399).
   - Otherwise both limit checks run, in order:
     - `WS-TEMP-BAL = CURR-CYC-CREDIT − CURR-CYC-DEBIT + AMT`. If `CREDIT-LIMIT < WS-TEMP-BAL` → **102** `OVERLIMIT TRANSACTION` (:403-413).
     - If `EXPIRAION-DATE < ORIG-TS(1:10)` (text compare of `YYYY-MM-DD`) → **103** `TRANSACTION RECEIVED AFTER ACCT EXPIRATION` (:414-420). This overwrites 102.
4. **Reject** (reason ≠ 0): add 1 to reject count. Write the raw 350-byte input plus the trailer to `DALYREJS` (:211-215, :446-465).
5. **Post** (reason = 0) (:424-444):
   - Build `TRAN-RECORD` field by field from the input. `TRAN-PROC-TS` = current date as `YYYY-MM-DD-HH.MM.SS.hh0000` (:692-705). The input's `PROC-TS` and 20-byte FILLER are not copied. Output FILLER stays at its initial value (spaces).
   - **Category balance** (:467-543). Key = xref acct id + type + cat. If found: `TRAN-CAT-BAL += AMT`, REWRITE. If not found (status 23): DISPLAY `TCATBAL record not found for key : <17-char key>.. Creating.`, then `INITIALIZE` and WRITE a new record with balance = AMT. Any other status → abend.
   - **Account** (:545-560): `CURR-BAL += AMT`. If `AMT >= 0`, `CURR-CYC-CREDIT += AMT`, otherwise `CURR-CYC-DEBIT += AMT`. REWRITE.
   - **Transaction master**: WRITE to `TRANFILE` (:562-579). Non-`00` status (e.g. a duplicate id) → abend.
6. Close all files (:221-226). DISPLAY `TRANSACTIONS PROCESSED :nnnnnnnnn` and `TRANSACTIONS REJECTED  :nnnnnnnnn`. **RC = 4 if any rejects**, else 0 (:227-232).

Updates are cumulative. A later transaction for the same account is validated against the cycle totals left by
earlier ones in the same run.

### Observed run (harness, stock data, clock frozen at `2022071800000000`)

`legacy/harness/run.sh CBTRN02C /tmp/run1` gives output byte-identical to `legacy/golden/CBTRN02C/`:

- 300 read (250 type/cat `01/0001`, 50 `03/0001`); 262 posted; 38 rejected, **all `0102`**.
- `TCATBALF`: 50 updated + 50 created (the `03` keys). SYSOUT has 54 lines. RC `4`.
- All 50 accounts changed. All 262 `TRAN-PROC-TS` = `2022-07-18-00.00.00.000000`.
- An independent Python model of the step 3 rule reproduces the same 38 reject ids in the same order.

A probe run with crafted input (`INPUT_DIR=/tmp/in2`: 4 transactions, account 50 removed) confirmed reasons
`0100`, `0101` and `0103`, and that 103 overrides 102. It also surfaced finding F1.

## Scope

**In scope**
- Java 21 Maven project `modernized/cbtrn02c/` (JUnit 5 only) reproducing steps 1-6.
- I/O in the harness's canonical text form (fixed-width lines, LF, overpunch signs; keyed files written in key order). Golden comparison runs on this form.
- Same SYSOUT lines and return codes (0 / 4 / 16 on fatal I/O error).
- Characterization tests: golden scenario, plus each reason code and each defect below.

**Out of scope**
- Fixing any legacy defect (F1-F8): flag only.
- Real VSAM/EBCDIC binary I/O, JCL/scheduler changes, container image/deploy wiring (Deploy stage), CBACT04C, the online CICS programs.

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbtrn02c/` passes on Java 21 with no dependencies beyond JUnit 5 and Surefire.
2. On the golden `CBTRN02C` inputs (`app/data/ASCII` dailytran, cardxref, acctdata, tcatbal), the Java outputs `TRANFILE.txt` (262), `DALYREJS.txt` (38), `ACCTFILE.txt` (50) and `TCATBALF.txt` (100) are **byte-identical** to `legacy/golden/CBTRN02C/`.
3. Exit code is `4` on golden data, `0` when there are no rejects, and `16` on a fatal I/O condition (e.g. duplicate `TRAN-ID`).
4. SYSOUT matches `legacy/golden/CBTRN02C/SYSOUT.txt` (54 lines) byte-for-byte (see Q1).
5. The processing timestamp comes from an injectable clock. With `2022-07-18 00:00:00.00` it yields `2022-07-18-00.00.00.000000`. Hundredths go in positions 21-22 and positions 23-26 are `0000`.
6. Tests pin reason codes 100, 101, 102, 103 and "103 overrides 102" against harness output for the same crafted input.
7. Tests pin each preserved defect F1-F5 with evidence from a harness run where possible.
8. Input `cardxref.txt` 36-byte lines and CRLF endings in `tcatbal.txt` are accepted (harness pads/strips them), and the output is the same as the harness's.

## Risks and findings

Legacy defects. All are **preserved** and pinned by tests.

- **F1. A created TCATBAL record inherits the FILLER of the previously read record.** `INITIALIZE` (:504) does not touch FILLER, and a failed `READ … INTO` leaves the work area as it was.
  - Golden: all 50 created records have FILLER `0000…` (22 zeros), copied from earlier stock records.
  - Probe: when the very first transaction creates a record, its FILLER is **22 spaces** (`/tmp/run2/TCATBALF.txt` line 21).
  - Java must carry this "last read record" state.
- **F2. The overlimit test counts debits as headroom.** It subtracts `CURR-CYC-DEBIT` (:403-405), but debits are stored negative (:551), so it computes credit + |debit| + amount. It also ignores `ACCT-CURR-BAL`. On stock data the formula `credit + debit + amt` would reject 23 transactions instead of 38.
- **F3. Only one reason is kept.** Expiry (103) silently overwrites overlimit (102) (:410, :417). Probe record `000000000068358X` shows `0103`.
- **F4. Reason 109 is dead/misleading.** If the account REWRITE fails (:554-559), the code sets 109 but the transaction is still written to `TRANFILE`. It is not counted or written as a reject, and there is no abend.
- **F5. `TRANFILE` is opened `OUTPUT`** (:256) although the JCL says `DISP=SHR` on the master KSDS. Each run replaces the transaction master with only that day's postings. A duplicate tran id in one day abends (RC 16) with files partly updated.
- **F6. Wrong status on the DALYREJS close error.** The message shows `XREFFILE-STATUS` (:649).
- **F7. `WS-TEMP-BAL` is `S9(9)V99`, but the account fields are `S9(10)V99`** (:187). `COMPUTE` has no `ON SIZE ERROR`, so sums ≥ 1,000,000,000.00 are truncated high-order and the limit check would be wrong. The same silent truncation applies to `TRAN-CAT-BAL` `S9(9)V99` (:508, :527). Java must truncate the same way (modulo 10^11 cents), not throw.
- **F8.** Not checked at all: `ACCT-ACTIVE-STATUS`, transaction type/category validity (`trantype`/`trancatg` are not read), and the input `PROC-TS`.

Modernisation risks
- **Zoned/overpunch codec.** It must write `{`/`}` for zero digits. Negative zero becomes `{` after arithmetic. Covered by golden data with 6 `}` and 25 `{` amounts.
- **Fatal-error semantics.** COBOL abends mid-run with files partly updated (KSDS updates are not rolled back). Default: Java writes whatever was processed up to the failure, then exits 16. This is only testable with crafted inputs.
- Golden data exercises only reason 102. Codes 100, 101 and 103 rest on the probe run plus new tests.

## Questions for the approver

1. **Is SYSOUT part of the contract?** The CR lists four files plus the RC. *Default: yes, match SYSOUT byte-for-byte too (AC4). It costs little and helps operations.*
2. **File format.** *Default: Java reads and writes the canonical text form used by `legacy/golden`. Bridging to real VSAM/EBCDIC is a separate CR.*
3. **F5 (TRANFILE truncated on every run)** is the most serious finding for production use. *Default: keep it like-for-like as requested, and raise it as a follow-up CR together with F2.*
