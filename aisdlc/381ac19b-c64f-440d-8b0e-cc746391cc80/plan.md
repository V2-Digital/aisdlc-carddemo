# CR-1 Plan — `modernized/cbact04c/`

Behaviour is defined in [spec.md](spec.md). Legacy facts are in [intent.md](intent.md).

## Files (in order)

1. `modernized/cbact04c/pom.xml`
   - Java 21, JUnit Jupiter (test scope), Surefire.
   - `maven-jar-plugin` manifest with `Main-Class`. No other plugins or dependencies, so the jar runs without shading.
2. `src/main/java/carddemo/cbact04c/`
   - `Zoned.java`: overpunch codec, BigDecimal with a fixed scale, plus modulo truncation.
   - `RecordIo.java`: reads lines (strips CR, pads to LRECL) and writes fixed-width records ending in LF.
   - `Clock.java`: `CARDDEMO_CURRENT_DATE` or the system UTC clock, formatted `YYYY-MM-DD-HH.MM.SS.hh0000`.
   - `Cbact04c.java`: the main loop, following spec rules 1–7. It takes a `PrintStream` for SYSOUT and returns the RC.
   - `AbendException` carries the exit code 16.
   - `Main.java`: argument parsing and `System.exit(rc)`.
3. `src/test/java/carddemo/cbact04c/`: `GoldenTest`, `InterestTest`, `DefectTest`, `RateTest`, `OverpunchTest`, `CliTest`.
   - Tests locate the repo root by walking up to `legacy/golden/`.
   - Error-path fixtures are built in `@TempDir`.
4. **T3** `legacy/tests/cbact04c_edges.sh`: a characterization test of the **COBOL** for R1.
   - It runs `run.sh CBACT04C` with `INPUT_DIR`. The inputs are a negative balance (−0.01 at rate 15.00 → −0.00), a non-DEFAULT group with rate > 0, and an account with 2 cards.
   - The COBOL outputs it produces are copied to `src/test/resources/edges/`, and `GoldenTest.edges` asserts Java matches them byte for byte.
5. `modernized/cbact04c/README.md`: how to run it, the env var, and the preserved-defect list.

## Test strategy

- **Equivalence:** golden `CBACT04C` and `CBACT04C-after-CBTRN02C`. TRANSACT, ACCTFILE and SYSOUT are compared byte for byte, and RC is checked.
- **Characterization:** the D1, D3 and D8 pins, rate 0, the DEFAULT fallback, the three abend paths, and the R1 edges proven against real COBOL output (T3).
- Commands:
  ```
  cd modernized/cbact04c && mvn -q verify
  bash legacy/tests/cbact04c_edges.sh            # regenerates COBOL edge outputs, diffs vs resources
  legacy/harness/golden.sh --check               # unchanged legacy still matches golden
  CARDDEMO_CURRENT_DATE=2022071800000000 java -jar modernized/cbact04c/target/cbact04c.jar 2022071800 \
    --TCATBALF=app/data/ASCII/tcatbal.txt --XREFFILE=app/data/ASCII/cardxref.txt \
    --DISCGRP=app/data/ASCII/discgrp.txt --ACCTFILE=app/data/ASCII/acctdata.txt --out=/tmp/o
  ```

## Top risks

| Risk | Mitigation |
|---|---|
| Sign of a negative-zero interest (`}` vs `{`) or truncation of negative values differs from GnuCOBOL | T3 runs the real COBOL. Java follows the COBOL output, and the spec is amended if they differ. |
| An alternate-key duplicate picks a different card than BDB does (R2) | T3 uses an account with 2 cards to observe which one wins. Java matches it. |
| An ACCTFILE rewrite disturbs unrelated bytes (filler, trailing spaces) | Patch the raw record in place, and compare the full file byte for byte against golden. |
| SYSOUT drifts (padding of the DISPLAY record, CRLF in tcatbal) | Pad to 50 bytes after stripping CR. The golden SYSOUT diff catches drift. |
| Clock and time zone nondeterminism | The env override plus UTC. Tests inject the clock directly. |

## Effort (agent)

Build ≈ 15 min, test (including T3 and golden runs) ≈ 10 min. **Total ≈ 25 min.**
