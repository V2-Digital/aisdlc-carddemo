# CR-1 Review: `CBACT04C` → Java 21

**Verdict: changes needed.** The code is correct: it is byte-identical to COBOL on both golden scenarios and on an extra overflow fixture I ran. The blocker is that `modernized/cbact04c/target/` (jar, classes, surefire reports) is in the change set.

## What I ran

| Check | Result |
|---|---|
| `mvn -q verify` (modernized/cbact04c) | exit 0, 25 tests, 0 failures/errors/skips |
| `legacy/tests/cbact04c_missing_default.sh` | PASS (COBOL RC 16) |
| `legacy/harness/golden.sh --check` | OK, two runs byte-identical |
| CLI jar, after-CBTRN02C inputs, clock `2022071800000000` | rc 0; TRANSACT, ACCTFILE, stdout `cmp`-identical to golden |
| **Own D3 cross-check**, COBOL vs Java on the same fixture (details below) | TRANSACT, ACCTFILE, SYSOUT identical |
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty (untouched) |

The D3 fixture had 4 TCATBAL rows, rate 9999.99 / 100.00, a balance of 9 900 000 000.00 and a balance of −99.13. COBOL and Java both gave:
- monthly `499992500.00` (7 499 992 500.00 wrapped);
- account balance `474992500.00` (10 474 992 500.00 wrapped);
- negative interest −99.13 × 9999.99 / 1200 = −826.0824… → `0000008260Q` = −826.08 (truncated toward zero), balance 1.00 → −825.08.

Before this run the D3 tests were only spec-derived. COBOL now confirms them.

## Findings

1. **blocker**: build output is in the change set. `modernized/cbact04c/target/**` has 28 files, including `cbact04c-1.0.jar` and `.class` files. Fix: delete `target/` and add `modernized/cbact04c/.gitignore` with `target/` (or put `**/target/` in the root `.gitignore`).
2. **should-fix**: `spec.md:37-38` still gives the ACCTFILE offsets as 84/96/118. `CVACT01Y.cpy` and the code (`Cbact04c.java:123,155-156`) use 78/90/112, and the golden diffs prove the code is right. Fix: correct the spec so it doesn't mislead the next CR (D1 fix, CR-2 fees).
3. **should-fix**: `Main.java:29`: a `CARDDEMO_CURRENT_DATE` shorter than 16 chars silently falls back to the system clock, and a non-numeric value throws `NumberFormatException` (exit 1 with a stack trace). Fix: treat only *unset/empty* as "system clock" and reject anything else with a clear message and a non-zero exit.
4. **should-fix**: bad input data has no defined exit code. `Zoned.decode` (`Zoned.java:21`, `IllegalArgumentException`) and an over-long record (`FixedFile.java:25`, `IOException`) escape `Cbact04c.run` and end in exit 1 plus a JVM stack trace. That is neither 0 nor 16, so a scheduler could misread it. Fix: document exit 1 = input data error in the README, or map both to a single explicit code. Don't present it as an abend.
5. **nit**: `FixedFile.java:21-23` silently skips empty lines anywhere in a file, not just a trailing LF. A blank record in the middle of a file would vanish. Fix: skip only the final empty element of the split.
6. **nit**: `Cbact04c.java:61`: a duplicate acct-id in ACCTFILE is silently overwritten (last one wins). A KSDS load would reject it. Fix: fail on a duplicate key, or note it in the spec's edge-case table.
7. **nit**: in `DefectTest.d3_totalAndBalanceWrap` / `totalWrapsAt1e9`, the comments are thinking-aloud ("7.2e9 x ... too big for bal", "is fine; use 2 cats of 7.5e8"). Fix: replace them with one line each giving the arithmetic.
8. **nit**: `GoldenTest.java:16`: `"legacy/../app/data/ASCII"` should be `"app/data/ASCII"`.
9. **nit**: `Cbact04c.java:82`: the XREFFILE open error appends `"35"` as a hard-coded string. It matches COBOL (`DISPLAY ... XREFFILE-STATUS`), but no COBOL run checks the absent-file path. A comment citing the COBOL line would help.

## Correctness against spec / AC

- AC1–AC8 are all met; I re-ran each check myself.
- The equivalence tests are real: `GoldenTest` reads the expected bytes straight from `legacy/golden/` and `assertArrayEquals` on the raw files. Nothing is regenerated.
- The tests are not tautologies:
  - Fixtures are built independently of the code under test (`Fx`).
  - The interest expectations are hand-computed.
  - `InterestTest.negative…` asserts the literal `0000000019Q`.
- Money handling:
  - The product is exact `BigDecimal` and the divide uses `RoundingMode.DOWN` (D2).
  - Truncation is sign-preserving, modulo 10⁹ for monthly/total and 10¹⁰ for the balance (D3).
  - Zero is written as `{`, and `-0` decodes as 0.
- D1 is pinned by `d1_*` and the after-scenario ACCTFILE diff. D4 and D5 are pinned too.
- Tran-id wrap `% 1_000_000` matches `9(6)`.

## What a human should look at before merging

- **Finding 1**: remove `target/` from the change set before the PR is cut.
- **Not verified against COBOL**: the account-missing, xref-missing and absent-file abend texts. Only missing-DEFAULT has a legacy test. A cheap follow-up is to add two more `legacy/tests/` fixtures.
- **Clock zone**: when `CARDDEMO_CURRENT_DATE` is unset, Java uses UTC. COBOL `CURRENT-DATE` is the LPAR's *local* time. Before go-live, decide the container TZ, or set the variable in the job.
- **Follow-up CRs**:
  - D1, the last account's interest is never posted: a nightly misstatement.
  - D2/D3, truncation and silent overflow: business rules.
  - The `ACCT-GROUP-ID` data issue: every lookup falls back to `DEFAULT`.
