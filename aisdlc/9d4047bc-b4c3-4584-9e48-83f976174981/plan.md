# CR-1 Plan — `CBACT04C` → `modernized/cbact04c/`

Behaviour: [spec.md](spec.md). Background: [intent.md](intent.md).

## Files (in build order)
1. `modernized/cbact04c/pom.xml`: Java 21, `junit-jupiter` (test scope) and `maven-surefire-plugin`, nothing else.
   Surefire passes `-Drepo.root=${project.basedir}/../..` so tests can find `app/data/ASCII` and `legacy/golden`.
2. `src/main/java/com/v2/carddemo/cbact04c/ZonedDecimal.java`: overpunch decode/encode to `long` cents, plus wrap modulo n digits.
3. `.../FixedRecord.java`: read lines (strip `\r`, pad to LRECL), get/put field by 1-based column, write LRECL + `\n`.
4. `.../Cbact04c.java`: `main` (arg parsing, exit code) and `run(Parm, Clock, inDir, outDir, PrintStream sysout) → int rc`.
   Contains the control-break loop, the rate lookup with `DEFAULT` fallback, interest, the tran builder, the account update and the abend path (spec R1-R9).
   `Abend` is a private exception that `run` catches and turns into `ABENDING PROGRAM` and rc 16.
5. `src/test/java/.../ZonedDecimalTest.java`, `InterestTest.java`, `CharacterizationTest.java`, `GoldenEquivalenceTest.java`.
   Small inline fixtures are written to `@TempDir`.
6. `legacy/tests/cbact04c_abend.sh`: runs the COBOL with `INPUT_DIR` holding a DISCGRP that has no `DEFAULT` rows. It asserts that RC is 16, the SYSOUT tail is `ABENDING PROGRAM`, and saves that SYSOUT so the Java abend test can match its text.
7. `modernized/cbact04c/README.md`: how to build and run, the CLI, and the defect list with links to the spec.

## Test strategy
- **Equivalence (AC 2-4):** `GoldenEquivalenceTest` runs `Cbact04c.run` with PARM `2022071800` and clock `2022071800000000`:
  - `CBACT04C`: inputs from `app/data/ASCII`.
  - `CBACT04C-after-CBTRN02C`: ACCTFILE and TCATBALF from `legacy/golden/CBTRN02C`.
  It then compares `TRANSACT`, `ACCTFILE` and `SYSOUT` byte for byte, and the RC, against `legacy/golden/<scenario>/`.
- **Characterization (AC 5-8, D1-D7):** see the test table in the spec. The direct-group-hit and abend cases are not in the golden data. They are cross-checked against real COBOL with `legacy/tests/cbact04c_abend.sh` (abend) and a direct-hit run (`INPUT_DIR` with `ACCT-GROUP-ID=A000000000` on account 1), whose outputs I compare by hand. The commands are recorded in the README.
- Commands:
  ```sh
  mvn -q -f modernized/cbact04c verify
  legacy/harness/golden.sh --check            # harness/golden unchanged
  bash legacy/tests/cbact04c_abend.sh
  ```

## Top risks
| Risk | Mitigation |
|---|---|
| Overpunch encoding of zero/negative zero is off by one char | `ZonedDecimalTest` covers all 20 chars, and the golden diff catches any error |
| Truncation done in two steps (÷100, ÷12) gives different cents | Truncate once on the exact `bal×rate/1200` with `BigDecimal` and `DOWN`. `InterestTest` checks all 50 scenario-3 amounts |
| SYSOUT abend/status text differs from COBOL | Derive it from the `:372-460`/`9910` code and confirm with the COBOL abend script |
| CRLF or 36-byte xref lines break column offsets | Normalise in `FixedRecord.read`, with a unit test |
| Path to golden breaks when Maven runs from a different cwd | Pass `repo.root` as a Surefire system property |

## Effort (agent)
About 25 min to build and about 10 min to test and reconcile with the golden data, so **about 35 min** in total.
