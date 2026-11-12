# CR-1 Review — `CBACT04C` → `modernized/cbact04c/`

**Verdict: changes needed.** The golden scenarios are byte-identical, but I ran the real COBOL on a negative sub-cent interest and it writes `+0` (`{`). The Java writes `-0` (`}`). The spec and a test pin the wrong value.

## What I ran
| Check | Result |
|---|---|
| `mvn -q -B -f modernized/cbact04c verify` | Green: 26 tests (Golden 2, Interest 5, Characterization 15, ZonedDecimal 4) |
| `legacy/harness/golden.sh --check` | OK. Outputs match golden, two runs byte-identical |
| `bash legacy/tests/cbact04c_abend.sh` | OK. COBOL RC=16, tail `ABENDING PROGRAM` |
| `git diff baseline --stat` | No changes under `legacy/golden/`, `legacy/harness/` or `.github/`. 16 files added |
| Deps (`pom.xml`) | `junit-jupiter` 5.10.2 (test) + Surefire 3.2.5 only |
| Golden test quality | Real `assertArrayEquals` on file bytes against `legacy/golden/*`. Inputs come from `app/data/ASCII` / `golden/CBTRN02C`. Expectations are not regenerated |
| COBOL vs Java, ad hoc (`INPUT_DIR`) | Interest overflow (99,999,999.99 × 99.99%): both `3333249999A` ✅. Balance wrap 9,999,999,999.99 + 15.00: both `00000000149I` ✅. **Negative-zero: differs ❌** (finding 1) |

## Findings

1. **Blocker: negative-zero sign is invented and doesn't match COBOL.**
   `Cbact04c.java:167-170` (`negZero` on the interest), `:201-203` (`negZero` on the balance), and the `totalNegZero` state at `:48, :108, :169`.
   Evidence, same inputs to both:
   - TCATBAL bal `-0.05` (`0000000005}`) at 15%: COBOL TRAN-AMT = `0000000000{`, Java = `0000000000}`.
   - Account bal `-0` (`00000000000}`) with that -0 interest: COBOL rewrites it as `00000000000{`, Java keeps `}`.

   GnuCOBOL normalises a zero result to a positive sign. The `InterestTest.negativeSubCentInterestIsNegativeZero` test (`InterestTest.java:38`) and spec edge case `spec.md:68` assert the wrong behaviour. The test was never cross-checked against COBOL.
   *Fix:* delete `negZero`/`totalNegZero` and the `encode(…, boolean)` overload (`ZonedDecimal.java:24`). Always encode 0 as `{`. Change the test to expect `0000000000{` and correct `spec.md:68`. Add a small COBOL-vs-Java check for this case (e.g. alongside `legacy/tests/cbact04c_abend.sh`). Real data with small negative (credit) balances would hit this path, and downstream byte comparison would fail.

2. **Should-fix: the default clock zone is hard-coded to UTC.** `Cbact04c.java:192` (`now.atZone(ZoneOffset.UTC)`) and `:227` (`Clock.systemUTC()`). COBOL `CURRENT-DATE` is the job's *local* time. In a container whose TZ isn't UTC, the timestamps would differ from the mainframe.
   *Fix:* use `clock.getZone()` in `timestamp()` and default to `Clock.systemDefaultZone()`. Keep the tests' fixed UTC clock. A human should confirm the intended TZ (see below).

3. **Should-fix: bad numeric input crashes with a Java stack trace (exit 1), and no outputs or SYSOUT are written.** `ZonedDecimal.decode` throws `NumberFormatException`/`IllegalArgumentException` (`ZonedDecimal.java:17-19`), and `run` only catches `Abend`. I saw this when I fed it `9999.9`. COBOL would not exit with RC 1, so ops runbooks keyed on RC 16 won't catch it.
   *Fix:* at minimum, document it. Better: catch the exception in `run`, DISPLAY the record and take the abend path with rc 16. Flag it as a deliberate deviation.

4. **Nit: CLI parsing silently ignores a trailing odd argument.** `Cbact04c.java:214` (`i + 1 < args.length`), e.g. `--clock` with no value. *Fix:* reject odd `args.length` with the usage message.

5. **Nit: a stray block.** `Cbact04c.java:71-73` has a bare `{ … }` around the ACCTFILE write. Remove it.

6. **Nit: `55 * 0`.** `CharacterizationTest.java:31` reads oddly. Use `0` and check debit (col 91) as well, as `cycleZeroedWhenInterestZero_D6` does.

7. **Nit: no direct D5 test.** The tran-id suffix wrap isn't tested (`test-report.md` says so). Making the starting suffix injectable, or unit-testing `(n+1) % 1_000_000`, would pin it without 1M records.

## What a human should look at before merging
- **Finding 1 must be fixed.** Re-run `mvn verify` and the golden check afterwards. It doesn't show up in the golden data because no balance there is negative.
- **Timezone** for `--clock` default (finding 2): should container runs stamp local time (like the mainframe) or UTC?
- **Partial outputs after an abend** (spec R9): Java writes TRANSACT-so-far and a partially rewritten ACCTFILE. This was not compared with COBOL, because the harness leaves no outputs on abend. Confirm the downstream restart procedure expects this.
- **New `legacy/tests/` directory:** approved in the plan, but it now sits next to the judged `legacy/harness/`. Confirm it's the right home.
- Defects D1-D7 are preserved as specified and still need a separate fix decision (especially D1: account 50's interest is charged as a transaction but never posted to its balance).
