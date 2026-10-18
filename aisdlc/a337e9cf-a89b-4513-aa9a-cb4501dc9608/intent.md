# CR-1 Intent — INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The scheduled interest run (`app/jcl/INTCALC.jcl`, COBOL `CBACT04C`) only runs on the mainframe. That blocks the move of batch work off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that can run in a container. For the same inputs it produces byte-identical interest transactions (`TRANSACT`) and an updated account master (`ACCTFILE`), proven on both golden scenarios. Legacy behaviour, defects included, is kept and pinned by tests. Any fixes are separate decisions.

## What the legacy code does today

Verified by running `legacy/harness/run.sh CBACT04C`, both on stock data and with `INPUT_DIR=legacy/golden/CBTRN02C`. All three outputs (`TRANSACT`, `ACCTFILE`, `SYSOUT`) match `legacy/golden/` byte-for-byte, with RC 0.

### Files (JCL `INTCALC.jcl` STEP15, `PARM='2022071800'`)

| DD | Org / access | LRECL | Key | Use | Ref |
|---|---|---|---|---|---|
| TCATBALF | KSDS, sequential, INPUT | 50 | acct(11)+type(2)+cat(4) | drives the run | CBACT04C.cbl:28, CVTRA01Y |
| XREFFILE | KSDS, random, INPUT | 50 | card(16); **AIX acct-id(11)** at offset 25 | card number per account | :34, CVACT03Y |
| ACCTFILE | KSDS, random, **I-O** | 300 | acct-id(11) | read + REWRITE | :41, CVACT01Y |
| DISCGRP | KSDS, random, INPUT | 50 | group(10)+type(2)+cat(4) | interest rate | :47, CVTRA02Y |
| TRANSACT | sequential, OUTPUT | 350 | n/a | new interest transactions | :53, CVTRA05Y |
| SYSOUT | DISPLAY | n/a | n/a | log | n/a |

The JCL's `XREFFIL1` (the AIX path) isn't needed under the harness: BDB holds the alternate key.

### Processing steps
1. Open all five files. If an open status is not `00`, the program abends (RC 16 under the harness) (:234-323).
2. Read TCATBAL sequentially in key order (:325). For each record, DISPLAY the 50-byte record (:193).
3. **Control break on account id** (:194). When the account changes:
   - rewrite the previous account (step 6), except for the first account (:195-199);
   - reset total interest to 0;
   - random-read ACCTFILE, then read XREFFILE by the alternate key acct-id (:202-205). A missing record gives status 23, which abends (:372-413).
