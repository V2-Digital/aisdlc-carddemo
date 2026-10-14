# CR-1 Review: `CBACT04C` → Java 21

**Verdict: ready to merge**, once the build output in `modernized/cbact04c/target/` is left out of the commit (finding 1). The Java output is byte-identical to live COBOL on both golden scenarios and on 3 extra fixtures I added. None of the protected paths were touched.

## What I ran

| Check | Result |
|---|---|
| `mvn -q test` (modernized/cbact04c) | Passes: 19 tests, exit 0 |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` |
| `legacy/tests/cbact04c-java-equiv.sh` | 6/6 identical (TRANSACT, ACCTFILE, SYSOUT × 2 scenarios) |
| `git diff baseline -- legacy/golden legacy/harness .github` | Empty |
| **Extra: live COBOL vs Java, synthetic fixture A.** Negative balances `-1234.59` and `-0.01`. One account with group `A000000000`, which matches a real DISCGRP row | TRANSACT, ACCTFILE and SYSOUT identical. `-0.01` gives interest `0000000000{` |
| **Extra: live COBOL vs Java, synthetic fixture B.** Overflow: balance `999999999.99`, rate `9999.99` | All 3 files identical. Amount `3333249999A` (high-order digits truncated) |
| **Extra: Java abend run.** DISCGRP missing | RC 16, legacy message text. ACCTFILE output has **0 lines** (see finding 2) |

The tests are real. `GoldenTest` compares the Java output to the committed golden files with `Files.mismatch`, so no expected values were regenerated. The equivalence script runs the COBOL harness itself.

## Findings

1. **should-fix**: `modernized/cbact04c/target/` (jar, classes, surefire reports, 19 files). This is build output and is untracked in the working tree. It should not be committed. Add `modernized/*/target/` to `.gitignore`, or leave it out when staging.
2. **should-fix**: `InterestCalc.java:78-80`. Both outputs are always written, even after an abend. On an open-error abend (any input missing) the account map is still empty, so `ACCTFILE.txt` is written with **0 records**. If someone runs with `OUT_DIR == IN_DIR`, or a later step copies OUT back to the master, all accounts are lost. VSAM would leave the master untouched. Suggestion: if the abend happens before ACCTFILE is loaded, do not write `ACCTFILE.txt`. The spec says abend outputs are not golden-checked, so this is a safety issue, not an equivalence issue.
3. **should-fix**: `spec.md` table "Record I/O". The ACCTFILE offsets are wrong: it gives 76/88/110, but the copybook gives 78/90/112. The code uses 78/90/112, which matches `CVACT01Y` and the golden files. build.md notes the correction, but spec.md was never updated. Fix spec.md so the approved document matches the code.
4. **should-fix**: `legacy/tests/cbact04c-java-equiv.sh`. The plan promised a COBOL cross-check of a negative balance and a non-DEFAULT group, but the script only runs the 2 golden scenarios. The JUnit tests for those branches use hand-derived expected values. I ran both fixtures by hand against COBOL and they match (see the table above). Add them to the script so the evidence can be reproduced.
5. **nit**: `GoldenTest.java:19-23`. The loop includes `SYSOUT`, then skips it with `continue`. Loop over TRANSACT and ACCTFILE only.
6. **nit**: `GoldenTest.java:27`. `assertEquals("0", golden RC.txt)` checks the golden file, not the Java run. It is a tautology. Remove it, since `fx.rc` is already asserted.
7. **nit**: `UnitTest.java:207-215`. The comment says the balance is `999999999.99`, but the fixture uses `...97`. The expected value is built with `Zoned.encode`, which is the code under test. The last `assertNotEquals(length, 11)` proves nothing. Pin the literal `3333249999A`, which I confirmed against COBOL on a matching input.
8. **nit**: `UnitTest.java:14`. `"0000000012}".replace('}','J')` is just `"0000000012J"`. Add a real `-0` decode case (`...}`).
9. **nit**: `Main.java:37`. If `CARDDEMO_CURRENT_DATE` has the wrong length, the program silently falls back to the system clock. A typo would then give real timestamps with no warning. Consider failing fast.
10. **nit**: `Zoned.java:14`. A non-numeric zoned field (for example, spaces) throws `NumberFormatException`. That exits with JVM code 1, not 16. The legacy behaviour with bad data is untested either way. Note it in the README, or map it to an abend.

Money handling checks out:
- `BigDecimal` is used throughout, with no floating point.
- The product is computed exactly, then divided with `divide(1200, 2, DOWN)`.
- High-order truncation is mod 10^11 for amounts and mod 10^12 for balances.
- Zero is always written as `{`.
- The COBOL-verified overflow and negative cases above confirm this.

## For the human before merging

- **Leave `target/` out of the commit** (finding 1).
- **Decide on finding 2.** Is writing an empty ACCTFILE on an early abend acceptable for how ops will wire up the container?
- **Raise the follow-up CRs** for D1 (last account not updated) and D2 (truncation), as approved in Q3. D1 changes customer balances every night: account `00000000050` was charged 18.77 in TRANSACT, but its balance was never updated.
- D4 (blank `ACCT-GROUP-ID`, so every lookup falls back to DEFAULT) is a data issue. It needs an owner.
