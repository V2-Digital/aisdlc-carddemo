# CR-2 Intent: over-limit fee in the interest batch (`CBACT04C`)

## Problem / outcome

**Problem.** Accounts that are over their credit limit pay nothing extra today. The nightly interest job
(`INTCALC` / `CBACT04C`) already has a place for fees, `1400-COMPUTE-FEES`, but it is empty.

**Outcome.** During each interest run, every account whose balance is above its credit limit is charged a flat
**25.00 over-limit fee**, once. The fee shows up next to the interest transactions in the job's output, and it
is added to the account balance the same way interest is. Accounts within their limit see no change at all.
This stays in COBOL. Nothing is migrated in this change.

## What the legacy code does today

**Job:** `app/jcl/INTCALC.jcl`, `EXEC PGM=CBACT04C,PARM='2022071800'` (the PARM is the run date and the tran-id prefix).

| DD | Org / access | LRECL | Key | Use | Layout |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | 50 | acct(11)+type(2)+cat(4) | input, drives the loop | `CVTRA01Y` |
| `XREFFILE` | KSDS, random, AIX on acct id | 50 | card num(16); alt acct id | input: card number for the tran | `CVACT03Y` |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct id(11) | read + REWRITE | `CVACT01Y` |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4) | interest rate | `CVTRA02Y` |
| `TRANSACT` | sequential, OUTPUT, RECFM=F | 350 | none | new system transactions | `CVTRA05Y` |

Fields that matter (`app/cpy/CVACT01Y.cpy`): `ACCT-CURR-BAL` and `ACCT-CREDIT-LIMIT` are both `S9(10)V99`
(record cols 13-24 and 25-36).

**Processing steps** (`app/cbl/CBACT04C.cbl`):
1. Read TCATBAL sequentially, one category balance per record (`:188-190`, `:325-348`).
2. **Account break**, when the acct id changes (`:194`): rewrite the previous account (`:195-196`), reset
   `WS-TOTAL-INT` (`:200`), read the account (`:1100`, `:372`) and its xref/card (`:1110`, `:393`).
3. Look up the rate by group/type/cat. If that misses, fall back to group `DEFAULT` (`:210-213`, `:415-460`).
4. **Only if the rate ≠ 0** (`:214`): compute the interest as `bal × rate / 1200` (`:464-465`), add it to
   `WS-TOTAL-INT` (`:467`), and write one tran (`:473-515`). Then `PERFORM 1400-COMPUTE-FEES` (`:216`), which is
   empty (`:518-520`).
5. Write each interest tran with: id `PARM-DATE` + 6-digit running suffix (`:474-480`), type `01`, cat `05`
   (it writes `0005`), source `System`, desc `Int. for a/c <acct id>` (`:482-489`), amount, card num from xref,
   and both timestamps set to the current timestamp (`:490-498`).
6. Account update (`:350-370`): `ACCT-CURR-BAL += WS-TOTAL-INT`, cycle credit/debit set to 0, then REWRITE.

**Business rules seen:** interest is per category balance, monthly (`/1200`). There is no active-status check.
An account with no TCATBAL record is never read or updated. `1400` runs **once per category record with a
non-zero rate**, not once per account.

**Observed run** (`legacy/harness/run.sh CBACT04C`): RC 0, 50 TRANSACT, 50 ACCTFILE, 152 SYSOUT lines.
The output is byte-identical to `legacy/golden/CBACT04C`. None of the 50 accounts are over limit in any golden
input or output. The highest balance/limit ratio is 0.939 (`golden/CBACT04C-after-CBTRN02C/ACCTFILE.txt`).

## Scope

**In**
- Implement the fee in `CBACT04C` (`1400-COMPUTE-FEES` and whatever per-account state it needs).
- Add `010006Over-limit Fee` to `app/data/ASCII/trancatg.txt`, in the existing 60-byte + CRLF format, after `010005`.
- Add a new test scenario (inputs plus expected outputs, outside `legacy/golden/`) with ≥2 over-limit accounts,
  and a script or check that runs it through `legacy/harness/run.sh`.

