# CR-2 Intent — Over-limit fee in the interest batch (CBACT04C)

## Problem / outcome

**Problem.** Accounts that carry a balance above their credit limit pay nothing extra today. The nightly
interest job (`CBACT04C`, job `INTCALC`) has a fee hook (`1400-COMPUTE-FEES`), but it is empty.

**Outcome.** The existing COBOL interest run charges a flat **25.00 over-limit fee** to every account whose
current balance is greater than its credit limit, **once per account per run**. The fee is written as a
system transaction next to the interest transactions and added to the account balance. Nothing changes for
accounts within their limit. No migration is part of this change.

## What the legacy code does today

Run evidence: `legacy/harness/run.sh CBACT04C` on stock data gives RC 0, 50 `TRANSACT` records, 50
`ACCTFILE` records and 152 `SYSOUT` lines, the same as `legacy/golden/CBACT04C`.

**Files** (JCL `app/jcl/INTCALC.jcl`, `PARM='2022071800'`)

| DD | Org / access | LRECL | Key | Use |
|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential read | 50 | acct(11)+type(2)+cat(4) | driver: one record per account × category (`CVTRA01Y`) |
| `XREFFILE` | KSDS, random by AIX acct id | 50 | card num; alt key acct id | card number for the transaction (`CVACT03Y`) |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4) | interest rate `DIS-INT-RATE` (`CVTRA02Y`) |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct id(11) | read, then REWRITE (`CVACT01Y`) |
| `TRANSACT` | sequential **OUTPUT** (new GDG) | 350 | — | generated transactions (`CVTRA05Y`) |

**Processing** (`app/cbl/CBACT04C.cbl`)

1. Read `TCATBALF` sequentially, one record per category balance (`:188-190`, `:325-348`).
2. When the account changes (`:194`):
   - REWRITE the previous account (`1050-UPDATE-ACCOUNT`, `:196`).
   - Reset `WS-TOTAL-INT` (`:200`).
   - Read the new account into `ACCOUNT-RECORD` (`:203`, `:372`) and its xref (`:205`, `:393`).
3. For each category: look up the rate by group/type/cat. A miss (status 23) falls back to group `DEFAULT`
   (`:210-213`, `:415-460`).
4. If the rate ≠ 0: interest = `TRAN-CAT-BAL × rate / 1200`. It is added to `WS-TOTAL-INT` and written as a
   transaction (`:214-216`, `:462-468`). **Then `1400-COMPUTE-FEES` is performed, once per category with a
   non-zero rate, not once per account** (`:216`, `:518-520`, an empty `EXIT`).
5. Interest transaction (`1300-B-WRITE-TX`, `:473-515`):
   - `TRAN-ID` = PARM date + 6-digit run sequence `WS-TRANID-SUFFIX`.
   - Type `01`, cat `05`, source `System`, desc `Int. for a/c <ACCT-ID>`.
   - Merchant fields zero/blank, card number from the xref.
   - Both timestamps are set to the current time.
6. `1050-UPDATE-ACCOUNT` (`:350-370`) does `ACCT-CURR-BAL += WS-TOTAL-INT`, zeroes cycle credit/debit, then
   REWRITEs. **This is where a fee must be added to the balance.**

**Business rules relevant to CR-2**
- `ACCT-CURR-BAL` and `ACCT-CREDIT-LIMIT` are both `S9(10)V99` (`app/cpy/CVACT01Y.cpy`). `ACCT-CURR-BAL` is
  the value read at the account break, before this run's interest is added.
- Only accounts with at least one `TCATBALF` record are processed. An account with no category balance is
  never read.
- `TCATBALF` is not updated by this program. Interest is not posted to a category balance, and the fee will
  not be either.
- `trancatg.txt` is not read by CBACT04C or the harness. It is used by `CBTRN03C` (report) and
  `TRANCATG.jcl`. It has 18 rows with CRLF endings. The layout is `CVTRA04Y`: type(2)+cat(4)+desc(50)+filler
  `0000`.

**Data facts (measured)**
- No account is over its limit in any golden input. The highest balance/limit ratio is 0.10 in
  `acctdata.txt` and 0.93 after CBTRN02C. No account balance equals its limit either. A fee therefore
  cannot change any golden output.
- In stock `tcatbal.txt`, each of the 50 accounts has 1 category (`010001`, DEFAULT rate 1.50).
- In `golden/CBTRN02C/TCATBALF.txt`, each account has 2 categories (`010001` rate 1.50, `030001` rate 0).

## Scope

**In**
- Implement the over-limit fee in `1400-COMPUTE-FEES` / `CBACT04C.cbl`, plus any working storage it needs.
- Add `010006Over-limit Fee` to `app/data/ASCII/trancatg.txt`, in the same 60-byte, CRLF format.
- Add a new test scenario with its own inputs and expected outputs, outside `legacy/golden/`, and a script to
  check it. It must have ≥2 over-limit accounts and at least one account with several category records.

