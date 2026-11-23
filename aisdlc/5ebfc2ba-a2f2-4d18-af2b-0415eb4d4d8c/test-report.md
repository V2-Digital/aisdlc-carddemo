# CR-1 Test report

**Result: all green, 0 fix iterations.**

## Commands and results
| Command | Result |
|---|---|
| `mvn -q -f modernized/cbact04c verify` | 18 tests, 0 failures (`GoldenTest` 2, `ZonedDecimalTest` 3, `BehaviourTest` 13) |
| `legacy/harness/golden.sh --check` | OK. COBOL outputs match `legacy/golden/`, two runs identical |
| `bash legacy/tests/xref_dup.sh` | exit 0. The harness refuses the duplicate card (status 22), and the script now pins that. |
| `bash legacy/tests/neg_balance.sh` | exit 0. COBOL: bal -1512.77 @15% gives `0000000189}` (-18.90) |

## Equivalence (byte-for-byte, exit 0)
- `CBACT04C`: TRANSACT 50×350, ACCTFILE 50×300, SYSOUT 152 lines. Diffs: 0.
- `CBACT04C-after-CBTRN02C`: TRANSACT 50×350, ACCTFILE 50×300, SYSOUT 302 lines. Diffs: 0.

## Acceptance criteria coverage
| AC | Test |
|---|---|
| 1 | `mvn verify`, JUnit 5 only |
| 2, 3, 8, 9 | `GoldenTest.stockData`, `GoldenTest.afterCbtrn02c` |
| 4 (D2) | `interestIsTruncatedNotRounded_acct48` (18.909625 → 18.90), `negativeBalanceTruncatesTowardZero` (→ -18.90, `}`). The sign is confirmed against COBOL by `neg_balance.sh`. |
| 5 (D1) | `lastAccountIsNeverRewrittenButItsInterestTransactionIsWritten` (acct 50 unchanged, tran `2022071800000050` = `0000000187G`), `lastAccountDefect_syntheticTwoAccounts` |
| 6 | `zeroRateWritesNoTransaction`, `zeroBalanceNonZeroRateWritesAmountZeroTransaction` |
| 7 | `missingSpecificGroupFallsBackToDefault`, `missingDefaultGroupAbends`, `missingAccountAbends`, `missingXrefAbends` (exit 16, `ABENDING PROGRAM`) |
| D6 | `overflowTruncatesHighOrderDigitsSilently` (S9(10)V99 wraps) |
| D7 | `duplicateXrefCardsAbend_D7` |
| edge | `emptyTcatbalLeavesAccountsUnchanged` |

## Gaps / for a human
- **D7 is unverified against real COBOL.** The harness cannot load duplicate alt keys. Java abends on duplicates, from reading the source. The intent said "first card". Please confirm.
- **D6 overflow is not cross-checked in COBOL.** Java matches the spec only.
- **D5** (open-error text) is not modelled.
- Nothing is failing. `neg_balance.sh` and `xref_dup.sh` are new CI tests in `legacy/tests/`.
