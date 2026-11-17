# CR-2 Review — Over-limit fee in CBACT04C

**Verdict: ready to merge.** The change follows spec rules 1–7 exactly. Golden outputs are byte-identical. The new scenario proves once-per-account fees with checks that do not depend on the committed expectations. Only nits remain.

## Evidence (run by reviewer)

| Check | Result |
|---|---|
| `legacy/harness/golden.sh --check` | OK: 3 scenarios match `legacy/golden/`, two runs byte-identical |
| `legacy/tests/cr2_overlimit.sh` | 24/24 PASS, exit 0 |
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty (untouched) |
| CI (`.github/workflows/ci.yml:36-39`) | already runs `legacy/tests/*.sh`, so the new script is picked up |

## Correctness vs spec

- **Spec rule 1 (fee once per account):** the fee is reset and evaluated at the account break (`CBACT04C.cbl:203`, `:209`). The per-category call inside `IF DIS-INT-RATE NOT = 0` is removed (`:217-219`). So the code structure, not a flag, guarantees one fee per account (F1). It also does not depend on the rate (Q2).
- **Spec rule 3 (strict compare):** the signed compare `ACCT-CURR-BAL > ACCT-CREDIT-LIMIT` (`:524`) runs on the balance as read, before interest is added (Q3).
- **Spec rule 4 (fee record):**
  - `TRAN-DESC` is cleared before the STRING and again after the WRITE (`:542`, `:573`), which covers the leftover-text risk R3.
  - Amount, category, source and card number match the spec table.
  - `TRAN-ID` uses the shared suffix.
- **Spec rule 5 (balance):** `ADD WS-TOTAL-FEES TO ACCT-CURR-BAL` (`:356`). It adds zero for accounts within their limit.
- **Money:** 25.00 is a `S9(09)V99` constant moved into a `S9(09)V99` `TRAN-AMT`, so nothing is truncated. On output it is `0000000250{` (+25.00 overpunch), which I confirmed in `expected/TRANSACT.txt` line 1.
- **No new `DISPLAY`:** the golden SYSOUT is unchanged. The scenario SYSOUT has 298 lines, not 302. The difference is that account 4 is now in `ZEROAPR`, which has no group fallback. This is explained in `test-report.md`.

## Do the tests really prove it?

- **Inputs:** the test input differs from `golden/CBTRN02C/ACCTFILE.txt` only in the edited limits (accounts 1–4) and account 4's group. I diffed this myself. `TCATBALF` is identical to golden.
- **Expected values come from golden, not from the new output:**
  - Interest rows are compared with **golden** scenario 3, minus account 4.
  - Balances for accounts 1 and 2 are checked as golden post-interest + 2500 cents.
  - Accounts 3 and 5–49 are byte-compared with golden, outside the edited limit field.
- **Fee records:** the fee count, accounts, uniqueness and contiguous IDs 1..52 are all asserted directly. The comparison with `expected/` is a regression lock on top of those checks, not the proof.

## Findings

1. **nit** — `legacy/tests/cr2_overlimit.sh:36`: `strip()` is defined and never used, and its `cut` ranges are confusing. Delete it.
2. **nit** — `legacy/tests/cr2_overlimit.sh:25-31`: only account 1's fee record is checked field by field. Accounts 2 and 4 (card numbers `0923877193247330`, `2988091353094312`) are covered only by `expected/`. Suggestion: loop the field check over accounts 1, 2 and 4, and assert `TRAN-CARD-NUM` (offset 262) against `XREFFILE`.
3. **nit** — `app/cbl/CBACT04C.cbl:171`: `WS-OVERLIMIT-FEE` has an extra space before `PIC`, so it is out of alignment with its neighbours.
4. **nit** — `app/cbl/CBACT04C.cbl` new lines (`:170-171`, `:203`, `:209`, `:356`, `:522-574`): they are not space-padded to 80 columns like the rest of the file. This does not affect compilation (max 72 columns, checked). Pad them for consistency if the team cares about diffs in the sequence area.
5. **nit** — `app/cbl/CBACT04C.cbl:356`: like the interest `ADD`, there is no `ON SIZE ERROR`. This keeps the existing pattern and does not matter at these magnitudes. No change suggested for this CR.

## What a human should look at before merging

- **Last-account defect (F3), kept by design (Q1).** If the last account in `TCATBALF` is over its limit, its fee transaction is written but its balance is **not** increased. With production data this produces a transaction that is never posted. Please confirm this is acceptable until the separate CR that fixes it is raised and scheduled.
- **Transaction IDs shift when fees are charged (R1).** Interest `TRAN-ID`s after the first fee move up by one. Check that downstream consumers (CBTRN03C report, any reconciliation) do not depend on interest IDs staying the same between runs.
- **`trancatg` is data only.** The VSAM category file is not reloaded by this change (out of scope), so `010006` will not appear in the CBTRN03C report until `TRANCATG.jcl` is run.
