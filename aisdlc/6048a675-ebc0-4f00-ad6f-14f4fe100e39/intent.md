# CR-1 Intent: INTCALC (`CBACT04C`) rewritten in Java 21

## Problem / outcome

**Problem.** The nightly interest calculation (`INTCALC`) runs only as COBOL on the mainframe. That blocks the move of batch workloads off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. For the same inputs it produces byte-identical interest transactions (`TRANSACT`) and an identical updated account master (`ACCTFILE`). This is proven on both golden scenarios. Legacy behaviour is kept exactly, defects included. Each defect is pinned by a test and listed below for a separate decision.

## What the legacy code does today

**Job.** `app/jcl/INTCALC.jcl:22` runs `EXEC PGM=CBACT04C,PARM='2022071800'`. The PARM is a 10-character date prefix used to build transaction IDs (`CBACT04C.cbl:176-178`).

**Files** (all records are fixed width; zoned decimals use overpunch signs):

| DD | Access | LRECL | Key | Copybook | Fields used |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential read (`:28-32`) | 50 | acct(11)+type(2)+cat(4), cols 1-17 | `CVTRA01Y` | `TRAN-CAT-BAL` S9(9)V99, cols 18-28 |
| `XREFFILE` | KSDS, random read **by alternate key** acct-id, cols 26-36 (`:34-39`, `:393-395`) | 50 | card no., cols 1-16 | `CVACT03Y` | `XREF-CARD-NUM` |
| `ACCTFILE` | KSDS, random, **I-O** (`:41-45`, `:291`) | 300 | acct-id, cols 1-11 | `CVACT01Y` | `CURR-BAL` 13-24, `CYC-CREDIT` 79-90, `CYC-DEBIT` 91-102, `GROUP-ID` 113-122 |
| `DISCGRP` | KSDS, random (`:47-51`) | 50 | group(10)+type(2)+cat(4), cols 1-16 | `CVTRA02Y` | `DIS-INT-RATE` S9(4)V99, cols 17-22 |
| `TRANSACT` | sequential, OUTPUT (`:53-56`, `:309`) | 350 | none | `CVTRA05Y` | whole record |

