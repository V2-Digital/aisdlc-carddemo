# CR-1 Build notes

Implemented `modernized/cbact04c/` per plan items 1–5 and 10: `pom.xml`, `Zoned`, `Records`, `Datasets`, `Cbact04c`, README.
Compiles with `mvn -q package -DskipTests` (Java 21, JUnit 5 declared test-only).

## Evidence (CLI, frozen clock `2022071800000000`, PARM `2022071800`)
| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` | identical | identical | identical | 0 |
| `CBACT04C-after-CBTRN02C` | identical | identical | identical | 0 |

## Not done (Test stage)
JUnit tests (plan 6–8) and `legacy/tests/cbact04c_abend.sh` (9). Abend/open-error SYSOUT lines (`FILE STATUS IS: NNNN0023`
for lookups; `NNNN0035` for a missing file at open is my assumption) are **unverified against COBOL**.

## Notes
- Main loop is a plain for-each over TCATBAL with no post-loop rewrite, which reproduces D1.
- On abend, TRANSACT and ACCTFILE are written as they stand, as with VSAM.
