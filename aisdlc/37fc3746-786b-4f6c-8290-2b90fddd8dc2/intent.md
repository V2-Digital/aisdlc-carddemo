# CR-3 Intent: POSTTRAN / CBTRN02C → Java 21

## Problem / outcome

**Problem.** The nightly transaction posting job (`POSTTRAN`, COBOL `CBTRN02C`) only runs on the mainframe toolchain. It validates the day's card transactions, posts the good ones, updates account and category balances, and writes rejects. Changing it or running it anywhere else needs COBOL skills and infrastructure that are getting scarce.

**Outcome.** A plain Java 21 program in `modernized/cbtrn02c/` that can run in a container and does exactly what the COBOL does today. Given the same inputs, it produces byte-identical `TRANFILE`, `DALYREJS`, `ACCTFILE` and `TCATBALF` and the same return code. It is proven against `legacy/golden/CBTRN02C`. Legacy defects are kept as they are, pinned by tests and listed below for a later decision.

## What the legacy code does today

**Evidence run:** `legacy/harness/run.sh CBTRN02C /tmp/out02` produced output byte-identical to all 6 golden files. It read 300 transactions, rejected 38 and returned RC `4`.

### Files (JCL `app/jcl/POSTTRAN.jcl:23-44`)

| DD | Org / access | Open | LRECL | Key | Copybook | Stock data |
|---|---|---|---|---|---|---|
| `DALYTRAN` | Sequential | INPUT | 350 | – | `CVTRA06Y` | `dailytran.txt`, 300 recs |
| `XREFFILE` | KSDS, random | INPUT | 50 | card num 1-16 | `CVACT03Y` | `cardxref.txt`, 50 recs (36-byte lines, padded) |
| `ACCTFILE` | KSDS, random | **I-O** | 300 | acct id 1-11 | `CVACT01Y` | `acctdata.txt`, 50 recs |
| `TCATBALF` | KSDS, random | **I-O** | 50 | acct(11)+type(2)+cat(4), 1-17 | `CVTRA01Y` | `tcatbal.txt`, 50 recs (CRLF) |
| `TRANFILE` | KSDS, random | **OUTPUT** | 350 | tran id 1-16 | `CVTRA05Y` | starts empty |
| `DALYREJS` | Sequential | OUTPUT | 430 | – | 350 tran + 80 trailer | new GDG |

SELECTs are at `CBTRN02C.cbl:29-62` and FDs at `:66-97`.

Key field offsets (1-based):
- **Tran:** amt `S9(9)V99` at 133-143, card at 263-278, orig-ts at 279-304, proc-ts at 305-330, filler at 331-350.
- **Account:** curr-bal 13-24, credit-limit 25-36, expiry `X(10)` 59-68, cyc-credit 79-90, cyc-debit 91-102. All amounts are `S9(10)V99`.
- **TCATBAL:** bal `S9(9)V99` at 18-28.
- **Signs:** all numbers are zoned with an overpunch sign.

### Processing, per DALYTRAN record (main loop `:202-219`)

1. **Read** the next record (`:345-369`). Status `10` means EOF. Any other non-`00` status abends.
2. **Count:** `WS-TRANSACTION-COUNT` +1. Then reset reason to 0 and description to spaces (`:206-209`).
3. **Validate** (`:370-422`):
   - **Card lookup:** look up `XREFFILE` by card number. Not found → **100** `INVALID CARD NUMBER FOUND` (`:383-387`).
   - **Account lookup** (only if the card was found): look up `ACCTFILE` by `XREF-ACCT-ID`. Not found → **101** `ACCOUNT RECORD NOT FOUND` (`:395-399`).
   - **Limit check** (account found): `WS-TEMP-BAL = CYC-CREDIT − CYC-DEBIT + AMT`. If `CREDIT-LIMIT < WS-TEMP-BAL` → **102** `OVERLIMIT TRANSACTION` (`:403-413`).
   - **Expiry check:** this is a separate IF. If `EXPIRAION-DATE < ORIG-TS(1:10)` (string compare) → **103** `TRANSACTION RECEIVED AFTER ACCT EXPIRATION` (`:414-420`).
