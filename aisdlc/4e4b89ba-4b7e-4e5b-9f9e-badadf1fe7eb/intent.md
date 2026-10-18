# CR-2 Intent: over-limit fee in the interest batch (CBACT04C)

## Problem / outcome

**Problem.** Customers who are over their credit limit pay nothing extra today. The nightly interest job
(`INTCALC` / `CBACT04C`) already has a slot for fees, but the slot is empty, so the bank loses fee income
and over-limit behaviour carries no penalty.

**Outcome.** During each interest run, every account whose balance is above its credit limit is charged a
flat **25.00** over-limit fee, once per account. The fee shows up as a normal system transaction next to
the interest and is added to the account balance in the same update. Accounts within their limit see no
change at all. The fee stays in the existing COBOL job; nothing is migrated.

## What the legacy code does today

Line references are to `app/cbl/CBACT04C.cbl`. JCL: `app/jcl/INTCALC.jcl`, `PARM='2022071800'`.

| DD | Org / access | LRECL | Key | Copybook | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | 50 | acct(11)+type(2)+cat(4) | `CVTRA01Y` | driver, input (`:28`, `:236`) |
| `XREFFILE` | KSDS, random, AIX on acct id | 50 | card num(16); alt acct(11) | `CVACT03Y` | input, card number for the tran (`:34`) |
| `ACCTFILE` | KSDS, random | 300 | acct id(11) | `CVACT01Y` | **I-O**, read + REWRITE (`:41`, `:291`) |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4) | `CVTRA02Y` | interest rates, input (`:47`) |
| `TRANSACT` | sequential, OUTPUT | 350 | none | `CVTRA05Y` | new system transactions (`:53`, `:309`) |

Processing:
1. Reads `TCATBALF` sequentially, one record per (account, type, category) (`:188-190`).
2. **Account break** (`:194-205`): when the account changes, it rewrites the *previous* account
   (`1050-UPDATE-ACCOUNT`), resets `WS-TOTAL-INT`, then reads `ACCTFILE` and `XREFFILE` for the new one.
3. Looks up the rate in `DISCGRP` by group/type/cat. If the key is missing it falls back to group `DEFAULT`
   (`:415-439`).
4. If the rate ≠ 0 (`:214`): interest = `TRAN-CAT-BAL * DIS-INT-RATE / 1200` (`:464-465`), added to
   `WS-TOTAL-INT` (`:467`). It writes a transaction (`1300-B-WRITE-TX`, `:473-515`) with ID = PARM date +
   6-digit running suffix (`:474-480`), type `01`, category `05`, source `System`, description
   `Int. for a/c <acct>`, card number from the xref, and both timestamps set to the current time. Then it
   performs **`1400-COMPUTE-FEES`, which is empty** (`:216`, `:518-520`).
5. `1050-UPDATE-ACCOUNT` (`:350-370`): `ACCT-CURR-BAL += WS-TOTAL-INT`, zeroes cycle credit and debit, REWRITE.

Facts from the harness run (`legacy/harness/run.sh CBACT04C`): RC 0, 50 `TRANSACT` records, 50 `ACCTFILE`
records, 152 SYSOUT lines. Output is identical to `legacy/golden/CBACT04C`. Stock `tcatbal.txt` has 50
records, all type/cat `01`/`0001`, one per account. **No account is over its limit** in `acctdata.txt`,
`golden/CBTRN02C/ACCTFILE.txt` or `golden/CBACT04C-after-CBTRN02C/ACCTFILE.txt` (0 of 50 in each).

`app/data/ASCII/trancatg.txt` (18 records, 60 bytes, CRLF): type(2)+cat(4)+desc(50)+filler. `010005 Interest
Amount` is the last type-01 entry. CBACT04C never reads it; `TRANCATG.jcl` and `TRANREPT.jcl` use it.

## Scope

**In**
- `CBACT04C.cbl`: implement the over-limit fee in the `1400-COMPUTE-FEES` hook. This includes the fee
  transaction write and adding the fee to the balance in `1050-UPDATE-ACCOUNT`.
- `trancatg.txt`: add `010006Over-limit Fee`, in key order after `010005`, same width and CRLF ending.
- New CI test scenario under `legacy/tests/` (CI runs `legacy/tests/*.sh`), with its own input data.
  It needs at least two over-limit accounts.

**Out**
- Java or any other migration; CBTRN02C; online (CICS) programs; the JCL.
- Fixing the existing last-account write-back defect (see Risks, Q1).
- Configurable fee amounts or rates, waivers, inactive-account rules, and accounts that have no `TCATBALF` record.
- `legacy/golden/`, `legacy/harness/`, `.github/` (not modified).

