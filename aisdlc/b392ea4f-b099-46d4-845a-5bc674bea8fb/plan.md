# CR-1 Plan — `CBACT04C` in Java 21

Spec: [spec.md](spec.md). Intent: [intent.md](intent.md).

## Files (in this order)
1. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter, Surefire, jar with `Main-Class` (no shading).
2. `src/main/java/carddemo/cbact04c/ZonedDecimal.java`: overpunch decode/encode, truncation to field width.
3. `.../Records.java`: fixed-width readers for TCATBAL, XREF, ACCOUNT, DISCGRP; TRAN-RECORD builder.
4. `.../Cbact04c.java`: R1–R8 as `run(parm, inputs, clock) → Result(rc, sysout, transact, acctfile)`, so tests
   run it in-process with no I/O. Abend is an exception caught in `run`.
5. `.../Main.java`: argument parsing, file I/O, `CARDDEMO_CURRENT_DATE` clock, exit code.
6. `src/test/java/...`: `ZonedDecimalTest`, `InterestTest`, `TranRecordTest`, `DefectCharacterizationTest`,
   `GoldenEquivalenceTest` (reads `../../legacy/golden` and `../../app/data/ASCII` relative to the module).
7. `legacy/tests/data/cbact04c-edge/{TCATBALF,ACCTFILE,XREFFILE,DISCGRP}.txt` + `legacy/tests/cbact04c_edge.sh`:
   a few accounts covering a real group hit, a negative balance, a truncation case and an overflow-size balance
   × a high rate. Runs COBOL with `INPUT_DIR`, runs Java, diffs all four files.
8. `modernized/cbact04c/README.md`: build/run commands, the defect list with a link to spec.

## Test strategy
- Equivalence: golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Byte compare of TRANSACT, ACCTFILE,
  SYSOUT, RC.
- Characterization: D1–D8 (unit tests on small in-memory inputs); D6 and D5 also checked against COBOL by the edge
  script, because golden data never reaches them.
- Commands:
  ```sh
  (cd modernized/cbact04c && mvn -q test && mvn -q package -DskipTests)
  legacy/tests/cbact04c_edge.sh /tmp/cbact04c-edge
  legacy/harness/golden.sh --check
  ```

## Risks
- Overpunch or negative-zero mismatch → round-trip test on every sign char; golden diff shows any byte drift.
- Arithmetic differs from GnuCOBOL on overflow/negatives → edge script compares against real COBOL, not my
  reading of the code. If GnuCOBOL disagrees with the spec, the COBOL output wins and the spec is amended.
- DISCGRP/TCATBAL key order (KSDS ASCII collation) → sort by raw string key, which matches the harness collation.
- Duplicate cards per account untested by data → first-by-card-number rule, flagged in README. Not proven.
- Path coupling of golden tests to the repo layout → paths resolved from `basedir`, fail clearly if missing.

## Effort (agent)
Build ~25 min, test + edge script ~15 min. Total ~40 min.
