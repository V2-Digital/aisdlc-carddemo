# CR-2 Intent: over-limit fee in the interest batch (`CBACT04C`)

## Problem / outcome

**Problem.** Card accounts can carry a balance above their credit limit, and nothing charges for it. The nightly interest job (`INTCALC` → `CBACT04C`) already has a fee hook, `1400-COMPUTE-FEES`, but it is empty, so no fee is ever raised.

**Outcome.** Each interest run charges **25.00** to every account whose current balance is above its credit limit, **once per account per run**. The fee is written as a normal system transaction next to the interest transactions (type `01`, category `0006`, "Over-limit fee for a/c …") and added to the account balance. Accounts within their limit see no change at all. The job stays in COBOL.

## What the legacy code does today

**Job:** `app/jcl/INTCALC.jcl`, `EXEC PGM=CBACT04C,PARM='2022071800'`. The PARM date becomes the prefix of each transaction ID.

**Files** (FDs at `app/cbl/CBACT04C.cbl:28-56`):

| DD | Org / access | LRECL | Key | Copybook | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | 50 | acct(11)+type(2)+cat(4) | `CVTRA01Y` | driver, read in key order (so grouped by account) |
| `XREFFILE` | KSDS, random via AIX acct id | 50 | card(16); alt acct(11) | `CVACT03Y` | card number for the transaction |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct(11) | `CVACT01Y` | read on account break, REWRITE with new balance |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4) | `CVTRA02Y` | interest rate (`DIS-INT-RATE`) |
| `TRANSACT` | sequential, OUTPUT | 350 | – | `CVTRA05Y` | new interest transactions |

Relevant account fields (`app/cpy/CVACT01Y.cpy`): `ACCT-CURR-BAL` S9(10)V99 at cols 13-24 and `ACCT-CREDIT-LIMIT` S9(10)V99 at cols 25-36.

**Processing steps**
1. Main loop over TCATBAL records (`CBACT04C.cbl:188-222`).
2. On an account break (`:194`), the previous account is rewritten (`1050-UPDATE-ACCOUNT`, `:196`). Then `WS-TOTAL-INT` is reset (`:200`), the account is read (`1100`, `:203`) and its xref/card is read (`1110`, `:205`).
3. Each category record looks up its rate in DISCGRP. A miss (status 23) falls back to group `DEFAULT` (`:415-440`).
4. If the rate is not zero (`:214`): `1300-COMPUTE-INTEREST` sets monthly interest = bal × rate / 1200, adds it to `WS-TOTAL-INT` and writes one transaction (`:462-515`). Then **`1400-COMPUTE-FEES` is performed, which does nothing** (`:216`, `:518-520`).
5. Transaction layout (`1300-B-WRITE-TX`, `:473-515`):
   - ID = PARM-DATE + `WS-TRANID-SUFFIX` 9(6). The suffix is a single run-wide counter, incremented per write (`:474`).
   - Type `01`, category `05` (→ `0005`), source `System`, desc `Int. for a/c <ACCT-ID>`.
   - Merchant zero/spaces, card from xref, both timestamps from the frozen clock.
6. `1050-UPDATE-ACCOUNT` (`:350-370`): `ACCT-CURR-BAL += WS-TOTAL-INT`, cycle credit/debit set to 0, REWRITE.

**Business rules today:** interest is charged per category balance, and only where the rate is non-zero. It is accumulated per account and posted once, at the account break. Nothing in the program reads the credit limit.

**Observed run** (`legacy/harness/run.sh CBACT04C`, stock data): RC 0. Output is byte-identical to `legacy/golden/CBACT04C` (50 TRANSACT, 50 ACCTFILE records). Every amount is zero because every stock TCATBAL balance is zero.

**Over-limit accounts in existing data:** none. I checked with an awk scan of cols 13-36:
- `acctdata.txt`: 0/50 (max balance 843.00)
- `golden/CBTRN02C/ACCTFILE.txt`: 0/50 (max 4212.54)
- `golden/CBACT04C-after-CBTRN02C/ACCTFILE.txt`: 0/50

**`trancatg.txt`:** 18 records, 61 bytes each, **CRLF** line endings. Layout: type(2)+cat(4)+desc(50)+`0000`. `CBACT04C` does not read it. It is read only by `CBTRN03C` and the `TRANCATG`/`TRANREPT` JCL, so adding a row cannot affect the golden outputs.

## Scope

**In**
- Implement `1400-COMPUTE-FEES` (or an equivalent per-account step) in `app/cbl/CBACT04C.cbl`: a fee of 25.00 when `ACCT-CURR-BAL > ACCT-CREDIT-LIMIT`, at most once per account per run.
- Write the fee transaction to `TRANSACT`: type `01`, cat `0006`, source `System`, desc `Over-limit fee for a/c <11-digit acct id>`, amount 25.00, card from xref, and the same ID scheme and timestamps as interest.
- Add the fee to `ACCT-CURR-BAL` in `1050-UPDATE-ACCOUNT`, alongside interest.
- Add `010006Over-limit Fee` to `app/data/ASCII/trancatg.txt` (61 bytes, CRLF).
- A new test scenario (input data + expected output + a check script) outside `legacy/golden/` and `legacy/harness/`, run with `INPUT_DIR`.

