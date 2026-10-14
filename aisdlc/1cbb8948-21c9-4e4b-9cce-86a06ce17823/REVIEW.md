# CR-1 Review: `CBACT04C` → Java 21

**Verdict: ready to merge.** Both golden scenarios are byte-identical, the protected dirs are untouched, and money, sign and truncation handling match the COBOL. The findings below are test and doc quality issues, not behaviour issues.

## Evidence (re-run by reviewer)
| Check | Result |
|---|---|
| `mvn -q verify` (`modernized/cbact04c`) | exit 0. 16 tests: Characterization 10, GoldenEquivalence 2, Zoned 4. 0 failures |
| `bash legacy/tests/cbact04c_abend.sh` | `PASS`: COBOL RC 16 + SYSOUT tail pinned, and Java SYSOUT is identical |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` |
| CLI run outside JUnit (stock inputs, clock frozen) | rc 0. `cmp` TRANSACT / ACCTFILE / SYSOUT vs `legacy/golden/CBACT04C/`: all identical |
| `git status legacy/golden legacy/harness .github` | empty |
| Dependencies | JUnit Jupiter 5.11.4 (test) + Surefire 3.5.2 only |

Equivalence tests compare whole files read straight from `legacy/golden/` (`GoldenEquivalenceTest.java:25-27`). They don't regenerate expectations and no bytes are masked.

## Correctness checks
- Interest: `bal × rate / 1200` with `divide(…, 2, DOWN)`, then mod 10¹¹ unscaled = S9(9)V99 (`Cbact04c.java:139-140`). This matches COBOL truncation (no `ROUNDED`).
- `WS-TOTAL-INT` is S9(9)V99 and the code truncates it to 11 digits (`:141`). Correct.
- `ACCT-CURR-BAL` is encoded at 12 digits, which drops high-order digits on overflow.
- Zero is always `{`, including −0 (`Zoned.java:35-37`).
- ACCT patch offsets in the code are correct per `CVACT01Y.cpy`: bal 12–23, cyc-credit 78–89, cyc-debit 90–101, group 112–121 (`Records.java:24,30`).
- D1 (last account not rewritten) is preserved: there is no rewrite after the loop (`Cbact04c.java:147`).

## Findings
1. **should-fix**: test fixture writes cycle credit/debit at the wrong offsets. `Fx.java:68` puts credit at 75 and debit at 87; the copybook has them at 78 and 90. `CharacterizationTest.java:40` asserts `substring(75,87)`, which mixes 3 bytes of `ACCT-REISSUE-DATE` with 9 bytes of credit, and passes only because the fixture happens to put zeros there. Golden still covers the real offsets. *Fix:* use 78/90 in `Fx.acct` and assert `substring(78,90)` and `substring(90,102)` equal `00000000000{`.
2. **should-fix**: `spec.md` field table is wrong. It says `ACCT-CURR-CYC-CREDIT/DEBIT` are at 75/87 and `ACCT-GROUP-ID` at 109; correct is 78/90 and 112. The code is right, the spec is not. *Fix:* correct the table so CR-2 doesn't inherit the error.
3. **should-fix**: only one abend path is pinned against real COBOL. `cbact04c_abend.sh` covers missing account only. The missing-xref and missing-DEFAULT messages (`Cbact04c.java:122-123,133-134`) are checked against the Java's own strings, not COBOL output. *Fix:* add two more `INPUT_DIR` fixtures to the script (xref without acct 25; discgrp without `DEFAULT 01 0001`).
4. **nit**: open-failure paths are untested. `openError` hard-codes `NNNN0035` (`Cbact04c.java:165`). Also, if a later open fails, the `finally` block (`:87-89`) still truncates or writes TRANSACT even though COBOL would never have opened it. Harmless for a missing file, but add one test or a comment.
5. **nit**: bad numeric data (spaces in a zoned field) throws `NumberFormatException` from `Zoned.decode` (`Zoned.java:25`). The JVM then exits with code 1 and a stack trace, not an RC 16 abend. That is acceptable for like-for-like, but operations should know the exit code differs.
6. **nit**: if `CARDDEMO_CURRENT_DATE` is set but shorter than 16 chars, it is silently ignored and the wall clock is used (`Cbact04c.java:42`). *Fix:* fail fast instead.
7. **nit**: TRANSACT is held in memory and written only at end or abend (`Cbact04c.java:87-88`). If the JVM is killed, no transactions are written, but ACCTFILE is also not rewritten, so the files stay consistent. Fine at current volumes.
8. **nit**: tidy-ups:
   - stray comment `0.99*? see below` (`CharacterizationTest.java:48`)
   - redundant `assertEquals(0, rc)` after the golden RC compare (`GoldenEquivalenceTest.java:24`)
   - the `Fx.run` javadoc says a null input leaves the DD "absent", but it actually maps a nonexistent path
   - `!contains` should be `assertFalse` (`CharacterizationTest.java:94`)

## What a human should look at before merging
- **D1 is a money defect, kept on purpose.** In the after-CBTRN02C scenario, account `00000000050` gets an 18.77 interest transaction, but its balance is never updated. Merging this CR keeps the defect in production on the new platform. Make sure the separate CR for D1 and D3 (truncation) is raised with the Cards business owner.
- The account-specific rate path (a non-blank `ACCT-GROUP-ID`) is covered only by `groupRateHit` with hand-built data, because no golden data exercises it (D6).
- Decide whether to fix findings 1–3 here or in a follow-up. None of them changes runtime behaviour.
