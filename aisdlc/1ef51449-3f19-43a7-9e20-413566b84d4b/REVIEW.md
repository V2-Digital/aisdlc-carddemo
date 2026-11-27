# CR-1 Review — `CBACT04C` → Java 21

**Verdict: ready to merge.** Both golden scenarios are byte-identical (TRANSACT, ACCTFILE, SYSOUT, RC). My own COBOL-vs-Java runs on edge cases also matched. The findings below are should-fix or nit only.

## What I ran (reviewer, fresh session)

| Check | Result |
|---|---|
| `mvn -q test` in `modernized/cbact04c` | exit 0, 20 tests (Golden 2, Interest 5, Defect 4, Abend 6, Zoned 3) |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden`, two runs byte-identical |
| `bash legacy/tests/cbact04c_negative.sh` (real COBOL) | 5/5 `ok` |
| `git diff baseline --stat -- legacy/golden legacy/harness .github app` | empty: protected paths untouched |
| **Differential A** (mine): bal −0.05 / 999,999,999.99 / 0 at DEFAULT rate 9999.99 | COBOL = Java on TRANSACT, ACCTFILE and SYSOUT. Amounts `0000000041O`, `3333249999A` (monthly overflow wraps), and the balance wraps to `03333251579A` |
| **Differential B** (mine): bal −0.01 at 15.00 (−0.000125 truncates to −0) | COBOL = Java. Both write `0000000000{` (positive zero, not `}`) |

What I checked in the code:
- **Golden tests are real.** `GoldenTest` uses `Files.mismatch` on the golden files, which are read-only, and full-string SYSOUT equality. No expectations are regenerated.
- **AC 1–8 are covered.** The pom has only JUnit 5 and Surefire (the jar plugin is build-only).
- **Truncation is exact.** `InterestCalc.java:143` does `divide(…,10,DOWN).setScale(2,DOWN)`, which equals floor-at-2 of the exact quotient.
- **D1 is preserved.** `InterestCalc.java:151` has no update after the loop.

## Findings

1. **should-fix**: `InterestTest.java:72`. The overflow test builds its expected value with the same `remainder(10^9)` formula as the production code. That is close to a tautology. Pin the literal COBOL value instead. Differential A shows COBOL writes `3333249999A` for 999,999,999.99 × 9999.99. Replace the computed expectation with a literal taken from a harness run, and consider adding Differential A as a `legacy/tests/` script, like the negative one.
2. **should-fix**: R2 (several cards per account means the lowest card wins) has no test, although plan risk 5 called for one. It also **cannot be shown with the harness**: loading a duplicate alt key fails with `CDFILES: LOAD XREFFILE file status 22`, because the harness AIX is unique. Add a unit test that pins the Java choice. Record in the README that this is an assumption the COBOL cannot confirm here, and that production AIX `NONUNIQUEKEY` must be checked.
3. **nit**: `AbendTest.java:69`. The comment `291.21 -> 291.21? see below` is wrong and refers to nothing. The value is correct (1164.87 × 30 / 1200 = 29.12175, giving `0000000291B`). Fix the comment.
4. **nit**: `InterestTest.java:45`. The comment mentions 100.07, but the input is 100.55. Drop the 100.07 part. Also, `truncatesNotRounds` (line 38) says itself that it cannot tell truncation from rounding, so rename it or merge it with the next test.
5. **nit**: `Main.java:70`. A malformed `CARDDEMO_CURRENT_DATE` throws `NumberFormatException`, which gives exit 1 and a stack trace. Validate it (16 digits) with a clear message.
6. **nit**: `Main.java:49`. An `IOException` on a readable file escapes as exit 1, not as the abend path (exit 16). This is rare, but operations will key on RC 16.
7. **nit**: `Zoned.java:21`. A field that is all spaces or non-numeric throws a raw exception. COBOL would also misbehave on that data, so this is acceptable. A one-line comment would help.
8. **nit**: plan drift. There is no `Records.java` (the offsets are inlined in `InterestCalc`), and the package is `com.v2.carddemo.cbact04c`, not `carddemo.cbact04c`. Both are fine, but the plan or build notes should say so.
9. **nit**: the abend tests print `CEE3ABD: ABEND U0999` to the Maven log (seen 4×), and nothing asserts it. Capture `System.err` in `AbendTest`, or leave it and note why.

## What a human should look at before merging

- **D1 is customer-impacting.** Account `00000000050` is charged 18.77 (tran `2022071800000050`), but its balance is never updated. This is preserved on purpose. Raise a separate high-priority CR, as intent Q4 recommends.
- **D2 (truncation) and D4 (silent overflow) are preserved.** They need a business decision in a follow-up CR.
- **Zero-amount transactions.** When the rate is not 0 and the balance is 0, or the interest truncates to 0, a transaction is still written with amount `0000000000{`. This matches COBOL (Differential A/B, negative script). Downstream consumers should expect it.
- **R2 assumption** (finding 2). Check whether the production XREF AIX is non-unique.
- **The new `legacy/tests/cbact04c_negative.sh` is picked up by CI** (`ci.yml:39` loops over `legacy/tests/*.sh`). It needs GnuCOBOL, which CI already has for `golden.sh`.
- **Partial outputs after an abend are not equivalence-tested** (by design, plan risk 3). Do not depend on them operationally.
