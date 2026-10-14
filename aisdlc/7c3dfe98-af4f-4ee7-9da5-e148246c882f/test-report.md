# CR-1 Test report

**Result: all green. 18 tests, 0 failures. 4 fix iterations, all in my test code. No production code changed.**

## Commands and results

| Command | Result |
|---|---|
| `mvn -q -f modernized/cbact04c verify` | 18 tests, 0 failures (JUnit 5 only) |
| `legacy/harness/golden.sh --check` | OK, two runs byte-identical, outputs match `legacy/golden` |
| CLI smoke: `java -jar target/cbact04c.jar --parm 2022071800 --in <stock> --out /tmp/o --now 2022071800000000` | rc 0. `TRANSACT`, `ACCTFILE` and `SYSOUT` are `cmp`-identical to `legacy/golden/CBACT04C/` |
| `git diff --stat -- legacy .github` | empty |

## Tests (→ AC)

| Test class | Covers | AC |
|---|---|---|
| `GoldenEquivalenceTest` (2 tests) | Both scenarios compared byte-for-byte on `TRANSACT`, `ACCTFILE`, `SYSOUT` and RC. | 2, 3, 4 |
| | Sizes: 50 × 351 B TRANSACT (350 + LF), 50 × 301 B ACCTFILE (300 + LF). | |
| | SYSOUT is 152 lines (stock) and 302 lines (after CBTRN02C). | |
| `InterestTest` | 2339.97 × 15.00 → `0000000292D`. A negative balance truncates toward zero (`…292M`). | 5 |
| `ZonedTest` | Overpunch round-trip, negative zero → `{`, high-order truncation, garbage rejected. | R1, D5 |
| `DefectCharacterizationTest` | | |
| ↳ `D1_lastAccountNotRewritten…` | Scenario 3: acct 50 has txn `0000000187G`, and its record is identical to the input. | 6 |
| ↳ `D1_otherAccountsAreRewritten` | Balance updated, cycles zeroed. | |
| ↳ `D4_defaultFallback…` | Both messages are printed and the DEFAULT rate is used. | 7 |
| ↳ `E1`, `E2`, `E3`, `E4` | Abend RC 16, SYSOUT tail lines as in the spec. | 7 |
| ↳ `zeroRate…` | No transaction, cycles zeroed, balance unchanged. | 8 |
| ↳ `emptyTcatbal…` | Banners only, ACCTFILE = input. | edge |

Records compared: 2 scenarios × (50 TRANSACT + 50 ACCTFILE + 152 or 302 SYSOUT lines + RC).

## Iterations

1. `ZonedTest` expected a negative zero to round-trip as `}`. The spec says zero is always `{`, so I fixed the test. I also fixed a wrong expectation for the high-order digit drop.
2. My synthetic fixtures used 11-digit values in 12-digit fields. Fixed.
3. Amount column offsets were wrong in my tests (the amount is cols 133-143). Fixed. A zero-balance category at a non-zero rate does write a 0.00 txn, which is legacy-correct. I corrected my expectation.
4. A wrong hand-computed expected value (0.10 = `0000000001{`). Fixed.

In every case the Java was right and the test was wrong. The golden diffs passed on the first run.

## Left failing / notes

- Nothing is failing.
- `legacy/tests/*.sh` was not added. This is a Java rewrite, and the Java tests read the goldens directly (per the plan).
- Not covered: E4 only checks the missing-file path generally (RC 16, no ACCTFILE written). It does not check the per-file message text. R2 (short PARM) and D5 overflow are covered only at unit level (`ZonedTest`).
- **Human decisions:** raise D1 (last account never rewritten) as its own CR, and decide on D3 (truncation vs rounding).
