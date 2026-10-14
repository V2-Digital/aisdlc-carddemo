# Plan — CR-1: `CBACT04C` → Java 21

Behaviour: [spec.md](spec.md). Background and defects: [intent.md](intent.md).

## Files (in order)

1. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter 5 (test scope), Surefire. Builds an executable jar (`Main-Class`). No other dependencies.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch encode/decode, PIC-width modulo truncation.
3. `.../Records.java`: fixed-offset field access for the 5 layouts (copybook paths in spec), plus padding to LRECL.
4. `.../InterestCalc.java`: the main loop from spec rules 1–6, with injected PARM, clock (`Supplier<String>`) and `PrintStream` for SYSOUT. Returns the exit code (0/16). Has an empty `computeFees()` hook.
5. `.../Main.java`: CLI `<PARM> <IN_DIR> <OUT_DIR>`, reads `CARDDEMO_CURRENT_DATE`, calls `System.exit(rc)`.
6. `src/test/java/...`: `ZonedTest`, `InterestTest`, `TransactionLayoutTest`, `ZeroRateTest`, `DefectD1Test`, `AbendTest`, `GoldenEquivalenceTest`. The golden tests read `../../legacy/golden/` directly; small cases use inline fixtures.
7. `modernized/cbact04c/README.md`: how to run, conventions, and the preserved-defect list (D1–D5).

No changes to `legacy/`, `app/` or `.github/`. No new COBOL tests are needed: the legacy behaviour is already pinned by golden.

## Test strategy

- **Equivalence**: golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. TRANSACT, ACCTFILE and SYSOUT are compared byte for byte (AC2, AC3, AC9).
- **Characterization**: D1 (acct 50 unchanged, txn 18.77 written), truncation 29.24, zero-rate rewrite, abend RC 16 ×3, ID/spacing layout.
- Commands:
  ```
  cd modernized/cbact04c && mvn -q verify
  legacy/harness/golden.sh --check          # legacy still green (sanity)
  # CLI cross-check vs golden:
  java -jar target/cbact04c.jar 2022071800 <in> /tmp/j && diff -r ...
  ```

## Risks

| Risk | Mitigation |
|---|---|
| Overpunch zero sign (`{` vs `}`) on results or negative zero | `ZonedTest`, plus golden diffs on ACCTFILE |
| Uninitialised bytes (desc tail, FILLER) must be spaces | Space-fill the record buffer first; layout test |
| Accidental float or HALF_UP rounding | `BigDecimal` + `RoundingMode.DOWN` only; 29.24 test |
| Input line endings and short lines (`cardxref` 36 bytes, CRLF in tcatbal) | Strip `\r`, pad to LRECL on read |
| SYSOUT trailing spaces in DISPLAY of the 50-byte record | Compare against golden SYSOUT byte for byte |
| Abend: whether partial outputs are expected | Spec: none are written. Only RC and SYSOUT are tested |

## Effort (agent)

Build ≈ 12 min, tests and golden green ≈ 8 min. **Total ≈ 20 min.**
