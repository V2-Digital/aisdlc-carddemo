# CR-3 Build notes

Implemented `modernized/cbtrn02c/` per plan: `pom.xml`, `Overpunch`, `RecordIo`, `KsdsFile`, `Cbtrn02c`, `Main`, `Dockerfile`, `README.md`.

## Evidence
- `mvn -q package -DskipTests` compiles.
- Stock inputs, clock `2022071800000000`: `TRANFILE`, `DALYREJS`, `ACCTFILE`, `TCATBALF` and `SYSOUT` are **byte-identical** to `legacy/golden/CBTRN02C/`. RC = 4.

## Not yet done (Test stage)
JUnit tests, `legacy/tests/cbtrn02c_equiv.sh` differential cases (100/101/103, dup TRAN-ID, overflow, first-create FILLER).

## Notes for the Test stage
- Abend paths flush partial outputs and return 16; whether COBOL's partial unload matches is unverified (differential case h).
- Open-error text is generic (`FILE STATUS IS: NNNN0035`); not golden-checked.
- The maven-resources plugin was not cached offline, so build with network (`mvn -q package`).
- `.gitignore` excludes `target/`.
