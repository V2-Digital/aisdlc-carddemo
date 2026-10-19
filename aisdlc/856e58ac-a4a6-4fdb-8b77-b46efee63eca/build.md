# Build — CR-1

Implemented `modernized/cbact04c/` (`Zoned`, `Records`, `Cbact04c`, `Main`, README, pom).

## Evidence
- `mvn -q verify`: passes; `GoldenTest.stock` and `GoldenTest.afterCbtrn02c` byte-compare TRANSACT, ACCTFILE, SYSOUT and RC against golden.
- CLI smoke test with the jar on stock data: SYSOUT, TRANSACT and ACCTFILE are byte-identical to `legacy/golden/CBACT04C/`.

## Deviation from spec
- The spec's ACCTFILE columns for cycle credit / debit / group (84, 96, 118) are wrong. The copybook gives **79, 91, 113**, and I used those. Golden confirms.
- Package is `com.v2.carddemo.cbact04c` (system rules), not `com.carddemo.cbact04c` as in the plan.

## Not done (Test stage)
- Unit tests: Zoned, Record, Interest, Rules, Defect.
- `legacy/tests/cbact04c-default-missing.sh`.
- Missing input files report status 0035 (spec only defines 0023).
- `mvn -o package` needs the jar plugin, so run it online once.
