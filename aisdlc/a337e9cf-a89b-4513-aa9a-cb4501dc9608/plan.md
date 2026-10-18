# CR-1 Plan: `modernized/cbact04c/`

Build to [spec.md](spec.md). No changes to `legacy/`, `.github/` or `app/`.

## Files (in order)
1. `modernized/cbact04c/pom.xml`: Java 21, `junit-jupiter` (test scope), Surefire, and a jar manifest with `Main-Class`. No other dependencies.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch decode/encode, modulo truncation, `RoundingMode.DOWN`.
3. `.../FixedText.java`: reads lines (strips CR, right-pads to the LRECL) and writes LRECL + LF.
4. `.../Records.java`: column offsets for TCATBAL, XREF, ACCT, DISCGRP and TRAN (from the copybooks), plus a TRAN record builder and an ACCT field patcher.
5. `.../InterestCalc.java`: the core loop from spec "Processing". Takes the loaded data, a PARM, a `Clock` and a SYSOUT `PrintStream`, and returns the result or throws `Abend`.
6. `.../Abend.java`: an exception that carries the SYSOUT lines.
7. `.../Main.java`: parses args/env, does file I/O, maps exit codes 0/16, prints `CEE3ABD: ABEND U0999` to stderr.
8. `src/test/java/...`: `ZonedTest`, `InterestCalcTest` (characterization tests: AC 5-9, F1-F8), and `GoldenEquivalenceTest` (AC 2-4, which reads `../../app/data/ASCII` and `../../legacy/golden/` relative to the module).
9. `modernized/cbact04c/README.md`: how to run it, plus the list of preserved defects (just a few lines).

## Test strategy
- **Equivalence:** golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Byte compare of `TRANSACT.txt`, `ACCTFILE.txt`, `SYSOUT.txt`, and exit code == `RC.txt`.
- **Characterization:** in-memory fixtures built in `InterestCalcTest`. These cover the group hit, DEFAULT fallback, zero rate, zero total, untouched accounts, last account not updated, truncation vs rounding, the three abend paths, and empty input.
- **No new COBOL tests.** The goldens already pin the legacy behaviour. If the abend SYSOUT text needs proof, I'll add `legacy/tests/cbact04c-abend.sh`: it runs `run.sh` with `INPUT_DIR` holding a TCATBALF that has an unknown account, and diffs SYSOUT/RC against the Java test expectations.
- Commands:
  ```sh
  cd modernized/cbact04c && mvn -q test
  mvn -q package && java -jar target/cbact04c.jar 2022071800 --TCATBALF=../../app/data/ASCII/tcatbal.txt ... # smoke run
  legacy/harness/golden.sh --check   # confirm goldens untouched
  ```

## Risks
| Risk | Mitigation |
|---|---|
| Byte drift (CRLF inputs, 36-byte xref lines, trailing spaces, `-0` sign) | Normalise on read, write a fixed LRECL; golden byte diff in the tests |
| Abend SYSOUT wording/status (`NNNN0023`, open status 35) is inferred from source, not observed | Run the optional `legacy/tests` script once; tests assert exit 16 plus key lines |
| TCATBAL input not in key order | Sort by the 17-byte key, as the KSDS does |
| BigDecimal scale/overflow differs from COBOL | Centralise in `Zoned`; unit tests for mod 10^n and truncation |
| Golden paths are relative to the module | Resolve from `${project.basedir}` through a Surefire system property |

## Effort
Build ~15 min, test and fix ~10 min. Total **~25 min**.
