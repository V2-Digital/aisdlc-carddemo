# CR-2 Test report

**Result: green, 0 fix iterations.** No Maven project touched (COBOL-only change), so no `mvn` run.

## Commands

| Command | Result |
|---|---|
| `legacy/harness/golden.sh --check` | OK. 3 scenarios byte-identical to `legacy/golden/`, RC 0, two runs identical (CBACT04C: 50 ACCTFILE, 50 TRANSACT, 152 SYSOUT; after-CBTRN02C: 50/50/302) |
| `bash legacy/tests/cr2_overlimit_fee.sh` | `PASS: cr2-overlimit`, exit 0 |

## New scenario `cr2-overlimit` (AC2-AC7)
- Runs twice under `INPUT_DIR=legacy/tests/cr2-overlimit/input`; both runs diffed against `expected/` (AC7).
- TRANSACT: baseline (unmodified program) 50 recs, modified 54 recs = +4 fee trans, one each for accounts 1, 2, 4, 50 (ids `…0001`, `…0004`, `…0007`, `…0053`). Account 1 has 2 non-zero-rate records, so it proves "once only". Account 4 (all rates 0) is still charged (Q1).
- Account 3 (balance = limit): no fee.
- ACCTFILE vs baseline: only accounts 1, 2, 4 differ, each by +25.00 (e.g. acct 4: 50000{ -> 50250{). Acct 50 (last) is unchanged: **R1 preserved**, fee tran written but not posted.
- The script's independent awk assertions check fee count, fields, `0000000250{`, balances, other accounts equal to baseline, and `trancatg.txt` (19 lines, `010006`).
- `trancatg.txt`: 19 lines; `git diff` shows exactly +1 line (`010006Over-limit Fee`).

## Left failing
Nothing. Known, deliberate behaviour (per approved spec): R1 last-account write-back, R6 accounts with no TCATBAL record skipped, test uses pre-interest balance.

## For a human
Raise a separate CR for the R1 defect; the last over-limit account is never billed the fee in its balance.
