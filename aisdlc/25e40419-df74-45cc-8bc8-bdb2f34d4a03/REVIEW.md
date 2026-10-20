# CR-2 Review — over-limit fee in `CBACT04C`

**Verdict: ready to merge.** The code follows spec.md rule by rule. Golden is byte-identical. The new scenario proves one fee per over-limit account, and I re-derived its baseline expectation myself from the `baseline` tag.

## What I ran

| Check | Result |
|---|---|
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty: protected paths untouched |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` (3 scenarios, two runs identical) |
| `bash legacy/tests/cr2_overlimit_fee.sh` | 21 × `ok`, exit 0 |
| Built `baseline` in a `git worktree` and ran it on `cr2_overlimit_fee/input` | RC 0. TRANSACT is **byte-identical** to `expected/BASELINE_TRANSACT.txt`, so the AC6 reference is real and was not regenerated from the new code |
| Baseline vs new ACCTFILE, same input | differs only for accts 1, 2, 4, 5, by exactly +25.00 each (e.g. `…250156{` → `…250406{`) |
| New trancatg line | `010006Over-limit Fee…0000^M$`: 60 bytes + CRLF, 19 lines |

## Correctness against spec

- Rule 1: `1400` is now called only at the account break, after `1110` (`app/cbl/CBACT04C.cbl:208-209`). The per-category call is gone (`:217-219`), so the fee is charged once per account by construction (R2), and it no longer depends on the interest rate (R3/Q1).
- Rule 2: strict `>` on the balance as read, before interest (`:524`).
- Rules 3/4: `MOVE SPACES TO TRAN-DESC` in both writers (`:488`, `:542`). This fixes the latent tail-bleed problem (R4).
- Rule 5: `ADD WS-TOTAL-INT WS-TOTAL-FEES TO ACCT-CURR-BAL` (`:355`).
- Rule 6: the fee record comes before the account's interest records (expected TRANSACT lines 1, 4, 7, 8).
- Money: `S9(09)V99 VALUE 25.00`, the same PIC as `TRAN-AMT`. No truncation risk, and it is stored as `0000000250{`.
- Test data covers each edge case: acct 1 has 2 non-zero-rate categories (AC3). Acct 3 has balance = limit and gets no fee (AC5). Acct 4 has only the rate-0 `03/0001` category and still gets the fee (Q1). Acct 5 is over by 0.01 (good boundary). The last acct, 50, is within its limit (R1).

## Findings

1. **should-fix** `legacy/tests/cr2_overlimit_fee.sh:43`: `ok "fee source/amount/card bytes checked"` always prints, even when the loop above it called `fail`. The exit code is still correct, but the log can say `ok` right after a `FAIL`. Suggestion: count failures inside the loop and print `ok` only when there were none.
2. **nit** `legacy/tests/cr2_overlimit_fee.sh:49-54`: the negative-number branch of `dec()` (`}`/`J-R`) is never exercised, because every test balance is positive. That is fine for now. Either add a one-line self-check (`dec 00000000001J` → `-11`) or drop the branch.
3. **nit** `legacy/tests/cr2_overlimit_fee.sh` has file mode `100644`. CI calls it with `bash`, so this works. Making it `chmod +x` would match the usage line in plan.md.
4. **nit** `CBACT04C.cbl:530-572`: `1400-B-WRITE-FEE-TX` copies about 40 lines from `1300-B-WRITE-TX`. This is acceptable for a minimal COBOL change. A shared write paragraph would be a later clean-up, not a job for this CR.

There are no blockers.

## What a human should look at before merging

- **R1 is still in place (an approved decision, but it matters for money).** If the last account in TCATBAL is over its limit, the fee transaction is written to SYSTRAN but never added to that account's balance. The same already happens with interest. Raise the follow-up CR that fixes it and regenerates golden.
- **Tran-id shift (R6).** Interest ids after the first fee move up by one per fee. Confirm that nothing downstream relies on interest ids being contiguous.
- **TCATBAL `01/0006` is not updated (R8).** If the posting job or statements reconcile against category balances, the fee shows only in TRANSACT and ACCTFILE.
- Check that the 25.00 fixed constant and the "pre-interest balance, strict >" rule are what Product signed off on (approved defaults Q1 and Q2).
