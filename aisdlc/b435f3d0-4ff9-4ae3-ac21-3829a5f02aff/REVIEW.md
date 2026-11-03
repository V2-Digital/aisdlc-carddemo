# CR-1 Review: `CBACT04C` → Java 21

**Verdict: ready to merge.** Both golden scenarios byte-match on all 4 outputs, I re-ran the tests myself, the protected paths are untouched, and the money arithmetic matches COBOL truncation. I found no blockers.

## Evidence (re-run by the reviewer)
| Check | Result |
|---|---|
| `mvn -q test` in `modernized/cbact04c` | exit 0 (16 tests; `zoned round-trips checked: 500`) |
| Jar, after-CBTRN02C inputs, `--now 2022071800000000` | rc 0. `cmp` TRANSACT, ACCTFILE, SYSOUT, RC vs `legacy/golden/CBACT04C-after-CBTRN02C/`: all identical |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` |
| `git diff baseline --stat` | only `aisdlc/` and `modernized/cbact04c/` (19 files, +986). Nothing changed in `legacy/`, `.github/` or `app/` |
| POM | `junit-jupiter` 5.10.2 (test scope), plus the surefire and jar plugins. No other dependencies |

## Correctness against the spec
- **Interest:** `bal × rate / 1200` with `divide(.., 2, DOWN)`, then `remainder(10^9)`, which truncates toward zero and keeps the sign. This matches COMPUTE without ROUNDED into S9(9)V99 (`InterestCalc.java:156-158`).
- **Balance:** `Zoned.encode` takes `mod 10^width` of the absolute value, which gives S9(10)V99 high-order truncation. `-0` encodes as `{` (`Zoned.java:35-43`).
- **R1:** there is no rewrite after the loop (`InterestCalc.java:164`). `DefectR1Test` pins it against `golden/CBTRN02C` (acct 50 balance `00000019458G`, transaction `0000000187G`).
- **Tests are not tautologies:** the golden tests compare with `Files.mismatch` against the committed `legacy/golden` files and do not regenerate them. `InterestTest` uses 1165.50, a case where HALF_UP gives a different result.
- **Order:** files open in the COBOL order (`:182-186`). The rate fallback and abend texts match `:415-460`, and the DEFAULT-miss SYSOUT parity was checked by hand against the COBOL (test-report).

## Findings
1. **should-fix**: open-error paths are untested and their message is improvised. See `InterestCalc.java:88-91`.
   - The XREF message hard-codes `"35"` with an inline comment. COBOL appends the real `XREFFILE-STATUS` (`CBACT04C.cbl:263`). All four open errors also assume status `0035`.
   - **Suggestion:** delete one input file in a temp dir, run it once through `run.sh` with `INPUT_DIR`, and pin the COBOL's SYSOUT tail in an `AbendTest` case. Replace the string concatenation with a `"ERROR OPENING CROSS REF FILE35"` constant that has a short comment, or pass the status through.
2. **should-fix**: the timestamp timezone. See `Main.java:17` and `InterestCalc.java:178`.
   - The default clock is UTC, as the spec says. COBOL `CURRENT-DATE` is local time, though.
   - In a container in production, `TRAN-ORIG-TS`/`TRAN-PROC-TS` will differ from the mainframe by the UTC offset.
   - **Suggestion:** the Deploy owner should decide: either set `TZ`/`--now` explicitly, or use `Clock.systemDefaultZone()`. The golden output is not affected, because tests inject the clock.
3. **nit**: duplicate ACCTFILE ids silently collapse. `TreeMap.put` keeps the last one (`InterestCalc.java:93-95`). A KSDS load would reject them. The input is trusted, but a duplicate check, or a comment saying it is out of contract, would help.
4. **nit**: errors outside the COBOL model surface as Java exceptions.
   - A malformed zoned field throws `IllegalArgumentException` or `NumberFormatException` (`Zoned.java:26-29`). An `IOException` gives a stack trace and exit 1, not RC 16.
   - This is acceptable, but the README should say so in one line so ops know what non-16 exits mean.
5. **nit**: `Main` CLI parsing is loose.
   - With an odd argument count, the trailing flag is silently ignored (`Main.java:18`).
   - The PARM is padded or cut to 10 characters without a warning (`InterestCalc.java:42`). That matches COBOL `PIC X(10)`, but a short PARM is probably an operator error.
6. **nit**: the R5 tran-id suffix wrap (`% 1_000_000`, `InterestCalc.java:159`) has no test. A small refactor would make the suffix seedable, so a 2-row test could pin `999999 → 000000`.
7. **nit**: R9 (spaces rather than `x'00'` in DESC tail/FILLER) is listed in the spec but missing from the README defect list (`modernized/cbact04c/README.md:11-17`).

## What a human should look at before merging
- **Finding 2 (timezone):** decide which clock production uses before the job is scheduled. It changes transaction timestamps.
- **R1 follow-up:** each nightly run bills interest to the last account in TCATBAL without posting it to the balance. This is preserved on purpose; confirm a separate CR is raised (Cards Finance owns it, per Q3).
- **R2 (no fees) and R3 (blank `ACCT-GROUP-ID`, so DEFAULT rates always apply):** both are carried over unchanged. Confirm the owners (CR-2 for fees, data team for R3).
- **Limit of the proof:** negative balances, overflow and duplicate XREF are only covered by unit tests or reasoning, not by golden data.
