# CR-1 Plan — `CBACT04C` → `modernized/cbact04c/`

See [spec.md](spec.md) for the behaviour and [intent.md](intent.md) for the evidence.

## Files (in order)

1. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter 5.x and Surefire only. Surefire runs from the module dir.
   The repo root is passed as the `repo.root` system property (`${project.basedir}/../..`).
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch parse/format for `S9(n)V99`, plus modulo truncation to n digits.
3. `.../RecordFiles.java`: reads lines as ISO-8859-1, strips CR, pads to LRECL; writes records with LF.
4. `.../Records.java`: fixed-offset accessors for the five layouts (column table in the spec).
5. `.../InterestCalc.java`: the core loop (rules 1-9). Inputs are maps/lists, the clock is a `Supplier<LocalDateTime>`, output goes to a `PrintStream`.
   An abend is an `AbendException(status)`.
6. `.../Main.java`: CLI `<PARM> <OUT_DIR> [INPUT_DIR]`, DD resolution, env clock, exit code (0/16).
7. Tests in `src/test/java/carddemo/cbact04c/`: `GoldenTest`, `InterestTest`, `ZonedTest`, `DefectTest`, `AbendTest`.
8. `modernized/cbact04c/README.md`: how to run it, plus the preserved-defect list (D1-D5) for the follow-up CR.

No changes to `app/`, `legacy/golden/`, `legacy/harness/` or `.github/`.

## Test strategy

- **Equivalence (gating):** `GoldenTest` runs `Main` in-process into `target/out/<scenario>/` and byte-compares against:
  - `legacy/golden/CBACT04C/` with stock `app/data/ASCII` inputs: TRANSACT 50×350, ACCTFILE 50×300, SYSOUT 152 lines, RC 0.
  - `legacy/golden/CBACT04C-after-CBTRN02C/` with `INPUT_DIR=legacy/golden/CBTRN02C`: TRANSACT 50, ACCTFILE 50, SYSOUT 302, RC 0.
- **Characterization:** D1 (acct 50 not rewritten, tran 50 = `0000000187G`) and D2 (1164.87 @ 15.00 → 14.56, 48/50 truncated in scenario 3).
  Also D4 overflow wrap, and negative-balance truncation and overpunch round-trip (R4).
- **Error paths:** small synthetic input dirs built in `@TempDir`: no DEFAULT row, missing account, missing xref → exit 16.
  The group-miss fallback is already covered by golden SYSOUT.
- **Legacy cross-check (non-gating, one-off):** run `legacy/tests/cbact04c_negative.sh` through `legacy/harness/run.sh` with `INPUT_DIR`
  set to one negative balance with a non-zero rate. Its outputs confirm the expected values in `InterestTest`, so R4 rests on COBOL evidence and not only on reasoning.

Commands:
```
mvn -q -f modernized/cbact04c test
legacy/harness/golden.sh --check          # confirms the reference is unchanged
bash legacy/tests/cbact04c_negative.sh    # COBOL evidence for negative/truncation case
```

## Risks (→ mitigation)

1. Overpunch or sign mismatch for −0, or for negative values written by Java → `ZonedTest` round-trips every sign char, plus the COBOL negative-case script.
2. Timestamp hundredths, or a clock that is not frozen → inject the clock. The golden tests set `2022071800000000` explicitly.
3. Abend partial outputs differ from VSAM → only exit code and SYSOUT are gated on error paths. Noted in the README.
4. Exact decimal division (`BigDecimal.divide` with a non-terminating quotient) → divide at scale ≥ 4 with `RoundingMode.DOWN`, then set scale 2 with DOWN. This is exact for truncation.
5. Non-unique xref alt key is untested by golden data (R2) → unit test asserts that the lowest card number wins, flagged as an assumption.

## Effort

Build ≈ 15 min, tests and golden green ≈ 10 min. **Total ≈ 25 min.**
