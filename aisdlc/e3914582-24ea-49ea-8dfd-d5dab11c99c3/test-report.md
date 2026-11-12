# CR-2 Test Report — Over-limit fee in CBACT04C

**Result: all green. 0 fix iterations needed.** Nothing left failing.

## Commands and results

| Command | Result |
|---|---|
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden`; two runs byte-identical (AC1). Covers CBACT04C, CBACT04C-after-CBTRN02C, CBTRN02C (e.g. TRANSACT 50 records, SYSOUT 302 lines, RC 0) |
| `bash legacy/tests/cr2_overlimit.sh` | 16 checks ok, exit 0 |
| Baseline vs new program on the CR-2 scenario input | see below |

No Maven project touched (COBOL-only change), so `mvn test` was not run.

## New scenario `legacy/tests/cr2_overlimit.sh`

Input: 50 accounts, 6 TCATBAL records. Output: 8 TRANSACT records, 50 ACCTFILE records, RC 0.

- Expected files (TRANSACT, ACCTFILE, RC) are byte-identical to the run output.
- Accounts 01, 04 and 05 (over limit) each get exactly 1 record with cat `0006`, source `System`,
  description `Over-limit fee for a/c <id>` and amount `0000000250{` (AC2).
- Account 01 has 2 TCATBAL records and gets one fee (AC3). Its balance is `00000010525{` = in + interest + 25.00 (AC4).
- Account 02 (balance = limit) and 03 (below) get no fee (AC5).
- TRAN-ID suffixes are 1..8 with no gaps (AC6).
- Account 04 (all rate 0) still gets the fee (Q1). Account 05 (last in TCATBAL) gets a fee record, but its ACCTFILE
  row is not rewritten (F3 preserved, asserted).
- `trancatg.txt`: 19 lines, line 6 is `010006Over-limit Fee` + padding + `0000` (AC7).

## AC5 / AC8 evidence: baseline program vs new, same scenario input

Baseline run from `git archive baseline`, with `INPUT_DIR` set to the scenario input.

- Both runs end with RC 0. SYSOUT is identical.
- Compiler/harness stderr has 0 warnings in both runs. The only diff is the output path and the TRANSACT count line
  (5 baseline vs 8 new, the 3 fees).
- Accounts 02 and 03: ACCTFILE rows byte-equal to baseline.
- Accounts 02 and 03: interest records are equal to baseline except TRAN-ID (the IDs shift by one or more because of
  fee records written earlier, F8).

## Observations

- Fee records come before the same account's interest records (spec rule 7), as designed.
- Nothing for a human to decide. F3 (last account never rewritten) and F4 remain deliberately unfixed, as approved.
