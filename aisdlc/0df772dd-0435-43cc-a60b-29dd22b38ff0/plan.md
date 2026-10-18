# CR-1 Plan: `CBACT04C` in Java 21

Implements [spec.md](spec.md). There is no change to `app/`, `legacy/golden/`, `legacy/harness/` or `.github/`.

## Files (in order)

1. `modernized/cbact04c/pom.xml`: Java 21, JUnit 5 (`junit-jupiter`), Surefire, jar with `Main-Class`.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch decode/encode for `S9(n)V99`, and wrap to n digits.
3. `.../Records.java`: field offsets from the copybooks, fixed-width slice/splice helpers, TRANSACT builder.
4. `.../Cbact04c.java`: main loop per the spec rules 1-9. Takes the `Clock`, PARM, paths and stdout/stderr as arguments, and returns the RC (no `System.exit` inside, so it is testable).
5. `.../Main.java`: reads the env (`DD_*`, `CARDDEMO_CURRENT_DATE`), calls `Cbact04c`, then `System.exit(rc)`.
6. `src/test/java/...`:
   - `ZonedTest`
   - `GoldenEquivalenceTest`: reads `../../legacy/golden` and `../../app/data/ASCII` directly
   - `DefectTest`, `RateLookupTest`, `TranIdTest`, `AbendTest`: small fixture files built in `@TempDir`
7. `modernized/cbact04c/README.md`: the run note (build, env vars, example against the sample data).

## Test strategy

- Equivalence uses the golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. It compares TRANSACT, ACCTFILE, SYSOUT and RC byte for byte, with the clock fixed at `2022071800000000`.
- Characterization: one test per preserved defect D1-D4 and D6. D7 uses an XREF fixture with 2 cards for one account. D8 uses a missing DISCGRP file.
- New COBOL tests: none needed. The goldens already pin legacy behaviour, and the defect fixtures are Java-side only. Optional: `legacy/tests/cbact04c-fixtures.sh` runs the same truncation/negative fixtures through `run.sh` with `INPUT_DIR`, to prove the COBOL agrees. I will add it only if it takes less than 5 minutes.
- Commands:
  ```sh
  cd modernized/cbact04c && mvn -q test
  legacy/harness/golden.sh --check
  # end-to-end diff against the harness:
  mvn -q package && DD_TCATBALF=../../app/data/ASCII/tcatbal.txt ... \
    CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar 2022071800 > SYSOUT.txt; \
    diff -r <out> ../../legacy/golden/CBACT04C
  ```

## Risks

| Risk | Mitigation |
|---|---|
| BigDecimal truncation differs from GnuCOBOL intermediate precision | Exact product/1200 → `DOWN`. Pinned by the 0.40 and negative fixtures (and the optional COBOL fixture run) |
| Overpunch −0 / sign edge cases | `ZonedTest` round-trips every overpunch char. The golden files contain `}` cases |
| XREF "first" card ordering ≠ BDB alternate-key order | The sample has no duplicate acct-ids. Use file (primary-key) order and document it under D7 |
| SYSOUT byte drift (trailing spaces, line endings) | TCATBALF lines are echoed verbatim. Golden SYSOUT diff in the tests |
| Clock leaks into the output | `Clock` is injected. The test fixes it at `2022-07-18T00:00:00Z` |

## Effort (agent)

Build about 15 min, tests and verification about 10 min. **About 25 min total.**
