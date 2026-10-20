# CR-1 Plan — `CBACT04C` in Java 21

Implements [spec.md](spec.md). It adds no new dependencies beyond JUnit 5 and Surefire.

## Files (in order)
1. `legacy/tests/cbact04c-edge.sh`: builds small `INPUT_DIR` fixtures and runs `legacy/harness/run.sh CBACT04C` on each. It saves `SYSOUT/RC/TRANSACT/ACCTFILE` to `modernized/cbact04c/src/test/resources/edge/<case>/`. With `--check`, it diffs against the saved copies instead. The cases are:
   - `overflow`: bal 999999999.99, rate 9999.99
   - `negzero`: bal -0.01, which gives a -0 result
   - `negative`
   - `missing-acct`
   - `missing-xref`
   - `missing-default`

   **This settles the F5 overflow and -0 sign behaviour from COBOL facts before any Java is written.**
2. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter, Surefire, and an executable jar `cbact04c.jar` (manifest only, no shade).
3. `src/main/java/carddemo/cbact04c/`:
   - `Zoned.java`: overpunch decode and encode, plus a modulo-10^n truncation helper.
   - `Records.java`: fixed-width field slices and record builders for the 5 layouts.
   - `Datasets.java`: text load (strip `\r`, pad to LRECL), keyed maps, and ordered write.
   - `Cbact04c.java`: rules R1–R8 and E1–E4, with an injected `Clock`, `PrintStream` and in/out dirs. `run()` returns the RC. Outputs are written only when RC is 0.
   - `Main.java`: CLI args, the `CARDDEMO_CURRENT_DATE` clock, and `System.exit(rc)`.
4. `src/test/java/carddemo/cbact04c/`: the tests listed in spec "Acceptance tests". Golden tests read `../../legacy/golden` and `../../app/data/ASCII` directly (no copies), with the clock fixed at `2022071800000000`.
5. `modernized/cbact04c/README.md`: how to build and run, and the list of preserved defects.

## Test strategy
- **Equivalence:** golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`, with byte compares of TRANSACT, ACCTFILE, SYSOUT and RC.
- **Characterization:** the edge fixtures, produced by the real COBOL, pin F1, F2, F5, F6/F7 and the abend paths.
- **Unit:** overpunch round-trip and interest arithmetic.

```sh
legacy/harness/golden.sh --check                 # legacy still matches golden
legacy/tests/cbact04c-edge.sh                    # generate COBOL edge fixtures (once)
legacy/tests/cbact04c-edge.sh --check            # re-verify fixtures vs COBOL
cd modernized/cbact04c && mvn -q verify          # all Java tests
# CLI smoke vs golden
java -jar target/cbact04c.jar 2022071800 --in ../../app/data/ASCII-mapped --out /tmp/o
```
(`GoldenTest` covers the CLI path through `Main` with temp dirs. The smoke command is optional.)

## Risks
| Risk | Mitigation |
|---|---|
| GnuCOBOL overflow / -0 semantics differ from my assumption | Settle them via edge fixtures from the real COBOL (step 1) and code to those facts |
| Processing order: text file order ≠ KSDS key order | Sort TCATBALF/ACCTFILE by key (R1). The golden diff catches any mismatch |
| Hundredths in the timestamp, or the time zone | The clock is injected, and UTC is used as the harness's `TZ=UTC` |
| `double` creeping into the arithmetic | `BigDecimal` only, with `RoundingMode.DOWN`. Unit test on acct 2 |
| Stock inputs are named `tcatbal.txt` etc., not `<DD>.txt` | The test maps DD→sample file the way `run.sh:50-51` does. The CLI takes `--dd NAME=path` overrides |

## Effort (agent)
Build ≈ 20 min, test + fixtures ≈ 15 min, **≈ 35 min total**.

## For the approver
- Confirm the Q1–Q3 defaults. The spec assumes SYSOUT and RC must match too, F1 is preserved, and I/O is text.
- Writing fixtures into `modernized/…/edge/` from a `legacy/tests` script couples the two trees. The alternative is to keep the fixtures under `legacy/tests/fixtures/`.
