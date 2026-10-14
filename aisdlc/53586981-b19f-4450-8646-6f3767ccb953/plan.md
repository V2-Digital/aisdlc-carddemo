# CR-1 Plan: `CBACT04C` in Java 21

The behaviour is defined in [spec.md](spec.md). There are no frameworks; the only dependencies are JUnit 5 and Surefire.

## Files, in order

1. `modernized/cbact04c/pom.xml`: Java 21, `junit-jupiter`, Surefire, jar with `Main-Class`. The test working directory is the repository root (`${project.basedir}/../..`), so tests can read `legacy/golden/` and `app/data/ASCII/`.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch decode/encode, plus `fit(value, intDigits)` for high-order truncation.
3. `.../Records.java`: fixed-width slicing and splicing at the spec offsets, LRECL padding, CR stripping.
4. `.../DdFiles.java`: resolves DDs (`DD_<DD>`, `INPUT_DIR`, `OUTPUT_DIR`); reads and writes `<DD>.txt`.
5. `.../Cbact04c.java`: rules 1-9; `run(parm, io, clock, out, err) → rc`; `Abend` exception → RC 16.
6. `.../Main.java`: parses args and env, sets the clock from `CARDDEMO_CURRENT_DATE`/`COB_CURRENT_DATE`, calls `System.exit`.
7. `src/test/java/...`: `ZonedTest`, `InterestTest`, `RateZeroTest`, `DefectTest`, `AbendTest`, `GoldenTest`.
8. `modernized/cbact04c/Dockerfile` (`eclipse-temurin:21-jre`, runs the jar) and a short `README.md` covering usage and env vars.
9. `legacy/tests/cbact04c-edge.sh`: builds edge inputs in a temp dir and runs `legacy/harness/run.sh CBACT04C` and the Java jar on each (`INPUT_DIR`, frozen clock). Then it diffs every output, `SYSOUT.txt` and `RC.txt`.

## Test strategy

- **Equivalence (golden):** `CBACT04C` and `CBACT04C-after-CBTRN02C`. The tests compare bytes for TRANSACT, ACCTFILE and SYSOUT, and check that RC is 0.
- **Characterization:** truncation (positive, negative and third decimal), rate 0, D1 on account 50, the three abend paths, and overflow wrap (D5).
- **COBOL cross-check** of the paths that have no golden copy (abends, negative balance, overflow): the legacy test script. It settles the GnuCOBOL size-error behaviour from the actual run.

Commands:
```sh
mvn -q -f modernized/cbact04c verify
legacy/tests/cbact04c-edge.sh        # needs GnuCOBOL + built jar
legacy/harness/golden.sh --check     # harness untouched, still green
```

## Top risks

| Risk | Mitigation |
|---|---|
| Overpunch or `-0` encoding is wrong, so bytes differ | `ZonedTest` plus the golden diff on 50 non-zero amounts |
| GnuCOBOL overflow is not a plain mod 10^n (D5) | Settle it with the edge script before fixing `Zoned.fit` |
| The abend leaves partial outputs that differ from COBOL | Edge script diffs ACCTFILE/TRANSACT after the abend; mirror whatever it shows |
| An unsorted input TCATBALF changes the order | Sort by key, like a KSDS load, and document it |
| Xref with several cards per account | Take the first in card order; the edge script adds a duplicate case |

## Effort estimate (agent)

Build ≈ 15 min, tests and edge script ≈ 15 min, fix loop ≈ 5 min. **Total ≈ 35 min.**
