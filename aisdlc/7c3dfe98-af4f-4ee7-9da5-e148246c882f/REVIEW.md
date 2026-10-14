# CR-1 Review: `CBACT04C` → Java 21

**Verdict: changes needed.** The code is correct: it is byte-identical on both goldens and I re-ran everything myself. One change is still required. The uncommitted `modernized/cbact04c/target/` build output (24 files, including the jar and `.class` files) would be merged along with the code. Remove it or ignore it first (F1). Everything else is should-fix or nit.

## Evidence (re-run by reviewer)

| Check | Result |
|---|---|
| `mvn -q -f modernized/cbact04c verify` | exit 0. 18 tests, 0 failures: Golden 2, Defect 9, Interest 2, Zoned 5 |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden`, two runs byte-identical |
| `git status --short legacy .github app` | empty: nothing protected was touched |
| CLI smoke test: `java -jar target/cbact04c.jar --parm 2022071800 --in <stock> --out /tmp/rv/out --now 2022071800000000` | rc 0. `TRANSACT`, `ACCTFILE` and `SYSOUT` `cmp`-identical to `legacy/golden/CBACT04C/` |
| Dependencies | `junit-jupiter` (test scope) only, plus the surefire and jar plugins (AC1) |

The tests really prove equivalence:
- `GoldenEquivalenceTest` compares bytes with `assertArrayEquals` against `legacy/golden/...` directly. The expected files are read from the goldens, not regenerated.
- The inputs are the stock `app/data/ASCII` files, plus `legacy/golden/CBTRN02C` for scenario 3.
- The D1 test compares acct 50 with the *input* record from the CBTRN02C golden, and pins `0000000187G`.

Correctness against spec (checked against `CBACT04C.cbl`):
- The break and EOF logic matches, including D1.
- DEFAULT fallback messages: match.
- Error text for E1-E3 and the `NNNN00xx` status format: match.
- XREF alternate key cols 26-36: match.
- Truncation: `divide(…, 2, DOWN)`. This is exact, with no `double`.
- Overpunch encoding writes zero as `{`.

## Findings

1. **should-fix (merge blocker for hygiene): build output in the change.** The 24 untracked files are under `modernized/cbact04c/target/`, and the root `.gitignore` doesn't cover them. **Fix:** add `modernized/**/target/` to `.gitignore`, or `rm -rf modernized/cbact04c/target` before commit.
2. **should-fix: a malformed `--now` crashes without writing outputs.** `Main.java` passes `--now` straight through, and `InterestCalc.java:163` `substring` throws `StringIndexOutOfBoundsException`. I verified `--now 2022`: stack trace, exit **1**, no output dir. **Fix:** in `Main`, check `now.matches("\\d{16}")`, else `usage()` (exit 2).
3. **should-fix: the spec has the wrong cycle-field columns.** `spec.md` (Fields table, and "patches cols 13-24 and 83-106") disagrees with the copybook. Under `CVACT01Y`, cycle credit/debit are at cols **79-90 / 91-102**. The code (`InterestCalc.java:143`, `patch(rec, 79, …)`) is right, and the goldens confirm it. **Fix:** correct the spec so future readers aren't misled.
4. **should-fix: the E4 test only checks the RC.** `DefectCharacterizationTest.java:128-132` doesn't assert the SYSOUT lines (`ERROR OPENING TRANSACTION CATEGORY BALANCE`, `FILE STATUS IS: NNNN0035`, `ABENDING PROGRAM`). It also doesn't cover the XREF variant, which appends `35` to the message (`InterestCalc.java:171`). **Fix:** add assertions for these lines.
5. **nit: E4 diverges from COBOL on `TRANSACT`.** On an open failure, Java still writes an empty `TRANSACT.txt` (`InterestCalc.java:50`). COBOL never reaches `OPEN OUTPUT TRANSACT-FILE` (`:309`). This doesn't matter for RC-gated schedulers. **Fix:** write `TRANSACT` only after the opens succeed, or document the difference.
6. **nit: the D5 overflow behaviour is asserted only in Java.** `ZonedTest.highOrderDigitsDropped` pins Java's mod-10ⁿ truncation. Nobody has checked that GnuCOBOL's `ADD` without `ON SIZE ERROR` truncates the same way (rather than leaving the target unchanged). The golden data doesn't exercise it. **Fix:** a one-off harness run with an oversized balance (`INPUT_DIR`) would settle it. Otherwise, record it as unverified in the README.
7. **nit: Main's argument parsing drops a trailing flag.** At `Main.java:17`, `i + 1 < args.length` silently ignores a trailing `--now` with no value, so the system clock is used. **Fix:** reject an odd argument count.
8. **nit: a garbled comment.** `InterestTest.java:32` reads `('M' would be -4 : 0000000292M)`. **Fix:** change it to `-29.24: last digit 4 negative → 'M'`.

## What a human should look at before merging

- **F1:** make sure the `target/` artifacts aren't committed.
- **D1 (last account never rewritten)** is kept on purpose. Every night, the last account's balance is wrong by its interest, and its cycle totals aren't reset. Decide whether to raise the separate CR now (intent Q2; the default was yes).
- **D3 (truncation), D2 (no fees) and D6 (wall-clock timestamps)** are business decisions that remain open. The port preserves all three.
- **`--now` defaults to UTC** (`Main.java:30`). The mainframe used local time. In production, set the container's clock or TZ deliberately, or pass `--now`.
