# CR-1 Test report

**Result: green. 17 tests, 0 failures. 1 fix iteration (test-side only).**

## Commands and results

| Command | Result |
|---|---|
| `mvn -q test` in `modernized/cbact04c` | 17 run, 0 failed (`GoldenTest` 2, `BehaviourTest` 15) |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` (legacy unchanged) |
| CLI smoke: `CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar 2022071800 <dir>` on stock data | RC 0; TRANSACT, ACCTFILE and stdout byte-identical to `golden/CBACT04C` (`cmp`) |

## Equivalence (AC2-4)

`GoldenTest` asserts byte equality of TRANSACT, ACCTFILE and SYSOUT, plus RC, against both scenarios:

- `CBACT04C`: 50 transactions, 50 accounts, 152 SYSOUT lines, RC 0.
- `CBACT04C-after-CBTRN02C`: 50 transactions, 50 accounts, 302 SYSOUT lines, RC 0.
- 0 diffs.

## Unit and characterization tests (`BehaviourTest`, AC5-9)

| AC | Test | What it pins |
|---|---|---|
| 5 | `interestIsTruncatedTowardZero…` | 471.78 → 5.89, −566.06 → −7.07 (not −7.08). `sourceHasNoFloatingPoint` checks there is no `double` or `float` in main. |
| 6 | `groupHitPrintsNoMissingLines`, `missFallsBackToDefault…`, `rateZeroWritesNothing…` | A group hit uses its own rate (D3 path). A miss prints the 2 lines and uses DEFAULT. Rate 0 writes no transaction and adds nothing. |
| 7 | `transactionLayout`, `timestampUsesHundredthsOnly` | Counter runs across accounts. Description padded to 100. Both timestamps `…hh0000`. FILLER 20 spaces. 350+LF per record. |
| 8 | `lastAccountNotRewritten_D1` | The last account's bytes are unchanged while TRANSACT charges it. The rewritten account zeroes the cycle fields (D8) and keeps the other bytes verbatim. |
| 9 | `abendMissingDd`, `abendMissingAccount`, `abendMissingXref`, `abendMissingDefaultGroup` | RC 16, the matching SYSOUT lines, `FILE STATUS IS: NNNN00xx`, `ABENDING PROGRAM`. |
| - | `zeroBalanceStillWritesZeroTransaction_D5`, `emptyTcatbalOnlyBanners`, `zonedMinusZero…` | D5, the empty-input edge case, and the `-0` → `{` round-trip. |

D2 is covered by the truncation test. D4, D6 and D7 are not tested separately: D4 has no behaviour to assert, D6 is covered by the timestamp test, and the D7 counter wrap is not tested.

## Iterations

1. First run: 5 failures, all mistakes in my new tests. The amount field is at offset 132, not 111, and one expected zoned value was wrong. I fixed the tests. No production code changed.

## Not done / for the human

- `legacy/tests/` is not created. This change touches no COBOL, and the plan said there would be no new COBOL tests.
- D7 counter wrap (999,999) is untested.
- Abend writes no output files (spec R9, an open decision). The tests assert TRANSACT is absent after an open failure.
- A missing file reports status 35, which is the Build stage's choice. The spec gave no code.
