# CR-1 Build notes

Implemented `modernized/cbact04c/` per plan items 1-5 and 8 (pom, `ZonedDecimal`, `Records`, `Cbact04c`, `Main`, README).
Tests and the COBOL edge script (plan items 6-7) are left to the Test stage.

## Evidence
- `mvn -q package -DskipTests` builds `target/cbact04c.jar` (Java 21, no runtime deps).
- Stock inputs, PARM `2022071800`, clock `2022071800000000`: `diff -r` against `legacy/golden/CBACT04C/` is clean
  (TRANSACT, ACCTFILE, SYSOUT, RC).
- Inputs from `legacy/golden/CBTRN02C/` (ACCTFILE, TCATBALF): `diff -r` against
  `legacy/golden/CBACT04C-after-CBTRN02C/` is clean.

## Notes
- `mvn -o` fails on a cold cache (resources plugin not downloaded); online `mvn` works.
- Abend path (R8) and overflow wrap (D6) are implemented per spec but **not yet checked against COBOL**; the edge
  script in Test stage must do that. Open-failure messages are untested.
- Open-error status text uses `35` as a guess for a missing file; the legacy shows the real status. Low risk.
- Decision for a human: D1 (last account never updated) is preserved; raise a separate CR.
