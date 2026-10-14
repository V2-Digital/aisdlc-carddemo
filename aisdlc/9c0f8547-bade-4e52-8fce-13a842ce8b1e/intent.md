# CR-2 Intent: over-limit fee in CBACT04C (COBOL)

## Problem / outcome

**Problem.** Accounts that carry a balance above their credit limit pay no penalty today. The interest job (`INTCALC` / `CBACT04C`) already has a slot for fees (`1400-COMPUTE-FEES`), but the slot is empty.

**Outcome.** The nightly interest run charges a flat **25.00 over-limit fee**, once per run, to every account whose current balance is above its credit limit. The fee shows up as its own transaction next to the interest transactions, and it is added to the account balance at the same moment interest is. Nothing changes for accounts within their limit.

## What the legacy code does today

Job `app/jcl/INTCALC.jcl`: `EXEC PGM=CBACT04C,PARM='2022071800'`.

| DD | Org / access | LRECL | Key | Copybook | Use |
|---|---|---|---|---|---|
| TCATBALF | KSDS, sequential | 50 | acct(11)+type(2)+cat(4) | CVTRA01Y | input, drives the loop |
| XREFFILE | KSDS, random by AIX acct id | 50 | card num(16); alt acct id | CVACT03Y | input, card number for the tran |
| ACCTFILE | KSDS, random, **I-O** | 300 | acct id(11) | CVACT01Y | read, then REWRITE |
| DISCGRP | KSDS, random | 50 | group(10)+type(2)+cat(4) | CVTRA02Y | interest rate |
| TRANSACT | sequential, **OUTPUT** | 350 | — | CVTRA05Y | new system transactions |

Processing (`app/cbl/CBACT04C.cbl`):
1. Read TCATBAL records in key order, so all of an account's category records arrive together (`:188-193`, `:325-348`).
2. **Account break** (`:194-206`): rewrite the previous account (`1050`), set `WS-TOTAL-INT = 0`, read ACCOUNT-RECORD and XREF for the new account.
3. Look up the rate by group/type/cat. If that misses, look it up under group `DEFAULT` (`:415-460`).
4. If the rate is not zero, compute interest = `TRAN-CAT-BAL * rate / 1200` and add it to `WS-TOTAL-INT` (`:462-468`). Then write a transaction (`:473-515`) with:
   - `TRAN-ID` = PARM-DATE + 6-digit run-wide suffix (`:474-480`)
   - type `01`, cat `05`, source `System`, description `Int. for a/c <ACCT-ID>`
   - merchant fields zero/blank, card number from XREF, both timestamps = current time
5. Then `PERFORM 1400-COMPUTE-FEES` (`:216`), which is an empty paragraph (`:518-520`). It is **only reached when the rate is not zero**, and it runs **once per TCATBAL record**, not once per account.
6. `1050-UPDATE-ACCOUNT` (`:350-370`): `ACCT-CURR-BAL += WS-TOTAL-INT`, cycle credit and debit set to 0, then REWRITE. Any I/O error abends (U0999, RC 16).

Field formats: `ACCT-CURR-BAL` and `ACCT-CREDIT-LIMIT` are `S9(10)V99` at cols 13-24 and 25-36 (CVACT01Y). `TRAN-AMT` is `S9(09)V99`, so 25.00 is written as `0000000250{`.

Reference data: `app/data/ASCII/trancatg.txt` has 18 records of 60 bytes + CRLF (type 2, cat 4, desc 50, filler `0000`). The last 01 entry is `010005Interest Amount`. CBACT04C does not read this file. It is loaded by `TRANCATG.jcl` and used by reports (`TRANREPT.jcl`).

Observed run (`legacy/harness/run.sh CBACT04C`): RC 0. 50 TCATBAL records, one per account, all type/cat `01`/`0001`, all balances 0. Result: 50 zero-amount interest transactions, and ACCTFILE identical to the golden output. Over-limit accounts (balance > limit) in `acctdata.txt`, `golden/CBTRN02C/ACCTFILE.txt` and `golden/CBACT04C-after-CBTRN02C/ACCTFILE.txt`: **0 / 0 / 0**.