**Out**
- The Java rewrite in `modernized/`, CBTRN02C, CICS screens, and the DISCGRP/TCATBAL entries for category 0006.
- Fixing the last-account write-back bug (see R1).
- Fee waivers, configurable amounts, and active-status rules.

## Acceptance criteria

1. `legacy/harness/golden.sh --check` passes. All 3 golden scenarios are byte-identical, RC 0.
2. In the new scenario, each account with `ACCT-CURR-BAL > ACCT-CREDIT-LIMIT` gets **exactly one** fee tran in
   `TRANSACT`, even when the account has several TCATBAL records with a non-zero rate. At least one such
   account must have ≥2 of these records.
3. The fee tran has type `01`, cat `0006`, source `System`, desc `Over-limit fee for a/c <11-digit acct id>`
   (padded with spaces), amount `25.00` (`0000000250{`), and the merchant fields set as on interest trans.
   Card num comes from the xref, timestamps are the run timestamp, and the id comes from the same running
   sequence as interest.
4. The over-limit account's REWRITTEN balance = old balance + total interest + 25.00. Cycle credit/debit are 0.
5. Accounts at or under their limit (including balance **equal** to the limit) get no fee tran, and their
   records match what the unmodified program produces for them.
6. `trancatg.txt` has 19 records, with `010006` holding desc `Over-limit Fee`. The other 18 lines are unchanged.
7. The new scenario passes twice in a row with byte-identical results, under the frozen clock.

## Risks and findings

- **R1: Last-account write-back defect (confirmed, pre-existing).** `ELSE PERFORM 1050-UPDATE-ACCOUNT`
  (`:219-220`) can never run, because the `UNTIL` is tested before the body once EOF is set (`:188`, `:340`).
  So the last account in TCATBAL never gets rewritten. Evidence: `golden/CBACT04C-after-CBTRN02C` charges
  account `00000000050` interest of 18.77 (TRANSACT rec `2022071800000050`), but its ACCTFILE record is
  byte-identical to `golden/CBTRN02C/ACCTFILE.txt` (balance still `00000019458G`). Fixing this changes that
  golden scenario, which AC1 forbids. **Impact on CR-2:** if the last account is over limit, its fee tran is
  written but **the fee never reaches its balance**. The new scenario should include this case and record it as
  known behaviour.
- **R2: Hook placement.** `1400` is called once per non-zero-rate category (`:214-216`). Called as it is, the
  fee would be charged several times per account (it needs a per-account "fee charged" flag that is reset at
  the account break). It would also never be charged on accounts where every rate is 0 (e.g. group `ZEROAPR`,
  or category `030001`, which has a 0 rate).
- **R3: Which balance gets tested.** At the hook, `ACCT-CURR-BAL` is the balance as read, **before** this run's
  interest is added (interest is applied in `1050`). The intent uses that balance.
- **R4: Tran-id shift.** Fee trans take numbers from the shared `WS-TRANID-SUFFIX` (`:474`), so every tran id
  after a fee moves up by one. The golden runs are not affected because they have no fees.
- **R5: Data quirks** (from `legacy/README.md`). `ACCT-GROUP-ID` is blank in the sample data, so every rate
  falls back to `DEFAULT`. `trancatg.txt` uses CRLF. `trancatg.txt` is **not** read by CBACT04C or the harness
  (it is used by `CBTRN03C` / `TRANCATG.jcl`), so AC6 is a data check, not a harness check.
- **R6:** Accounts that have no TCATBAL record are never visited, so they are never charged, even when they
  are over limit.

## Questions for the approver

1. **Fee on accounts with no interest charged (all rates 0)?** The rule says "an account whose balance >
   limit is charged". **Default: yes.** Check the fee once per account visited, whatever the rate.
2. **Last-account defect (R1): fix now or leave it?** Fixing it breaks AC1 (the golden scenario would change).
   **Default: leave it**, document it, and raise a separate CR. The last over-limit account gets its fee tran,
   but its balance is not updated.
3. **Over-limit test uses the pre-interest balance (R3).** **Default: yes**, the balance as read at the start
   of the account.
