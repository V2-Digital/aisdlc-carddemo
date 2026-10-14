# CR-1 Test report

**Result: green.** 25 JUnit tests, the COBOL differential and `golden.sh --check` all pass. Fix iterations: **0 production fixes**. 2 test-code mistakes of mine were corrected (see Iterations).

## Commands and results
| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn test` | 25 run, 0 failures |
| `legacy/tests/cbact04c-java-vs-cobol.sh` | PASS, 6 transactions byte-equal |
| `for t in legacy/tests/*.sh; do bash $t; done` (the CI loop) | PASS |
| `legacy/harness/golden.sh --check` | OK, harness and golden untouched |

## Coverage against acceptance criteria
| AC | Evidence |
|---|---|
| 1 | `mvn test` green on Java 21. JUnit 5 is the only dependency. |
| 2, 4, 6 | `GoldenEquivalenceTest.stock`: TRANSACT (50), ACCTFILE (50, key order), SYSOUT (152 lines) and RC are byte-equal to `golden/CBACT04C`. |
| 3, 4, 6 | `GoldenEquivalenceTest.afterCbtrn02c`: the same, with 302 SYSOUT lines, against `golden/CBACT04C-after-CBTRN02C`. |
| 5 | `InterestTest`: 1164.67 × 15 / 1200 gives 14.55 (rounded would be 14.56). Negative amounts truncate toward 0. A negative amount that truncates to 0 is written as `{`. The sources contain no `double` or `float`. |
| 7 | `RateLookupTest`: group-specific rate with no MISSING lines (F1), DEFAULT fallback with both messages, and missing DEFAULT, account and xref each exit 16 with the exact message tail. A zero rate writes no TRAN and the sequence does not advance. |
| 8 | `DefectCharacterizationTest`: `d1_lastAccountNotRewritten`, `d2_noFeesCharged`, `d3_interestTruncatedNotRounded`, `d4_discgrpOpenErrorSaysDalyRejects`. F2 overflow wrap is in `InterestTest.overflowWrapsSilently_F2`. |
| 9 | `CliTest`: arg > env > system clock precedence, F4 PARM padding, usage error returns 2. |
| R1, F1 | The differential script runs real COBOL and Java on crafted fixtures in `legacy/tests/cbact04c-java-vs-cobol/`. The fixtures cover negative balances, the `}`/`J`-`R` signs, a group-specific rate (`GRPX`) next to DEFAULT fallback (3 MISSING pairs), a rounding-sensitive amount, a rate of 0, a near-maximum balance (999999999.99, no wrap) on the last account (D1). All four outputs are byte-equal. |

The differential script was mutation-checked: switching the Java rounding to `HALF_UP` makes it exit 1.

## Not proven / for a human
- **F3 (duplicate card per account) is unverified against COBOL.** The harness loader rejects a duplicate on the xref alternate key (`CDFILES LOAD XREFFILE file status 22`). The fixture is therefore unique, and Java's "lowest card wins" remains an assumption. It cannot be tested without changing the harness.
- F2 overflow wrap is covered by a Java unit test only, not diffed against COBOL.
- Abend-path SYSOUT and RC are asserted in Java unit tests only. Not diffed against COBOL: the harness exports nothing after an abend.
- `build.md` notes that spec offsets for cycle credit/debit (75/87) and group id (109) are wrong. The code uses the copybook values 78/90/112, which the golden and differential tests confirm. `spec.md` still needs correcting.
- Open decisions, unchanged: D1 and D3 as follow-up CRs, D2 is CR-2, and R2 (hard-coded date) for ops.

## Iterations
Two of my new tests failed on first run (a wrong expected zoned value in `ZonedTest`, and a fixture adding an unwanted group row in `RateLookupTest`). Both were test bugs, fixed in the tests. There were no production code changes in the Test stage.

## Files added
`modernized/cbact04c/src/test/java/.../{Fixture,ZonedTest,InterestTest,RateLookupTest,DefectCharacterizationTest,CliTest}.java`, `legacy/tests/cbact04c-java-vs-cobol.sh`, `legacy/tests/cbact04c-java-vs-cobol/*.txt`.
