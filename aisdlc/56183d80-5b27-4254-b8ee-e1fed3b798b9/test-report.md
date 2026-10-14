# CR-1 Test report

**Result: green.** 22 JUnit tests pass, the golden equivalence script passes 8/8, and the legacy golden check is unchanged.

## Commands and results

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | 22 tests, 0 failures (`GoldenEquivalenceTest` 3, `ProgramTest` 16, `ZonedDecimalTest` 3) |
| `legacy/tests/cbact04c_java_equiv.sh` | 8/8 `cmp` OK: `TRANSACT`, `ACCTFILE`, `SYSOUT` and `RC` for both scenarios |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden/` |

## Evidence by acceptance criterion

| AC | Test | Records compared |
|---|---|---|
| 2 | `GoldenEquivalenceTest.stock` | `TRANSACT` 50, `ACCTFILE` 50, `SYSOUT` 152 lines, `RC`: byte-equal, 0 diffs |
| 3 | `GoldenEquivalenceTest.afterCbtrn02c` | `TRANSACT` 50, `ACCTFILE` 50, `SYSOUT` 302 lines, `RC`: byte-equal, 0 diffs |
| 4 (D1) | `lastAccountOfGoldenRunIsUnchanged_D1` (golden data), `lastAccountNotRewritten_D1` (synthetic) | Account 50 in = out. Transaction `2022071800000050` is present |
| 5 (D2) | `truncatesNotRounds`, `truncatesBelowHalfCent`, `truncatesThirdDecimalAtLeastFive`, `negativeBalanceTruncatesTowardZero` | 1.3325→1.33; 0.0125→0.01; 0.015→0.01; −0.015→−0.01 |
| 6 | `zeroRateSkipped`, `defaultFallbackPrintsMessages`, `specificGroupUsedWhenPresent` | The two fallback SYSOUT lines are asserted |
| 7 | `transactLayout` | 350-byte record; desc tail and FILLER are spaces; timestamps `2022-07-18-00.00.00.000000` |
| 8 | `missingAccountAbends`, `missingXrefAbends`, `missingDefaultGroupAbends` | Exit 16, `ABENDING PROGRAM` |
| 9 | SYSOUT and RC asserts in the AC2/AC3 tests | — |
| D7 | `missingDiscgrpFileReportsLegacyWrongMessage_D7` | Prints `ERROR OPENING DALY REJECTS FILE` |
| — | `ZonedDecimalTest` | All 20 overpunch characters round-trip. `-0` encodes as `{`. High-order digits are truncated |

Also covered: an account change rewrites the previous account (balance plus interest, cycle fields zeroed), the TRAN-ID suffix increments per transaction, and an empty TCATBALF writes nothing.

## Iterations

One fix iteration, after the first test run failed (4 failures). All four were bugs in the new tests (wrong fixture field widths, wrong expected values). No production code changed in this stage. A second run failed once on my own wrong expected amount and passed after the test was corrected.

## Not covered / open

- The D6 wrap at 1,000,000 transactions is not tested (it would need a very large input). The code uses `% 1_000_000`.
- R2, the duplicate-xref first-card rule, is not tested, because the golden data has none.
- Abend persistence of partial outputs is best effort. The golden data has no abend scenario.
- Open-failure status text is a fixed `0035`.
- `legacy/tests/cbact04c_java_equiv.sh` builds the jar (`mvn -q package -DskipTests`) when it is missing. CI needs Maven and network access for that.
