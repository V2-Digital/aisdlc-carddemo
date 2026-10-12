# CR-1 Build notes

## Done
`modernized/cbact04c/` (Maven, Java 21, JUnit 5 test-scope only, package `com.v2.carddemo.cbact04c`):
`Zoned`, `FixedText`, `Ksds`, `Abend`, `InterestCalc` (mirror of the COBOL paragraphs), `Main` (CLI), `README.md`.
Plan files 1-8 and 11. Tests (9, 10) are left to the Test stage.

## Evidence
| Check | Result |
|---|---|
| `mvn -q package -DskipTests` | OK |
| Stock inputs, PARM `2022071800`, clock `2022071800000000` | TRANSACT, ACCTFILE, SYSOUT byte-identical to `golden/CBACT04C`; rc 0 |
| ACCTFILE/TCATBALF from `golden/CBTRN02C` | TRANSACT, ACCTFILE, SYSOUT byte-identical to `golden/CBACT04C-after-CBTRN02C`; rc 0 |
| `legacy/harness/golden.sh --check` | OK |

## Status-code probe (cobc, GnuCOBOL 3.2)
Open INPUT / I-O on a missing indexed file → `35`; OPEN OUTPUT in a missing directory → `30`.
These confirm the codes in spec §4 E1/E2. E6 (`30`) and E9 (`34`) remain unverified.

## Notes for Test stage
- Offline Maven (`-o`) fails on a cold cache (resources plugin); run `mvn` online.
- `Main.run(args, out, err)` is callable in-process and returns the exit status.
- Dataset load happens in the open step; a missing file → E1 message and exit 16. Duplicate key → stderr + exit 3.
- On abend the rewritten accounts so far are written out and the transactions already written stay.
- Added `[ASSUMPTION]`: non-numeric balance in the ACCT record during update is reported as `ERROR RE-WRITING ACCOUNT FILE` / status 0034 (E9 guard).

## Decisions for a human
None new. E9 deviation, and D1/D2 follow-up CRs, as approved in the plan.
