# CR-1 Review: `modernized/cbact04c`

**Verdict: changes needed.** The output matches the golden files byte for byte on both scenarios, and the differential test against GnuCOBOL passes. But the TRANSACT output file is never "opened" up front. If its path is missing, the job exits 0, rewrites ACCTFILE and writes no interest transactions. That breaks AC4 and can move money silently.

## What I ran (reviewer, fresh session)

| Command | Result |
|---|---|
| `mvn -q -f modernized/cbact04c test` | green (21 tests) |
| `mvn -q -f modernized/cbact04c package -DskipTests` then `legacy/tests/cbact04c-diff.sh` | `ok` for TRANSACT, ACCTFILE, SYSOUT, RC |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` |
| `git diff baseline --stat -- legacy/golden legacy/harness .github app` | empty: protected paths untouched |
| jar run, `DD_TRANSACT` unset | prints `END OF EXECUTION…`, **rc=0**, ACCTFILE rewritten, no TRANSACT |
| jar run, `DD_TRANSACT=/nonexistent/dir/T` | `END OF EXECUTION…` already printed, then a Java stack trace, **rc=1** (not 16) |

**The tests prove real equivalence.** `GoldenEquivalenceTest` byte-compares against the files in `legacy/golden/`; nothing is regenerated. D1 is pinned both ways: account 50 is unchanged and accounts 1-49 are all rewritten. The differential script runs real COBOL on data the golden files never exercise: a direct group hit, negatives, −0.00x and overflow. CI runs it (`.github/workflows/ci.yml:39`).

**Money handling is right.** All arithmetic uses `BigDecimal`, with no `double`. Division is exact and truncated `DOWN` to scale 2. `fit` truncates high-order digits at 9/10 integer digits, matching `WS-MONTHLY-INT`/`WS-TOTAL-INT` (S9(9)V99, CBL:168-169) and `ACCT-CURR-BAL` (S9(10)V99). Zero is written as `{` (+0), and the differential test confirms this.

## Findings

1. **blocker:** `Main.java:43-50`, `InterestCalc.java:50-60`. COBOL opens TRANSACT OUTPUT before any processing (CBL:309-318). If that fails, it abends with `ERROR OPENING TRANSACTION FILE` / `FILE STATUS IS: NNNN…` / `ABENDING PROGRAM` and exit 16. The Java code has two failure modes here:
   - With `DD_TRANSACT` unset, the TRANSACT write is skipped but ACCTFILE is still rewritten, and the exit code is 0.
   - With an unwritable path, `IOException` escapes after `END OF EXECUTION`, and the exit code is 1.

   *Fix:* in `run()`, after the ACCTFILE open, check that `DD_TRANSACT` is set and its parent is writable (or create or truncate the file). On failure, `fail("ERROR OPENING TRANSACTION FILE", null, "0035")`, matching the COBOL open order. Wrap the final writes so an `IOException` maps to the abend lines plus exit 16. Add one `AbendTest` case.
2. **should-fix:** `Main.java:49-51`. ACCTFILE is overwritten in place, non-atomically, and after TRANSACT. A crash mid-write can leave a truncated account master. *Fix:* write to `ACCTFILE.tmp`, then `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`.
3. **should-fix:** `Zoned.java:26`, `Main.java:58-62`. Bad data crashes the JVM with rc=1 and a stack trace, never with the exit 16 an operator expects:
   - A non-numeric zoned field, such as a blank balance, throws `NumberFormatException`.
   - A malformed `CARDDEMO_CURRENT_DATE` that is not 16 characters falls back to the system clock without a warning; one that is 16 characters but non-numeric throws.

   *Fix:* at minimum, catch `RuntimeException` in `Main.run` and exit non-zero with a clear message. Pin the chosen behaviour with a test, and note in the README that COBOL data-exception behaviour is not replicated.
4. **should-fix:** `Main.java:49`. `!calc.accountFile().isEmpty()` means an empty ACCTFILE is never written back. That is harmless today, but it is an unexplained special case. *Fix:* write whenever `DD_ACCTFILE` is bound.
5. **nit:** `Main.java:24-26`. The `run(String[] …)` overload is unused dead code. Remove it.
6. **nit:** `InterestCalc.java:22`. The field comment ("output paths and the DISPLAY sink") describes more than `in` holds. Reword it to "Input datasets by DD name".
7. **nit:** `InterestCalc.java:53`. `"ERROR OPENING CROSS REF FILE35"` hard-codes the status that COBOL concatenates (CBL:263). Build it as `"ERROR OPENING CROSS REF FILE" + status.substring(2)` so a reader sees why.
8. **nit:** `DefectCharacterizationTest.java:61`. The stray `Fx.w(d, "T", List.of());` does nothing. Remove it. The D2 comment ("rounding would also give .56") is muddled; state only that 0.129 → 0.12.
9. **nit:** `AbendTest` A2, A3 and missing-file. These do not assert `CEE3ABD: ABEND U0999` on stderr; only A1 does. Add the one-line assert.
10. **nit:** `Datasets.pad` silently truncates input lines longer than LRECL. That is acceptable for canonical unloads, but a one-line comment would help.

## What a human should look at before merging

- **Finding 1:** make sure the fix lands, then re-run `mvn test` and `legacy/tests/cbact04c-diff.sh`. It is a small change, and the golden paths are unaffected.
- **Abend paths A1-A3 and the open-error texts are verified against the spec, not against GnuCOBOL.** The differential script covers only the success path (test-report "Gaps"). I spot-checked the texts against CBL:245-455 and they match, but you might want one COBOL-vs-Java abend scenario in the diff script.
- **D1 (last account never rewritten, so interest is lost every night) is preserved as instructed.** Raise the fix CR now. D2 (truncation vs rounding) needs a business decision.
- The package is `com.v2.carddemo.cbact04c`, not the planned `carddemo.cbact04c`. This deviation is documented in build.md and is cosmetic.
- The Dockerfile is a run note only, not built (in scope per intent).
