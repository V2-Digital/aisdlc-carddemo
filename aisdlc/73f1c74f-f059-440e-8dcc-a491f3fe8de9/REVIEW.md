# CR-1 Review: `CBACT04C` → Java 21

**Verdict: ready to merge.** All 7 acceptance criteria hold when I re-run them. The tests compare against the
untouched golden files and a real COBOL run, so they are not tautologies. There are no blockers. The should-fix items
are about robustness on paths the golden data never exercises.

## What I ran (reviewer's own runs)
| Check | Result |
|---|---|
| `mvn -q test` in `modernized/cbact04c` | 22 tests, 0 failures (Abend 3, Behaviour 10, Defect 3, Golden 2, Zoned 4) |
| `bash legacy/tests/cbact04c-negative.sh` (real GnuCOBOL) | 4/4 ok: `0000000145O`, `0000000000{`, `0000000000J`, `0000000000{` |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` |
| Jar smoke test on `app/data/ASCII` (`--clock 2022071800000000`) | rc 0, `cmp` identical to `golden/CBACT04C/{TRANSACT,ACCTFILE}.txt` |
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty, so these paths are untouched |
| pom deps | `junit-jupiter` (test scope), Surefire and the jar plugin only. AC1 is met |

## Correctness notes (verified, no action needed)
- **Field offsets:** the code uses CYC-CREDIT @78, CYC-DEBIT @90 and GROUP-ID @112, which matches `CVACT01Y.cpy`.
  The spec table says 66/78/100, which is wrong (see finding 1). `afterCbtrn02c` byte-compares records with non-zero
  cycle fields, so the golden test proves the code's offsets are right.
- **Money:** the multiply is exact, followed by one `divide(1200, 2, DOWN)`. `WS-MONTHLY-INT` and `WS-TOTAL-INT` are
  truncated to 11 digits and `ACCT-CURR-BAL` to 12, matching `S9(9)V99` and `S9(10)V99`. A −0.000125 result becomes
  a BigDecimal with signum 0, which writes `{`. Legacy writes `{` too (from the script). Correct.
- **D1:** there is no update after the loop (`InterestCalc.java:155`). It is pinned on golden data
  (`DefectTest.lastAccountNotRewritten`, plus 49/50 records changed) and on a single-account fixture.
- **Test independence:** the expected outputs are read from `legacy/golden/` and `app/data/ASCII/` in place, and
  nothing is regenerated. The negative-balance expectations in `BehaviourTest` come from the COBOL script above.

## Findings
1. **should-fix (docs):** the spec has wrong offsets: `spec.md` Record I/O table, ACCTFILE row (`CURR-CYC-CREDIT 66`,
   `CURR-CYC-DEBIT 78`, `GROUP-ID 100`). They should be **78 / 90 / 112**. The code is correct. Fix the spec so the
   next reader (CR-2) doesn't copy the error.
2. **should-fix:** `Main.java:229-238` doesn't validate `--clock`. A short or non-numeric value throws
   `StringIndexOutOfBoundsException` or `NumberFormatException` with a stack trace (exit 1). Validate it with
   `^\d{16}$`, print the usage line and return 2.
3. **should-fix:** `Main.java:261-262` uses two separate `move`s, so the output pair isn't atomic. If the second move
   fails, a new `TRANSACT.txt` sits next to a stale `ACCTFILE.txt`. Also, on abend (`:248-250`), files left in
   `--out` by an earlier run stay there, and a scheduler could read them as success. Suggestion: either require
   `--out` to be empty or delete stale outputs before you start, and use `ATOMIC_MOVE`. Otherwise, document that
   callers must key off the exit code only.
4. **nit:** `Main.java:216` uses `i + 1 < args.length`, so a trailing option without a value (e.g. `--out`) is
   silently ignored. Reject odd argument counts.
5. **nit:** the abend DISPLAY output omits the `9910-DISPLAY-IO-STATUS` line (`FILE STATUS IS: NNNN0023`) that legacy
   prints before `ABENDING PROGRAM` (`CBACT04C.cbl:388-389`, `:410-411`). Abend SYSOUT is outside acceptance (bonus
   only), but if ops tooling greps for that line, add it.
6. **nit:** the Javadoc at `InterestCalc.java:47` is garbled ("in {@link Abend#getMessage()}'s companion
   {@link #stdout()}"). It should read: "On Abend, the DISPLAY lines so far are available from `stdout()`."
7. **nit:** the code drifts from the plan in naming. The package is `com.v2.carddemo.cbact04c`, not
   `carddemo.cbact04c`. The spec's `InterestTest`/`GroupFallbackTest`/`ZeroRateTest`/`SuffixTest`/`ClockTest` are
   merged into `BehaviourTest`, though every case is present. This is fine, but update the spec's AC table or the
   traceability won't match.
8. **nit:** `InterestCalc` holds per-run state (`out`) in an instance field, so you can't reuse one instance for two
   runs (the stdout would accumulate). Document it as single-use, or reset `out` at the start of `run`.
9. **nit:** duplicate DISCGRP keys keep the **last** record (`:101`, `HashMap.put`), while xref keeps the **first**
   (`putIfAbsent`). KSDS primary keys can't be duplicates, so this is harmless, but `putIfAbsent` would be consistent.

## What a human should look at before merging
- **D1 customer impact (decision needed, separate CR):** each night the highest-numbered account with category
  balances gets an interest transaction posted, but its balance is never updated and its cycle totals are never reset.
  The rewrite preserves this on purpose.
- **Abend semantics differ from legacy by design (Q1):** legacy leaves partial ACCTFILE rewrites and a partial
  TRANSACT, while Java writes nothing. Confirm that downstream restart/recovery procedures expect this (see finding 3).
- **Default clock is UTC.** Mainframe `CURRENT-DATE` is local time. In production, pass `--clock` explicitly or
  agree on the container TZ, because TRANSACT timestamps will differ otherwise.
- Negative-balance and overflow behaviour is proven only on small fixtures plus one 4-row COBOL run. The golden
  data has no non-zero negative interest.
