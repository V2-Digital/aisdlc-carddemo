# CR-1 Test report

**Result: all green, nothing failing.** 16 JUnit tests, 1 shell test, golden check.

## Commands and results
| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | pass: 16 tests (GoldenEquivalence 2, Characterization 10, Zoned 4), 0 failures |
| `bash legacy/tests/cbact04c_abend.sh` | `PASS` (COBOL RC 16, abend SYSOUT pinned, Java identical) |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden`, two runs identical |
| `git diff --stat baseline -- legacy/golden legacy/harness .github` | empty |

## Equivalence vs golden (clock `2022071800000000`, PARM `2022071800`, no masking)
| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` | 50 records, byte-identical | 50, identical | 152 lines, identical | 0 |
| `CBACT04C-after-CBTRN02C` | 50, identical | 50, identical | 302 lines, identical | 0 |

Compared: 200 records and 454 SYSOUT lines, 0 diffs. Covers AC1–4 and AC9.

## Characterization tests (`CharacterizationTest`)
- `d1_lastAccountNotRewritten_butItsTranIsWritten` (AC5): the last account's row is unchanged and its tran is still posted.
  Uses small hand-built data, not the golden acct 50 values. The golden scenario also shows it.
- `d3_interestTruncatesNotRounds` (14.5609 → 14.56; 0.009625 → 0.00) and `d3_negativeBalance…` (−8.3415 → −8.34) (AC6)
- `zeroRateProducesNoTranAndNoInterest` (AC7)
- `defaultFallbackPrintsBothMessages`, `groupRateHit_usesAccountGroupNotDefault` (the `00` path, not in golden),
  `missingAccountAbends`, `missingXrefAbends`, `missingDefaultRateAbends` (RC 16, `ABENDING PROGRAM`) (AC8)
- `emptyTcatbalIsTwoLinesRc0`
- `ZonedTest`: overpunch decode, round-trip, `-0` → `{`, high-order overflow truncation (R1, R2)

## Abend text checked against COBOL
`legacy/tests/cbact04c_abend.sh` uses fixture `legacy/tests/cbact04c_abend/ACCTFILE.txt` (49 accounts, acct 25 removed) and runs the real COBOL.
It gives RC 16 and ends with `ACCOUNT NOT FOUND: 00000000025` / `ERROR READING ACCOUNT FILE` / `FILE STATUS IS: NNNN0023` / `ABENDING PROGRAM`.
Java prints the same full SYSOUT and exits 16. This settles the build-stage doubt for the account-lookup abend.

## Iterations
2 fix iterations. **All were mistakes in my new test fixtures and none were in the program.** They were field widths (ACCT amounts are 12 wide),
the TRAN amount offset, and rate/balance values off by 10× (`DIS-INT-RATE` is `S9(4)V99`). No production code changed in the Test stage.

## Not covered / for humans
- Missing-xref and missing-DEFAULT-rate abend text is checked only in Java (same code pattern, `NNNN0023`). COBOL was not run for them.
- The open-error status `NNNN0035` for a missing file is still an **assumption**, not checked against COBOL.
- The account-specific rate path (`00` read) is covered only by a unit test. The golden data cannot reach it (D6).
- On abend the harness exports no datasets, so the partial-output behaviour is not compared with COBOL.
- D1 (last account not updated, a money defect) and D3 (truncation) are preserved. They need a business decision in separate CRs.
