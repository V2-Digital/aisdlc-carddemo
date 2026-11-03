# CR-1 Test report: `CBACT04C` → Java 21

**Result: all green.** 26 JUnit tests pass; both golden scenarios are byte-identical. 2 fix iterations, both in test code (none in production code).

## Commands and results

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | pass, 26 tests, 0 failures. JUnit 5 is the only dependency. |
| `java -jar target/cbact04c.jar --data ../../app/data/ASCII --out /tmp/j1 --parm 2022071800 --clock 2022071800000000` then `diff -r /tmp/j1 legacy/golden/CBACT04C` | rc 0, **no diff**: TRANSACT 50, ACCTFILE 50, SYSOUT 152, RC |
| Same with `--input-dir legacy/golden/CBTRN02C`, `diff -r` against `legacy/golden/CBACT04C-after-CBTRN02C` | rc 0, **no diff**: TRANSACT 50, ACCTFILE 50, SYSOUT 302, RC |
| `legacy/harness/golden.sh --check` | OK, outputs match golden, two runs byte-identical |
| `git diff --stat baseline -- legacy .github app` | empty |

## Tests by acceptance criterion

| Test class (tests) | AC | What it proves |
|---|---|---|
| `GoldenTest` (3) | 2, 3, 9 | Byte compare of TRANSACT, ACCTFILE, SYSOUT and RC for both scenarios (4 files × 2). Also run through the CLI entry point. Line counts 50/50/152 and 302. |
| `InterestTest` (4) | 4 | 0.40 × 15% → `0000000000{` (half-up would be 0.01). -0.40 → `{`, not `}`. 1945.87 → 24.32. D7: -1.00 → -0.01 (`J`). |
| `DefectTest` (6) | 5 | **D1** on golden data: account 50 is byte-identical in and out, 49 is changed, and the account 50 transaction is `2022071800000050` / `0000000187G`. **D1** again on a synthetic 2-account case. **D5** timestamp from the clock. **D6** zero-amount transaction. **D8** first card used. **D9** "DALY REJECTS FILE" text. |
| `DiscGrpTest` (3) | 6 | Group present → used and no fallback lines. Group missing → DEFAULT plus the 2 SYSOUT lines. DEFAULT missing → rc 16, `NNNN0023`, only SYSOUT and RC written. |
| `AbendTest` (3) | 7 | Missing account or xref → rc 16 with the exact 4 SYSOUT lines. CLI returns 16. |
| `RateZeroTest` (2) | 8 | No transaction, balance unchanged, cycle fields still zeroed on rewrite, tran-ID suffix not consumed. |
| `ZonedCodecTest` (5) | R1 | All 20 sign characters, plain digits, round trip, -0 → `{`, high-order overflow truncation. |

## Iterations

1. First run: 17 errors and 1 failure, all in my test code. The fixture helper did not create the input dir, and one overflow expectation was miscounted by a digit. Fixed.
2. Second run: green.

No production code changed in the Test stage.

## Not covered or left open

- No `legacy/tests/*.sh` was added. The plan says none is needed, since there are no COBOL changes.
- Not tested: tran-ID suffix wrap at 10^6 (D7). The code uses `% 1_000_000`, but a 1M-row fixture was not built.
- Not tested: the open-error abend for the files other than DISCGRP, and the `--clock` default (system UTC).
- Equivalence is proven only on the two golden scenarios. D2, D3 and D7 need business decisions (see intent Q3).
- Nothing is failing.
