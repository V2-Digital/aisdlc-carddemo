# CR-1 Review: `CBACT04C` in Java 21

**Verdict: changes needed.** The logic is correct and byte-identical on both golden scenarios. The only blocker is that Maven build output (`target/`, including a jar and `.class` files) is in the tree and would be committed. Everything else is should-fix or nit.

## Evidence (run by the reviewer)

- `cd modernized/cbact04c && mvn -q test`: exit 0. Surefire: 19 + 3 + 4 = **26 tests, 0 failures, 0 errors**.
- `legacy/harness/golden.sh --check`: `two runs byte-identical`, `OK - outputs match legacy/golden`.
- `git diff baseline --stat -- legacy .github app`: **empty**. Golden files, harness and CI are untouched.
- `GoldenEquivalenceTest` reads the expected bytes straight from `legacy/golden/{CBACT04C,CBACT04C-after-CBTRN02C}`. It does not regenerate them. It compares TRANSACT, ACCTFILE, SYSOUT and RC, plus 50/50 record counts. This is real proof of AC 1, 2 and 10.

## Findings

1. **Blocker**: `modernized/cbact04c/target/` (16 files: `cbact04c.jar`, `.class` files, surefire reports) is untracked and would go into the PR. There is no `.gitignore` entry for it. *Fix:* add `modernized/*/target/` to `.gitignore` (or to `modernized/cbact04c/.gitignore`), and run `git rm -r --cached` on it if it was already staged.
2. **Should-fix**, `Cbact04c.java:144-145`: ACCTFILE is overwritten **in place** before TRANSACT is written. If the TRANSACT write fails, the run returns RC 16, but the input master has already been changed and cannot be "discarded" as the README says. *Fix:* write TRANSACT first, then write ACCTFILE through a temp file + `Files.move(ATOMIC_MOVE)`. This does not change behaviour on the golden runs.
3. **Should-fix**, `Main.java:247-251`: if `CARDDEMO_CURRENT_DATE` is malformed or shorter than 16 chars, it throws an uncaught exception, so the JVM exits with **1**, not 16. AC 8 says I/O and setup errors give exit 16. *Fix:* catch the parse error, print a message, and exit 16.
4. **Should-fix**, `Main.java:258-261`: an unset `DD_*` silently defaults to `<DD>.txt` in the cwd. In a container, this can read or overwrite the wrong file. *Fix:* treat a missing env var like a missing DD: print the open-error text and exit 16. Alternatively, document the default in the README.
5. **Should-fix (test strength)**, `BehaviourTest.java:58-66` (`defectD6_interestWrapsToNineDigits`): the expected value comes from `Zoned.encode` itself, which makes it a tautology for the wrap arithmetic. The test report says so. *Fix:* hard-code the expected 11-byte string (for example, compute the low 11 digits by hand for the fixture).
6. **Nit**, `Cbact04c.java:187-192`: a malformed numeric field abends with no message. Ops would see only `ABENDING PROGRAM`. *Suggest:* print the field/record before abending. Also, `TRAN-CAT-BAL` is decoded only when rate ≠ 0 (`:134`), so a bad balance on a rate-0 row is not detected. That is acceptable, but it differs from spec's "malformed → abend".
7. **Nit**, `Cbact04c.java:225`: an ACCTFILE write failure prints `ERROR WRITING TRANSACTION RECORD`. *Suggest:* use `ERROR RE-WRITING ACCOUNT FILE` (`CBACT04C.cbl:365`) for the account file.
8. **Nit**, `Zoned.java:299-303`: `wrap` cannot represent −0 (BigDecimal has no signed zero). So an interest value that wraps to exactly ±0 is encoded `{`, and nobody has checked that against COBOL. This is unreachable with realistic data. *Suggest:* note it under D6.
9. **Nit (plan drift)**: `Records.java` from the plan was folded into `Cbact04c.java`, and the package is `com.v2.carddemo.cbact04c`, not `carddemo.cbact04c`. `build.md` explains the package change. The offsets are inline literals (`substring(112, 122)`, etc.). Named constants would make the copybook mapping easier to audit.

## Checked and correct

- **Money:** BigDecimal throughout. `bal × rate / 1200` → `divide(…, 2, DOWN)` truncates toward zero for both signs, which matches COBOL `COMPUTE` without `ROUNDED`. Interest/total wrap at 11 digits (S9(9)V99) and balance at 12 (S9(10)V99).
- **Sign:** overpunch encode/decode for all 20 characters (`ZonedTest`). Negative interest −1.25 → `0000000012N` is pinned.
- **D1:** there is no rewrite after the loop (`:142`). It is pinned in both the golden and the fixture tests, and acct 50 matches `golden/CBTRN02C`.
- **Control flow:** the order of rewrite-on-break / reset / account / XREF lookups and the DEFAULT fallback matches `CBACT04C.cbl:188-217`. The suffix advances only when rate ≠ 0.
- **Q3 deviation** (PARM must be exactly 10 chars → 16) is implemented and tested, and it is the only intentional deviation.

## What a human should look at before merging

- Make sure `target/` is **not** in the commit (finding 1).
- Decide whether findings 2-4 (in-place overwrite on partial failure, exit code on a bad clock env, silent DD defaults) are fixed now or tracked. All three only matter for container operation, not for golden equivalence.
- Raise **D1** (last account never rewritten) as its own CR. It is the defect with business impact, and it is preserved here on purpose.
- Note that the truncation and negative-interest expectations are not cross-checked against COBOL. The optional fixture run through `run.sh` was skipped, and the evidence rests on COBOL semantics plus the golden 14.560875 → 14.56 case.
