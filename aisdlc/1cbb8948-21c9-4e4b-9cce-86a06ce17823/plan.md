# CR-1 Plan: `CBACT04C` → `modernized/cbact04c/`

Behaviour is defined in [spec.md](spec.md). Package `carddemo.cbact04c`, Java 21, no frameworks.

## Files (in build order)
1. `modernized/cbact04c/pom.xml`: Java 21 (`maven.compiler.release`), JUnit Jupiter 5.x (test), Surefire 3.x. Nothing else.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch decode/encode, fixed width, mod 10ⁿ truncation, `{` for zero.
3. `…/Records.java`: field offsets from the copybooks. Includes the TCATBAL/ACCT/DISCGRP accessors and the TRAN record builder.
4. `…/Datasets.java`: reads and writes the canonical text files by DD name (strips CR, pads to LRECL). Includes KSDS-as-map and sequential writer.
5. `…/Cbact04c.java`: `run(parm, dds, clock, out) → rc` covers spec Processing steps 1–8. `main` reads env and `CARDDEMO_CURRENT_DATE`.
6. `src/test/java/carddemo/cbact04c/ZonedTest.java`: codec, −0, negative digits, overflow.
7. `…/GoldenEquivalenceTest.java`: runs both golden scenarios in `@TempDir` and does a byte compare of TRANSACT, ACCTFILE, SYSOUT and RC.
   Inputs come from `../../app/data/ASCII/` and `../../legacy/golden/CBTRN02C/`, with paths resolved from `basedir`.
8. `…/CharacterizationTest.java`: AC5–AC8 on small hand-built inputs (including a non-DEFAULT group hit and the 3 abend paths).
9. `legacy/tests/cbact04c_abend.sh`: runs `INPUT_DIR` with an ACCTFILE missing one account and checks that COBOL gives RC 16 and the SYSOUT text.
   This pins the exact abend lines that the Java test asserts.
10. `modernized/cbact04c/README.md`: build/run commands, env vars, and the preserved defect list (a link to spec).

## Test strategy
- Equivalence: the golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C` (AC2–4). They use a byte compare with no masking,
  because the clock is frozen at `2022071800000000`.
- Characterization: D1, D3 (truncation, negative), rate 0, DEFAULT fallback, the group `00` path (not covered by golden), abends.
- Commands:
  ```sh
  cd modernized/cbact04c && mvn -q verify
  bash legacy/tests/cbact04c_abend.sh
  legacy/harness/golden.sh --check
  # end-to-end CLI check against golden, outside JUnit:
  DD_TCATBALF=… DD_ACCTFILE=<copy> … CARDDEMO_CURRENT_DATE=2022071800000000 \
    java -cp target/classes carddemo.cbact04c.Cbact04c 2022071800 > /tmp/SYSOUT.txt; diff …
  git diff --stat baseline -- legacy/golden legacy/harness .github   # must be empty
  ```

## Risks
| Risk | Mitigation |
|---|---|
| Overpunch or −0 encoding off by one byte | `ZonedTest`, plus a full-record golden diff |
| Truncation done as rounding (BigDecimal default) | explicit `RoundingMode.DOWN`. The golden 14.56 / 29.24 values and a negative-balance test catch it |
| The SYSOUT echo of TCATBAL differs (CRLF in stock `tcatbal.txt`) | strip CR on read, echo the padded 50 bytes, diff against golden SYSOUT |
| ACCTFILE output order or untouched bytes changed | `TreeMap` on the raw key. Copy the raw record and patch only 3 fields |
| Abend text differs from COBOL | `legacy/tests/cbact04c_abend.sh` pins it from the real program |
| Golden paths are fragile in Maven | resolve from `${project.basedir}/../..` through a Surefire system property |

## Effort (agent)
Build ≈ 15 min, tests and golden diff ≈ 10 min. **Total ≈ 25 min.**
