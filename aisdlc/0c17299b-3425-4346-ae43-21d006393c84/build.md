# CR-1 Build notes

Implemented `modernized/cbact04c/` (Zoned, FixedFile, Cbact04c, Main, pom, README). Package `carddemo.cbact04c`.

## Evidence (CLI jar, PARM 2022071800, clock 2022071800000000)
| Scenario | TRANSACT | ACCTFILE | stdout vs SYSOUT.txt | RC |
|---|---|---|---|---|
| CBACT04C | identical | identical | identical | 0 |
| CBACT04C-after-CBTRN02C | identical | identical | identical | 0 |

## Spec correction
Spec offsets for ACCTFILE were wrong. From `CVACT01Y.cpy`: cycle credit 78, cycle debit 90, group id 112
(spec said 84 / 96 / 118). Code uses the copybook offsets; the golden diff confirms them. Update spec.md.

## Not done (left to Test stage)
- JUnit tests (Zoned, Interest, Golden, Abend, Defect) — no tests exist yet.
- `legacy/tests/cbact04c_missing_default.sh`.
- Abend paths, D3 wrap and unsorted-input handling are implemented but not yet exercised.
- `mvn -o` fails offline (plugins not cached); build with network `mvn -q package`.
