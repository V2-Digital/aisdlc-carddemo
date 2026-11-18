# CR-1 Test report — `modernized/cbact04c`

**Result: all green.** 1 fix iteration (of 5 allowed), and it was a bug in a test, not in the Java program.

## Commands and results

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | 23 tests, 0 failures (ZonedTest 6, BehaviourTest 15, GoldenEquivalenceTest 2). Only JUnit 5 + Surefire. |
| `legacy/tests/cbact04c-java-vs-cobol.sh` | 8/8 files identical. Live GnuCOBOL and the jar run on the same inputs (stock and post-CBTRN02C). |
| `legacy/harness/golden.sh --check` | OK. Two runs byte-identical, outputs match `legacy/golden`. |
| `git diff --stat baseline -- legacy/golden legacy/harness .github` | Empty. |

## Equivalence evidence (AC 2, 3, 4)

Compared byte for byte, both against stored golden (JUnit) and against the live COBOL run (script):

| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` (stock) | 50 records, identical | 50, identical | 152 lines, identical | 0, identical |
| `CBACT04C-after-CBTRN02C` | 50, identical | 50, identical | 302 lines, identical | 0, identical |

Total: 8 files per comparison, 0 diffs.

## Tests added (→ AC)

- `ZonedTest`: all 20 sign chars round-trip, negative zero → `{`, max/min, high-order truncation, invalid chars (R1).
- `BehaviourTest` (AC 5-9):
  - **AC5:** `interestTruncatedNotRounded` (2339.97 @15% → 29.24), `negativeBalanceTruncatesTowardZero` (→ −29.24), `tinyNegativeInterestIsPositiveZero`.
  - **AC6:** `zeroRateWritesNoTranButAccountRewritten`.
  - **AC7:** `lastAccountNotRewritten_D1`.
  - **AC8:** `fallbackToDefault_D2`, `missingDefaultAbends`, `missingAccountAbends`, `missingXrefAbends`, `missingInputFileAbends` (D5 text). Each checks SYSOUT and RC 16.
  - **AC9:** `tranIdCounterSpansAccounts`, `suffixWraps_D7` (1,000,001 records: 999999 → 000000 → 000001).
  - **Other:** `duplicateXrefFirstWins_D9`, `emptyTcatbalNoOutputRc0`, `transactionLayout`.
- `GoldenEquivalenceTest`: both golden scenarios, TRANSACT, ACCTFILE, SYSOUT and RC, with the first differing line reported on failure.
- `legacy/tests/cbact04c-java-vs-cobol.sh` (CI picks it up as `legacy/tests/*.sh`): builds the jar, runs live COBOL and Java on both input sets, and exits non-zero on any difference.

## Iterations

1. `tinyNegativeInterestIsPositiveZero` expected 1 transaction. With a non-zero rate, a 0.00 balance also writes a 0.00 transaction, which matches the golden stock run. I fixed the test expectation. No production code changed.

## Not covered / for humans

- Abend-path outputs (the files left behind after an abend) are not covered by golden. Tests assert SYSOUT and RC only, as the plan says.
- D9 (duplicate xref) and D7 (wrap) are verified only against the spec, not against COBOL. The golden data has neither case.
- The `cbact04c-java-vs-cobol.sh` script needs Maven and GnuCOBOL in the CI environment. Its `mvn package` step needs Maven Central on a cold cache.
- Defects D1-D3 are preserved and pinned. They still need separate fix decisions. D1 (last account under-posted every night) should go first.