## Acceptance criteria

1. `legacy/harness/golden.sh --check` passes unchanged for all 3 scenarios (`CBACT04C`, `CBTRN02C`,
   `CBACT04C-after-CBTRN02C`). Outputs are byte-identical, including SYSOUT and RC.
2. An account is over limit when `ACCT-CURR-BAL > ACCT-CREDIT-LIMIT` (strictly greater), using the balance
   as read from `ACCTFILE` at the start of that account's processing, before this run's interest. An
   account exactly at its limit is not charged.
3. Each over-limit account processed in the run gets **exactly one** fee transaction, even when it has
   several `TCATBALF` records.
4. The fee transaction is a 350-byte `TRANSACT` record with:
   - type `01`, category `0006`, source `System`
   - description `Over-limit fee for a/c <11-digit acct id>` with the rest of the field blank-padded
   - amount `+25.00` (`0000000250{`)
   - merchant id 0 and blank merchant name, city and zip
   - card number from the xref
   - timestamps set the same way as for interest
   - transaction ID from the same `PARM-DATE + suffix` sequence, so IDs stay unique and contiguous.
5. In `ACCTFILE`, an over-limit account's new balance = old balance + total interest + 25.00. Cycle fields
   are handled as today.
6. Records of accounts within their limit are unchanged in content (ACCTFILE fields, transaction fields
   other than transaction ID).
7. `trancatg.txt` has 19 records. The new line is `010006Over-limit Fee` padded to the same 60-byte layout
   with CRLF, placed directly after `010005`.
8. The new scenario test (`legacy/tests/*.sh`) runs CBACT04C via `run.sh` with `INPUT_DIR`. It has at least
   two over-limit accounts, at least one of them with more than one `TCATBALF` record, plus at least one
   account within its limit. It asserts:
   - exactly one fee per over-limit account and none for others
   - the record layout from criterion 4
   - the balances from criterion 5
   - RC 0.

   It exits non-zero on failure.

## Risks and findings

- **F1 – the hook runs per category, not per account.** `1400-COMPUTE-FEES` is performed for every
  `TCATBALF` record with a non-zero rate (`:214-216`). Putting the fee there without a guard would charge it
  once per category. The fix needs a per-account "fee charged" flag that is reset at the account break
  (`:194-205`).
- **F2 – the hook only runs when the rate is non-zero.** An over-limit account whose categories all have a
  0% rate never reaches `:216`. `DEFAULT` gives a 0 rate to types 02 and 03 (`discgrp.txt` lines 22-27).
  This is resolved by Q2.
- **F3 – leftover description text (latent defect).** `TRAN-RECORD` is never initialised (see
  legacy/README "Known quirks"), and `STRING ... INTO TRAN-DESC` (`:485-489`) does not clear the field.
  The fee description is 34 characters and the interest description is 24. Without a fix, the first
  interest record written after a fee would end with leftover text from the fee description (characters
  25-34). The design must blank `TRAN-DESC` before each STRING. Golden output stays the same because today
  the leftover tail is always spaces.
- **F4 – existing defect: the last account is never rewritten.** This is confirmed in
  `golden/CBACT04C-after-CBTRN02C`: account `00000000050` is charged 18.77 interest (TRANSACT record
  `2022071800000050`), but its ACCTFILE record is byte-identical to the input. The cause is that the
  `ELSE PERFORM 1050-UPDATE-ACCOUNT` at `:219-220` can never run after EOF. **A fee on the last account
  would be written to TRANSACT but never added to its balance.** Fixing this changes golden output, which
  conflicts with criterion 1.
- **R1 – transaction IDs shift.** Fee transactions use the shared suffix counter, so every later
  transaction ID moves up by one per fee. That is expected and invisible in golden output (no fees there).
- **R2 – no golden coverage.** Golden data can't exercise the fee path, so the new scenario test is the
  only evidence that it works.

## Questions for the approver

1. **Last-account defect (F4): fix it in this CR?** *Default: no.* Golden must match exactly, so fixing it
   needs its own CR with a golden refresh. The new test will keep its over-limit accounts off the last
   `TCATBALF` account, and will document (not assert) the known gap.
2. **Should the fee depend on the interest rate (F2)?** *Default: no.* Charge every over-limit account that
   the run processes, whatever its rates. The check runs once at the account break, and the fee paragraph
   is still the `1400-COMPUTE-FEES` hook.
3. **Inactive accounts (`ACCT-ACTIVE-STATUS` ≠ `Y`)?** *Default: charge them anyway.* The rule as written
   does not mention status.
