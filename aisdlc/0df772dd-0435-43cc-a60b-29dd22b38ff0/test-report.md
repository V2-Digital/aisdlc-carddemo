# CR-1 Test report — `CBACT04C` Java 21

**Result: green.** 26 JUnit tests pass; `golden.sh --check` OK. No changes to `app/`, `legacy/golden|harness`, `.github/`. No production code changed in this stage.

## Commands
```sh
cd modernized/cbact04c && mvn test      # Tests run: 26, Failures: 0, Errors: 0
legacy/harness/golden.sh --check        # "two runs byte-identical", "OK - outputs match legacy/golden"
```

## Results
| Class | Tests | Covers (AC) |
|---|---|---|
| `GoldenEquivalenceTest` | 3 | `stock` and `afterCbtrn02c`: TRANSACT, ACCTFILE, SYSOUT, RC byte-equal to golden, 50 + 50 records each (AC 1, 2, 10). `defectD1_lastAccountNotRewritten`: acct 50 equals `golden/CBTRN02C/ACCTFILE.txt`, tx amount `0000000187G` (AC 3) |
| `ZonedTest` | 4 | every overpunch char round-trips, known values, wrap, bad field |
| `BehaviourTest` | 19 | D1 (also single account), D2 (0.40@15% → `0000000000{`), D3 (−100.00 → `0000000012N`, balance 998.75), D4, D6 (interest overflow; tran-id suffix wraps `999999`→`000000` over 1,000,001 rows), D7 (first card), D8 (text), default fallback and group-specific rate, DEFAULT missing → 16, rate 0 → rewrite, no tx, contiguous IDs + blank filler/desc tail + timestamp, missing account / XREF / file / bad PARM → 16, empty TCATBALF → RC 0 unchanged (AC 4-8) |

Records compared against golden: 2 scenarios × (50 TRANSACT + 50 ACCTFILE + SYSOUT 152 / 302 lines + RC). Diffs: none.

## Iterations
1 fix iteration, of 5 allowed. All failures were defects in my test fixtures, not in the program: unsigned last bytes in numeric fields, a wrong temp filename, a fixture whose second account lacked a group. The Java program was not changed.

## Gaps / caveats
- The D6 interest-overflow test computes its expected value with `Zoned.encode`, so it checks wiring and that the fixture really overflows, not independent wrap arithmetic. No COBOL reference for overflow exists.
- The optional COBOL cross-check (`legacy/tests/*.sh` running the truncation/negative fixtures through `run.sh`) was **not** added. The truncation expectation rests on COBOL semantics and the golden data (golden 14.560875 → 14.56 matches). No `legacy/tests/` script was needed since COBOL is unchanged.
- D7 uses file order, not the BDB alternate-key order. The sample data has no duplicates.
- The 1M-row wrap test adds about 7 s to `mvn test`.

## For a human to decide
- D1 (last account never rewritten) is the high-impact defect. It is preserved and pinned. Raise it as a separate CR.
- D2-D9 stay preserved. The only intentional deviation is the Q3 PARM check, which exits 16 on a bad PARM.
