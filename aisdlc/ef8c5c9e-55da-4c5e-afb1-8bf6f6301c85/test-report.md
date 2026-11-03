# CR-1 Test report

**Result: all green. 1 fix iteration (a test typo; no production code changed).**

## Commands and results

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q test` | pass, 19 tests, 0 failures |
| `legacy/harness/golden.sh --check` | OK, legacy outputs unchanged |
| `legacy/tests/cbact04c_edge.sh` | 8/8 file comparisons identical, COBOL vs Java |

## Golden equivalence (`GoldenTest`)

| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` | 50 records, byte-identical | 50, byte-identical | 152 lines, identical | 0 |
| `CBACT04C-after-CBTRN02C` | 50, byte-identical | 50, byte-identical | 302 lines, identical | 0 |

Zero diffs. Whole files are compared as bytes.

## Test inventory (→ AC)

- `GoldenTest` (2): AC2, AC3, AC9.
- `InterestTest` (4): 1164.87@15 → 14.56 truncated; −1164.87 → `0000000145O`; −0.01 → `0000000000{`; overflow wrap (D5, D6). AC4.
- `ZeroRateTest` (2): 50 type-03 rows give no transaction (50 tx from 100 rows); the account is still rewritten with cycle fields zeroed (D7). AC5.
- `RateFallbackTest` (5): group-specific rate, DEFAULT fallback, and exit 16 when DEFAULT, xref or account is missing. AC6.
- `LastAccountDefectTest` (2): `D1_*`. Acct 50 row is identical to the input (`00000019458G`), yet its 18.77 tx is written. AC7.
- `TransactLayoutTest` (2): 350 bytes, blank tails, injected clock timestamps. AC8.
- `ZonedTest` (2): codec.

## COBOL cross-check (`legacy/tests/cbact04c_edge.sh`)

CI runs it with bash from the repo root. It runs COBOL (harness `INPUT_DIR`) and the Java jar on three synthetic fixtures in
`legacy/tests/cbact04c_edge/` and compares them with `cmp`. It exits non-zero on any difference or wrong RC.

- `ok`: negative balance, −0.01, a group-specific rate (`GRPX`), DEFAULT fallback, a zero rate and the last-account defect. TRANSACT, ACCTFILE, SYSOUT and RC are identical.
- `abend_xref` and `abend_default`: SYSOUT and RC (16) are identical. The `FILE STATUS IS: NNNN0023` text flagged as unverified in `build.md` is confirmed.

The script builds the jar if missing (`mvn package`), so the Maven build must be available in CI.

## Findings

- No Java defects found. I changed no production code.
- Iteration 1: my own test had a wrong expected value for the overflow encoding (10^11+1 wraps to `0000000000A`). Fixed in the test.
- Note: `build.md` says the spec's account offsets were wrong. Tests use the corrected offsets (cycle credit/debit 79/91, group 113) and the harness agrees.
- Not covered: ACCTFILE contents after an abend are not compared with COBOL, because `run.sh` skips the unload on RC 16. The Java output follows spec R9.
- Still open for a human: separate CRs for D1 (last account), D2 (fees) and D5 (truncation). None is fixed here.
