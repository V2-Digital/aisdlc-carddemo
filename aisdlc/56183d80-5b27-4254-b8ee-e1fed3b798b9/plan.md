# CR-1 Plan: `modernized/cbact04c/`

Behaviour is in [spec.md](spec.md) and the legacy analysis in [intent.md](intent.md).

## Files (in order)

1. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter 5.10.x, Surefire 3.x. Jar manifest main class. No other dependencies.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch decode/encode with truncation to the PIC (spec "Zoned encoding").
3. `.../FixedFile.java`: reads LF/CRLF lines and pads them to LRECL; writes records with LF.
4. `.../Cbact04c.java`: the program (spec rules 1-8). `run(String parm, Map<String,Path> dd, Clock clock, PrintStream sysout) → int rc`. `main` reads the `DD_*` env vars and calls `System.exit(rc)`.
5. `.../Abend.java`: unchecked exception carrying the messages that lead to exit 16.
6. Tests in `src/test/java/carddemo/cbact04c/`:
   - `ZonedDecimalTest`
   - `InterestTest`
   - `TransactLayoutTest`
   - `DefectTest` (D1, zero rate, DEFAULT fallback)
   - `AbendTest`
   - `GoldenEquivalenceTest`: copies inputs to `@TempDir` and resolves the repo root via `../../`
7. `legacy/tests/cbact04c_java_equiv.sh`: runs the jar on both scenarios with the same inputs `golden.sh` uses, then `cmp`s against `legacy/golden/`.
8. `modernized/cbact04c/README.md`: how to run it, and the defect list linked to spec.

## Test strategy

- **Equivalence:** golden `CBACT04C` (stock inputs from `app/data/ASCII`) and `CBACT04C-after-CBTRN02C` (ACCTFILE + TCATBALF from `golden/CBTRN02C`). For each scenario, `TRANSACT.txt`, `ACCTFILE.txt`, `SYSOUT.txt` and `RC.txt` must be byte-equal.
- Before writing the equivalence test, check `legacy/harness/golden.sh` for the exact input wiring of the second scenario.
- **Characterization:** D1 (AC4), truncation (AC5), zero rate and DEFAULT (AC6), layout spaces (AC7), abends (AC8).
- **Commands:**
  - `cd modernized/cbact04c && mvn -q verify`
  - `legacy/tests/cbact04c_java_equiv.sh`
  - `legacy/harness/golden.sh --check` (the legacy side is unchanged)

## Risks

| Risk | Mitigation |
|---|---|
| Overpunch or negative-zero encoding mismatch | Exhaustive `ZonedDecimalTest`, plus golden byte diff |
| BigDecimal rounding mode slip (HALF_UP vs DOWN) | One helper `truncate(v, scale, intDigits)`; the AC5 test |
| SYSOUT DISPLAY of a record differs (trailing spaces, CRLF) | Print the padded 50-byte record exactly as read; diff against golden SYSOUT |
| Timestamp hundredths format | Fixed clock in tests; golden `…00.00.000000` asserted |
| XREF read with no AIX: wrong card on duplicates | Index by acct-id, keeping the first in card-number order (R2) |
| Repo-relative paths in tests break in CI | Resolve from `basedir` system property set in Surefire config |

## Effort (agent)

Build about 15 min, test and fix about 10 min. **About 25 min in total.**
