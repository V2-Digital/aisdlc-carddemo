# CR-1 Plan: `CBACT04C` in Java 21

Implements [spec.md](spec.md). Background: [intent.md](intent.md).

## Files (in order)

All files are under `modernized/cbact04c/`. The package is `com.carddemo.cbact04c`.
1. `pom.xml`: Java 21 (`maven.compiler.release`), JUnit Jupiter 5 (test scope), Surefire. A runnable jar with a `Main-Class` manifest.
2. `ZonedDecimal.java`: overpunch decode/encode `S9(n)V99` ↔ `BigDecimal`, with high-order truncation.
3. `FixedWidth.java`: read lines (strip `\r`, pad to LRECL), write lines with LF, and fixed-field slice/overlay helpers.
4. `AbendException.java`: carries the SYSOUT lines that were already printed. Mapped to exit 16.
5. `InterestCalc.java`: the main loop (spec rules 1-7). Inputs are file paths, PARM, `Clock` and a `PrintStream` sysout. Returns the RC.
6. `Main.java`: parses the `--dd` arguments and `--sysout`, then calls `System.exit(rc)`.
7. `src/test/java/...`: `ZonedDecimalTest`, `InterestTruncationTest`, `RulesTest`, `AbendTest`, `DefectF1Test`, `GoldenEquivalenceTest`. Tests find the repo root by walking up from `user.dir` to the directory that contains `legacy/golden`.
8. `modernized/cbact04c/README.md`: how to build and run, the DD mapping, and the preserved defects F1-F6 (short).

No changes outside `modernized/cbact04c/`.

## Test strategy

- **Equivalence (AC2-4):** the two golden scenarios. Inputs come from `app/data/ASCII/{tcatbal,cardxref,acctdata,discgrp}.txt`. In the after-scenario, `ACCTFILE` and `TCATBALF` are replaced from `legacy/golden/CBTRN02C/`. PARM is `2022071800` and the clock is `2022-07-18T00:00:00Z`. The tests compare `TRANSACT`, `ACCTFILE` and `SYSOUT` with `assertArrayEquals` on bytes, and the RC with `RC.txt`.
- **Characterization (AC5-9):** small inline fixtures written to `@TempDir`. F1 is also pinned on the after-scenario golden data.
- **Cross-check against the real COBOL (no new harness code):** `legacy/tests/cbact04c_java_equiv.sh` runs `legacy/harness/run.sh CBACT04C` and the Java jar on the same `INPUT_DIR`, then diffs `TRANSACT`/`ACCTFILE`/`SYSOUT`/`RC`. It runs on both golden input sets and on one edge fixture (negative balance, rate 0, a non-DEFAULT group).
- Commands:
  ```
  cd modernized/cbact04c && mvn -q verify
  legacy/tests/cbact04c_java_equiv.sh
  legacy/harness/golden.sh --check
  git diff --stat baseline -- legacy/golden legacy/harness .github   # must be empty
  ```

## Top risks

| Risk | Mitigation |
|---|---|
| Off-by-one in loop/F1 (rewriting the last account "by accident") | Byte compare on the after-scenario, where acct 50 is the discriminator |
| Rounding drift (`double`, HALF_UP) | BigDecimal + `RoundingMode.DOWN` only. 28 golden amounts catch it |
| Overpunch sign of zero / negative encode differs from GnuCOBOL | Round-trip test, plus the `legacy/tests` script with a negative-balance fixture |
| Timestamp format (hundredths + `0000`) | Golden compare with the frozen clock, plus a unit test with non-zero hundredths |
| SYSOUT abend-path text differs from COBOL | The cross-check script runs a missing-account fixture through both and diffs SYSOUT/RC |
| Hidden bytes in untouched ACCTFILE fields | Overlay only the 3 fields. Never re-serialise the whole record |

## Effort

Build about 15 min, tests and cross-check about 10 min. **Total about 25 min.**
