# CR-1 Plan — `CBACT04C` in Java 21

Contract: [spec.md](spec.md). Everything goes under `modernized/cbact04c/`. Package: `com.carddemo.cbact04c`.

## Files (in order)
1. `pom.xml`: Java 21, JUnit Jupiter, Surefire. Builds a runnable jar (manifest `Main-Class`, no shade).
2. `src/main/java/.../Zoned.java`: overpunch parse/format, plus `trunc(n digits)`.
3. `.../Records.java`: fixed-width readers (strip CR, pad to LRECL) and field accessors for the 5 layouts.
4. `.../Cbact04c.java`: the main loop, Rules 1–8. Takes a `Clock` and a `PrintStream`, and returns the RC.
5. `.../Main.java`: CLI arguments plus `CARDDEMO_CURRENT_DATE` → `Clock`, then `System.exit(rc)`.
6. `src/test/java/...`: `ZonedTest`, `RecordTest`, `InterestTest`, `RulesTest`, `DefectTest`, `GoldenTest`. Tests read the inputs from `../../app/data/ASCII` and `../../legacy/golden` (read-only).
7. `modernized/cbact04c/README.md`: how to run it, and the list of preserved defects.

## Test strategy
- **Equivalence:** `GoldenTest` runs both scenarios in-process with PARM `2022071800` and a fixed UTC clock at 2022-07-18T00:00:00.00. It byte-compares `TRANSACT.txt`, `ACCTFILE.txt`, `SYSOUT.txt` and `RC.txt` against `legacy/golden/CBACT04C/` and `legacy/golden/CBACT04C-after-CBTRN02C/`. The stock inputs are mapped to DD names the way `run.sh` does: `tcatbal.txt`, `cardxref.txt`, `acctdata.txt`, `discgrp.txt`.
- **Characterization:** small synthetic temp-dir datasets for D1, zero rate, group hit, DEFAULT missing, account or xref missing (RC 16), overflow, and suffix wrap.
- **COBOL side:** add `legacy/tests/cbact04c-default-missing.sh`. It runs `run.sh CBACT04C` with an `INPUT_DIR` whose DISCGRP has no `DEFAULT` rows, and asserts RC 16 and the SYSOUT tail. This confirms the abend text that Rule 4 copies. If the output differs, fix the spec text, not the harness.
- Commands:
  ```
  cd modernized/cbact04c && mvn -q verify
  legacy/harness/golden.sh --check          # legacy unchanged
  bash legacy/tests/cbact04c-default-missing.sh
  # CLI smoke check vs golden:
  CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar 2022071800 <in> /tmp/out && diff -r ...
  ```

## Risks
| Risk | Mitigation |
|---|---|
| The COMPUTE intermediate precision differs from `BigDecimal` | Exact product, then `divide(1200, 2, DOWN)`. Pinned by the 50 non-zero amounts in after-CBTRN02C and by the 29.24 test |
| Negative-zero sign on output (`}` vs `{`) | Normalise to `{`. Check the negative-interest case once against COBOL through `legacy/tests` |
| SYSOUT line differences (trailing spaces, DISPLAY of a signed field) | Print the raw 50-byte record. A byte-diff against golden SYSOUT catches any mismatch |
| XREF alternate-key duplicates | Spec says the first record in file order wins. The stock data has no duplicates |
| Golden-path coupling from tests | Resolve the repo root from `${maven.multiModuleProjectDirectory}`/basedir, and fail clearly if it is not found |

## Effort (agent)
About 25 min build and about 15 min test/verify, so roughly 40 min in total.
