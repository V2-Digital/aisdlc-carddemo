# CR-1 Test report — CBACT04C → Java 21

**Result: all green. 0 fix iterations needed.**

## Commands and results
| Command | Result |
|---|---|
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` (COBOL unchanged) |
| `legacy/tests/cbact04c-edge.sh --check` | PASS ×8 (COBOL re-run equals stored fixtures) |
| `cd modernized/cbact04c && mvn -q verify` | 16 tests, 0 failures (GoldenTest 2, CharacterizationTest 6, EdgeFixtureTest 8) |

## Equivalence against golden (byte compare)
| Scenario | Compared | Diffs |
|---|---|---|
| `CBACT04C` (stock) | TRANSACT 50 recs, ACCTFILE 50 recs, SYSOUT 152 lines, RC 0 | 0 |
| `CBACT04C-after-CBTRN02C` | TRANSACT 50, ACCTFILE 50, SYSOUT 302 lines, RC 0 | 0 |

## COBOL-generated edge fixtures (`legacy/tests/cbact04c-edge/<case>/{in,expected}`)
Produced by the real GnuCOBOL run, then Java is compared to them (TRANSACT, ACCTFILE, SYSOUT, RC; abend cases: no outputs + `CEE3ABD: ABEND U0999`).
| Case | Pins |
|---|---|
| `overflow` | F5: silent high-order truncation (S9(9)V99 and S9(10)V99) |
| `negzero` | sign written for a negative amount that truncates to zero |
| `negative`, `negrate` | truncation toward zero, `}`/`J-R` overpunch output |
| `empty` | empty TCATBALF: start/end only |
| `missing-acct`, `missing-xref`, `missing-default` | E1/E2/E3: RC 16, SYSOUT, no output files |

This settles the spec's open assumptions (overflow, -0) from COBOL facts. Java matched them without changes.

## Acceptance criteria coverage
- AC1 `mvn -q verify` passes; JUnit 5 is the only dependency.
- AC2/3/4 `GoldenTest.stock`, `.afterCbtrn02c`.
- AC5 `interestTruncatesTowardZero_acct2_29_24` (`0000000292D`).
- AC6 `defectF1_lastAccountNotRewritten_acct50` (acct 50 equals input; tran `…0050` exists; acct 49 is rewritten).
- AC7 F3 test (100 fallbacks, with both display lines); `missing-default` fixture for the abend.
- AC8 `rateZero_…` (50 trans from 100 records; cycle fields zeroed on non-last accounts).
- AC9 `overpunch_allTwentySignCharsRoundTrip`, plus negative fixtures.
- AC10 `missing-acct`, `missing-xref`.

## Left failing / gaps
- Nothing failing.
- F6 suffix wrap (1M+ transactions) and F7 duplicate XREF are not tested, since there is no practical fixture. They follow the spec only.
- `legacy/tests/cbact04c-edge.sh` has no top-level `--update` run in CI. Only `--check` runs.
- A negative amount that truncates to zero is written by COBOL as `{` (positive zero). Java matches (`negzero`).
- The spec put fixtures under `modernized/…/edge/`. I put them in `legacy/tests/cbact04c-edge/`, the plan's stated alternative, so CI finds the script and its fixtures together.
