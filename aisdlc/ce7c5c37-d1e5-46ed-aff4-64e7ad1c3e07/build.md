# CR-1 Build notes

Built `modernized/cbact04c/` per plan: `pom.xml`, `Zoned`, `Records`, `Cbact04c`, `Main`, `README.md`.

**Evidence** (jar run on the harness input layout, clock `2022071800000000`):
- `CBACT04C` (stock): TRANSACT, ACCTFILE, SYSOUT, RC byte-identical to `legacy/golden/CBACT04C` (`cmp`).
- `CBACT04C-after-CBTRN02C`: all four byte-identical to the golden.

**Deviations from plan**
- Package is `com.v2.carddemo.cbact04c` (system rule), not `carddemo.cbact04c`.
- `mvn -o` fails on a fresh cache; first build needs Maven Central.
- Abend is an internal exception; `Main` prints `CEE3ABD: ABEND U0999` to stderr.

**Not done (Test stage):** unit/characterization/golden tests, `legacy/tests/cbact04c-java-vs-cobol.sh`, `golden.sh --check`.
