# CR-1 Plan — `CBACT04C` → `modernized/cbact04c/`

Behaviour: [spec.md](spec.md). Context: [intent.md](intent.md).

## Files (in order)

1. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter 5, Surefire. Runnable jar (`Main-Class`), no other dependencies.
2. `src/main/java/carddemo/cbact04c/ZonedDecimal.java`: overpunch parse/format, `S9(n)V99`, modulo truncation (D6).
3. `.../Records.java`: fixed-width field accessors for TCATBAL, XREF, ACCT, DISCGRP, TRAN (offsets per spec).
4. `.../FixedWidthFiles.java`: read lines (strip CR, pad to LRECL) and write LF records.
5. `.../InterestCalc.java`: rules 1–9 of the spec. Takes `Clock` and `PrintStream`, and an `AbendException` carries exit 16.
6. `.../Main.java`: CLI args, plus `CARDDEMO_CURRENT_DATE` → fixed `Clock`. Maps the exit code.
7. `src/test/java/...`: `ZonedDecimalTest`, `InterestTest`, `GoldenCbact04cTest`, `GoldenAfterCbtrn02cTest`,
   `LastAccountDefectTest`, `ZeroRateZeroBalTest`, `AbendTest`.
   - The golden tests read `../../legacy/golden/*` and `../../app/data/ASCII/*` from the repo.
8. `legacy/tests/xref_dup.sh`: runs the COBOL with a duplicate card for one acct (`INPUT_DIR`). It records the status/card actually used, which settles D7.
9. `modernized/cbact04c/README.md`: how to run, the args, and the preserved defects D1–D7.

## Test strategy

- **Equivalence**: golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. `TRANSACT.txt`, `ACCTFILE.txt` and `SYSOUT.txt` are compared
  byte-for-byte, and the exit code must be 0. Clock = `2022071800000000`.
- **Characterization**: D1 (acct 50), D2 (acct 48: 18.909625 → 18.90), D6 overflow, D7 (driven by the legacy test result), zero-rate / zero-balance,
  and the abend paths (exit 16 + messages).
- **Legacy cross-check**: `xref_dup.sh`, plus one negative-balance input run through the harness to confirm the truncation sign and the overpunch on output.
- Commands:
  ```
  mvn -q -f modernized/cbact04c verify
  legacy/harness/golden.sh --check          # legacy is still green (unchanged)
  bash legacy/tests/xref_dup.sh
  java -jar modernized/cbact04c/target/cbact04c.jar ... && diff vs legacy/golden/<scenario>/   # CLI smoke
  ```

## Risks

| Risk | Mitigation |
|---|---|
| COBOL intermediate precision in `bal×rate/1200` differs from exact BigDecimal | Exact compute + `DOWN`. Pin acct 48. All 50 golden amounts must match. |
| Sign of zero / negative overpunch on write | Unit tests plus a harness run with a negative balance. |
| Duplicate xref semantics (D7) unknown | `xref_dup.sh` settles it before `AbendTest` is finalised. |
| SYSOUT record display trailing spaces / encoding | Byte-compare stdout, write ISO-8859-1, and print LF only. |
| Test paths depend on the working dir | Resolve the repo root from `pom.xml` basedir (`../..`). |

## Effort (agent)

Build ~15 min · tests + legacy cross-checks ~10 min · total **~25 min**.
