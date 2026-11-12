# CR-1 Test report

**Result: all green, 0 fix iterations needed.**

## Commands and results
| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | pass, 16 tests (AbendTest 5, ClockInjectionTest 1, DefectCharacterizationTest 5, GoldenEquivalenceTest 2, ZonedTest 3) |
| `bash legacy/tests/cbact04c_negzero.sh` | PASS |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` |
| `git diff -- legacy/golden legacy/harness .github` | empty |

## Equivalence (GoldenEquivalenceTest)
Byte-identical TRANSACT, ACCTFILE, SYSOUT and RC for both scenarios:
- `CBACT04C`: 50 transactions, 50 accounts, 152 SYSOUT lines, RC 0.
- `CBACT04C-after-CBTRN02C`: 50 transactions, 50 accounts, 302 SYSOUT lines, RC 0.
- Diffs: none.

## Characterization (DefectCharacterizationTest)
F1 last account not rewritten; F2 truncation (1512.77 -> 18.90); F3 zero-amount transaction; DEFAULT fallback and abend when DEFAULT is missing; plus new `negativeInterestTruncatedToZeroIsWrittenPositive`.

## Added this stage
- `legacy/tests/cbact04c_negzero.sh` (+ fixture `legacy/tests/cbact04c_negzero/TCATBALF.txt`): real COBOL run with balance -0.01 at the DEFAULT rate. It truncates to -0.00 and COBOL writes `0000000000{`. This confirms the Java zero-sign rule (plan item 6, left open at Build).
- The matching Java test above.

## Notes
- Build noted the spec's ACCTFILE offsets are wrong (cyc-credit 78, cyc-debit 90, group 112 per `CVACT01Y`). Code is right and golden bytes confirm. spec.md should be corrected.
- Nothing left failing. F1/F2 remain preserved for separate CRs.