**Processing steps:**
1. Open all five files. Any status other than `00` displays a message, then the file status, then `ABENDING PROGRAM`, and calls `CEE3ABD` (U0999; the harness maps it to RC 16) (`:234-323`, `:628-648`).
2. Read `TCATBALF` in key order and `DISPLAY` each 50-byte record to SYSOUT (`:190-193`). Status `10` means EOF; any other non-`00` status abends (`:325-348`).
3. **Account break** (`:194-206`). When the account ID changes, rewrite the previous account (skipped for the first record). Then reset the total interest to 0, read `ACCTFILE` by account ID, and read `XREFFILE` by account ID through the alternate key. A not-found on either read displays `ACCOUNT NOT FOUND: …` and then **abends** (`:372-413`).
4. **Rate lookup** (`:210-213`, `:415-460`). The lookup key is `ACCT-GROUP-ID` + type + category. On status `23` it displays `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retries with group `DEFAULT`. If that retry also fails, the program abends.
5. **Interest**, only when the rate is non-zero (`:214-217`, `:462-468`). `monthly = bal * rate / 1200`, stored in S9(9)V99 **without ROUNDED**, so it is **truncated toward zero**. That amount is added to the account's total, and a transaction is written for it even if the amount is 0.
6. **Transaction record** (`:473-515`):
   - `TRAN-ID` = PARM date + a 6-digit running suffix (`2022071800000001`…).
   - Type `01`, category `0005`, source `System`, description `Int. for a/c <ACCT-ID>`.
   - Amount = the monthly interest. Merchant ID 0, merchant name/city/ZIP blank, card number from XREF.
   - `ORIG-TS` = `PROC-TS` = `YYYY-MM-DD-HH.MM.SS.hh0000` built from `CURRENT-DATE` (`:613-626`).
   - The 20-byte FILLER and the tail of the description are never initialised, so they are spaces.
7. `1400-COMPUTE-FEES` is empty (`:518-520`).
8. **Account update** (`:350-370`). `CURR-BAL += total interest`, `CYC-CREDIT = 0`, `CYC-DEBIT = 0`, then REWRITE. There is no ON SIZE ERROR.
9. Close all files and display the end banner. RC 0.

**Observed run (harness, PARM `2022071800`, clock frozen at 2022-07-18).** Both scenarios re-ran byte-identical to `legacy/golden/` (all 4 files each):
- `CBACT04C` (stock data): 50 TCATBAL records, all category `01/0001` at balance 0, so the `DEFAULT` rate is 15.00%.
  - 50 transactions, all with amount `000000000{` (zero).
  - `ACCTFILE` is byte-identical to `acctdata.txt`. The cycle fields are already 0 and the interest is 0.
  - SYSOUT has 152 lines; RC 0.
- `CBACT04C-after-CBTRN02C`: 100 TCATBAL records (50 × `01/0001`, 50 × `03/0001`). `DEFAULT 030001` has rate 0, so it produces no transaction.
  - 50 transactions with non-zero amounts, e.g. account 1: 116.48 × 15 / 1200 = 1.456, written as `0000000145F` (1.45).
  - 49 of 50 accounts change. SYSOUT has 302 lines; RC 0.
- Every lookup misses and falls back to `DEFAULT`, because `ACCT-GROUP-ID` is blank in the sample data (legacy/README.md "Known quirks").

## Scope

**In:**
- A Java 21 Maven project, `modernized/cbact04c/` (JUnit 5 only).
- It reads the same five datasets in the harness canonical text form (`legacy/README.md` "Outputs") and writes `TRANSACT` and the updated `ACCTFILE`.
- It takes the same 10-character PARM and an injectable clock.
- Tests: golden equivalence for both scenarios, plus characterization tests for each defect below.

**Out:**
- Fixing any legacy defect.
- Implementing fees (CR-2).
- CBTRN02C.
- Real VSAM/EBCDIC binary I/O, scheduler/JCL changes, and container image publishing (that belongs to the Deploy stage).
- Changes to `legacy/golden`, `legacy/harness` and `.github`.

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbact04c/` passes on Java 21, with JUnit 5 as the only test dependency.
2. **Stock scenario.** Inputs are the `app/data/ASCII` files; PARM is `2022071800`; the clock is 2022-07-18 00:00:00.00 UTC. `TRANSACT` must be byte-identical to `legacy/golden/CBACT04C/TRANSACT.txt` (50 × 350 bytes) and `ACCTFILE` to `legacy/golden/CBACT04C/ACCTFILE.txt` (50 × 300).
3. **After-CBTRN02C scenario.** Same as criterion 2, but `ACCTFILE` and `TCATBALF` come from `legacy/golden/CBTRN02C/`. Outputs must be byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/{TRANSACT,ACCTFILE}.txt`.
4. Input files with CRLF line endings (`tcatbal.txt`) and short records (`cardxref.txt`, 36 bytes) are accepted, exactly as the harness loads them.
5. Interest is truncated toward zero at 2 decimals. A test pins at least one case where rounding would give a different result, e.g. 116.48 → 1.45, not 1.46.
6. The last-account defect (finding F1) is preserved and pinned by a test. Account `00000000050` comes out unchanged in criterion 3.
7. A non-zero rate on a zero balance still writes a zero-amount transaction. A zero rate writes no transaction and adds no interest, but the account is still rewritten with its cycle fields zeroed (unless it is the last account).
8. A missing group falls back to `DEFAULT`. A missing `DEFAULT`, account or XREF stops the run with a non-zero exit and no partial success claimed (see Q2).
9. SYSOUT lines match `legacy/golden/*/SYSOUT.txt` (see Q1).

## Risks and findings

- **F1 Last account never written back (defect, confirmed).** The `ELSE PERFORM 1050-UPDATE-ACCOUNT` at `CBACT04C.cbl:219-220` is unreachable. `PERFORM UNTIL` tests `END-OF-FILE` before each iteration, so once the EOF read sets `'Y'` the loop exits.
  - Evidence: in the golden after-CBTRN02C `TRANSACT`, record `2022071800000050` charges account 50 interest of 18.77 (`0000000187G`). Yet account 50 in `ACCTFILE` is byte-identical to the CBTRN02C input: its balance is still `00000019458G` and its cycle credit/debit are not zeroed. Only 49 of 50 accounts change.
  - Business impact: the customer gets an interest transaction but the balance never includes it.
- **F2 Truncation, not rounding (behaviour; possibly a defect).** There is no `ROUNDED` at `:464-465`. In the after-CBTRN02C data, **28 of 50** interest amounts would differ by 0.01 if rounded half-up (checked against the golden output). Java must use `BigDecimal` with `RoundingMode.DOWN`. `FLOOR` would be wrong for negative balances.
- **F3 Silent overflow.** `WS-MONTHLY-INT` S9(9)V99 and `ACCT-CURR-BAL` S9(10)V99 have no ON SIZE ERROR (`:464`, `:352`), so high-order digits are dropped silently. This cannot happen with the sample data. Java must truncate the same way (modulo 10^n), and a unit test pins it.
- **F4 Zero-amount transactions.** A non-zero rate on a zero balance writes a 0.00 transaction (all 50 on stock data).
- **F5 Disclosure-group key.** The program uses `ACCT-GROUP-ID`, which is blank in every sample account; the group code sits in `ACCT-ADDR-ZIP`. Every lookup therefore falls back to `DEFAULT`. This is a data issue rather than code, but the rate-by-group path is never exercised by the golden data, so a unit test must cover it.
- **F6 Minor.**
  - The DISCGRP open error message says `ERROR OPENING DALY REJECTS FILE` (`:281`).
  - `PARM-LENGTH` is ignored (`:476`): a PARM shorter than 10 characters builds the TRAN-ID from whatever bytes follow it.
  - The timestamp has only hundredths of a second, with `0000` padding.
  - With duplicate account IDs in XREF, the alternate-key read returns the first match. The sample data has none (0 duplicates).
- **Risk: the golden data covers a narrow path.** It has only categories 01/0001 and 03/0001, the DEFAULT group, no negative balances and no abends. Characterization unit tests have to cover the rest. Where COBOL semantics are unclear (negative overpunch, overflow), check them through the harness with `INPUT_DIR`.
- **Risk: I/O model.** The rewrite works on the canonical text form, not VSAM. `ACCTFILE` is rewritten in place by key, so the Java version must keep record order and every untouched byte (FILLER, dates) exactly.

## Questions for the approver

- **Q1 SYSOUT parity?** The CR requires only `TRANSACT` and `ACCTFILE`. **Default: yes, reproduce SYSOUT line-for-line and RC 0** as well. It costs little and keeps operational logs comparable.
- **Q2 Abend behaviour.** The COBOL calls CEE3ABD U0999, which the harness maps to RC 16, and leaves partially written files. **Default: print the same messages, exit with code 16, and do not roll back partial output** (like-for-like).
- **Q3 Defects F1-F3.** **Default: preserve them in CR-1 and raise them as separate tickets.** F1 (unposted interest on the last account) is customer-impacting and should be prioritised.