4. **Reject** (reason ≠ 0): reject count +1. Write `DALYTRAN-RECORD` + `9(4)` reason + `X(76)` description to `DALYREJS` (`:446-465`).
5. **Post** (reason = 0), `:424-444`:
   - **Build the record:** copy the fields into `TRAN-RECORD`. `TRAN-PROC-TS` = current timestamp formatted `YYYY-MM-DD-HH.MM.SS.hh0000` (`:692-705`). Input `DALYTRAN-PROC-TS` is ignored. Filler 331-350 is never set (spaces).
   - **Category balance** (`:467-543`): read `TCATBALF` by (xref acct, type, cat).
     - Found: add amt and REWRITE.
     - Not found: DISPLAY `TCATBAL record not found for key : <key>.. Creating.`, INITIALIZE the record (zeros/spaces), add amt, WRITE.
   - **Account** (`:545-560`): `CURR-BAL += amt`. If `amt >= 0`, `CYC-CREDIT += amt`, else `CYC-DEBIT += amt`. Then REWRITE.
   - **Transaction:** WRITE `TRANFILE` (`:562-579`).
6. **End of run** (`:221-234`):
   - Close all files.
   - DISPLAY the processed and rejected counts as `9(9)`.
   - **RC = 4 if rejects > 0, else 0.**
7. **Errors:** any unexpected file status → DISPLAY the message + `FILE STATUS IS: NNNN…` → `CEE3ABD` U999 (`:707-727`). In the harness that is exit **16**.

### Golden results (`legacy/golden/CBTRN02C/`)

| File | Recs | Notes |
|---|---|---|
| `TRANFILE.txt` | 262 | 300 − 38. Proc-ts `2022-07-18-00.00.00.000000` (frozen clock) |
| `DALYREJS.txt` | 38 | All `0102OVERLIMIT TRANSACTION` |
| `ACCTFILE.txt` | 50 | All 50 updated. 50 accounts now have a negative cyc-debit |
| `TCATBALF.txt` | 100 | 50 created (50 "Creating" lines in SYSOUT) + 50 stock |
| `SYSOUT.txt` / `RC.txt` | 54 / `4` | |

**Independent check:** I re-implemented the validation rule in a throwaway Python model on the stock data. It predicts exactly the 38 golden rejects, with 0 mismatches.

## Scope

**In**
- Java 21 Maven project `modernized/cbtrn02c/` (JUnit 5 only). It reads and writes the harness's canonical text format: fixed width, LF, overpunch signs. Files are located by DD name.
- Same outputs, SYSOUT lines and RC (0/4, and 16 on abend-equivalent errors).
- Exact fixed-point arithmetic and truncation behaviour of the COBOL PICs.
- A clock injectable to match `COB_CURRENT_DATE`/`CARDDEMO_CURRENT_DATE` (frozen `2022071800000000`).
- Characterization tests: the golden scenario plus targeted cases for reasons 100/101/102/103, the 103-over-102 precedence, create vs update of TCATBAL, and negative amounts.
- A Dockerfile/container entry point only if trivial (see Q3).

