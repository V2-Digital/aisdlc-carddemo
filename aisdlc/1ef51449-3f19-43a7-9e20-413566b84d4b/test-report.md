# CR-1 Test report — `CBACT04C` Java 21

**Result: green.** 20 JUnit tests pass, the golden check is OK, and the new COBOL script passes.

## Commands
| Command | Result |
|---|---|
| `mvn -f modernized/cbact04c test` | 20 run, 0 failures (Golden 2, Interest 5, Defect 4, Abend 6, Zoned 3) |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` |
| `bash legacy/tests/cbact04c_negative.sh` | 4/4 checks ok, exit 0 |

## Golden equivalence (AC 2, 3, 4, 8: PARM `2022071800`, clock `2022071800000000`)
| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` (stock) | 50×350 byte-identical | 50×300 byte-identical | 152 lines identical | 0 = 0 |
| `CBACT04C-after-CBTRN02C` | 50×350 byte-identical | 50×300 byte-identical | 302 lines identical | 0 = 0 |

0 diffs. Comparison uses `Files.mismatch` on the files, plus file sizes (351 and 301 bytes per record with LF).

## Other acceptance criteria
- **AC5:** 1164.87 at 15.00 gives 14.56. 48 of 50 amounts in scenario 3 are truncated, recomputed independently from `CBTRN02C/TCATBALF` and `discgrp`.
  Negative values truncate toward zero: -100.00 gives -1.25 and -1.27 gives -0.01. A value where truncation and rounding differ is covered too (100.55 gives 1.25, not 1.26).
- **AC6 (D1):** account 50 is byte-identical to `CBTRN02C/ACCTFILE` (balance 1945.87), while tran `2022071800000050` is `0000000187G`.
  Exactly 49 accounts change. The tests are named `D1_*`.
- **AC7:** a missing DEFAULT row, account or xref gives exit 16 and the expected SYSOUT tail (status `NNNN0023`, `ABENDING PROGRAM`). A missing group falls back to DEFAULT. A missing input file also gives exit 16.
- **D4:** an overflow of the monthly amount wraps (8,333,324,999.99 stored as 333,324,999.99). `ZonedTest` round-trips all 20 sign characters.
- **D3, D5** are pinned: type/cat/source are fixed, and all 50 lookups in the stock data miss and fall back.
- **COBOL cross-check (R4):** `legacy/tests/cbact04c_negative.sh` runs the real COBOL (harness, `INPUT_DIR` fixture) on balances -100.00, -1.27 and 0.
  It got `0000000012N`, `0000000000J` and a zero-amount transaction. These are the same values `InterestTest` expects, so the negative case rests on COBOL evidence.

## Iterations
- **Fix iterations on Java: 0.** No production code changed in this stage.
- Test-authoring corrections: 2 rounds. They were wrong expectations in my new tests, not code bugs.
  - Overpunch arithmetic in expected values.
  - The extra zero-amount transaction for a rate-non-zero record with zero balance.
  - A COBOL fixture off by a factor of 10.

## Notes and gaps
- **Rate ≠ 0 with a zero balance still writes a transaction (amount 0).** This is legacy behaviour and is matched.
  The spec's "rate 0 writes none" is unchanged.
- **R2 (lowest card wins) is not tested.** The golden data is 1:1, so it stays an assumption. I did not add a test for it.
- **Abend partial outputs are not gated** (as planned). Only the exit code and the SYSOUT tail are checked.
- **Package name:** the tests use `com.v2.carddemo.cbact04c`, as in the build, not the plan's `carddemo.cbact04c`.
- **CI:** `legacy/tests/cbact04c_negative.sh` is the only script CI runs. It runs the real COBOL, so it needs GnuCOBOL.
- **Not done:** the stderr line `CEE3ABD` is not asserted.
