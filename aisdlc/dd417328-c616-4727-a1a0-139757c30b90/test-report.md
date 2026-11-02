# CR-1 Test report

**Result: all green.** 1 fix iteration, and it was a wrong expected value in my own test, not a defect in the Java code.

## Commands and results

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | pass, 17 tests, 0 failures (JUnit 5 only) |
| `legacy/tests/cbact04c-diff.sh` | exit 0, 5 of 5 cases identical to GnuCOBOL |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` (legacy untouched) |

## Java tests (17)

| Class | Tests | Proves |
|---|---|---|
| `GoldenEquivalenceTest` | 2 | AC2-4. For `CBACT04C` and `CBACT04C-after-CBTRN02C`, TRANSACT (50 records) and ACCTFILE (50 records) are byte-identical to golden. SYSOUT and RC match too (152 / 302 lines, RC 0). Per scenario that is 100 records plus SYSOUT and RC compared. |
| `InterestTest` | 4 | AC5. Zoned codec round-trip (`-0` is written as `{`). Overflow drops high-order digits. Positive truncation: 79.99 → 0.99, where rounding would give 1.00. Negative truncation: -10.07 → `-0.12` (`0000000001K`), -79.99 → `-0.99`, -0.50 → `0.00`. |
| `DefectCharacterizationTest` | 5 | AC6. `D1_lastAccountGetsTransactionButIsNotRewritten`: the last account is byte-identical to its input, while the first gets balance +15.00 and zeroed cycle fields. `F1_zeroBalanceWritesZeroAmountTransaction`. `rateZero...StillRewritten`. `F2_tranIdCounterWrapsAfter999999` (1,000,000 transactions, 999,999 +1 → `000000`). `F2_interestOverflowDropsHighOrderDigits`. |
| `ErrorPathTest` | 6 | AC7, AC8. Fallback to DEFAULT plus a real group hit (30% vs 15%). Missing account, xref or DEFAULT → RC 16, exact SYSOUT lines, no further records, no `END OF EXECUTION`. Empty TCATBALF → accounts copied unchanged. Default clock check. |

D2 (truncation) has no separate class: it is covered by the `InterestTest` truncation tests.

## COBOL vs Java (`legacy/tests/cbact04c-diff.sh`, closes risk R1)

It runs the real COBOL via `run.sh` with `INPUT_DIR` and the Java jar on crafted fixtures in `legacy/tests/cbact04c-diff/`, then diffs. CI runs it with bash.

| Case | Covers | Compared | Result |
|---|---|---|---|
| `edge-mix` | multi-category accounts, negative balances, real group hit (GOLD 30.25%), rate 7.5%, rate-0-only account | TRANSACT, ACCTFILE, SYSOUT, RC | identical, RC 0 |
| `edge-zero-and-unsorted` | zero balance, negative tiny balance, unsorted TCATBAL and XREF input (KSDS order) | same | identical, RC 0 |
| `err-missing-account` | account abend | SYSOUT, RC | identical, RC 16 |
| `err-missing-xref` | xref abend | SYSOUT, RC | identical, RC 16 |
| `err-missing-default` | DEFAULT abend | SYSOUT, RC | identical, RC 16 |

## Iterations

1. First `mvn verify` run: 1 failure, `InterestTest.overflowDropsHighOrderDigits`. My expected value was wrong (0.12 encodes as `...01B`). I fixed the test and no production code changed. All 17 pass after that.

## Gaps and notes

- **Abend output files.** On an abend the harness does not unload TRANSACT or ACCTFILE, so the diff script compares only SYSOUT and RC for the error cases. The spec says the Java writes the partial files. That is covered by the Java unit test (`missingAccountAbends` asserts 1 transaction kept), but not checked against COBOL.
- **Input-open failure (status 35).** Its SYSOUT text is Build's reading of the CBL and is not tested against COBOL or in JUnit. This is low risk, because a missing input file is an environment fault.
- **F2 counter wrap** is checked in Java only. COBOL is not run with 1M transactions.
- No fixture covers a negative cycle credit or debit being zeroed beyond the existing tests.
- Nothing is left failing.
