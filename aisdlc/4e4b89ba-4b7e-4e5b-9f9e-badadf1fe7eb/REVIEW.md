# CR-2 Review: over-limit fee in CBACT04C

**Verdict: ready to merge.** The code does what spec R1–R7 says. Golden output is byte-identical, the new test checks hard-coded values rather than regenerated ones, and none of the protected paths were touched. The findings below are should-fix or smaller.

## Evidence (re-run by the reviewer)
| Check | Result |
|---|---|
| `legacy/harness/golden.sh --check` | OK. 3 scenarios match, and two runs are byte-identical |
| `bash legacy/tests/cr2_overlimit_fee.sh` | 24 PASS, 0 FAIL, exit 0 |
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty (untouched) |
| CI hook | `.github/workflows/ci.yml:39` runs `legacy/tests/*.sh`, so the new test gets picked up |

## Spec conformance
- R1: the fee runs once per account break (`CBACT04C.cbl:208`), and the call inside the rate branch was removed (`:218-220`). So the fee is charged at most once and does not depend on the interest rate. ✔
- R2: the strict signed comparison `ACCT-CURR-BAL > ACCT-CREDIT-LIMIT` runs right after the account is read, before any interest is added (`:523-528`). ✔
- R3/R4: the fee record copies the interest write. `TRAN-DESC` is blanked before both STRINGs, which fixes F3 (`:489`, `:544`). ✔
- R5: `ADD WS-TOTAL-FEE` in `1050-UPDATE-ACCOUNT` (`:356`), and the fee total is reset at the break (`:203`). ✔
- R7: no new DISPLAY on the normal path. ✔
- `trancatg.txt`: 19 records, the new line comes after `010005`, it is 60 bytes + CRLF, and the test asserts this. ✔

## Findings
1. **should-fix: one timestamp assertion can fail at random.** `legacy/tests/cr2_overlimit_fee.sh:35` requires the fee record's timestamp to equal the next interest record's timestamp. Both come from separate `FUNCTION CURRENT-DATE` calls at 1/100 s precision, so the test fails whenever the clock ticks between the two writes. The spec only says "set the same way". *Fix:* check that the timestamp has the right format, for example with the regex `^[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]{2}\.[0-9]{2}\.[0-9]{2}\.[0-9]{2}0000$` on both TS fields, or that ORIG-TS equals PROC-TS within the same record.
2. **should-fix: the test does not stop when the run fails.** `cr2_overlimit_fee.sh:5` uses `set -uo pipefail`, but the plan said `-euo`. All `run.sh` output also goes to `/dev/null`. If the build fails, the script still reaches the RC check and fails there, but nothing tells you why. *Fix:* on failure, print `$OUT/SYSOUT.txt`, or stop sending stderr to `/dev/null`.
3. **nit: no fixture for a negative balance or a zero limit.** The spec's edge-case table lists "negative balance / zero limit", but no fixture covers it. COBOL compares signed numbers correctly, so the risk is low. *Fix (optional):* add a fixture account with a negative balance below its limit to prove it is not charged.
4. **nit: 80-column padding.** The new and changed lines in `CBACT04C.cbl` (e.g. `:169-171`, `:202-209`, `:523-573`) are not space-padded to 80 columns like the original lines. It compiles, but some mainframe transfer tools expect fixed-length lines. *Fix:* pad them to 80 columns, or confirm this doesn't matter for this repo.
5. **nit: duplicated write and error handling.** `1400-B-WRITE-FEE-TX` (`:531-573`) repeats the WRITE, status check and abend block from `1300-B-WRITE-TX`. That is acceptable as the spec says "mirrors". A shared `1350-WRITE-TRAN` paragraph would remove about 15 lines, but it would also change the existing paragraph, so leaving it as is is fine.
6. **nit:** `MOVE 0006 TO TRAN-CAT-CD` (`:541`) is correct for `9(04)`, but the interest paragraph's alphanumeric `'05'` style differs from this. Cosmetic only.

## What a human should look at before merging
- **F4 money gap (approved, but matters in production).** If the *last* account in `TCATBALF` is over its limit, its 25.00 fee is written to TRANSACT but never added to its ACCTFILE balance. Interest on that account is already lost the same way today. Decide whether a follow-up CR to fix the last-account rewrite (with a golden refresh) should be scheduled now.
- **Q3 (approved default):** inactive accounts are charged. Product should confirm.
- **Downstream ID shift:** each fee moves all later TRAN-IDs up by one in that run. Check whether any reconciliation or report depends on interest IDs lining up with account order.
- Finding 1: decide whether to fix the test now or accept the small risk that it fails at random in CI.
