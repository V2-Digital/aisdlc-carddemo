# CR-1 Build notes

Implemented `modernized/cbact04c/` (Java 21, JUnit 5 + Surefire only declared; no tests written yet, left to Test stage).

Classes in `com.v2.carddemo.cbact04c`: `Zoned`, `Clock`, `Cbact04c`, `Main`. Records are handled inline in `Cbact04c` (no separate `Records` class), a small deviation from the plan.

## Evidence
CLI run with `--parm=2022071800 --now=2022071800000000`:

| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` | byte-identical | byte-identical | identical | 0 |
| `CBACT04C-after-CBTRN02C` | byte-identical | byte-identical | identical | 0 |

## Findings
- **Spec offset error:** spec.md gives ACCTFILE cycle credit/debit at 77/89 and group id at 111. The copybook `CVACT01Y` puts them at 79/91 and 113 (1-based). The code uses the copybook, and the goldens confirm it. spec.md was not edited.
- Abend paths (missing account/XREF/DEFAULT, missing input file) are implemented from reading the COBOL and are **not yet verified** against real COBOL or tests. The missing-file status (`NNNN0035`) is a guess.
- D1 (last account not rewritten), D2 (truncation), D5, D6 and D7 are preserved.
