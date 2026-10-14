# CR-2 Review: over-limit fee in CBACT04C

**Verdict: ready to merge.** The change matches spec rules 1–6 line for line. Golden is byte-identical. The new scenario proves exactly one fee per over-limit account, and I checked its expected files against the real baseline program myself.

## What I ran

| Check | Result |
|---|---|
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty, so the protected paths are untouched |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden`, two runs byte-identical |
| `legacy/tests/cr2_overlimit.sh` | 18/18 PASS, exit 0 |
| Ran the `baseline`-tag program (separate worktree) on the CR-2 inputs and compared with the checked-in `baseline_TRANSACT.txt` (cols 1-262) and `baseline_ACCTFILE.txt` | identical, RC 0. The expectations come from the old program's real output, not from the new code. |
| Looked at the fee output | `2022071800000001 01 0006 System "Over-limit fee for a/c 00000000001"…0000000250{`. The fee record comes before that account's interest records, and the IDs run 000001..000054 with no gaps. |

Fixtures: accounts 1, 2 and 4 are over limit (500.00 > 202.00, 700.00 > 613.00, 4000.00 > 350.30). Account 3 has balance = limit (490.90). Account 5 is within limit and has interest. Account 1 has 2 TCATBAL records (`0001` and `0002`), in key order. The last account (50) is within limit.

## Findings

1. **nit** `app/cbl/CBACT04C.cbl:531-574`: `1400-B-WRITE-FEE-TX` copies about 40 lines of `1300-B-WRITE-TX`. The spec asks for this and it keeps the interest path untouched, so it is fine for this CR. A later clean-up could pull out a shared write paragraph that takes cat, description and amount as parameters.
2. **nit** `app/cbl/CBACT04C.cbl:526-527`: `MOVE WS-FEE-AMT TO WS-TOTAL-FEE` is correct because the paragraph runs once per account. If a second fee type is ever added, `ADD` would be safer. No change needed now.
3. **nit** `legacy/tests/cr2_overlimit.sh:56`: the cycle credit/debit = 0 check only covers account 1. Accounts 2 and 4 are covered indirectly, because the script compares `substr($0,37)` with the baseline, and the baseline zeroes those fields too. Suggestion: loop the check over 1, 2 and 4 so it is explicit.
4. **nit** No fixture has a negative balance or a negative limit. The compare is a plain signed `>` on `S9(10)V99`, so the risk is low. A credit-balance account (`}`/`J`-`R` overpunch) would cover the sign edge case from the spec.
5. **nit** `legacy/tests/cr2_overlimit.sh:72`: AC7 depends on the `baseline` git tag. This is fine in this repo, but it fails in a shallow CI clone without tags.

No blockers. No should-fix items.

## Money, truncation, sign
- The fee `S9(09)V99 VALUE 25.00` is written as `0000000250{`, which is correct.
- `ACCT-CURR-BAL` is `S9(10)V99`, so adding 25.00 to it cannot overflow in practice.
- `MOVE SPACES TO TRAN-DESC` in both write paragraphs fixes F4. The test checks that the 66-byte tail of the fee description is spaces. Interest records still match the baseline, so this clearing has no visible effect on interest output.

## What a human should look at before merging
- **F1 (preserved, not tested):** if an over-limit account is the last one processed, it gets a fee *transaction* but its balance is never updated. The fixture avoids this case on purpose. Put the follow-up CR to fix F1 (golden re-baseline) on the backlog before this runs in production.
- **F3 (preserved):** every fee charged earlier in the run shifts later interest TRAN-IDs by one. Check that no downstream consumer depends on interest IDs being stable between runs.
- **Business sign-off:** the fee is checked against the balance before interest, and it is charged even when the rate is zero (approver defaults Q1/Q2). The amount 25.00 is hard-coded.
- `trancatg.txt` is reference data. The new `010006` line only reaches the reports after `TRANCATG.jcl` is reloaded.
