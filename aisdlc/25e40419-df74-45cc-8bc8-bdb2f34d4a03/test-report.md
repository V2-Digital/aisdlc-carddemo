# CR-2 Test report

**Result: green.** 0 fix iterations to the program. 2 script typos fixed (my decoder width, CRLF length check).

## Commands
```
legacy/harness/golden.sh --check          # AC1
bash legacy/tests/cr2_overlimit_fee.sh    # AC2-8, 21 checks, exit 0
```
No Maven project is touched (COBOL-only change).

## AC1: golden
`golden.sh --check`: OK. All 3 scenarios are byte-identical (CBACT04C 50 TRANSACT + 50 ACCTFILE, CBACT04C-after-CBTRN02C, CBTRN02C), and the two runs match each other.

## New scenario `legacy/tests/cr2_overlimit_fee/`
Input = `golden/CBTRN02C` ACCTFILE/TCATBALF, hand-edited:

| Acct | Setup | Result |
|---|---|---|
| 1 | balance 25,000.00 > limit 20,200.00; 3 TCATBAL records, 2 with non-zero rate (added `01/0002`) | 1 fee, 2 interest records |
| 2 | over limit, 1 interest category | 1 fee |
| 3 | balance **=** limit | no fee |
| 4 | over limit, only rate-0 `03/0001` left | 1 fee, no interest |
| 5 | over by 0.01 | 1 fee |
| 6 | within limit | no fee |

Output: RC 0, TRANSACT 54 records (50 baseline + 4 fees), ACCTFILE 50. Expected files in `expected/` were checked against the spec by hand (e.g. acct 1: 25,000.00 + 14.56 + 1.04 + 25.00 = `00000250406{`).

## What the script asserts
- RC 0, TRANSACT/ACCTFILE/RC byte-identical to `expected/`.
- Exactly 1 fee (`01`/`0006`, desc blank-padded) for accts 1, 2, 4, 5; 0 for accts 3 (equal) and 6; 4 fee records in total.
- Fee source `System`, amount `0000000250{`, card number = XREF card of that account.
- Tran ids unique and `2022071800` + 000001..N.
- Balance = old + Σ interest + 25.00 for accts 1-5 (decoded from overpunch).
- AC6: interest records (cols 17+) equal `expected/BASELINE_TRANSACT.txt`, produced by the `baseline` tag source (git worktree) on the same input. I saved it rather than rebuilding it in CI.
- `trancatg.txt`: 19 lines, `010006` record is 60 bytes + CRLF.

Negative check: corrupting `expected/RC.txt` made the script exit 1. It was then restored.

## Notes / not failing
- R1 kept as approved: the last account (50) is never rewritten, and no test account is in the last position.
- Fee ordering confirmed: the fee record comes before the account's interest records, so interest tran ids shift only in the new scenario.
- Not tested: an over-limit last account (preserved defect), an over-limit account without TCATBAL records (R7).
