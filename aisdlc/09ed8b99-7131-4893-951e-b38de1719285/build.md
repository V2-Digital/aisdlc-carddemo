# CR-1 Build notes

Implemented plan items 1-7 and 10 in `modernized/cbact04c/` (package `com.v2.carddemo.cbact04c`):
`Overpunch`, `Records`, `Clock`, `Abend`, `Sysout`, `Cbact04c`, `Main`, `pom.xml`, `README.md`.

## Checked
- `mvn -q package -DskipTests` compiles on Java 21 (needs online Maven: `-o` lacks the resources plugin).
- Jar run, PARM `2022071800`, clock `2022071800000000`, `cmp` against goldens:
  - `CBACT04C`: TRANSACT, ACCTFILE, SYSOUT identical.
  - `CBACT04C-after-CBTRN02C` (inputs from `golden/CBTRN02C/`): TRANSACT, ACCTFILE, SYSOUT identical.

## Not yet done (Test stage)
Tests (plan item 8), `legacy/tests/cbact04c_edge.sh` (item 9), `golden.sh --check`.

## Notes for Test
- Abend paths follow the spec; the open-error status `NNNN0035` and the non-numeric/duplicate-key messages are
  my own (legacy undefined), the edge script should not compare those.
- Output files are written after an abend only if all inputs opened (spec rule 10).
