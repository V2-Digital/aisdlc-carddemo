# CR-1 Build summary

Implemented `modernized/cbact04c/` per the approved plan (package `com.v2.carddemo.cbact04c`: `Zoned`, `FixedFile`, `Abend`, `Cbact04c`), plus `legacy/tests/cbact04c_java_equiv.sh` and a README.

**Evidence** (`legacy/tests/cbact04c_java_equiv.sh`): for both `CBACT04C` and `CBACT04C-after-CBTRN02C`, `TRANSACT`, `ACCTFILE`, `SYSOUT` and `RC` are byte-identical to `legacy/golden/` (8/8 `cmp` OK).

**Deviations / notes**
- Package is `com.v2.carddemo.cbact04c` (per the build rules), not `carddemo.cbact04c` as in the plan.
- `-Dcarddemo.clock=<ISO local datetime>` fixes the clock for the CLI (needed by the equivalence script).
- `mvn -o` failed (plugins not cached); online `mvn -q package -DskipTests` works.
- Open-failure status text is a fixed `0035`; abend persistence is best effort (no golden scenario).
- JUnit tests are not written yet (Test stage); the pom already has JUnit 5 + Surefire.
