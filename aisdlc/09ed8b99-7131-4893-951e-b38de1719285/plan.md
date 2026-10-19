# CR-1 Plan: `CBACT04C` to Java 21

Implements [spec.md](spec.md). Nothing under `legacy/golden/`, `legacy/harness/`, `app/` or `.github/` changes.

## Files (in order)
All under `modernized/cbact04c/`, package `com.v2.carddemo.cbact04c`:
1. `pom.xml`: Java 21, JUnit 5 (`junit-jupiter`), Surefire, jar with `Main-Class`. No other deps.
2. `Overpunch.java`: zoned-decimal decode/encode (`{A-I}J-R`), scale and length aware, plus mod-10^n truncation.
3. `Records.java`: fixed-width read/write (pad/CR-strip on read, LF on write), field accessors per copybook.
4. `Clock.java`: 16-digit `COB_CURRENT_DATE`, else system UTC → DB2 timestamp.
5. `Abend.java` (exception carrying SYSOUT lines) + `Sysout.java` (stdout + optional file).
6. `Cbact04c.java`: rules 1-10, mirroring the COBOL paragraph structure (comments cite `CBACT04C.cbl` lines).
7. `Main.java`: CLI args → `Cbact04c.run(...)` → exit 0/16.
8. Tests in `src/test/java/...`: `OverpunchTest`, `InterestTest`, `GoldenEquivalenceTest`,
   `DefectCharacterizationTest`, `AbendTest`. These read inputs from `app/data/ASCII/` and `legacy/golden/` by
   relative path (`../../`).
9. `legacy/tests/cbact04c_edge.sh`: builds edge inputs (negative balance, rate-0-only account, missing
   DEFAULT, missing xref), runs them through `harness/run.sh` with `INPUT_DIR` and through the Java jar, then diffs.
10. `modernized/cbact04c/README.md`: how to build and run (short).

## Test strategy
- Equivalence: golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. `TRANSACT.txt`, `ACCTFILE.txt`,
  `SYSOUT.txt` and `RC.txt` are compared byte for byte (`Files.mismatch == -1`). PARM `2022071800`, clock
  `2022071800000000`, injected (no real clock).
- Characterization: D1-D4 per spec. D1/D2/D3 cite golden bytes; negative-balance and D4 are unit-level.
- Cross-check of the new edge cases against real COBOL via `legacy/tests/cbact04c_edge.sh`, so no edge-case
  expectation is guessed.
- Commands:
  ```sh
  mvn -q -f modernized/cbact04c test
  mvn -q -f modernized/cbact04c package && bash legacy/tests/cbact04c_edge.sh
  legacy/harness/golden.sh --check
  ```

## Risks
| Risk | Mitigation |
|---|---|
| Overpunch zero/negative-zero encoding differs from GnuCOBOL | golden bytes + edge script with a negative balance |
| Abend SYSOUT/RC differs | edge script diffs SYSOUT + RC (`run.sh` exports no data files on abend, so partial outputs are not comparable) |
| Hidden float or rounding use | BigDecimal only, `RoundingMode.DOWN`; unit test on 29.2496 → 29.24 |
| Unsorted or CRLF text input | sort TCATBALF by key, strip CR, pad short lines (xref 36 B) |
| Test relative paths break outside repo | resolve repo root from `user.dir`; fail clearly if goldens are missing |

## Effort (agent)
Build ~15 min, tests + edge script ~15 min, so **~30 min** total.

## Decision for the approver
- Partial outputs on abend (spec rule 10) cannot be verified against COBOL, because `run.sh` skips the unload when RC is not 0 or 4.
  Proposal: Java writes them as specified, and ops treat any RC 16 output as invalid (same as a mainframe rerun).
