# CR-1 Plan — `CBACT04C` in Java 21

This plan builds the program described in [spec.md](spec.md), with the behaviour defined in [intent.md](intent.md).

## Files, in build order (all new under `modernized/cbact04c/`)

1. `pom.xml`: Java 21, JUnit Jupiter 5.x (test scope), Surefire 3.x, and a jar with `Main-Class`. No other dependencies.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch zoned decimal parse/format, scale, mod-10^n truncation.
3. `.../Records.java`: field offsets from the copybooks (spec table) and in-place patching of a fixed-width record.
4. `.../Datasets.java`: reads `<DD>.txt` (strips CR, pads to LRECL) and writes LF lines.
5. `.../Abend.java`: the exception that carries the U999 abend, plus the `FILE STATUS IS: NNNN00xx` formatter.
6. `.../Cbact04c.java`: rules R1–R8. Uses an injectable clock and PARM, and takes sysout/syserr streams.
7. `.../Main.java`: CLI arguments, environment fallback, exit code.
8. `src/test/java/carddemo/cbact04c/`:
   - `ZonedTest`
   - `InterestTest` (D2)
   - `LookupTest` (AC 7, group hit)
   - `DefectTest` (D1, D3, D4, D5)
   - `GoldenTest` (both scenarios, read straight from `../../legacy/golden`)
9. `legacy/tests/cbact04c_equiv.sh`: builds three small input sets in a temp dir, then runs `legacy/harness/run.sh CBACT04C` and the Java jar with the same `INPUT_DIR`, and diffs all outputs, including RC. The three sets:
   - account group `A000000000` hit
   - negative balance
   - `DEFAULT` row removed (both sides RC 16, compare SYSOUT only)
10. `modernized/cbact04c/README.md`: how to run, and the list of preserved defects. Short.

## Test strategy

- **Equivalence proof:** golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. `GoldenTest` compares TRANSACT, ACCTFILE, SYSOUT and RC byte for byte.
- **New cases** (not in golden): unit tests, plus the COBOL-vs-Java script for the group hit, negative balance and missing DEFAULT. Running the real COBOL there settles any doubt about D4 sign handling.
- **Commands:**
  ```sh
  mvn -q -f modernized/cbact04c test
  mvn -q -f modernized/cbact04c package -DskipTests
  legacy/tests/cbact04c_equiv.sh
  legacy/harness/golden.sh --check
  git diff --stat baseline -- legacy/golden legacy/harness .github   # must be empty
  ```

## Top risks

| Risk | Mitigation |
|---|---|
| Overpunch or -0 mismatch, especially for negative values (D4) | `ZonedTest` table; COBOL-vs-Java run on a negative balance |
| `BigDecimal` truncation differs from COBOL intermediate precision | Exact arithmetic then `RoundingMode.DOWN`; 50 golden amounts plus the 3577.41 case |
| D1 "fixed" by accident through natural loop structure | `DefectTest.lastAccountNotRewritten` plus the after-CBTRN02C golden (acct 50) |
| SYSOUT differences (trailing spaces, ordering of the stderr abend line) | Byte compare of `SYSOUT.txt`; the abend line goes to stderr only |
| Blank `ACCT-GROUP-ID` key padding when building the DISCGRP key | Keys are built as fixed-width 16-char strings; golden fallback lines prove it |

## Effort (agent)

Build ≈ 15 min, tests + equivalence script ≈ 10 min, **≈ 25 min total**.
