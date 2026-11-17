# CR-1 Test report

**Result: all green.** 22 JUnit tests pass, the new legacy script passes and `golden.sh --check` passes.

## Commands and results
| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q test` | 22 tests, 0 failures (Abend 3, Behaviour 10, Defect 3, Golden 2, Zoned 4) |
| `bash legacy/tests/cbact04c-negative.sh` | 4/4 amounts match the real COBOL run, exit 0 |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` (legacy untouched) |

## Equivalence (AC2, AC3): byte-identical
| Scenario | TRANSACT | ACCTFILE | stdout vs SYSOUT.txt |
|---|---|---|---|
| `CBACT04C` (stock, `app/data/ASCII`) | 50 records, identical | 50 records, identical | 152 lines, identical |
| `CBACT04C-after-CBTRN02C` | 50 records, identical | 50 records, identical; 49 differ from input | 302 lines, identical |

## Characterization (AC4-AC6)
- **D1 last account:** account 50 gets tran `2022071800000050`, amount `0000000187G`, and its ACCTFILE record equals the input (`DefectTest`). A one-account run also leaves the account unchanged.
- **D2 truncation:** 1164.87 gives 14.56, 79.99 gives 0.99 (rounding would give 1.00), and negatives truncate toward zero. The negative expectations are taken from the real COBOL run in `legacy/tests/cbact04c-negative.sh`:
  -1164.87 gives `0000000145O`, -0.01 gives `0000000000{`, -0.99 gives `0000000000J` (-0.01).
- **-0.00 sign (open item from build):** legacy writes a **positive** zero (`{`). `Zoned.format` already does the same, so no code change was needed.
- Also covered: S9(9)V99 high-order overflow, DEFAULT fallback (and a group hit that skips it), rate 0 (no tran and no suffix use, but the account is rewritten with cycle fields zeroed), the suffix running across accounts, timestamps from the clock and not PARM, and PARM padded or cut to 10.
- **AC6 abends:** missing account, xref or DEFAULT rate each give exit 16, the matching DISPLAY lines plus `ABENDING PROGRAM`, and an empty `--out`.

## Iterations
1 fix iteration, in the **tests only** (my fixtures had wrong field widths and a few expected values were wrong). No production code changed. Nothing is left failing.

## Files added
- `modernized/cbact04c/src/test/java/com/v2/carddemo/cbact04c/*Test.java`, `Fx.java`
- `legacy/tests/cbact04c-negative.sh` and `legacy/tests/cbact04c-negative/TCATBALF.txt`. CI picks the script up.

## Gaps
- Only the first `01/0001` category was used for negative balances (the sample rates make other negatives zero).
- The suffix wrap at 999,999 is not tested (it needs 1M rows).
- The `--clock`-less (system clock) CLI path is untested.
