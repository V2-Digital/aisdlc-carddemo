# CR-2 Intent — Over-limit fee in the interest batch (`CBACT04C`, COBOL)

## Problem / outcome

**Problem.** Accounts that run over their credit limit are not charged for it. The nightly interest job
(`INTCALC` / `CBACT04C`) already has a fee hook, `1400-COMPUTE-FEES`, but it is empty.

**Outcome.** On each interest run, every account whose balance is over its credit limit is charged one
25.00 over-limit fee. The fee goes into the same transaction output as interest, and it is added to the
account balance in the same way as interest. Accounts within their limit see no change at all.

## What the legacy code does today

Job `app/jcl/INTCALC.jcl`: `EXEC PGM=CBACT04C,PARM='2022071800'` (PARM = run date, used as the tran-id prefix).

| DD | Org / access | LRECL | Key | Copybook | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential read | 50 | acct(11)+type(2)+cat(4) | `CVTRA01Y` | driver: one record per account × category |
| `XREFFILE` | KSDS + AIX on acct id | 50 | card num; alt acct id | `CVACT03Y` | card number for the tran record |
| `ACCTFILE` | KSDS, I-O random | 300 | acct id | `CVACT01Y` | read, then REWRITE with the new balance |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4) | `CVTRA02Y` | interest rate (`DIS-INT-RATE`) |
| `TRANSACT` | sequential output (`SYSTRAN(+1)` GDG) | 350 | — | `CVTRA05Y` | new system transactions |

Processing (`app/cbl/CBACT04C.cbl`):
1. It reads TCATBAL sequentially (`:190`, `:325`). Records arrive grouped by account in key order.
2. **Account break** (`:194-205`): it rewrites the previous account (`1050`), resets `WS-TOTAL-INT` to 0,
   then reads ACCTFILE (`1100`, `:372`) and the XREF record by acct id (`1110`, `:393`).
3. For each category record, it looks up the rate by group/type/cat. If the lookup misses (status 23), it
   retries with group `DEFAULT` (`1200`, `:415-440`).
4. **If the rate is not 0** (`:214`): it runs `1300-COMPUTE-INTEREST`, monthly interest =
   `TRAN-CAT-BAL × rate / 1200` (`:464`), and adds it to `WS-TOTAL-INT` (`:467`). It writes one tran record
   (`1300-B-WRITE-TX`, `:473-515`) and **then performs `1400-COMPUTE-FEES`** (`:216`), which is empty
   (`:518-520`).
5. The interest tran record has: id = `PARM-DATE` + 6-digit `WS-TRANID-SUFFIX` (`:474-479`), type `01`,
   cat `05` (stored `0005`), source `System`, desc `Int. for a/c <acct>` built with STRING (`:485-489`),
   merchant fields zero or blank, card = `XREF-CARD-NUM`, orig/proc timestamp = current timestamp (`:482-498`).
6. `1050-UPDATE-ACCOUNT` (`:350-370`): `ACCT-CURR-BAL += WS-TOTAL-INT`, cycle credit and debit set to 0,
   then REWRITE.

Balances: `ACCT-CURR-BAL` and `ACCT-CREDIT-LIMIT` are `S9(10)V99` (`CVACT01Y`). `TRAN-AMT` is `S9(09)V99`.

Observed run (`legacy/harness/run.sh CBACT04C`, stock data): RC 0, TRANSACT 50, ACCTFILE 50, SYSOUT 152. This
matches `legacy/golden/CBACT04C`. Every disclosure lookup falls back to `DEFAULT`.

## Scope

**In**
- Implement the over-limit fee in `CBACT04C.cbl` (COBOL only), using the `1400-COMPUTE-FEES` hook.
- Fee record: type `01`, cat `0006`, source `System`, desc `Over-limit fee for a/c <acct id>`, amount
  `25.00`, written to `TRANSACT`. Same id scheme, card number and timestamps as interest.
- Add the fee to `ACCT-CURR-BAL` in `1050-UPDATE-ACCOUNT`, together with the interest.
- Add `010006Over-limit Fee` to `app/data/ASCII/trancatg.txt` after `010005`: a 60-byte record with a CRLF
  line ending like the other lines.
- A new test scenario (input dir + expected outputs) with at least 2 over-limit accounts.

**Out**
- Java migration, CBTRN02C, CICS screens, the TCATBAL/DISCGRP layouts, and the downstream posting of SYSTRAN.
- Fixing the last-account write-back bug (see R1), unless the approver asks for it.
- Configurable fee amount or fee tables. The fee is a fixed 25.00 constant.

