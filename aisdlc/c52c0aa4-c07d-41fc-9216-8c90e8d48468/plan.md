# CR-1 Plan: CBACT04C in Java 21

Behaviour is defined in [spec.md](spec.md). No legacy, harness or golden files are changed.

## Files, in order (`modernized/cbact04c/`)

1. `pom.xml`: Java 21, JUnit Jupiter 5.x, Surefire. Builds a runnable jar with main class `carddemo.cbact04c.Cbact04c`.
2. `src/main/java/carddemo/cbact04c/Overpunch.java`: zoned-decimal decode/encode (`BigDecimal`, scale, width).
3. `.../FixedFile.java`: reads canonical text (strips CRLF, pads to LRECL) and writes LF records.
4. `.../Abend.java`: an exception that carries the DISPLAY lines. `main` maps it to `ABENDING PROGRAM` + RC 16.
5. `.../Cbact04c.java`: `run(parm, inDir, outDir, Clock, PrintStream sysout) → rc`, which applies spec rules 1-8, plus `main`.
6. `src/test/java/...`: `OverpunchTest`, `InterestTest`, `TranIdTest`, `DefaultFallbackTest`, `AbendTest`, `CharacterizationTest`, `GoldenEquivalenceTest`. Small fixtures are built in-test under `@TempDir`.
7. `legacy/tests/cbact04c-abends.sh`: runs the real COBOL with `INPUT_DIR` fixtures (account missing, xref missing, DEFAULT missing) and saves SYSOUT/RC. Its outputs are the expected values for `AbendTest`.
8. `modernized/cbact04c/README.md`: usage and the D1-D4 list (short).

## Test strategy

- **Equivalence (AC 2, 3, 9):** `GoldenEquivalenceTest` runs `Cbact04c.run` with clock `2022-07-18T00:00Z` and PARM `2022071800`:
  - The `CBACT04C` scenario takes its inputs from `app/data/ASCII/` (tcatbal, cardxref, acctdata, discgrp).
  - The `CBACT04C-after-CBTRN02C` scenario takes ACCTFILE/TCATBALF from `legacy/golden/CBTRN02C/` and the rest from ASCII.
  - Each scenario does a `byte[]` compare of TRANSACT, ACCTFILE and SYSOUT against `legacy/golden/<scenario>/`.
- **Characterization (AC 8):** D1, D2 and D3 are asserted on golden data (account `00000000050` unchanged while txn `…000050` = 18.77) and on fixtures.
- **Abend paths (AC 4):** each Java abend is checked against the COBOL abend output from step 7.
- **End-to-end with the CLI:** build the jar, run it on the after-CBTRN02C inputs, then `diff` the outputs against the golden files.

Commands:
```sh
cd modernized/cbact04c && mvn -q verify
legacy/tests/cbact04c-abends.sh            # COBOL abend references
legacy/harness/golden.sh --check           # legacy still matches golden
java -jar target/cbact04c.jar 2022071800 <in> /tmp/o && diff /tmp/o/TRANSACT.txt ../../legacy/golden/CBACT04C-after-CBTRN02C/TRANSACT.txt
```

## Top risks

| Risk | Mitigation |
|---|---|
| Overpunch/sign mismatch on negative or zero amounts | `OverpunchTest` covers all 20 characters; byte compare against golden |
| Scale or truncation drift in `BigDecimal` | Use `setScale(2, DOWN)` once at the COMPUTE point only; `InterestTest` pins it |
| ACCTFILE record order or untouched bytes differ | Patch fields in the original record string; write in input key order |
| SYSOUT trailing spaces or line endings | Print the raw 50-byte TCATBAL record; compare bytes, not lines |
| Abend message text drifts from COBOL | Expected values come from the real COBOL run (`legacy/tests/cbact04c-abends.sh`) |
| Harness `INPUT_DIR` quirks when building abend fixtures | Follow `legacy/README.md`; keep fixtures minimal (1-2 accounts) |

## Effort (agent)

About 20 minutes for build and about 10 minutes for tests and verification, so about 30 minutes in total.