4. **Rate lookup** (:210-213, :415-460). The key is `ACCT-GROUP-ID` + TCATBAL type + cat.
   - On status `23`, DISPLAY `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and re-read with group `DEFAULT`.
   - If the DEFAULT record is also missing, abend.
5. **If the rate is not 0** (:214-217):
   - `monthly = TRAN-CAT-BAL * DIS-INT-RATE / 1200` into `S9(9)V99`. There is **no ROUNDED**, so the result is truncated toward zero at cents (:464).
   - Add it to the account total, then write one transaction (:473-515):
     - `TRAN-ID` = PARM-DATE(10) + 6-digit running suffix, which only advances for written transactions;
     - type `01`, cat `0005`, source `System`;
     - desc `Int. for a/c ` + ACCT-ID(11), padded with spaces;
     - amount = monthly interest; merchant id 0; merchant name, city and zip blank;
     - card = XREF card number;
     - `ORIG-TS` = `PROC-TS` = current date as `YYYY-MM-DD-HH.MM.SS.hh0000` (:613-626), *not* the PARM date;
     - FILLER (cols 331-350) is spaces.
   - `1400-COMPUTE-FEES` is an empty stub (:518-520).
6. **Account update** (:350-370): `CURR-BAL += total interest`, `CURR-CYC-CREDIT = 0`, `CURR-CYC-DEBIT = 0`, then REWRITE. This runs even when the total is 0.
7. Close all files and DISPLAY the end message. RC 0.

### Data facts
- **Stock data:** 50 TCATBAL records, all type/cat `01/0001`, all with balance 0.
  - Result: 50 zero-amount transactions; ACCTFILE is unchanged from `acctdata.txt`; SYSOUT has 152 lines.
- **After CBTRN02C:** 100 TCATBAL records (50 × `01/0001` and 50 × `03/0001`).
  - DEFAULT rates: `01/0001` = 15.00%; `03/0001` = 0.00%, so no transaction is written.
  - Result: 50 transactions and 302 SYSOUT lines.
- `ACCT-GROUP-ID` is blank in every account, so all 100 rate lookups (2 per account) fall back to `DEFAULT`.

## Scope

**In**
- Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only).
- Reads and writes the harness's canonical fixed-width text format, including overpunch-signed zoned decimals.
- Same PARM date input and the same deterministic-clock override as the harness.
- Same exit codes (0 / 16).
- Characterization tests for every defect and quirk listed below.
- A golden equivalence test for both scenarios.

**Out**
- Fixing any legacy defect, including the empty fees paragraph (that is CR-2).
- CBTRN02C.
- Real VSAM/EBCDIC I/O.
- Containerisation and scheduler wiring (Deploy stage).
- Changes to `legacy/` or `.github/`.

## Acceptance criteria
1. `mvn test` in `modernized/cbact04c/` passes on Java 21 with JUnit 5 as the only test dependency.
2. Scenario `CBACT04C`: given `app/data/ASCII` inputs, PARM `2022071800` and clock `2022-07-18 00:00:00.00`, the Java `TRANSACT.txt` (50 records × 350 bytes) and `ACCTFILE.txt` (50 × 300) are byte-identical to `legacy/golden/CBACT04C/`.
3. Scenario `CBACT04C-after-CBTRN02C`: with ACCTFILE and TCATBALF taken from `legacy/golden/CBTRN02C/`, both outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. Exit status is 0 for both scenarios, matching `RC.txt`.
5. Interest is truncated toward zero at 2 decimals. A test pins a case where rounding would differ; 28 of the 50 golden amounts would differ under rounding.
6. Characterization test: the last account in TCATBAL order is **not** updated. Account `00000000050` is unchanged even though a transaction of 18.77 is written for it.
7. Characterization tests:
   - a missing group falls back to `DEFAULT`;
   - a zero rate writes no transaction and does not advance the TRAN-ID suffix;
   - zero-total accounts still get their cycle fields zeroed;
   - accounts with no TCATBAL records are left untouched.
8. A missing account, xref or DEFAULT rate ends the run with exit 16 and the legacy error text.
9. ACCTFILE output contains all input accounts, in key order.

## Risks and findings

| # | Finding | Evidence | Action |
|---|---|---|---|
| F1 | **Defect: the last account is never rewritten.** `ELSE PERFORM 1050-UPDATE-ACCOUNT` (:219-220) can't run: once EOF is set, the `PERFORM UNTIL` (:188) exits before the body runs again. The last account loses its interest, and its cycle fields aren't reset. | `golden/CBACT04C-after-CBTRN02C`: TRANSACT `2022071800000050` charges acct 50 `0000000187G` (18.77). Its ACCTFILE row is byte-identical to `golden/CBTRN02C/ACCTFILE.txt` (bal 1945.87). 49 of 50 rows change. | Preserve and pin it. Fix in a separate CR. |
| F2 | Interest is truncated, not rounded (no `ROUNDED`, :464). | Recomputed all 50 golden amounts: truncation matches 50/50; rounding would differ on 28. | Preserve; use BigDecimal `RoundingMode.DOWN`. |
| F3 | Fees are not implemented (`1400-COMPUTE-FEES` is just `EXIT.`, :518). | Source | Preserve (CR-2). |
| F4 | Timestamps come from the wall clock, not the PARM date, and only to hundredths (`hh0000` µs, :621-622). | Golden `2022-07-18-00.00.00.000000` (frozen clock) | The clock must be injectable to reproduce the golden outputs. |
| F5 | Silent high-order truncation: `WS-MONTHLY-INT S9(9)V99`, `ACCT-CURR-BAL S9(10)V99` and the 6-digit TRAN-ID suffix (wraps after 999,999) have no `ON SIZE ERROR`. | Source :168, :173, :474 | Emulate COBOL modulo truncation; pin it with unit tests. Not reachable with the sample data. |
| F6 | `ACCT-GROUP-ID` is blank in all sample accounts. The group code actually sits in `ACCT-ADDR-ZIP`. Every lookup uses `DEFAULT`, so group-specific rates are never exercised by the golden outputs. | SYSOUT: 100 × `DISCLOSURE GROUP RECORD MISSING` | Cover the group-hit path with a unit test. |
| F7 | Wrong message: a DISCGRP open failure prints `ERROR OPENING DALY REJECTS FILE` (:281). | Source | Preserve the text. |
| F8 | The XREF alternate key allows duplicates. With several cards per account, the first card in alternate-key order is used. The sample data has no duplicates (0 repeated acct ids). | `cardxref.txt` | Use the first match in file order; note the assumption. |
| F9 | The CR says "nightly", but Control-M schedules INTCALC as `MONTHLY` (`app/scheduler/CardDemo.controlm:69`), and `/1200` is a monthly rate. Running it nightly would charge interest about 30× per month. | Scheduler file | Information only; no code impact. |
| R1 | Byte-exactness depends on the text format: fixed width, trailing spaces kept, LF line endings, overpunch signs, and `-0` written as `{` (positive zero). | README "Outputs" | Golden diff tests. |

## Questions for the approver
1. **SYSOUT parity.** Should the Java program also reproduce `SYSOUT.txt` byte-for-byte (152 / 302 lines)? *Default: yes.* It is deterministic, cheap, and keeps operations runbooks valid, but it is not a hard acceptance gate beyond AC 2-4.
2. **ACCTFILE I/O shape.** In the container there is no KSDS. *Default:* read the account master text file and write the full updated master to a separate output path. Rewriting in place isn't needed because the deploy plumbing swaps the files.
3. **F1 (last-account defect).** It is preserved per the CR. *Default:* raise a follow-up CR to fix it in both COBOL and Java, because it under-charges one account per run.
