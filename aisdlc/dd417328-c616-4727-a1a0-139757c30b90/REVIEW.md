# CR-1 Review: `CBACT04C` → Java 21

**Verdict: ready to merge.** Both golden scenarios are byte-identical across all 4 outputs. The COBOL-vs-Java diff passes on 5 crafted edge cases. The protected paths are untouched. The findings below are should-fix/nit only; none is a blocker.

## Evidence (run by the reviewer)

| Check | Result |
|---|---|
| `git diff baseline --stat -- legacy/golden legacy/harness .github app` | empty (untouched) |
| `mvn -q verify` (modernized/cbact04c) | rc 0. 17 tests, 0 failures (Golden 2, Interest 4, Defect 5, ErrorPath 6) |
| `legacy/tests/cbact04c-diff.sh` | 5/5 ok: `edge-mix`, `edge-zero-and-unsorted` (RC 0, all 4 files compared); `err-missing-account/-xref/-default` (RC 16, SYSOUT+RC compared) |
| `legacy/harness/golden.sh --check` | OK, two runs byte-identical |
| pom dependencies | `junit-jupiter` 5.10.2 (test) only. Plugins: Surefire and jar |

The tests prove equivalence. `GoldenEquivalenceTest` byte-compares the program's output against the files in `legacy/golden/`, and it does not regenerate the expectations. The diff script runs real GnuCOBOL against the jar on inputs outside the golden data: several categories per account, a negative balance, a real group hit, unsorted keys, and the abends. It covers the gaps flagged in R1.

## Money, sign and truncation: verified

- Interest: `divide(1200, 2, DOWN)`, then wrapped to S9(9)V99. The total is wrapped too (`Cbact04c.java:136-139`). Truncation also holds for negative values (`-79.99 → -0.99`, `-0.50 → +0 {`). `InterestTest` covers this and the diff script confirms it against COBOL.
- The balance is wrapped mod 10^12 and cycle fields are set to `{` zero (`Records.java:37-41`). Bytes that are not touched pass through.
- A negative zero is always written as `{` (`Zoned.encode`), and decode accepts `}`.
- D1 (last account not rewritten) is pinned by `DefectCharacterizationTest.D1_…`, and the golden scenario reproduces it (acct 50).

## Findings

1. **should-fix**: `Main.java:61` reads the system clock in **UTC**. COBOL `CURRENT-DATE` returns *local* time. That follows the spec, but in production the timestamps in cols 279-330 will be shifted from today's values unless the container runs in UTC and the mainframe did too. *Suggestion:* a human confirms UTC is intended, or switch to `ZonedDateTime.now()` and set the container's TZ.
2. **should-fix**: the abend paths' TRANSACT/ACCTFILE contents are asserted only on the Java side (`ErrorPathTest`), because the harness does not unload datasets after an abend (`cbact04c-diff.sh:23-25`). The claims "records written so far" and "ACCTFILE with the rewrites done so far" are spec intent, not proven against COBOL. *Suggestion:* accept this and state it in the PR. Ops must also make sure downstream jobs don't consume a partial `TRANSACT.txt` when RC=16.
3. **should-fix**: malformed numeric input (for example a non-digit in `TRAN-CAT-BAL`) throws an uncaught `NumberFormatException` in `Zoned.java:27`. The JVM then exits 1 with a stack trace, writes no outputs, and doesn't follow the spec's RC 16 convention. *Suggestion:* map it to the abend path, or at least document it. COBOL would not stop at all in this case (it would compute garbage), so this is a behaviour difference on bad data only.
4. **should-fix**: SYSOUT encoding. Records are read as ISO-8859-1, but `System.out` (`Main.java:34`) uses the platform charset. Any non-ASCII byte in a record would be re-encoded, so SYSOUT would no longer be byte-identical. All current data is ASCII. *Suggestion:* wrap stdout as `new PrintStream(new FileOutputStream(FileDescriptor.out), true, ISO_8859_1)`.
5. **nit**: `Datasets.java:28` skips blank lines and `Datasets.java:32` lets the last duplicate key win silently. A KSDS load would reject duplicates. *Suggestion:* fail on a duplicate key.
6. **nit**: `--current-date` is not validated (`Main.java:27`). A value shorter than 16 characters throws `StringIndexOutOfBounds` at the first transaction (`Cbact04c.java:175`). *Suggestion:* check `\d{16}` at parse time.
7. **nit, dead code:** `Records.tcatKey` (`Records.java:21`) and `Datasets.empty()` (`Datasets.java:49`) are unused. *Suggestion:* delete them.
8. **nit, readability:** the `xrefMissing` field is declared *after* the constructor (`Cbact04c.java:62-65`). *Suggestion:* move it up with the other fields.
9. **nit, test comments:** some comments are confusing or stale. `InterestTest.java:30` says "half-up would also be 0.13 at 0.1259? 0.13". `DefectCharacterizationTest.java:62-64` has a rambling F2 preamble, and line 89 has an unused `bal` variable with a wrong comment. *Suggestion:* trim them. The assertions themselves are correct.
10. **nit**: AC5 says the test covers "28 golden cases where half-up would differ". `InterestTest` has 3 hand-picked cases (`79.99 → 0.99` shows the difference). The 28 are covered only implicitly by the golden byte-compare. That is acceptable, but the wording of the test-report/spec overstates it.

## What a human should look at before merging

- **Answer the open intent questions:** raise a follow-up CR for **D1**, where the last account each night misses its interest posting to the balance. Have Finance review **D2** (truncation). Both are preserved here on purpose.
- **Timezone (finding 1):** decide UTC vs local before this runs for real.
- **Partial output on RC 16 (finding 2):** the scheduler/JCL replacement must not pass `TRANSACT.txt` downstream after an abend.
- **R2:** key order is Java `String` order, which is ASCII order. EBCDIC KSDS order differs when keys mix letters and digits (for example `TRAN-TYPE-CD`). This is fine for the canonical text form, and the real file conversion belongs to the deploy plumbing.
