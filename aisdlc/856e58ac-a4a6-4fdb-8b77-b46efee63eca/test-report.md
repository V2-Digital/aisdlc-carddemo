# Test report — CR-1 (`CBACT04C` → Java 21)

**Result: green. 25 JUnit tests pass, golden check passes, COBOL test passes. 0 fix iterations on production code.**

## Commands and results
| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | 25 tests, 0 failures, builds `target/cbact04c.jar` |
| `legacy/harness/golden.sh --check` | OK: outputs match `legacy/golden/` (legacy unchanged) |
| `bash legacy/tests/cbact04c-default-missing.sh` | PASS |
| CLI smoke: `CARDDEMO_CURRENT_DATE=2022071800000000 java -jar … 2022071800` on stock data | RC 0; TRANSACT, ACCTFILE and SYSOUT `cmp`-identical to `golden/CBACT04C/` |

## Equivalence (AC 2, 3, 9)
`GoldenTest` byte-compares TRANSACT, ACCTFILE, SYSOUT and RC.
- `stock` → `golden/CBACT04C`: 50 TRANSACT + 50 ACCTFILE records, 152 SYSOUT lines, RC 0. **No diffs.**
- `afterCbtrn02c` → `golden/CBACT04C-after-CBTRN02C`: 100 TCATBALF records in, 50 TRANSACT, 50 ACCTFILE, SYSOUT, RC. **No diffs.**

## Tests added (this stage)
| Class (tests) | Covers |
|---|---|
| `ZonedTest` (4) | overpunch round-trip, plain digits, negative zero → `{`, truncation of high digits |
| `InterestTest` (4) | AC4 acct 2: 2339.97 × 15.00 → 29.24; negative sub-cent → 0.00; negative keeps sign; monthly overflow wraps (D5) |
| `RulesTest` (7) | zero rate (AC6); group hit with no MISSING lines (D3 path); DEFAULT fallback; missing DEFAULT / account / xref → RC 16, ABENDING PROGRAM, no END line (AC7); multi-category accumulation |
| `DefectTest` (4) | **D1** last account not rewritten, on real data (acct 50: tx `2022071800000050` = 18.77, balance 1945.87, cycle 1501.75 / −47.88 unchanged; AC5) and synthetic; **D2** no fee transactions; **D5** balance overflow wraps |
| `RecordTest` (4) | TRAN-ID sequence, desc, filler, timestamps (AC8); short PARM padding; hundredths; suffix wraps after 999999 (1,000,001 rows; about 8 s) |
| `GoldenTest` (2) | existing, from Build |

COBOL side: `legacy/tests/cbact04c-default-missing.sh` (fixture `legacy/tests/cbact04c-default-missing/DISCGRP.txt` = stock DISCGRP minus `DEFAULT` rows). It asserts RC 16, the `ERROR READING DEFAULT DISCLOSURE GROUP` text and `ABENDING PROGRAM`, and no END line. **The real COBOL output matches the abend text in the spec's Rule 4**, so the spec needs no change.

## Iterations
The production code needed no change. The first test runs failed because of fixture mistakes in my new tests, not Java bugs: wrong rate literals (`01200{` is 120.00, not 12.00), a wrong amount column and wrong expected counts. I fixed the tests. Zero of the 5 allowed fix iterations were spent on `src/main`.

## Not covered / for a human
- The negative-interest sign case (`}` vs `{`) is checked against my reading of the spec, not run through COBOL. The golden data has no negative interest.
- Missing-input-file path (status `0035`, not defined in the spec) and the D4 text are untested against COBOL.
- Group-hit (D3 non-default path) is covered only by synthetic Java tests, because the golden data cannot exercise it.
- Defects D1 and D2 remain preserved, as intended. Raise D1 as its own CR (see intent Q3).
- `target/` is untracked build output; make sure it is not committed.
