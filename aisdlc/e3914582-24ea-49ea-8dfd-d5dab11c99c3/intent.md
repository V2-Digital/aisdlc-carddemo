# CR-2 Intent — Over-limit fee in the interest batch (CBACT04C)

## Problem / outcome

**Problem.** Accounts whose balance has gone over their credit limit pay no penalty. The nightly interest job
(`INTCALC` → `CBACT04C`) already has a fee hook for this, `1400-COMPUTE-FEES`, but it is empty.

**Outcome.** On every interest run, each account whose current balance is over its credit limit is charged one
**25.00 over-limit fee**. The fee shows up as a system transaction next to the interest transactions and is added
to the account balance in the same update. Accounts within their limit see no change at all.

## What the legacy code does today

Runs as `app/jcl/INTCALC.jcl` with `PARM='2022071800'`. The harness run matches `legacy/golden/CBACT04C`
exactly (RC 0, TRANSACT 50, ACCTFILE 50, SYSOUT 152 lines).

**Files** (DD, organisation, record length, key, copybook)

| DD | Use | Org / LRECL | Key | Layout |
|---|---|---|---|---|
| TCATBALF | input, sequential read (driver) | KSDS / 50 | acct(11)+type(2)+cat(4) | `CVTRA01Y` (`CBACT04C.cbl:28-32,61-67`) |
| XREFFILE | input, random by AIX acct id | KSDS / 50 | card(16); AIX acct(11) | `CVACT03Y` (`:34-39,69-74`) |
| ACCTFILE | **I-O**, random read + REWRITE | KSDS / 300 | acct id(11) | `CVACT01Y` (`:41-45,84-87`) |
| DISCGRP | input, random | KSDS / 50 | group(10)+type(2)+cat(4) | `CVTRA02Y` (`:47-51,76-82`) |
| TRANSACT | **output**, sequential (new GDG generation) | PS / 350 | — | `CVTRA05Y` (`:53-56,89-92`; JCL DCB LRECL=350) |

`trancatg.txt` (TRAN-CAT, LRECL 60, `CVTRA04Y`) is **not read by CBACT04C**. It is read by `CBTRN03C`
(the transaction report) and loaded by `TRANCATG.jcl`.

**Processing steps** (`CBACT04C.cbl`)

1. Open all five files (`:182-186`). ACCTFILE is opened I-O (`:291`) and TRANSACT OUTPUT (`:309`).
2. Read TCATBAL records in key order (`:188-190`, `:325-348`). Each record is one balance for one
   account/type/category.
3. **When the account changes** (`:194`), it rewrites the previous account (`1050`, `:196`), resets
   `WS-TOTAL-INT` (`:200`), reads the account (`1100`, `:203`, `:372-391`) and reads the xref for its card
   number (`1110`, `:205`, `:393-413`).
4. Looks up the interest rate by group/type/cat. If that misses, it uses group `DEFAULT` (`1200`, `:415-460`).
5. **If the rate ≠ 0** (`:214`): interest is `TRAN-CAT-BAL × rate / 1200` (`:464-465`). It is added to
   `WS-TOTAL-INT` (`:467`) and one transaction is written (`1300-B`, `:473-515`), **then `1400-COMPUTE-FEES`
   runs, which is empty** (`:216`, `:518-520`).
6. `1050-UPDATE-ACCOUNT`: `ACCT-CURR-BAL += WS-TOTAL-INT`, both cycle credit and cycle debit are set to 0, then
   REWRITE (`:350-370`).

**Business rules of the interest transaction** (the template for the fee)

- `TRAN-ID` = PARM date (10) + `WS-TRANID-SUFFIX` 9(6). A single counter runs across the whole job (`:474-480`).
- Type `01`, category `05` (stored as `0005`), source `System`, description `Int. for a/c <ACCT-ID>`
  (`:482-489`).
- Amount = monthly interest. Merchant id 0, merchant name/city/zip blank, card = xref card, orig/proc timestamp =
  run timestamp (`:490-498`).
- Evidence: `TRANSACT.txt` line 1 = `2022071800000001` `01` `0005` `System` `Int. for a/c 00000000001`.

**Data facts (measured)**

- Stock `tcatbal.txt`: 50 records, **one per account, all `01/0001`**, all balances zero.
- The rate always falls back to `DEFAULT 01 0001` = 15.00% (50 × `DISCLOSURE GROUP RECORD MISSING`), so the
  hook at `:216` is reached for every account in the sample data.
- Accounts over their limit (`ACCT-CURR-BAL > ACCT-CREDIT-LIMIT`): **0** in `acctdata.txt`, **0** in
  `golden/CBTRN02C/ACCTFILE.txt`, **0** in `golden/CBACT04C-after-CBTRN02C/ACCTFILE.txt`. So no golden output
  can change.

## Scope

**In**
- Over-limit fee logic in `app/cbl/CBACT04C.cbl` (the fee hook and the account update).
- New line `010006Over-limit Fee` in `app/data/ASCII/trancatg.txt`, using the file's format (60 bytes + CRLF,
  key order, i.e. right after `010005`).