**Out**
- Any Java/modernized rewrite.
- Fixing the last-account write-back defect (see Risks, Q1).
- Configurable fee amount, fee waivers, a category-balance (TCATBAL) update for the fee, DISCGRP changes, and other fee types.
- Accounts with no TCATBAL record. The driver never visits them, so they get no interest and no fee.

## Acceptance criteria

1. `legacy/harness/golden.sh --check` passes. All 3 golden scenarios (`CBACT04C`, `CBTRN02C`, `CBACT04C-after-CBTRN02C`) are byte-identical, including SYSOUT and RC.
2. A new scenario has ≥2 over-limit accounts (balance > limit), and at least one of them has **multiple TCATBAL category records**. For it, `TRANSACT` contains **exactly one** fee record per over-limit account, and none for any other account.
3. Every fee record has:
   - type `01`, cat `0006`, source `System`
   - desc `Over-limit fee for a/c <acct id>`, space-padded
   - amount `0000000250{` (+25.00)
   - the account's xref card number
   - ID `<PARM-DATE><6-digit suffix>`, unique in the file
4. In the scenario's `ACCTFILE`, each over-limit account's balance = original balance + its interest + 25.00, and its cycle credit/debit are zero. Exception: an account hit by the last-account defect (Q1).
5. An account whose balance **equals** its limit is not charged (strictly greater). The scenario includes one such account.
6. An over-limit account is charged even when all its category rates are zero. The fee does not depend on interest.
7. Accounts within their limit get the same transactions and balances as without the change. The only exception is that TRANSACT IDs shift by one after each fee record, because the counter is shared.
8. `trancatg.txt` has a new line `010006` + `Over-limit Fee` padded to 50 + `0000`. It keeps CRLF and 61 bytes, and the existing 18 lines are untouched.
9. The program compiles under `cobc -fsign=EBCDIC -fassign-clause=ibm` with no new warnings, and the scenario run returns RC 0.

## Risks and findings

- **Hook placement would double-charge or miss fees.** `1400-COMPUTE-FEES` is performed **per category record**, and only **when the rate ≠ 0** (`CBACT04C.cbl:214-217`).
  - After CBTRN02C every account has 2 TCATBAL records (`golden/CBTRN02C/TCATBALF.txt`: 50 accounts × 2, categories `010001` and `030001`). Charging inside the hook as it stands would bill twice.
  - An account where every rate is zero (for example group `ZEROAPR`, or `DEFAULT` type 07) would never be billed.
  - The fee must be decided once, on the account break, using a per-account flag or step.
- **Defect: last account never rewritten** (confirmed in `legacy/README.md`, evidence in `golden/CBACT04C-after-CBTRN02C`).
  - After EOF, the `ELSE PERFORM 1050-UPDATE-ACCOUNT` at `:219-220` is unreachable, because `PERFORM UNTIL` tests before the body. In that run account `00000000050` gets an 18.77 interest transaction, but its balance stays 1945.87.
  - With this CR, an over-limit **last** account would get a fee transaction, but its balance would not be updated. Fixing the defect changes `golden/CBACT04C-after-CBTRN02C/ACCTFILE.txt`, which breaks AC1. See Q1.
- **Transaction-ID shift:** fees use the same `WS-TRANID-SUFFIX` counter, so later interest records get new IDs. This is harmless for golden (no fees there), but downstream reconciliation by ID will see different numbering on fee nights.
- **Balance basis:** the comparison uses `ACCT-CURR-BAL` as read at the account break, before this run's interest and fee. This matches the CR wording. An account pushed over the limit by tonight's interest is charged on the next run.
- **Uninitialised `TRAN-RECORD`:** the tail of `TRAN-DESC` keeps the previous record's bytes beyond the STRING (README "Known quirks"). The fee description (34 chars) is longer than the interest one (24 chars), so the interest record **after** a fee could carry fee text in bytes 25-34 of its description. The design must clear `TRAN-DESC` (or space-fill it) before the STRING. Doing that for every record does not change golden, because the field is spaces there today.
- **Test data constraint:** golden inputs can't be changed. The new scenario supplies its own `ACCTFILE.txt` (and optionally `TCATBALF.txt`) through `INPUT_DIR`, built from `golden/CBTRN02C` outputs so interest is non-zero.

## Questions for the approver

1. **Fix the last-account write-back defect in this CR?** **Default: no.** Fixing it changes a golden output, which the CR forbids. Keep the over-limit test accounts away from the last account, document the defect, and raise a separate CR.
2. **Should inactive accounts (`ACCT-ACTIVE-STATUS` ≠ `Y`) be charged?** The CR doesn't say. **Default: yes, charge them.** Apply the rule exactly as written. No stock account is inactive, so this has no effect today.
3. **Should the fee be recorded in TCATBAL (category balance `01/0006`)?** Interest isn't recorded there either. **Default: no**, matching how interest is handled.
