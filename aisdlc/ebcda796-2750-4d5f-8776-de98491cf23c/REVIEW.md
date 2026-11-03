# CR-1 Review: `modernized/cbact04c`

**Verdict: ready to merge.** Both golden scenarios are byte-identical (TRANSACT, ACCTFILE, SYSOUT, RC), and the money and sign handling matches COBOL, including a negative-balance case I ran on both sides. No blockers. The open findings are should-fix and nit items that can go in a follow-up.

## Evidence (re-run by the reviewer)

| Check | Result |
|---|---|
| `mvn -q verify` (Java 21, JUnit 5.10.2 test scope only) | rc 0. 26 tests, 0 failures (Abend 3, Defect 6, DiscGrp 3, Golden 3, Interest 4, RateZero 2, ZonedCodec 5) |
| CLI jar, stock → `diff -r` vs `legacy/golden/CBACT04C` | identical, rc 0 |
| CLI jar, `--input-dir legacy/golden/CBTRN02C` → `diff -r` vs `CBACT04C-after-CBTRN02C` | identical, rc 0 |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` |
| `git diff --stat baseline -- legacy .github app` | empty |
| Extra: COBOL vs Java on 4 rows with balances `-0.40`, `0`, `-1.00`, `0` at 15% | TRANSACT amounts `{ { J {` on both sides. ACCTFILE and SYSOUT identical. This confirms that -0.005 → `+0` (`{`) and -0.0125 → `-0.01` (`J`) match COBOL |

The tests are real. `GoldenTest` byte-compares against the committed `legacy/golden/` files and does not regenerate them. The AC4 truncation case (0.40 × 15 / 1200 → `{`, where half-up would give 0.01) and the D1 case (account 50 unchanged, its transaction `0000000187G`) both use literal expectations.

## Findings

1. **should-fix: the spec has the wrong ACCTFILE offsets.** See `aisdlc/.../spec.md:25`. It lists `CYC-CREDIT 81`, `CYC-DEBIT 93` and `GROUP-ID 115`, but `CVACT01Y.cpy` gives **78, 90 and 112**. The code is correct (`InterestCalc.java:125,154`), and the golden tests prove it. Fix the spec so a later maintainer (CR-2) doesn't "correct" the code to match it.
2. **should-fix: unexpected errors don't exit 16.** A malformed zoned field (`Zoned.java:21`, `IllegalArgumentException`) or an `IOException` in `RecordFiles.load/write` propagates out of `Main.main`. The JVM then exits **1** with a stack trace and writes no `RC.txt`. The scheduler contract is 0/16. Suggestion: in `Main.main`, catch `RuntimeException`/`IOException`, print `ABENDING PROGRAM`, write `RC.txt` = 16 and return 16. A human should decide whether that counts as "like-for-like". COBOL would not abend on bad numeric data, but a container job needs a deterministic RC.
3. **should-fix: TCATBALF is re-sorted, but the spec says "file order".** `InterestCalc.java:83-84` sorts by the 17-byte key. That models a KSDS sequential read and is arguably more faithful. It contradicts spec rule 2, though, and makes the spec's "non-adjacent rows" edge case unreachable. Duplicate keys are kept (a stable sort), whereas a real KSDS cannot hold them. Pick one and align: either keep the sort and update the spec, or drop it. The golden inputs are already in key order, so outputs don't change either way.
4. **nit: a few synthetic tests use the codec under test as their oracle.** `DefectTest.java:38` and `RateZeroTest.java:22` assert with `Zoned.encode(...)`. `ZonedCodecTest` and the golden compares cover the codec independently, so the risk is low. Literal strings (`00000000101{`, `00000001000{`) would make these tests self-evident.
5. **nit: the CLI parser is permissive.** `Main.java:21-23` silently ignores unknown flags and drops a trailing flag that has no value. A malformed `--clock` throws `NumberFormatException` or `StringIndexOutOfBounds` (exit 1). Exit `2` for usage errors isn't in the spec. Suggestion: reject unknown or odd arguments with the usage message and exit 2, and document exit 2 in the README.
6. **nit: over-length input lines are silently cut to LRECL** (`RecordFiles.java:37-42`, used by `load`). That matches the spec ("pad"), but a corrupt input would go unnoticed. Consider failing on lines longer than LRECL.
7. **nit: D7 suffix wrap is untested.** `InterestCalc.java:141` wraps at 10^6 as the spec requires, but no test pins it. A unit test on the formatting would be cheap, or leave it as a known gap.
8. **nit: SYSOUT goes to stdout only after the run finishes** (`Main.java:32`), not as lines are produced. That's fine for batch, but container log streaming will show nothing until the job ends.

Readability is good: about 320 lines of main code, the paragraph mapping is commented, there's no dead code, and no floating point. `BigDecimal.divide(1200, 2, DOWN)` is followed by field truncation, as the spec requires.

## What a human should look at before merging

- **D1 (last account never rewritten) changes customer balances.** In the nightly scenario, account 50's 18.77 interest is posted to TRANSACT but never to its balance. Merging preserves this on purpose. Confirm that the fix CR is raised and scheduled.
- **D2 truncation vs rounding:** the business must confirm the policy. It affects 28 of 50 golden amounts.
- **Finding 2:** decide the exit code for non-COBOL failures (bad data, I/O) before this runs under a scheduler.
- **Finding 1:** correct the spec offsets so the next CR builds on accurate facts.