**Out**
- Fixing any legacy defect below.
- CBACT04C (CR-2), the online CICS programs, and the other JCL steps.
- Real VSAM/EBCDIC I/O. Text-file I/O is the contract, as in the harness.
- Scheduler changes, and changes to `legacy/`, `legacy/golden/` or `.github/`.

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbtrn02c/` passes on Java 21 with only JUnit 5 + Surefire.
2. Running the Java program on the stock inputs (same files `run.sh` loads) with the clock frozen at `2022071800000000` produces `TRANFILE.txt`, `DALYREJS.txt`, `ACCTFILE.txt` and `TCATBALF.txt` that are **byte-identical** to `legacy/golden/CBTRN02C/`.
3. The process exit code is `4` on that scenario, `0` when no rejects occur, and `16` on an unrecoverable file error.
4. SYSOUT lines (start/end banners, "Creating" lines, counts) are identical to `golden/CBTRN02C/SYSOUT.txt` (proposed default, Q1).
5. KSDS outputs are written in primary-key order and sequential outputs in write order, matching the harness unload.
6. Characterization tests pin each defect in *Risks and findings* (#1-#6) with the legacy result, named so they are easy to find later.
7. Feeding the Java `ACCTFILE`/`TCATBALF` to CBACT04C (`INPUT_DIR`) reproduces `golden/CBACT04C-after-CBTRN02C`. This proves the outputs are drop-in compatible.
8. `legacy/harness/golden.sh --check` still passes, which shows the legacy side is untouched.

## Risks and findings

### Legacy defects (preserve, pin with tests, do not fix)

1. **The over-limit check uses gross cycle activity instead of balance** (`:403-407`). `CYC-DEBIT` is stored negative (`:551`; 50/50 accounts are negative in golden `ACCTFILE` cols 91-102), so `CREDIT − DEBIT` *adds* refunds/debits to the exposure. `ACCT-CURR-BAL` is ignored. In the Python model, 20 of the 300 accept/reject decisions would flip if the check used net activity. This is the biggest business impact.
2. **Reason 103 silently overwrites 102** (`:407-420`). Both IFs run, so an over-limit transaction on an expired account reports only 103. It is not exercised by the stock data, because all expiries are ≥ 2023 and transactions are dated 2022-06-10.
3. **Reason 109 is dead** (`:554-559`). If the account REWRITE fails, the program sets 109 but still writes the transaction, does not count a reject and writes no reject record. The TCATBAL update has already happened.
4. **Non-atomic posting** (`:440-442`). The order is TCATBAL, then account, then TRANFILE. An abend midway leaves the balances updated without the transaction.
5. **Wrong status on DALYREJS close error** (`:649`). It displays `XREFFILE-STATUS` instead of `DALYREJS-STATUS`.
6. **Silent truncation.** `WS-TEMP-BAL` and `TRAN-CAT-BAL` are `S9(9)V99`, while the account amounts are `S9(10)V99`. There are no `ON SIZE ERROR` clauses, so high-order digits are dropped silently. The Java must truncate the same way.

### Behaviours to keep (not defects)

- **TRANFILE is opened OUTPUT** (`:256`). The KSDS is effectively recreated each run, even though the JCL uses `DISP=SHR` on the transaction master. A duplicate `TRAN-ID` in DALYTRAN → WRITE status `22` → abend. There are no duplicates in the stock data (checked).
- **The proc timestamp is `hh0000`** (hundredths + `'0000'`), not real microseconds (`:700-701`). The input `DALYTRAN-PROC-TS` is discarded.
- **The expiry check is a string comparison** of `X(10)` dates. It works only for ISO `YYYY-MM-DD`.

### Delivery risks

- **Equivalence on one dataset:** the golden scenario exercises only reason 102 and `amt` of both signs. Reasons 100/101/103 rely on hand-built test inputs, and no COBOL reference exists for them. Mitigation: generate extra reference outputs by running those inputs through `run.sh` (`INPUT_DIR`) during Test, without adding them to `golden/`.
- **Zoned/overpunch encoding and `-0`:** the Java must emit `{` for +0 and keep the COBOL sign normalisation on results. Golden diffs will catch mistakes.

## Questions for the approver

1. **Should SYSOUT be part of the equivalence contract?** The CR lists only the 4 data files + RC. *Default: yes, match `SYSOUT.txt` byte-for-byte as well. It is cheap and operators rely on those lines.*
2. **Defect #1 (over-limit formula) changes 20/300 decisions on the stock data.** Should it be raised as a follow-up CR? *Default: preserve it now (like-for-like), log it as a follow-up for the Head of Cards.*
3. **Container packaging.** Is a minimal `Dockerfile` (JRE 21, runs the jar with DD paths from env vars) in scope? *Default: yes, minimal, no orchestration.*
