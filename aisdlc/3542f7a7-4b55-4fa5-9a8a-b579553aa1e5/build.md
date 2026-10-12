# Build notes: CR-1

Implemented `modernized/cbact04c/` per plan (package `com.v2.carddemo.cbact04c`):
`Zoned`, `FixedFile`, `Abend`, `InterestCalc`, `Main`, `pom.xml`, `README.md`.

## Evidence
`mvn -q package -DskipTests` succeeds. Ran the jar with PARM `2022071800`, clock `2022071800000000`:

| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| stock data | byte-identical | byte-identical | byte-identical | 0 |
| after CBTRN02C | byte-identical | byte-identical | byte-identical | 0 |

## Deviations from plan
- Records are held as strings inside `InterestCalc` (no separate `Records.java`); account bytes are patched in place.
- Missing-input open errors print `FILE STATUS IS: NNNN0035`; not golden-covered.

## For the Test stage
JUnit tests (plan items 8-11) are not written yet. Abend, overflow, F1-F7 paths are implemented but untested;
verify against `INPUT_DIR` COBOL probes. `golden.sh --check` not re-run (nothing under `legacy/` touched).
