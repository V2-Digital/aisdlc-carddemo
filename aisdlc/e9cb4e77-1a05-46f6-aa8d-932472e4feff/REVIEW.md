# CR-1 Review: `CBACT04C` → Java 21

**Verdict: ready to merge**, after the untracked `target/` directory is kept out of the commit (finding 1). The outputs are byte-identical to both goldens, the tests check against the real golden files, and every defect is preserved and pinned.

## What I ran (reviewer session)

| Command | Result |
|---|---|
| `mvn -q -f modernized/cbact04c test` (clean build dir) | exit 0, 21 tests green |
| `bash legacy/tests/cbact04c_abend.sh` (real GnuCOBOL) | 3/3 ok, RC 16 with the legacy lines |
| `legacy/harness/golden.sh --check` | OK, two runs byte-identical |
| CLI jar on after-CBTRN02C inputs, `cmp` against golden | rc 0. TRANSACT, ACCTFILE and stdout are **identical** |
| `git diff baseline -- legacy/golden legacy/harness .github` | empty, nothing untracked there either |

## Correctness vs spec (spot-checked against `CBACT04C.cbl`)

- Interest: exact multiply, `divide(1200, 2, DOWN)`, then `fit` to S9(9)V99. Matches `:464` (no ROUNDED) and D7. The total is held in the same PIC (`:169`, S9(09)V99). The balance is wrapped to S9(10)V99 by `encode`.
- D1: the loop ends without rewriting the last account (`Cbact04c.java:70-107`). Pinned by `DefectTest`: account 50 is unchanged, its 18.77 txn exists, and exactly 49 of 50 accounts change.
- Overpunch: `{A-I` / `}J-R`, and a negative zero cannot be encoded. Fine.
- The tests are not tautologies. `GoldenTest` compares against `legacy/golden/*` files that were never regenerated. `truncatesNotRounds` checks all 50 golden amounts and counts the 28 HALF_UP differences.

## Findings

1. **should-fix**: `modernized/cbact04c/target/`. About 30 build artifacts (jar, `.class`, surefire reports) are untracked, and there is no `.gitignore` to exclude them, so `git add .` would commit them. Delete `target/` before committing, or add `modernized/cbact04c/.gitignore` with `target/`.
2. **should-fix**: `Cbact04c.java:128-133`, `:143-146`. The missing-input-file path is guessed and not verified against COBOL: the `"35"` suffix only on XREFFILE, status `NNNN0035`, and START printed before the error. AC-8 doesn't cover it, and the test report says so. Either add a case to `legacy/tests/cbact04c_abend.sh` that deletes an input DD, or record in the README that open-failure text is best-effort.
3. **nit**: `InterestTest.java:71-75`. `suffixWrapsAtOneMillionLikePic9x6` asserts `(999999+1)%1000000==0` and never touches production code, so it's a tautology. Remove it, or pull the suffix increment into a small method and test that.
4. **nit**: `spec.md` table. The ACCTFILE offsets 77 / 89 / 111 are off by 2. The copybook and code use 79 / 91 / 113, and the goldens confirm the code. Correct the spec so the next reader isn't misled.
5. **nit**: `Cbact04c.java:77`. `lastAcct.substring(0, 11)` is redundant because `lastAcct` is already 11 characters. Use `accounts.put(lastAcct, …)`.
6. **nit**: `Main.java` / `Clock.java:11`. A malformed `CARDDEMO_CURRENT_DATE`, or an I/O error, gives a Java stack trace and exit 1, not a clean message. That's acceptable for CR-1. Consider catching it in `Main` and returning a documented RC.
7. **nit**: `pom.xml`. It adds `maven-jar-plugin` (for `Main-Class`) beyond "JUnit 5 + Surefire". It's a build plugin, not a dependency, and the plan requires the jar. Just confirm that's acceptable.

## What a human should look at before merging

- **Abend behaviour differs on output files.** On RC 16 the Java writes **no** TRANSACT/ACCTFILE. The COBOL would leave a partial TRANSACT and already-rewritten accounts. The spec puts this outside the contract, but operations should know that restart and recovery after an abend will look different.
- **D1 has money impact.** The last account's interest is billed as a transaction but never added to its balance. It is preserved here as the CR requires. Raise the follow-up CR, and decide D2/D4/D5 there too.
- Findings 1 and 2 above.
