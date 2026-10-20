# CR-1 Test report: `CBACT04C` → Java 21

Run `e2137536-1d75-488a-9bcd-4aa967f2d9cf` · Stage: Test · **Result: all green, 0 production-code fixes needed.**

## Commands and results

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn verify` | 20 tests, 0 failures, BUILD SUCCESS (JUnit 5 only) |
| `legacy/tests/cbact04c-diff.sh` | 5/5 fixtures OK (COBOL vs Java), exit 0 |
| `legacy/harness/golden.sh --check` | OK, golden still reproduces (scenarios `CBACT04C`, `CBACT04C-after-CBTRN02C`, `CBTRN02C`) |

## Golden equivalence (AC 2, 3, 4)

`GoldenEquivalenceTest` byte-compares `TRANSACT`, `ACCTFILE`, `SYSOUT`, `RC` with `legacy/golden/`:

| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC | Byte diffs |
|---|---|---|---|---|---|
| `CBACT04C` (stock, `app/data/ASCII`) | 50 | 50 | 152 | 0 | 0 |
| `CBACT04C-after-CBTRN02C` (inputs from `golden/CBTRN02C/`) | 50 | 50 | 302 | 0 | 0 |

Total: 100 transactions, 100 account records, 454 SYSOUT lines compared, no differences.

## Other tests (20 total)

| Class | Tests | Covers (AC) |
|---|---|---|
| `GoldenEquivalenceTest` | 2 | 2, 3, 4 |
| `OverpunchTest` | 4 | 9: all 20 sign chars round-trip; −0 → `{`; D7 high-order truncation |
| `RulesTest` | 10 | 5: truncation (10.04 → 0.12, half-up gives 0.13), negative balance → −0.12, −0.000375 → `{`. 7: DEFAULT fallback + real group hit, zero rate → no tx, ids `<PARM>000001..` across accounts, rewrite touches only bytes 12–23 and 78–101. 6: `D1_lastAccountIsNeverRewritten`. Also `D7_…`, `D8_…`, `D9_…` |
| `AbendTest` | 4 | 8: missing account, xref, DEFAULT row, each input file missing → RC 16, `ABENDING PROGRAM`, status text |

## COBOL-vs-Java differential (`legacy/tests/cbact04c-diff.sh`, fixtures in `legacy/tests/cbact04c-diff/`)

| Fixture | COBOL | Java | Compared |
|---|---|---|---|
| `mixed` (5 accts, real group hit, zero rate, negative balance, truncation cases, 7 tcatbal rows) | RC 0 | RC 0 | 6 tx, 5 accts; 4 files byte-equal |
| `empty_tcatbal` | RC 0 | RC 0 | 0 tx, 5 accts; 4 files byte-equal |
| `missing_account` / `missing_xref` / `missing_default` | exit 16 | exit 16 | exit codes |

## Iterations

0 fix iterations on production code. The first test run failed 7 tests; every failure was a bug in my own test (wrong byte offset for the amount, a miscounted fixture field, wrong expected values for the rate scale). Fixed in the tests, and the Java behaviour matched the golden/COBOL throughout.

## Gaps and notes for reviewers

- **Abend fixtures compare exit code only.** `run.sh` exports no outputs when the program abends, so the partial TRANSACT/ACCTFILE/SYSOUT after an abend are checked against the spec, not against COBOL. `AbendTest` pins the SYSOUT text from the COBOL source.
- **Two cards per account (XREF duplicate-key order) is untested against COBOL.** The harness loader rejects duplicate alternate keys (file status 22), so the spec R1 "first in file order" rule can't be exercised. The stock data has no such case. Flagging it as unverified.
- Spec's `InterestTest` and `DefectTest` are folded into `RulesTest`. Same assertions, one class.
- `mvn -q verify` is what the intent asks for. The diff script builds the jar itself if `target/cbact04c.jar` is missing.
- D2–D6 have no dedicated test: D2 and D3 are visible in the golden output, D5 in `AbendTest`. D4 is visible in the golden SYSOUT (every lookup misses). D6 (suffix wrap after 999,999) is untested because it needs about 1M interest rows.
- Nothing is committed. New files: `modernized/cbact04c/src/test/`, `legacy/tests/`.
