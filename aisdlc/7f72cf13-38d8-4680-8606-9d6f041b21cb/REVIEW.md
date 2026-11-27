# CR-1 Review: CBACT04C in Java 21

**Verdict: ready to merge.** Both golden scenarios are byte-identical (TRANSACT, ACCTFILE, SYSOUT and RC). Real COBOL agrees on 4 crafted cases, and also on an overflow case I added. The protected directories are untouched. My findings are small and none of them blocks the merge.

## What I ran

| Command | Result |
|---|---|
| `git diff --stat baseline -- legacy/golden legacy/harness .github` | empty |
| `cd modernized/cbact04c && mvn -q verify` | pass: 24 tests (Abend 4, Defect 9, Golden 2, Interest 4, ZonedDecimal 5) |
| `legacy/tests/cbact04c-edge.sh` | PASS on `mixed` (rc 0), `abend-account`, `abend-xref`, `abend-default` (rc 16) |
| `legacy/harness/golden.sh --check` | OK, two runs byte-identical |
| **My own check**: `mixed` with balance `9999999999I` (999,999,999.99) and DEFAULT 01/0001 rate `99999I` (9999.99%) | COBOL = Java on TRANSACT, ACCTFILE and SYSOUT. The interest `…3333249999A` wraps silently and the balance `…03333238350D` is cut to 10 digits. This closes the "overflow not cross-checked" gap in test-report.md |

## Spec and AC check

- AC 2–4: `GoldenEquivalenceTest` compares raw bytes against `legacy/golden/*` and reads the expected values from disk. No expectations were regenerated.
- AC 5: `RoundingMode.DOWN` in `Cbact04c.java:156`, with exact `BigDecimal`. Negative values and the -0.00 case are cross-checked against COBOL in `mixed`.
- AC 6: D1 is covered by a crafted case and by golden account `…050` (`0000000187G`, record unchanged).
- AC 7, 8: covered. Abend SYSOUT and RC are compared against COBOL. The partial outputs are checked by the spec only, because the harness does not unload datasets after an abend.
- Money and signs: the codec has no negative zero, keeps the sign on modulo truncation, and passes unchanged bytes through via `substring` (`Cbact04c.java:170`). Correct.

## Findings

1. **should-fix**: `Cbact04c.java:142,144,167` / `ZonedDecimal.java:23`. A non-numeric zoned field (for example a blank or corrupt balance) throws an uncaught `NumberFormatException`. The program then exits with a stack trace and exit code 1, and writes **no** outputs. COBOL would carry on with whatever the bytes mean. That case is outside the spec and the sample data. *Suggestion:* at least catch the exception in `run()`, print a clear message, return a non-zero code the scheduler recognises, and record the behaviour in the README. Alternatively, raise it as a separate decision.
2. **should-fix**: `InterestTest.java:largestBalanceFitsIn9Digits`. This test is a tautology: `truncate(...,9,2)` can never return more than 999,999,999.99. *Suggestion:* pin the exact COBOL value instead: `i("9999999999.99","9999.99")` wraps to the interest `333324999.91` (`0000000000…`, see my run above: TRANSACT amount `3333249999A` = 333,324,999.91). That turns R2 into a real characterization test.
3. **nit**: `Cbact04c.java:56`. The message `"ERROR OPENING CROSS REF FILE35"` hardcodes status `35`. COBOL appends the real status, and Java always reports 35 for a missing or unreadable file. This is fine for the spec, but a comment would help. The open-abend messages are not cross-checked against COBOL (test-report.md already says so).
4. **nit**: `Cbact04c.java:49-64`. All inputs are loaded into memory up front. That works for this job's volumes, but at production ACCTFILE sizes memory is O(n). Note it in the README so operations can size the container.
5. **nit**: `FixedFile.java:215`. Empty lines are silently skipped. Check that this matches the harness loader. It does not matter for the current data.
6. **nit**: `Support.java:lines(...)` is unused, so remove it. `Cbact04c.java:41` `print(s + "\n")` is deliberate (LF on every platform), and a short comment would say so.
7. **nit**: `InterestTest.java:234`. The comment says the values were "pinned from" the edge script, but `99.99 × 11.88` and `1000 × 0.01` are not in any fixture. Either reword the comment or add them to `mixed`.

## What a human should look at before merging

- **Decide D1 and D2 separately.** These are the last-account rewrite and the group code stored in the zip field. They are preserved on purpose, and the test names say so.
- **Abend-path partial outputs** (TRANSACT records written so far, plus ACCTFILE with the updates applied) are checked against the spec only, never against COBOL. Confirm that the downstream consumers accept this, given the write-then-rename model (R6). Note also that the Java program writes a **complete new** ACCTFILE on abend, whereas VSAM updates records in place.
- **Finding 1**: corrupt input gives exit code 1, not 16. Check that the scheduler treats any non-zero exit as a failure.
- `legacy/tests/` is a new directory with an edge script and fixtures. It is not wired into `.github/` CI (it cannot be, by rule). Decide whether to run it in CI later.
