# CR-1 Plan: `CBACT04C` in Java 21

Spec: [spec.md](spec.md) · Intent: [intent.md](intent.md)

## Files, in order

1. `legacy/tests/cbact04c-edge.sh` runs the COBOL via `INPUT_DIR=… legacy/harness/run.sh CBACT04C` on synthetic inputs. It prints the SYSOUT and RC for E1, E2 and E3, plus TRANSACT for a group-specific rate and a small negative balance. Its output settles the exact expected text before the Java is written.
2. `modernized/cbact04c/pom.xml`: Java 21, junit-jupiter 5.x, surefire. Builds a runnable jar via the manifest only, no plugins beyond the defaults.
3. `src/main/java/com/carddemo/cbact04c/`:
   - `Zoned.java`: overpunch decode/encode, fixed-width scale-2 `BigDecimal`, modulo truncation.
   - `FixedFile.java`: read lines (strip CR, pad to LRECL) and write lines.
   - `Cbact04c.java`: the R1-R8 core. `run(parm, inDir, outDir, Clock, PrintStream out, PrintStream err)` returns the exit code. Abend is an internal exception mapped to 16.
   - `Main.java`: CLI wrapper calling `System.exit(run(...))`.
4. `src/test/java/...`: `OverpunchTest`, `InterestTest`, `GoldenTest`, `DefectTest`, `AbendTest`, `RateLookupTest` (AC map in spec). Tests stage inputs into `@TempDir` as `<DD>.txt`:
   - Stock inputs: `app/data/ASCII/tcatbal.txt`, `acctdata.txt`, `cardxref.txt`, `discgrp.txt`.
   - Goldens are read from `../../legacy/golden/`.
5. `modernized/cbact04c/README.md`: how to build and run, and the list of preserved defects.

## Test strategy

- Equivalence: the golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C` compare TRANSACT, ACCTFILE and SYSOUT byte for byte, plus RC 0.
- Clock fixed at `2022-07-18T00:00:00.00`, PARM `2022071800`.
- Characterization: F1 (`DefectTest`), F2/F6/F9 (`InterestTest`), E1-E3 (`AbendTest`), group rate (`RateLookupTest`). Expected values come from the step 1 COBOL runs, not guesses.
- Commands:
  ```
  legacy/tests/cbact04c-edge.sh
  mvn -q -f modernized/cbact04c test
  legacy/harness/golden.sh --check     # unchanged legacy still green
  ```

## Top risks

| Risk | Mitigation |
|---|---|
| Arithmetic drift (rounding, sign of zero, overflow) | `BigDecimal` only, `RoundingMode.DOWN`, explicit modulo; zero/negative cases confirmed by COBOL edge run |
| Overpunch/sign encoding mismatch | Exhaustive round-trip test; golden files contain both `{` and `}`/`J-R` signs |
| Timestamp/clock differences break byte identity | Injected `Clock`; hundredths truncated, `0000` suffix pinned in golden test |
| Abend text/order differs from COBOL | Expected lines captured from `legacy/tests/cbact04c-edge.sh`, not from reading code |
| Accidentally "fixing" F1 | Dedicated `DefectTest`; golden after-CBTRN02C fails if acct 50 changes |

## Effort estimate (agent)

Build ~15 min, tests + golden green ~10 min: **~25 min**.
