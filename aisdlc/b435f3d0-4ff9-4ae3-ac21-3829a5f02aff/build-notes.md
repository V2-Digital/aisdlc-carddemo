# CR-1 Build notes

Implemented `modernized/cbact04c/` per plan (pom, `Zoned`, `Records`, `InterestCalc`, `Main`, README). Tests are left to the Test stage.

## Evidence (jar run, `--now 2022071800000000`, PARM `2022071800`)
- Stock data: `diff -rq` of TRANSACT, ACCTFILE, SYSOUT, RC vs `legacy/golden/CBACT04C` → identical.
- ACCTFILE/TCATBALF from `golden/CBTRN02C`: identical to `legacy/golden/CBACT04C-after-CBTRN02C`.

## Deviations / notes
- Maven plugins were not cached offline; built online (`mvn -q package`).
- Output also writes `RC.txt` (needed for RC parity with golden; spec lists it via AC4).
- Abend paths are implemented but not yet exercised (Test stage: `AbendTest`).
- Open-error file status for a missing input is `0035` (assumption; COBOL status for missing file).
