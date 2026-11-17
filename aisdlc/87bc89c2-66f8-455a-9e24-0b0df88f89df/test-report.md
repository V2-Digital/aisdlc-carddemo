# CR-1 Test report

**Result: green.** 16/16 JUnit tests pass. COBOL live cross-check, `golden.sh --check` and the protected-paths diff are all clean.

## Commands and results

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | 16 tests, 0 failures |
| `legacy/tests/cbact04c_java_vs_cobol.sh` | 8/8 OK: TRANSACT, ACCTFILE, SYSOUT, RC for stock and after-CBTRN02C, COBOL run live vs Java |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` |
| `git diff --stat baseline -- legacy/golden legacy/harness .github` | empty |

## Tests (→ AC)

- `GoldenEquivalenceTest` (2): `stock` and `afterCbtrn02c` compare TRANSACT (50 records), ACCTFILE (50), SYSOUT and rc against `legacy/golden/<scenario>/`, all byte-identical. (AC 2, 3, 4)
- `InterestTest` (2):
  - Truncation: a/c 1 gives `0000000145F`. A synthetic 79.99 balance gives 0.99, where half-up would give 1.00.
  - D7: negative balances give negative interest, with no negative zero. (AC 5)
- `CharacterizationTest` (8):
  - D1: last account not rewritten. (AC 6)
  - DEFAULT fallback. (AC 7)
  - Abend with rc 16 and no output files for missing account, missing xref and missing DEFAULT rate. (AC 7)
  - Rate-0 category: no txn, no counter use, account still rewritten. (AC 8)
  - Empty TCATBAL.
  - PARM padding and cutting.
- `ZonedTest` (4): every sign char round-trips, −0 input, high-order digit wrap.

## Iterations

The `src/test` directory was empty when this stage started, so I wrote all the tests.
One fix iteration: three of my own tests had wrong fixture arithmetic (balance digits and an expected wrap value). Production code was not changed.
Nothing is left failing.

## Gaps

- The D3 6-digit transaction counter wrap (999999→000000) is not tested. It would need 1M transactions. The `% 1_000_000` is visible in the code only.
- Duplicate xref acct-ids (R4) and file-open errors are untested, as the spec says.
- The Java abend deliberately differs from COBOL: it writes no output files, where COBOL leaves partial output.
- `InterestTest` still has some leftover scratch comments. They are harmless.

## Decisions for a human

None blocking. The defect follow-ups (D1, D2, D4, D7) stay as separate CRs.
