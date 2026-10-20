# CR-1 Review: `CBACT04C` → Java 21

Run `e2137536-1d75-488a-9bcd-4aa967f2d9cf` · Stage: Deploy (independent review) · Diff vs `baseline`

## Verdict

**changes needed.** Golden equivalence holds, but interest amounts of 1,000,000.00 or more are cut short in Java and not in COBOL. I reproduced this against the harness. The fix is one line.

## What I ran

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | exit 0. 20 tests, 0 failures (Abend 4, Golden 2, Overpunch 4, Rules 10) |
| `legacy/tests/cbact04c-diff.sh` | 5/5 ok (`mixed` 6 tx / 5 accts byte-equal, `empty_tcatbal`, 3 abend fixtures exit 16) |
| `legacy/harness/golden.sh --check` | OK, two runs byte-identical, outputs match `legacy/golden` |
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty, so protected paths are untouched |
| Ad-hoc differential: `mixed` fixture, acct 1 TCATBAL bal `9999999999I` (99,999,999.99), CURR-BAL 0 | **TRANSACT and ACCTFILE differ** (see F1) |

`GoldenEquivalenceTest` is a real proof. It byte-compares Java output with the checked-in `legacy/golden/` files, and none of the expectations were regenerated. The `mixed` fixture's expected values come from running the COBOL (diff script), not from the author.

## Findings

1. **blocker: monthly/total interest wraps at 7 integer digits, not 9.** `Cbact04c.java:175-177`
   `Overpunch.fit(v, digits)` counts *unscaled* digits (`format(v, 11)` = 9 integer + 2 dp). The calls pass `9`, so `WS-MONTHLY-INT` and `WS-TOTAL-INT` (`S9(9)V99`) lose everything from 1,000,000.00 up. Evidence: with bal 99,999,999.99 at 15%, COBOL writes TRAN-AMT `0124999999I` and CURR-BAL `00124999999I`. Java writes `0024999999I` / `00024999999I`. This breaks spec R6 ("keep low 9 integer digits").
   *Fix:* `Overpunch.fit(..., 11)` on both lines. Better, rename the parameter to `width` or `totalDigits` so it matches `format`. Add this case to `legacy/tests/cbact04c-diff/` (for example a `large_balance` fixture) so the diff script pins it against COBOL. The existing `D7_…` test only covers the 12-wide balance, which is correct (`Cbact04c.java:190`).
2. **should-fix: abend-path outputs are not proven against COBOL.** `legacy/tests/cbact04c-diff.sh:19`
   The harness exports nothing on abend, so for abends the script only compares exit codes. Partial TRANSACT/ACCTFILE and the SYSOUT wording are checked only against the spec and the author's reading of the source (`AbendTest`). This is acceptable as a known gap. State it in the PR, and treat the abend SYSOUT text as unverified.
3. **should-fix: duplicate XREF acct-id order is unverified.** `Cbact04c.java:107-108`
   The "first in file order" rule (spec R1) can't be exercised, because the harness loader rejects duplicate alternate keys (test-report). The stock data has no duplicates. Production data with several cards per account could pick a different card than VSAM's AIX does. A human should confirm the real AIX ordering (insertion order for non-unique AIX) before cut-over.
4. **nit: duplicate primary keys in input text are silently last-wins.** `Datasets.java:244`
   The COBOL load would fail. Consider throwing, or at least documenting it in the README.
5. **nit: malformed sign bytes are not rejected.** `Overpunch.java:344`
   A space or other non-digit last byte produces a garbage digit (`last - '0'`). Throw `NumberFormatException` for any byte outside `0-9{A-I}J-R`.
6. **nit: `Main` gives an unhelpful error on bad args.** `Main.java:292-298`
   `--dd FOO` without `=`, or a trailing flag with no value, throws `ArrayIndexOutOfBoundsException` instead of printing usage.
7. **nit: fully-qualified `java.math.RoundingMode` inside `Overpunch.fit`.** `Overpunch.java:351`
   Import it instead, like `Cbact04c` does.
8. **nit: test classes differ from the spec table.** Spec names `InterestTest`/`DefectTest`, but these tests were folded into `RulesTest`, which the test-report says. Fine, but update the spec table or the README so traceability to the ACs isn't lost.

Checked and OK:
- Open order and messages, including D8 and the `CROSS REF FILE35` concatenation.
- DEFAULT fallback and its SYSOUT lines.
- `rate ≠ 0` gate, including negative rates.
- Truncation via `RoundingMode.DOWN` (10.04 → 0.12, −10.04 → −0.12).
- −0 normalised to `{`.
- Tran-id suffix mod 1e6.
- Rewrite touches bytes 12–23 and 78–101 only, with 10-integer-digit wrap on the balance.
- D1: no final rewrite.
- Per-record timestamp.
- ISO-8859-1, LF on the last line, CR stripped, padding to LRECL.
- No `double` anywhere.
- Only JUnit 5 + Surefire/jar plugins in the pom.

## What a human should look at before merging

- **F1 fix and a new COBOL-diffed fixture.** Re-run `mvn -q verify` and `legacy/tests/cbact04c-diff.sh` after the fix. Stock and posted golden data never exceed 1M of interest, which is why the golden tests didn't catch this.
- **XREF multi-card accounts (F3).** Confirm against production VSAM which card the AIX read returns.
- **Abend behaviour (F2).** Accept that partial outputs on abend are spec-checked only, or ask for a harness change in a separate CR.
- **Defect follow-ups.** Raise CR-3 for D1 (last account's interest never added to its balance) and D4 (group-specific rates never used), per approver Q3. Both mean real money today.
- **Commit status.** `legacy/tests/` and `modernized/cbact04c/src/test/` are uncommitted (untracked in git status). Make sure they are part of the PR.
