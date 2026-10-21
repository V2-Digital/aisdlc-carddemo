# CR-1 Plan: `CBACT04C` → Java 21

Implements [spec.md](spec.md). There are no changes outside `modernized/cbact04c/`, `legacy/tests/` and this
artifacts dir.

## Files (in order)

1. `modernized/cbact04c/pom.xml`
   - Java 21, JUnit Jupiter 5.x, Surefire 3.x. An executable jar via `maven-jar-plugin` manifest only, with no
     shade.
2. `src/main/java/carddemo/cbact04c/`:
   - `Zoned.java`: overpunch parse/format for `S9(n)V99` to `BigDecimal`, with high-order truncation.
   - `RecordFile.java`: reads LRECL lines (strips CR, pads short lines, rejects long ones) and writes LF.
   - `Records.java`: column accessors for TCATBAL, ACCT, XREF, DISCGRP and the TRAN builder.
   - `Cbact04c.java`: the main loop, paragraph for paragraph (`1000`/`1050`/`1100`/`1110`/`1200`/`1300`/`1400`),
     with `Abend` → exit 16.
   - `Main.java`: reads the `DD_*` env vars, PARM and `CARDDEMO_CURRENT_DATE`, and injects `Clock`/`PrintStream`
     for tests.
3. `legacy/tests/cbact04c-abends.sh`
   - Runs the real COBOL via `run.sh` with `INPUT_DIR` fixtures for 3 cases: account missing, XREF missing, no
     `DEFAULT` row.
   - Asserts RC 16 and captures the exact `SYSOUT.txt`. The Java abend tests compare against these.
4. `src/test/java/...`:
   - `ZonedTest`
   - `GoldenTest`: both scenarios, byte compare of all 4 files.
   - `CharacterizationTest`: D1, D2, fallback, zero rate, F4 cases, CRLF.
   - `AbendTest`
5. `modernized/cbact04c/README.md`: how to run, the DD contract and the list of preserved defects.

## Test strategy

- **Equivalence:** the goldens `CBACT04C` and `CBACT04C-after-CBTRN02C`.
  - Tests read the inputs straight from `app/data/ASCII/` and `legacy/golden/CBTRN02C/`, run with PARM
    `2022071800` and clock `2022071800000000`, then do a byte compare of TRANSACT, ACCTFILE, SYSOUT and RC.
- **D2 pin:** the test picks a golden TRANSACT amount where `bal×15/1200` has a 3rd decimal ≥ 5. The intent
  reports 28 such cases.
- **Abend parity:** the Java SYSOUT and RC for the 3 abend fixtures must equal the COBOL output from
  `legacy/tests/cbact04c-abends.sh`.

Commands:
```sh
cd modernized/cbact04c && mvn -q verify            # AC1-9
legacy/tests/cbact04c-abends.sh                     # COBOL abend reference (RC 16 ×3)
legacy/harness/golden.sh --check                    # goldens untouched / COBOL still matches
```

## Risks

- **Overpunch or zero-sign mismatch** (`{` vs `}` on -0). Mitigation: always write `{` for zero, and get
  `ZonedTest` passing before the golden runs.
- **Rate × balance overflow or scale loss.** Mitigation: `BigDecimal` exact product, then `setScale(2, DOWN)`.
  No `double` anywhere.
- **TCATBAL order in text files differs from key order** (R1). Mitigation: sort by bytes 1-17 before
  processing, and test it with a shuffled input.
- **Abend SYSOUT text drifts from COBOL.** Mitigation: the reference comes from running the real COBOL (step 3),
  not from reading the code.
- **ACCTFILE rewrite clobbers the input on crash.** Mitigation: write to a temp file, then atomic rename.

## Effort

Build ~15 min, tests + fixtures ~15 min, about **30 min** in total.