- A new test scenario (its own input data + expected outputs + a runner) with at least 2 over-limit accounts.
  It lives outside `legacy/golden/` and `legacy/harness/`, which are read-only.

**Out**
- Java/modernised rewrite, JCL changes, copybook changes, CICS screens.
- Fixing the existing last-account write-back defect (see Risks): it would change golden output.
- A configurable fee amount, fee waivers, or reading the fee from DISCGRP.
- A TCATBAL balance for category 0006. CBACT04C never writes TCATBAL today, and interest doesn't get one either.

## Acceptance criteria

1. `legacy/harness/golden.sh --check` passes. All 3 golden scenarios are byte-identical, including SYSOUT and RC.
2. In the new scenario, every account with `ACCT-CURR-BAL > ACCT-CREDIT-LIMIT` (at least 2) gets **exactly one**
   TRANSACT record with type `01`, category `0006`, source `System`, description
   `Over-limit fee for a/c <11-digit acct id>` and amount `25.00` (`0000000250{`). Merchant fields, card number
   and timestamps are set the same way as on interest records.
3. At least one over-limit account in the scenario has **≥ 2 TCATBAL records**, and it still gets only one fee.
4. Each over-limit account's rewritten `ACCT-CURR-BAL` = old balance + its interest + 25.00. Cycle credit and
   debit are 0, as today.
5. Accounts with balance **equal to** the limit, or below it, get no fee, and their records match what the
   unmodified program produces.
6. Transaction IDs stay unique and sequential across interest and fee records (same counter).
7. `trancatg.txt` has 19 lines. The new line is `010006` + `Over-limit Fee` padded to 50 + `0000` + CRLF, and it
   follows `010005`.
8. The program compiles with the harness flags with no new warnings and ends with RC 0.

## Risks and findings

- **F1 — The hook is called once per category, not once per account.** `1400-COMPUTE-FEES` runs inside the
  per-TCATBAL-record loop (`:214-217`). If the fee logic goes straight into the hook, an account with N
  rate-bearing categories gets charged N times. A per-account "fee charged" flag is needed, reset at the account
  break (`:194-205`).
- **F2 — The hook is skipped when the interest rate is 0** (`:214`). If an account's only categories have
  rate 0, the hook never runs and an over-limit account would escape the fee. The stock DEFAULT group has rate 0
  for types 02, 03, 07. See Q1.
- **F3 — Existing defect: the last account is never rewritten** (`:188-221`). After EOF the loop ends before
  the `ELSE PERFORM 1050-UPDATE-ACCOUNT` (`:219-220`) can run. Evidence is in
  `golden/CBACT04C-after-CBTRN02C`: account `00000000050` gets an interest transaction of 18.77, but its ACCTFILE
  record is unchanged. **For CR-2, if the last account in TCATBAL order is over its limit, its fee transaction is
  written but the balance never includes it.** We can't fix this without breaking AC1, because golden
  ACCTFILE account 50 would change. See Q2.
- **F4 — Only accounts that have TCATBAL records are visited.** An over-limit account with no category balance
  is never charged. This is how the driver loop works. Documented, not changed.
- **F5 — Which balance is compared.** `ACCT-CURR-BAL` is the value read at the account break. Interest from this
  run is added to it only at `1050`. The default reading is "balance as read at the start of the run" (see Q3).
- **F6 — The data has no natural over-limit accounts.** CBTRN02C rejects transactions that would go over the
  limit (all 38 rejects are `0102 OVERLIMIT TRANSACTION`), so the new scenario needs hand-built ACCTFILE/TCATBAL
  inputs.
- **F7 — Uninitialised `TRAN-RECORD` tail.** The STRING into `TRAN-DESC` leaves the old bytes after the text
  (README "Known quirks"). The fee description (34 chars) is longer than the interest one (24 chars), so it fully
  overwrites it. But an interest record written *after* a fee record would keep fee text in bytes 25-34 of
  `TRAN-DESC`. Design must clear `TRAN-DESC` (or order the writes) so interest records are not altered.
- **F8 — Transaction IDs shift.** Fee records take the next ID suffix, so in the new scenario every later
  transaction ID shifts by one per fee. That is expected. Golden is unaffected because it has no over-limit
  accounts.

## Questions for the approver

- **Q1 — Charge the fee even when the account's interest rate is 0?** *Default: yes.* Check the fee once per
  account at the account break, not inside the rate ≠ 0 branch, because the business rule doesn't depend on the
  rate. The alternative is to keep it inside the existing hook, which means zero-rate accounts escape the fee.
- **Q2 — Fix the last-account write-back defect (F3) in this CR?** *Default: no.* AC1 requires golden to match
  exactly, and the fix changes golden account 50. Raise it as a separate CR. The new scenario will put an
  over-limit account last, so the current behaviour is visible and recorded.
- **Q3 — Compare before or after this run's interest?** *Default: before.* Use `ACCT-CURR-BAL` as read, which is
  the plain reading of "current balance". An account pushed over its limit only by tonight's interest is charged
  on the next run.