**Out**
- Java migration (`modernized/`).
- Changes to fee amount configuration. 25.00 is a constant.
- Posting the fee to `TCATBALF`.
- Changes to CBTRN02C.
- Fixing the last-account write-back defect (see Risks, Q1).
- `trancatg` VSAM reload JCL.

## Acceptance criteria

1. `legacy/harness/golden.sh --check` passes unchanged for all 3 scenarios (byte-identical outputs and RC).
2. An account with `ACCT-CURR-BAL > ACCT-CREDIT-LIMIT` gets exactly **one** fee transaction per run, even if
   it has several `TCATBALF` records.
3. An account with balance ≤ limit, including equal, gets no fee transaction.
4. The fee transaction is a 350-byte record:
   - `TRAN-TYPE-CD` = `01`, `TRAN-CAT-CD` = `0006`, `TRAN-SOURCE` = `System`.
   - `TRAN-DESC` = `Over-limit fee for a/c <11-digit acct id>`, space-padded.
   - `TRAN-AMT` = +25.00 (`0000000250{`).
   - Card number from the xref, merchant fields zero/blank, timestamps as for interest.
   - `TRAN-ID` = PARM date + the next value of the shared run sequence. IDs stay unique and contiguous.
5. On REWRITE, the over-limit account's `ACCT-CURR-BAL` = old balance + total interest + 25.00. Cycle fields
   are zeroed as today.
6. The new scenario has ≥2 over-limit accounts, and at least one of them has ≥2 category records. It shows
   one fee per over-limit account, the balance effect from criterion 5, and RC 0. Running it twice gives
   byte-identical output.
7. `trancatg.txt` has 19 rows. The new row `010006Over-limit Fee` sits after `010005` and is padded to the
   same width with `0000` filler and CRLF.

## Risks and findings

- **F1: the hook runs per category, not per account** (`:214-216`). If it fires on every call, an account
  with 2 categories is charged twice. With the post-CBTRN02C data, every account has 2 categories. A
  once-per-account guard is required, reset at the account break (`:194-205`).
- **F2: the hook only runs when the rate ≠ 0** (`:214`). An over-limit account where every category has a
  zero rate (for example group `ZEROAPR`) would never be charged if the fee only lives in the hook. See Q2.
- **F3: confirmed legacy defect, last account never rewritten.** The `ELSE PERFORM 1050-UPDATE-ACCOUNT`
  (`:219-220`) is unreachable, because `PERFORM UNTIL` tests before the body. Evidence: in
  `golden/CBACT04C-after-CBTRN02C`, account `00000000050` is charged 18.77 in `TRANSACT`, but its
  `ACCTFILE` record is unchanged (balance `00000019458G`). The same will happen to a fee on the last
  account: the transaction is written but the balance is not updated. Fixing this changes golden scenario 3,
  which conflicts with acceptance criterion 1. See Q1.
- **R1: transaction IDs shift.** Fee records use the shared `WS-TRANID-SUFFIX`, so the interest IDs after the
  first fee move up by one. This only happens in over-limit scenarios, so the golden outputs are unaffected.
- **R2: no natural over-limit data.** CBTRN02C rejects over-limit postings (38 `0102 OVERLIMIT` rejects), so
  the new scenario needs a constructed `ACCTFILE`, supplied via `INPUT_DIR`.
- **R3: uninitialised `TRAN-RECORD` tail.** The `TRAN-DESC` tail and FILLER are spaces under the default
  dialect. The fee description is longer than the interest description, and a STRING does not clear the rest
  of the field. If an interest transaction follows a fee in the same record buffer, its description could
  keep characters left over from the fee. The fee write must leave the buffer clean, for example by moving
  spaces to `TRAN-DESC` first. Evidence: `:485-488` STRINGs into `TRAN-DESC` without clearing it.

## Questions for the approver

1. **Last-account defect (F3).** Fix it in this CR? **Default: no.** Golden scenario 3 must stay
   byte-identical. Document it, keep the test's over-limit accounts away from the last account, and raise a
   separate CR.
2. **Zero-rate accounts (F2).** Charge the fee even when no category has a non-zero rate? **Default: yes.**
   The rule depends only on balance vs limit. Evaluate the fee once per account, at the account break or on
   the first category, not only inside the rate ≠ 0 branch.
3. **Which balance is compared.** **Default:** `ACCT-CURR-BAL` as read at the start of this run, before this
   run's interest. Interest alone never triggers a fee in the same run.
4. **Fee transaction order.** **Default:** write the fee immediately before that account's first interest
   transaction, so the order is deterministic and ACs are easy to check. Any fixed order is acceptable to the
   ACs.
