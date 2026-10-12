# CR-1 Test report: CBACT04C to Java 21

Run `dry5-10121214` · Stage: Test · **Result: all green, 1 fix iteration (of 5)**

## Commands and results

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q test` | **22 tests, 0 failures** (GoldenTest 2, ZonedTest 6, CharacterizationTest 9, AbendTest 5) |
| `mvn -q package -DskipTests` + jar smoke on both scenarios, `diff -r` vs golden | both identical (TRANSACT, ACCTFILE, SYSOUT, RC) |
| `legacy/harness/golden.sh --check` | OK, COBOL side unchanged, two runs byte-identical |

## Equivalence (byte-identical)

| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` | 50 / 50 records, 0 diffs | 50 / 50, 0 diffs | 152 lines, 0 diffs | 0 |
| `CBACT04C-after-CBTRN02C` | 50 / 50, 0 diffs | 50 / 50, 0 diffs | 302 lines, 0 diffs | 0 |

Compared in-process by `GoldenTest` and end-to-end through the CLI jar (`CARDDEMO_CURRENT_DATE=2022071800000000`, PARM `2022071800`).

## Acceptance criteria

| AC | Evidence |
|---|---|
| 1 | `mvn test` green on Java 21; deps are JUnit 5 + Surefire only |
| 2, 3 | `GoldenTest.stock`, `GoldenTest.afterCbtrn02c` |
| 4 | `interestTruncatesTowardZero` (P2: `3333249999A`, `0000000000Q`, `0000001032M`; asserts HALF_UP differs from DOWN), `tinyNegativeWritesZeroAmountTran` (P3) |
| 5 | `lastAccountNotRewritten` (synthetic) and `lastAccountNotRewrittenGolden`: acct 50 identical to `golden/CBTRN02C`, tran `...0050` exists, 49 of 50 accounts changed |
| 6 | `defaultFallbackAndZeroRate`: MISSING/TRY lines, rate 0 gives no tran, cycle fields `00000000000{`, group rate wins when present |
| 7 | `AbendTest`: P1, P4, P5, missing file, invalid input. RC 16, SYSOUT tail as in spec §4, no outputs. `openErrorMessages` pins all four open texts |
| 8 | `ZonedTest`: sign tables, zero gives `{`, widths, rejects non-numeric |
| 9 | SYSOUT and RC compared with golden in `GoldenTest` |

Other pins: F4 `timestampFromClockNotParm`, F5 `overflowTruncatesHighOrder` (P6 `00000000012D`, short PARM padding), F7 `openErrorMessages`, plus `unsortedTcatbalIsSorted`.

## Iterations

1. `defaultFallbackAndZeroRate` failed. My test expectation was wrong (the fixture legitimately yields 2 trans and 6 MISSING/TRY lines). The test was corrected; **no production code changed**.

## Not covered / notes

- The jar's exit-2 usage path and the system-clock default are not unit-tested.
- Suffix wrap past 999999 and multi-card XREF order (R3) are untested, as planned.
- Test expectations for synthetic cases come from the spec's COBOL probes P1–P6 (not re-run in this stage). Only the golden scenarios were re-run against the COBOL harness.
- Nothing is left failing. The preserved defects F1–F7 still need a separate decision.
