# CR-1 Test report

## Result: green (20/20 JUnit tests, golden check OK)

## Commands
| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn test` | 20 run, 0 failures, 0 errors (JUnit 5 only), BUILD SUCCESS |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden`, two runs byte-identical |

## Golden equivalence (`GoldenEquivalenceTest`)
Compared byte-for-byte: `TRANSACT.txt`, `ACCTFILE.txt` (the Java `ACCTOUT`), `SYSOUT.txt`, and exit code vs `RC.txt` (0).

| Scenario | TRANSACT | ACCTFILE | SYSOUT | Diffs |
|---|---|---|---|---|
| `CBACT04C` (stock, PARM 2022071800, clock 2022071800000000) | 50 x 350 | 50 x 300 | 152 lines | 0 |
| `CBACT04C-after-CBTRN02C` (inputs from `golden/CBTRN02C`) | 50 x 350 | 50 x 300 | 302 lines | 0 |

This covers AC 2, 3 and 4 and AC 9 (50 accounts, key order). It also covers the spec's SYSOUT parity default.

## Characterization tests (`InterestCalcTest`, 14) and `ZonedTest` (4)
- AC 5: `truncatesNotRounds` (79.97 at 15% gives 0.99, rounding would give 1.00), `negativeBalanceTruncatesTowardZero`, and Zoned truncation.
- AC 6 (F1): `lastAccountNotUpdated`. The last account's row is unchanged while its transaction is written.
- AC 7: `groupHit`, `groupMissFallsBackToDefault`, `zeroRateNoTranNoSuffix`, `zeroTotalStillZeroesCycles`, `untouchedAccounts`, `emptyTcatbal`.
- AC 8: `missingAccount`, `missingXref`, `missingDefault`. They assert the abend SYSOUT lines.
- F8: `firstXrefCardWins`.
- F5 / Zoned: overpunch round-trip, -0 written as `{`, modulo overflow at 9 and 10 digits.

## Iterations
1 fix iteration, test code only. Initial failures were bugs in my fixtures: bad field widths, a size assertion that forgot the LF byte, and a wrong expected value in `groupHit`. No production code changed.

## Gaps and notes
- **Suffix wrap at 999,999 (F5) is not tested end-to-end.** `suffixWrapsAfter999999` only asserts the modulus arithmetic. Reaching the wrap needs 1M transactions.
- **Open-failure SYSOUT is not tested.** The `Main` path for unreadable inputs (exit 16, status 0035, the F7 text) has no test.
- **Abend text is unverified against COBOL.** The SYSOUT text and file status are inferred from source, not observed. The optional `legacy/tests/cbact04c-abend.sh` was not added, so there are no new COBOL tests.
- No COBOL, `legacy/` or `.github/` files were changed.

## Still failing
Nothing.