## Scope

**In**
- Implement the over-limit fee in `CBACT04C.cbl`. The fee is assessed once per account per run and added through the existing `1050-UPDATE-ACCOUNT` path.
- Fee transaction fields: type `01`, cat `0006`, source `System`, description `Over-limit fee for a/c <11-digit acct id>`, amount 25.00. Merchant, card and timestamp fields are filled the same way as for interest.
- Add `010006Over-limit Fee…0000` to `trancatg.txt` after `010005`, padded to 60 bytes with CRLF to match the file.
- A new test scenario with at least 2 over-limit accounts, kept outside `legacy/golden/`.

**Out**
- Java migration. Changes to CBTRN02C, DISCGRP, JCL or copybooks. A configurable fee amount. Fee waivers.
- Fixing the last-account write-back defect (see F1).

## Acceptance criteria

1. `legacy/harness/golden.sh --check` passes. All 3 golden scenarios are byte-identical, including SYSOUT and RC.
2. In the new scenario, each over-limit account gets **exactly one** TRANSACT record with type `01`, cat `0006`, source `System`, amount `0000000250{`, description `Over-limit fee for a/c <id>`, and the account's card number from XREF.
3. This holds even if the account has more than one TCATBAL record (the scenario includes one such account).
4. Each over-limit account's ACCTFILE `ACCT-CURR-BAL` = old balance + interest + 25.00, and its cycle credit/debit are 0. This applies to every account except the last one processed (F1).
5. Within-limit accounts, and accounts where balance **equals** the limit, get no fee. Their records match a run without the change.
6. TRAN-IDs stay unique and sequential across the interest and fee records. RC is 0.
7. `trancatg.txt` has 19 records. The new line is `010006` + `Over-limit Fee` padded to 50 + `0000` + CRLF. The other 18 lines are unchanged.

## Risks and findings

- **F1: last-account write-back defect (confirmed, already in `legacy/README.md`).** The `ELSE PERFORM 1050-UPDATE-ACCOUNT` at `:219-220` can never run, so the last account is never rewritten. Evidence: in `golden/CBACT04C-after-CBTRN02C`, account `00000000050` is charged 18.77 interest, yet its ACCTFILE record is byte-identical to the input. With this CR, an over-limit *last* account would get a fee transaction but no balance update. Fixing F1 would change golden account 50 and break AC1, so this CR leaves it alone.
- **F2: the hook is in the wrong place for this rule.** `1400-COMPUTE-FEES` is called per category record and only when the rate is non-zero (`:214-217`). Using it as-is would charge an account several times, or never if all its rates are zero. The fee check has to happen once per account. The natural place is the account break, after `1100-GET-ACCT-DATA`, using the balance as read.
- **F3: TRAN-ID suffix is shared.** Fee records take numbers from the same `WS-TRANID-SUFFIX` counter, so interest IDs shift after the first fee. On golden data no fee is charged, so the IDs are unchanged.
- **F4: `TRAN-RECORD` is never initialised.** Leftover bytes in `TRAN-DESC` would leak from the previous record. Both descriptions are written with `STRING`, which does not clear the tail. The longer one is 34 bytes, so the fee write must clear `TRAN-DESC` first.
- **F5: sample data has no over-limit accounts.** The new scenario needs hand-built ACCTFILE/TCATBALF inputs, fed in through `INPUT_DIR`.

## Questions for the approver

1. **Which balance is compared to the limit?** Default: the balance **as read at the start of the run, before this run's interest**. It is deterministic and matches "current balance".
2. **Charge the fee when the account's interest rate is zero?** Default: **yes**. The rule depends only on balance vs. limit.
3. **Over-limit account processed last (F1).** Default: leave F1 unfixed in this CR, and put over-limit accounts before the last account in the test. Raise a separate CR to fix F1, which needs the golden outputs re-baselined.
4. **Accounts with no TCATBAL record** are never visited by this job, so they are never charged. Default: accept this as how the job works.
