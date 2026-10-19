# CR-1 Review — `CBACT04C` → Java 21

**Verdict: ready to merge**, once the committed `target/` build output is removed (finding 1). The code is correct against the spec. The golden tests are real byte comparisons. On edge cases, the Java output matches the real COBOL output, which I checked separately.

## What I ran
| Check | Result |
|---|---|
| `mvn -q verify` (JDK 21, deps: junit-jupiter 5.11.4 test-scoped + Surefire 3.5.2) | exit 0. 25 tests, 0 failures (Golden 2, Defect 4, Interest 4, Record 4, Rules 7, Zoned 4) |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden`, two runs byte-identical |
| `bash legacy/tests/cbact04c-default-missing.sh` | `PASS` |
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty, so those directories are untouched |
| **Separate COBOL-vs-Java check**: 2 synthetic `INPUT_DIR` datasets through `run.sh CBACT04C` and `java -jar target/cbact04c.jar`. They cover ACCTFILE balance overflow (9999999999.00 + 12.00), negative interest (−1200.00 @ 1% → `0000000120}`), sub-cent negative → `{`, and monthly overflow at rate 9999.99 | `TRANSACT`, `ACCTFILE` and `SYSOUT` byte-identical, RC 0 in all cases. The D5 truncation and the sign handling are confirmed against GnuCOBOL, not only against the spec |

## How the tests prove equivalence
- `GoldenTest` reads expectations straight from `legacy/golden/<scenario>/` (TRANSACT, ACCTFILE, SYSOUT, RC). Nothing is regenerated or copied into the test tree. Inputs are mapped to DD names the same way `run.sh` does.
- `DefectTest.D1_lastAccountNotRewritten_afterCbtrn02c` pins AC5 on real data: tx `2022071800000050` = `0000000187G` (18.77), and account 50 is byte-equal to its input (1945.87 / 1501.75 / −47.88).
- `InterestTest` pins 29.24 (AC4) and negative truncation. The monthly-overflow test computes its expected value with `Zoned.trunc` itself, so it partly checks the code against itself. The COBOL run above backs it up (`3333249999A`).

## Findings
1. **should-fix**: `modernized/cbact04c/target/**`, 29 files including `cbact04c.jar`, `.class` and surefire reports. Build output is in the change set. Delete `target/` and add `modernized/cbact04c/.gitignore` with `target/`.
2. **should-fix (doc)**: `spec.md` I/O table gives ACCTFILE cyc-credit at col **84**, cyc-debit at **96** and group at **118**. `CVACT01Y.cpy` puts them at **79 / 91 / 113**. The code (`Cbact04c.java:101,153-154`) and `Fx.acct` correctly use 79/91/113, and the golden diff proves it. Correct the spec so the next reader isn't misled.
3. **should-fix (ops)**: `Cbact04c.java:159` and `Main.java:195`: timestamps use UTC. COBOL `FUNCTION CURRENT-DATE` uses the job's local time. On a mainframe running local time, the TRANSACT timestamps will shift by the UTC offset. The spec chose UTC, but a human should confirm downstream consumers accept it (see below).
4. **nit (performance)**: `Cbact04c.java:90-95`: the XREF lookup is a linear scan for each account, O(accounts × xref). That's fine for 50 records but quadratic at production volume. Build a `Map<acct, card>` once with `putIfAbsent` (first record wins, as for `rates`).
5. **nit**: `Cbact04c.java:131-138`: only `NoSuchFileException` maps to RC 16. Any other `IOException` (permissions, a directory instead of a file) escapes `Main` with a stack trace and exit 1. Consider catching `IOException` → abend message + 16, so monitoring keyed on RC 16 still fires.
6. **nit**: `Main.java:197-200`: if `CARDDEMO_CURRENT_DATE` is shorter than 16 chars, it throws `StringIndexOutOfBoundsException`. Validate the length and print usage.
7. **nit**: `Records.java:232`: blank lines are dropped anywhere in the file, not only the trailing one. Lines longer than LRECL are silently truncated (`pad`). This matches the canonical format, so it's harmless, but worth a comment.
8. **nit**: `Cbact04c.java:118`: inline `java.math.RoundingMode.DOWN`. Import it like the other types.
9. **nit**: stale or confusing comments. `InterestTest.java:17` says "Runs two accounts", but the fixture has one. `DefectTest.java:85` says "999999999.99... balance 9999999999.00".
10. **nit**: the package is `com.v2.carddemo.cbact04c`, but `plan.md` names `com.carddemo.cbact04c`. That's harmless; either rename it or update the plan.

## What a human should look at before merging
- **Finding 1**: make sure `target/` doesn't get merged.
- **UTC vs local time (finding 3)**: decide whether TRANSACT `orig/proc` timestamps in UTC are acceptable for consumers such as the posting and statement jobs.
- **Abend outputs**: on RC 16, Java writes **no** output files. COBOL leaves a partial TRANSACT and any already-rewritten ACCTFILE records. The spec accepts this ("not compared"). Restart and rerun runbooks that inspect partial output need to know about it.
- **Defects preserved on purpose** (D1 last account never charged, D2 no fees, D3 group id blank in data, D4 wrong DISCGRP text, D5 silent overflow). Approve the follow-up CR for D1 (lost interest every night) as agreed in intent Q3.
