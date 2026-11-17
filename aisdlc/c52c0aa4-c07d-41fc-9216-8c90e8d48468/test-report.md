# CR-1 Test report

**Result: green. 26 JUnit tests pass, 0 fail. 0 fix iterations needed. Nothing left failing.**

## Commands run
| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | pass. 26 tests: AbendTest 7, GoldenEquivalenceTest 2, LogicTest 14, OverpunchTest 3 |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` |
| `legacy/tests/cbact04c-abends.sh` | OK. The real COBOL still gives RC 16 and the saved SYSOUT for 3 abend fixtures |
| CLI end to end: `java -jar target/cbact04c.jar 2022071800 <in> <out>` on the after-CBTRN02C inputs, with `CARDDEMO_CURRENT_DATE=2022071800000000` | RC 0, `cmp` identical for TRANSACT, ACCTFILE and SYSOUT |

## Equivalence (byte compare against `legacy/golden/`)
| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` (raw CRLF `tcatbal.txt`, 36-byte xref lines) | 50 records identical | 50 identical | 152 lines identical | 0 |
| `CBACT04C-after-CBTRN02C` | 50 identical | 50 identical | 302 lines identical | 0 |

That is 100 transactions and 100 account records compared, with 0 diffs.

## Acceptance criteria coverage
| AC | Test |
|---|---|
| 1 | `mvn -q verify` passes. The only dependencies are JUnit 5 and Surefire (plus the jar plugin). |
| 2, 3, 9 | `GoldenEquivalenceTest.stock` and `.afterCbtrn02c` |
| 4 | `AbendTest`: account missing, xref missing, DEFAULT missing (SYSOUT equals the COBOL output). Each of the 4 input files missing gives RC 16, `CEE3ABD` on stderr and no output files. |
| 5 | `LogicTest`: 14.56 case, 100.47 gives 1.25 (HALF_UP would give 1.26), -10.01 gives -0.12 encoded `…1K`, account rewrite with cycle fields zeroed and other bytes untouched |
| 6 | `LogicTest.idsSequential…`: zero-amount rows count, rate-0 rows don't |
| 7 | `LogicTest.groupMissFallsBackToDefault…` (plus a group-hit counter-case) |
| 8 | `LogicTest.D1_…` (synthetic and golden account 50 vs txn `…050` = 18.77), `D2_…` (50 blank group IDs and 50 MISSING lines in golden), `D3` (truncation test), `D4_…` (zero-amount rows, DALY REJECTS text) |
| Extra | empty TCATBALF, duplicate xref uses the lowest card, `OverpunchTest` covers all 20 signs and zero encodes as `{` |

## New files
- `legacy/tests/cbact04c-abends.sh` runs under CI. It runs the real COBOL on `legacy/tests/cbact04c-abends/{account-missing,xref-missing,default-missing}/` and fails unless RC is 16, CEE3ABD appears and SYSOUT equals `expected-SYSOUT.txt`. `--write` regenerates the references.
- `modernized/cbact04c/src/test/java/...`: the test classes above and the helper `T`.

## Verified against COBOL
- The missing-input-file abend text that the build stage had guessed is now confirmed. I pointed each DD at a nonexistent file in the real COBOL: status `35`, `FILE STATUS IS: NNNN0035`, RC 16. The XREF message really has the status appended inline (`ERROR OPENING CROSS REF FILE35`). The Java matches.
- The `AbendTest` text for the 4 missing-file cases is hard-coded in the test from that manual COBOL run. It is not in a CI script.

## Limitations
- The 6-digit counter wrap (D4) is not exercised, because it would need over 1M rows. The test only pins the 6-digit sequence.
- Duplicate-xref "lowest card wins" is tested against the spec, not against COBOL, because the harness has no duplicate-account fixture.
- `modernized/cbact04c/target/` is untracked build output, so check that it is ignored before committing.

## For a human to decide
D1 to D4 are preserved and pinned, as in intent Q3. D1 (ledger and master disagree for the last account every night) is the highest impact and needs its own CR.
