# CR-1 Build notes

Implemented `modernized/cbact04c/` (pom, `Zoned`, `Records`, `Abend`, `InterestCalc`, `Main`, README) per plan/spec R1–R9.

## Evidence (CLI run on the built jar)
- Stock inputs, PARM `2022071800`, clock `2022071800000000`: `TRANSACT.txt`, `ACCTFILE.txt` and stdout are
  byte-identical to `legacy/golden/CBACT04C/` (`cmp`, 152 SYSOUT lines).
- ACCTFILE + TCATBALF from `golden/CBTRN02C`: outputs and stdout are byte-identical to
  `legacy/golden/CBACT04C-after-CBTRN02C/` (302 SYSOUT lines).

## Not yet done (Test stage)
JUnit tests, and `legacy/tests/cbact04c-negative.sh` (the sign of a −0.00 result is untested: `Zoned.format` currently writes `{` for zero).
`legacy/` was not touched.

## Notes
- The first `mvn -o` failed because the plugins weren't cached, so I built online (Maven Central).
- Account is looked up and xref-checked in the same order as legacy, so the abend DISPLAY lines match.
