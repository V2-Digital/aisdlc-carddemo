# CR-1 Build notes

Implemented `modernized/cbact04c/` (Java 21, JUnit 5 only) per spec R1–R8, E1–E4.

- `Zoned` (overpunch, mod-10^n truncation), `Datasets` (fixed-width I/O), `Cbact04c` (rules), `Main` (CLI).
- `mvn -q verify` passes. `GoldenTest` (stock + after-CBTRN02C) byte-compares TRANSACT, ACCTFILE, SYSOUT and RC against `legacy/golden/`.
- CLI smoke on after-CBTRN02C data via the jar: identical to golden. Missing XREFFILE → RC 16.

## Not done (Test stage)
- Characterization tests (F1, F2, F5, overpunch, abends) and `legacy/tests/cbact04c-edge.sh` COBOL fixtures.
- Overflow and -0 sign behaviour is coded to the spec assumptions and is **not yet checked against COBOL**.
- README for the module.

## Notes
- Input-open failure messages are taken from the COBOL (XREF appends the status, as `DISPLAY` does: `...FILE35`).
- E1/E3 abend paths are implemented but untested.
