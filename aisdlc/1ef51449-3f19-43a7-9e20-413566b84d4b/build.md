# CR-1 Build notes

Implemented `modernized/cbact04c/` per plan (pom, `Zoned`, `RecordFiles`, `InterestCalc`, `AbendException`, `Main`, README).
Package is `com.v2.carddemo.cbact04c`, as the Build brief requires. The plan said `carddemo.cbact04c`; Test-stage files go under the same package.

## Evidence (jar run with `CARDDEMO_CURRENT_DATE=2022071800000000`, PARM `2022071800`)
| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` | byte-identical | byte-identical | byte-identical | 0 |
| `CBACT04C-after-CBTRN02C` | byte-identical | byte-identical | byte-identical | 0 |

## Notes
- `mvn -o` fails here because the resources plugin is not cached, so the build used online Maven (Central only).
- Surefire is pinned in the pom; no tests written yet (Test stage: Golden, Interest, Zoned, Defect, Abend).
- Abend path implemented (exit 16, status lines) but not yet exercised by tests.
- Open-error message for XREFFILE appends a status (`...CROSS REF FILE` + status in COBOL); the missing-file path uses status 35. Not gated.