## Acceptance criteria

1. `legacy/harness/golden.sh --check` passes. All 3 golden scenarios are byte-identical (no golden account
   is over its limit, see R5).
2. In a new scenario with ≥2 over-limit accounts (balance > limit), each one gets **exactly one** TRANSACT
   record with type `01`, cat `0006`, source `System`, desc `Over-limit fee for a/c <11-digit acct>`
   (rest of the 100-byte field blank), amount `0000000250{` (+25.00), and that account's card number.
3. This holds even when the account has several TCATBAL records with a non-zero rate. At least one test
   account has ≥2 such records.
4. For each over-limit account (not the last account, see R1), the new `ACCT-CURR-BAL` = old balance +
   interest + 25.00.
5. Accounts where balance ≤ limit get no fee record. Balance **equal** to the limit gets no fee, and the
   scenario includes one such account.
6. Interest records in the new scenario are the same as without the change (amount, type, cat, desc). Only
   their tran-id suffixes may shift, because fee records take sequence numbers.
7. Tran ids stay unique and sequential across interest and fee records. RC = 0.
8. `trancatg.txt` has 19 records. The new record is key `010006` + desc `Over-limit Fee` (padded to 50) +
   `0000`, which makes 60 bytes + CRLF like the other lines.

## Risks and findings

- **R1 – Last-account write-back bug (confirmed, existing).** After EOF, the `ELSE PERFORM
  1050-UPDATE-ACCOUNT` at `:219-220` can never run, because `PERFORM UNTIL END-OF-FILE = 'Y'` (`:188`)
  exits first. Evidence: in `golden/CBACT04C-after-CBTRN02C`, account `00000000050` is charged 18.77
  interest in TRANSACT but is unchanged in ACCTFILE (balance still `00000019458G`). **Impact on CR-2:** if
  the last account is over its limit, its fee record is written but the fee never reaches the balance.
  Fixing the bug changes golden account 50, which breaks AC1.
- **R2 – The hook is called per category, not per account.** `1400` runs once per TCATBAL record with a
  non-zero rate (`:214-216`). An account with 2 interest-bearing categories would be charged twice. A
  per-account "fee charged" flag, reset at the account break (`:200`), is required.
- **R3 – The hook only runs when the rate is non-zero.** An over-limit account whose categories all have
  rate 0 (for example, only `DEFAULT 03/0001`, rate 0.00) would never reach `1400`. The business rule says
  nothing about interest, so the fee check has to be account-level (see Q1).
- **R4 – `TRAN-DESC` is not cleared.** The STRING at `:485` overwrites only the first 24 bytes. The fee
  description is 34 bytes, so the next interest record would carry the leftover tail
  (`Int. for a/c 00000000001 a/c 00000000001`-style). Both descriptions must start from SPACES. Today all
  interest descriptions are the same length, so this is latent.
- **R5 – Golden data has no over-limit accounts.** Measured max balance/limit: stock 0.099, after CBTRN02C
  0.928, after the interest run 0.939. All over-limit cases come from the new scenario only. CBTRN02C
  rejects over-limit postings (38 × `0102 OVERLIMIT`), so test data has to be crafted by hand in
  ACCTFILE/TCATBALF.
- **R6 – Tran-id sequence.** Fee records take `WS-TRANID-SUFFIX` numbers, so interest ids after the first fee
  shift. This is invisible in golden (no fees). Downstream consumers only need uniqueness.
- **R7 – Only accounts with TCATBAL records are processed** (TCATBAL drives the loop). An over-limit account
  with no category balance record is never seen. All 50 sample accounts have records.
- **R8 – No category balance update.** Like interest, the fee does not update TCATBAL `01/0006`. DISCGRP has
  no `01/0006` rate, so there is no interest-on-fee side effect in this job.

## Questions for the approver

1. **Should the fee depend on interest being computed?** Default: **no**. The check runs once per account,
   whatever the rates are, using the balance as read from ACCTFILE at the start of that account (before
   this run's interest).
2. **Which balance is compared to the limit?** Default: the **pre-interest** balance from the file, strictly
   `>` the limit. Interest from this run does not push an account over the limit.
3. **Last-account bug (R1).** Default: **leave it unfixed** in CR-2 so the golden scenarios still match. The
   new scenario keeps the over-limit accounts away from the last position, and the bug is documented. A
   separate CR fixes it and regenerates golden. If you want it fixed here, AC1 must allow account
   `00000000050` in `CBACT04C-after-CBTRN02C` to change.
