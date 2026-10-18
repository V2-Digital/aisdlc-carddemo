# CR-1 Review: `modernized/cbact04c/`

**Verdict: changes needed.** Both golden scenarios are byte-identical. However, the missing-DEFAULT abend prints its SYSOUT lines twice, which breaks AC 8 ("legacy error text"), and the test didn't catch it. The fix is one line.

## What I ran
| Check | Result |
|---|---|
| `mvn -q test` (JDK 21) | **20/20 pass**: Golden 2, InterestCalc 14, Zoned 4 |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` |
| `git diff baseline --stat -- legacy .github app` | empty: protected paths untouched |
| Golden test compares against the real files? | Yes. It reads `legacy/golden/*/{TRANSACT,ACCTFILE,SYSOUT,RC}.txt` byte-for-byte, so the expectations are not regenerated. |
| COBOL vs Java, account `00000000010` removed from ACCTFILE | Both RC 16; SYSOUT identical (ignoring the stderr `CEE3ABD` line) |
| COBOL vs Java, `DEFAULT 010001` removed from DISCGRP | Both RC 16; **SYSOUT differs** (finding 1) |
| Java, DISCGRP path missing | RC 16, `ERROR OPENING DALY REJECTS FILE` / `NNNN0035` (F7 kept) |

Dependencies are JUnit 5 plus Surefire and the jar plugin only. Money uses `BigDecimal` throughout. Truncation is `RoundingMode.DOWN` followed by mod 10^n, and negative zero is written as `{`. All of that matches the spec.

## Findings

1. **Blocker**: `InterestCalc.java:76-82` together with `Main.java:44`. When DEFAULT is missing, the code adds `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` to `sysout`, then puts the same two lines in the `Abend`. `Main` appends them again. Observed diff against COBOL:
   ```
   4a5,6
   > DISCLOSURE GROUP RECORD MISSING
   > TRY WITH DEFAULT GROUP CODE
   ```
   Fix: `throw new Abend("ERROR READING DEFAULT DISCLOSURE GROUP", "FILE STATUS IS: NNNN0023");`.
2. **Should-fix**: `InterestCalcTest.java:135-139` (`missingDefault`) only checks `e.sysout().get(2)`, which is why finding 1 got through. Assert the full SYSOUT tail as produced by `Main.run`, for all three abend paths (`missingXref` at :128-132 checks just one line too).
3. **Should-fix**: `InterestCalcTest.java:156-160` (`suffixWrapsAfter999999`) is a tautology. It tests `(999_999 + 1) % 1_000_000`, not the production code. Either move the suffix step into a testable helper (e.g. `Zoned`/`InterestCalc.nextSuffix`) and test that, or drop the claim of F5 suffix coverage.
4. **Should-fix**: `Main.java:41-42`. Failing to write `TRANSACT`/`ACCTOUT` (bad path, or the option left out, which causes an NPE) escapes as an exception with JVM exit 1, not 16. Map output I/O failures to an `Abend` (`ERROR OPENING TRANSACTION FILE`, status 0035) so the 0/16 contract holds in the container.
5. **Nit**: `Main.java:39-40` loads inputs in the order TCATBAL, XREF, ACCT, DISCGRP. COBOL opens TCATBAL, XREF, DISCGRP, ACCT, TRAN (:234-323). If several inputs are missing, a different open error is reported first. Reorder to match.
6. **Nit**: `Main.java:74` hardcodes `"35"` after the XREF open message. COBOL appends the real status. That's fine while the only failure is "missing file", but add a comment.
7. **Nit**: `Main.java:83` silently falls back to the system clock when `CARDDEMO_CURRENT_DATE` is set but isn't 16 digits. The harness README warns that non-16-digit forms make runs non-reproducible. Consider rejecting a malformed value.
8. **Nit**: `InterestCalcTest.java:43-51` (`truncatesNotRounds`) has a garbled comment and never asserts the 1945.87 → 24.32 case it mentions. Clean up the comment and add the assertion.
9. **Nit**: `Main.java:9` has an unused `import java.time.Instant`. `InterestCalc.java:39,41` uses fully qualified `java.util.HashMap`; import it instead.
10. **Nit**: `InterestCalc.java:38`. If ACCTFILE contains duplicate account ids, the last one silently wins. A KSDS can't hold duplicates, so this is fine, but it's worth a one-line comment.

## What a human should look at before merging
- Confirm findings 1–2 are fixed. Then re-run `mvn test` and the DISCGRP-without-DEFAULT comparison above. COBOL SYSOUT ends: record, `MISSING`, `TRY`, `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM`.
- **F1 (last account never rewritten)** is preserved on purpose. Account `00000000050` is charged 18.77 in TRANSACT, but its balance doesn't change. Raise the follow-up CR before this goes live.
- **F9**: INTCALC is scheduled `MONTHLY` in Control-M, not nightly. Make sure the container scheduler keeps it monthly.
- Abend-path SYSOUT is now partly verified against real COBOL (missing account matches; missing DEFAULT doesn't yet). The missing-xref path is still checked only against the source.
