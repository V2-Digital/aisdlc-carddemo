# CR-1 Test report — `CBACT04C` → Java 21

Run `5c27f7ac-7a36-4166-a830-3749ca91113d` · Stage: Test · **Result: all green**

## Commands and results

| Command | Result |
|---|---|
| `mvn -q test` in `modernized/cbact04c` | 20 tests, 0 failures |
| `bash legacy/tests/cbact04c-abend.sh` | `ok noacct`, `ok noxref`: real COBOL and Java both match the expected SYSOUT + RC 16 |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden`; two runs byte-identical |

The `CEE3ABD: ABEND U0999` lines in the mvn output are the expected stderr of the abend tests.

## Equivalence (`GoldenEquivalenceTest`, byte-for-byte)

| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` | 50 records identical | 50 identical | 152 lines identical | 0 |
| `CBACT04C-after-CBTRN02C` | 50 identical | 50 identical | 302 lines identical | 0 |

Total 100 transactions, 100 account records and 454 SYSOUT lines compared, with 0 diffs.

## Other tests (AC → test)

| AC | Test |
|---|---|
| 5 | `InterestTest.truncatesNotRounds` (100.40 → 1.25), `negativeTruncatesTowardZero`, `negativeSubCentTruncatesToPositiveZero` (→ `{`) |
| 6 | `CharacterizationTest.lastAccountNotRewritten_D1` |
| 7 | `defaultGroupFallback_D2`, `knownGroupNeedsNoFallback`, `missingDefaultAbends` |
| 8 | `zeroRateNoTranButCycleZeroed` |
| 9 | `missingAccount`, `missingXref` in Java. `legacy/tests/cbact04c-abend.sh` runs the real COBOL and the Java port on the same fixtures (`legacy/tests/cbact04c-abend/`) and compares both to the expected SYSOUT/RC. |
| 10 | `golden.sh --check` |
| D4–D7 | `abendKeepsPartialOutput_D4`, `openErrorMessageSaysDalyRejects_D5`, `balanceOverflowDropsHighDigits_D6`, `firstXrefCardWins_D7` |
| Zoned | `ZonedTest`: all 20 sign chars, `-0`, overflow modulo |

## Iterations

- **Fix iterations used: 0 of 5.** No production code was changed.
- The first synthetic run had 11 failures, all in my test fixtures: the xref account id sat at col 26 instead of the alt key at offset 25, and some assertions were wrong. I fixed the tests, and the production code was not at fault.

## Notes for reviewers

- The D5 open-error and D6 overflow cases are checked only against the spec, not the real COBOL. The abend script covers only missing account and missing xref.
- **Spec column error:** the spec lists cycle credit/debit as cols 179-190 / 191-202 and group id as 213-222. The code uses 0-based offsets 78-102 and 112-122, i.e. cols 79-102 and 113-122, matching the copybook. The golden tests confirm the code is right and the spec table is wrong.
- Nothing is left failing. `legacy/golden`, `harness` and `.github` are untouched.
