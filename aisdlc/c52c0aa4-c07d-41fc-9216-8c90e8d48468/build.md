# CR-1 Build notes

Built `modernized/cbact04c/` (pom, `Overpunch`, `FixedFile`, `Abend`, `Cbact04c`, README) per plan.

## Verified (jar run vs golden, clock `2022071800000000`)
| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` | identical | identical | identical | 0 |
| `CBACT04C-after-CBTRN02C` | identical | identical | identical | 0 |

Abend check: unknown account `00000009999` gives the same SYSOUT as the real COBOL run (RC 16).

## Notes / for later stages
- No tests written yet (Test stage). `legacy/tests/cbact04c-abends.sh` is also still to do.
- `mvn -o` fails on a cold cache; use online `mvn -q package`.
- Missing-input-file abend text is a guess: GnuCOBOL status `35` and `FILE STATUS IS: NNNN0035`. The Test stage
  should check it against COBOL. The XREF open message appends the status inline in COBOL.
- `run(...)` takes an extra `syserr` stream (spec had only sysout), so tests can capture the abend line.
- Abend prints `ABENDING PROGRAM` after the error lines and writes no output files, as specified.
