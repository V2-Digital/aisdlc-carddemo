# CR-1 Review: CBACT04C to Java 21

Run `dry5-10121214` · Stage: Deploy · Independent reviewer (no approval rights)

## Verdict

**Ready to merge.** Both golden scenarios are byte-identical. I checked this in-process and again through the CLI jar. The protected paths are untouched. The findings below are should-fix or nit, and none of them changes the golden-path behaviour.

## What I ran

| Check | Result |
|---|---|
| `git diff baseline --quiet -- legacy .github app` | clean: no changes outside `modernized/` and `aisdlc/` |
| `mvn -q test` in `modernized/cbact04c` | 22 tests, 0 failures (Abend 5, Characterization 9, Golden 2, Zoned 6) |
| `mvn package` + jar on both scenarios, `diff -r` against golden | `CBACT04C OK`, `CBACT04C-after-CBTRN02C OK` (TRANSACT, ACCTFILE, SYSOUT, RC) |
| `legacy/harness/golden.sh --check` | OK, two runs byte-identical |
| `pom.xml` dependencies | only `junit-jupiter` 5.11.4 (test scope), plus the Surefire and jar plugins |

What I verified by reading the code against the spec:
- `InterestCalc.java:108` truncates the exact product with `divide(1200, 2, DOWN)`, then wraps to 11 digits. The balance wraps to 12 digits.
- A zero result is always written as `{`.
- The TRAN layout adds up to 350 bytes.
- ACCTFILE rewrites change only cols 13–24 and 79–102, and the output is in key order.
- F1 is preserved because there is no update after the loop.
- The open order matches `.cbl:182-185`.
- The XREF lookup returns the first card for an account in file order.

## Findings

1. **should-fix**: output write failure does not follow spec §4. `Main.java:26-30`: an `IOException` from `FixedFile.write` escapes `main`, so the run ends with exit 1 and a stack trace. The spec requires `ERROR WRITING TRANSACTION RECORD` / `ERROR RE-WRITING ACCOUNT FILE`, then `FILE STATUS IS: NNNN0030` and RC 16. A partial write could also leave `TRANSACT.txt` behind without `ACCTFILE.txt`.
   *Fix:* catch the exception around each write, print the §4 lines plus `ABENDING PROGRAM`, and exit 16. Write each file to `*.tmp` and move it into place.

2. **should-fix**: a bad `CARDDEMO_CURRENT_DATE` is not handled cleanly. `Main.java:38-45`:
   - a value shorter than 16 characters silently falls back to the wall clock, so a typo gives today's timestamps;
   - non-digits throw `NumberFormatException`, which ends the run with exit 1 and a stack trace.

   *Fix:* reject any value that is set but malformed, with a clear message and exit 2. Add a unit test for `Main.clock`.

3. **should-fix**: the golden F1 test never runs the Java code. `CharacterizationTest.java:65-74` (`lastAccountNotRewrittenGolden`) only compares golden files with each other, so it would still pass if the Java program rewrote account 50. `GoldenTest.afterCbtrn02c` covers the behaviour indirectly. AC5 asks for an explicit pin, including the tran amount `0000000187G`, and this test does not assert that amount.
   *Fix:* run `InterestCalc` on the CBTRN02C inputs. Assert that output account 50 equals the input line, and that `transact().get(49).substring(132,143)` is `"0000000187G"`.

4. **nit**: the truncation pin is partly circular. `CharacterizationTest.java:39-40` computes HALF_UP and DOWN on a constant inside the test, which proves nothing about the code. The real pin is the P2 literal `0000001032M`, which is fine. The plan also wanted a golden record where HALF_UP ≠ DOWN; that one is covered only implicitly by `GoldenTest`.
   *Fix:* either drop those two lines, or assert one golden TRANSACT amount where HALF_UP differs.

5. **nit**: XREFFILE is read twice, and one read swallows errors. `InterestCalc.java:52-56`, `:155-161`: `load(...)` result is discarded, and `rawLines` re-reads the file and returns `List.of()` on `IOException`.
   *Fix:* add a `load` variant that returns the list, and delete `rawLines`.

6. **nit**: field offsets are magic numbers spread through `InterestCalc.java`, for example `112,122`, `16,22`, `132,143` and `78`/`102`. The plan listed a `Records.java` file to hold them, but it was never created.
   *Fix:* use named constants per layout, such as `ACCT_GROUP = 112..122`, so each offset can be traced to the spec §2 tables.

7. **nit**: `InterestCalc.java:71` declares `BigDecimal rate = BigDecimal.ZERO` outside the loop and only assigns it inside, so the initial value is never used.
   *Fix:* make it a local declared inside the loop.

8. **nit**: `ZonedTest.java:13-14` (`positiveDigits`) builds its expected value with a noisy chain: `.movePointRight(0).setScale(2)…multiply(ONE).setScale(2)`.
   *Fix:* replace it with `assertEquals(new BigDecimal("0.0" + i), Zoned.decode(...))`.

9. **nit**: `FixedFile.read` turns trailing blank lines into all-space records. For an input ending `…\n\n`, the second empty line becomes a 50-byte or 300-byte space record. That record then abends with `0090`, or with a duplicate key on the blank key.
   *Fix:* this is defensible under D2, but spec §2 says "an empty final line is ignored". Either skip trailing empty lines or reword the spec.

## What a human should look at before merging

- **Preserved defects F1–F7** are kept deliberately, and each needs its own decision. F1 matters most: in production the last account of every run never gets its interest posted or its cycle fields reset, even though its interest transaction is written.
- **New behaviour with no COBOL equivalent:** D2 (malformed input abends with `0090`), D3 (exit 2 on bad usage), and findings 1 and 2 above. Confirm that these are acceptable for the operator runbooks.
- **Untested assumptions:** with several cards per account, the first card in file order wins (R3). Suffix wrap past 999999 is also untested. Neither appears in golden data.
- **Probe-derived expectations:** the synthetic tests use the P1–P6 COBOL results quoted in `spec.md`. They were not re-run in the Test stage or in this review.
