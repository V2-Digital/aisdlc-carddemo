# CR-1 Test report

**Result: green. 0 fix iterations needed for the Java code.** My first test run had 8 failures. All were mistakes in the new tests (wrong column offset, wrong expectations), not in the program. I fixed the tests; no production code changed.

## Commands and results
| Command | Result |
|---|---|
| `mvn -q -f modernized/cbact04c test` | pass, 18 tests (JUnit 5 only) |
| `legacy/tests/cbact04c_edge.sh` | PASS TRANSACT, ACCTFILE, RC (COBOL vs Java) |
| `legacy/harness/golden.sh --check` | OK, golden outputs unchanged |

## Equivalence (gate): `GoldenTest`
- `stock` compares Java with `legacy/golden/CBACT04C`. TRANSACT has 50 records and ACCTFILE has 50. Both are byte-identical, and RC is 0.
- `afterCbtrn02c` uses ACCTFILE and TCATBALF from `golden/CBTRN02C`. TRANSACT has 50 records and ACCTFILE has 50. Both are byte-identical to `CBACT04C-after-CBTRN02C`, and RC is 0.
- SYSOUT is also identical in both scenarios (152 and 302 lines). It is informational only.
- `truncatesNotRounds` counts exactly 28 golden amounts that rounding would change (AC5).

## Characterization and unit tests (`BehaviourTest`)
| Test | Covers |
|---|---|
| `lastAccountNotRewritten_D1` | D1 / AC6. The last account is left untouched, though its interest is posted. |
| `truncatesNotRoundsUnit` | D2. 11.00 at 18% gives 0.16, not 0.17. |
| `negativeBalanceTruncatesTowardZero` | -0.015 gives -0.01, and a result of -0 is encoded as `{`. |
| `overflowDropsHighOrderDigits_D3` | Interest wraps at 9 integer digits and the balance at 10. Nothing throws and nothing widens. |
| `counterWrapsAt999999_D6` | Wraps to 000000 after 999999, using 1,000,001 rows. |
| `defaultFallback`, `directGroupHit`, `zeroRateNoTranButRewrite` | AC7 |
| `globalCounterTypeCatClock` | AC8. Covers the counter, `01`/`0005`, the injected clock and the filler. |
| `untouchedAccountsAndFiller`, `emptyTcatbal` | AC9 |
| `abendMissingAccount`, `abendMissingXref`, `abendMissingDefaultRate` | AC10. Each abends and prints no end banner. |
| `overpunchRoundTrip` | Round-trips including `}`. |

## COBOL cross-check: `legacy/tests/cbact04c_edge.sh`
Fixtures are in `legacy/tests/cbact04c_edge/`: 5 accounts, 6 TCATBAL rows, a direct `A000000000` group hit, a negative balance, a zero rate, and a mix of default fallbacks. The script runs `run.sh CBACT04C` and the Java jar on the same inputs. It compares TRANSACT, ACCTFILE and RC, and exits non-zero on any difference. Real GnuCOBOL and Java match byte for byte (4 transactions, 5 accounts, 6 `MISSING` fallbacks, RC 0). This confirms the unit-test expectations against COBOL.

## Not covered
- The abend exit code 16 is tested at the `InterestCalc` level (`AbendException`). The CLI path that turns it into exit 16 is not driven by a test; it is only read in `Main`.
- The CLI skips `RC.txt` and `SYSOUT.txt` files. RC is the process exit code and SYSOUT goes to stdout.

## For a human to decide
Defects D1–D6 are preserved and pinned. D1 (the last account's interest is never added to its balance) and D2 (truncation) have real money impact. They need a separate change.
