# CR-1 Plan: `modernized/cbact04c/`

The spec is in [spec.md](spec.md). Package `carddemo.cbact04c`, Java 21, no frameworks.

## Files, in order

1. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter 5.x (test scope), and Surefire. Builds a runnable jar (manifest `Main-Class`).
2. `src/main/java/.../Zoned.java`: overpunch decode/encode, `fit(n digits)` high-order truncation, and truncate-toward-zero at scale 2.
3. `.../Datasets.java`: read lines (strip CR, pad to LRECL) and write fixed width + LF.
4. `.../Abend.java`: an exception carrying the SYSOUT lines, which maps to exit 16 plus `CEE3ABD: ABEND U0999` on stderr.
5. `.../InterestCalc.java`: the spec rules 1-8 as a pure function `(inputs, parm, Clock, sysout) → outputs`.
6. `.../Main.java`: the CLI. `java -jar cbact04c.jar <PARM>`, with each file path taken from env `DD_<NAME>` (the same names as the JCL and harness). The clock comes from `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`, UTC), else the system clock.
7. Tests in `src/test/java/...`: `ZonedTest`, `InterestTest`, `DefectCharacterizationTest`, `AbendTest`, `GoldenEquivalenceTest`. The golden test reads `../../legacy/golden` and `../../app/data/ASCII` relative to the module.
8. `legacy/tests/cbact04c-diff.sh`: a differential test. It runs `legacy/harness/run.sh CBACT04C` and the Java jar on the same synthetic `INPUT_DIR`, then diffs all outputs.
9. `modernized/cbact04c/README.md` and `Dockerfile` (`eclipse-temurin:21-jre`, run note only, not built).

## Test strategy

- **Equivalence (AC2/3):** golden `CBACT04C` (stock inputs) and `CBACT04C-after-CBTRN02C` (ACCTFILE and TCATBALF from `golden/CBTRN02C`). Both use PARM `2022071800` and clock `2022-07-18T00:00:00.00Z`. The test byte-compares TRANSACT, ACCTFILE, SYSOUT and the RC.
- **Unit and characterization:** use the tests mapped in the spec's AC table, including D1 (acct 50), truncation (14.56), the zero-rate path, the direct-hit/DEFAULT paths, and the abends.
- **Differential vs COBOL (T5):** `legacy/tests/cbact04c-diff.sh` uses a synthetic dataset that golden data never exercises (D8, R2, D4). It covers:
  - a real `ACCT-GROUP-ID` with a direct DISCGRP hit
  - negative balances, including a result of −0.00x
  - a near-overflow balance
  - an all-0% account in the middle
  
  This settles the sign-of-zero and negative-truncation assumptions against GnuCOBOL itself, not from my reading of the code.
- Commands:
  ```sh
  mvn -q -f modernized/cbact04c test
  mvn -q -f modernized/cbact04c package -DskipTests
  legacy/tests/cbact04c-diff.sh
  legacy/harness/golden.sh --check
  git diff --stat baseline -- legacy/golden legacy/harness .github   # must be empty
  ```

## Top risks

| Risk | Mitigation |
|---|---|
| Overpunch sign of zero or negative results differs from GnuCOBOL | The T5 differential test against real COBOL output. Fix Java to match it. |
| Truncation done on a rounded intermediate | Use an exact `BigDecimal` divide (`bal×rate` is exact; then divide with scale 2, `DOWN`). No `double` anywhere. |
| Byte drift (CRLF, short xref lines, trailing spaces, FILLER) | Pad and strip on read. Build records from a space-filled buffer. The golden byte compare catches any drift. |
| Golden test paths break when run from another dir | Resolve paths from `${maven.multiModuleProjectDirectory}` or the module basedir via a Surefire system property. |
| Someone "fixes" D1 by accident | A dedicated characterization test with the defect id in its name. |

## Estimate

Build about 15 min and tests about 10 min, so **about 25 min** of agent time.
