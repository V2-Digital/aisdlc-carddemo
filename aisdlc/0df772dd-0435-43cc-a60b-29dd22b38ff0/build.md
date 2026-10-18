# CR-1 Build notes

Implemented `modernized/cbact04c/` per plan/spec (no changes to `app/`, `legacy/golden|harness`, `.github/`).

- `Zoned` (overpunch decode/encode, digit wrap), `Cbact04c` (main logic, injected `Clock`, returns RC), `Main` (env binding), `pom.xml`, `README.md`.
- Package is `com.v2.carddemo.cbact04c` (plan said `carddemo.cbact04c`; the project convention is `com.v2.carddemo`).

## Evidence
Built with `mvn -q package -DskipTests`, ran the jar on both scenarios with clock `2022071800000000`:
- `CBACT04C` — TRANSACT, ACCTFILE, SYSOUT, RC all identical to `legacy/golden/CBACT04C` (`diff -r` clean).
- `CBACT04C-after-CBTRN02C` (inputs from `golden/CBTRN02C`) — all four identical.

## Not done (Test stage)
JUnit tests (equivalence, defects D1–D4/D6, lookup, tran-id, abend) and `golden.sh --check`.
