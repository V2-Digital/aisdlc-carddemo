# CR-1 Test report

**Result: green.** 26 JUnit tests pass, golden check passes, abend script passes. Nothing left failing.

## Commands and results
| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn test` | 26 run, 0 failures (Golden 2, Interest 5, Defect 7, Abend 5, ZonedCodec 6, RecordLayout 1). BUILD SUCCESS |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden`, two runs byte-identical |
| `bash legacy/tests/cbact04c-abend.sh` | 3/3 ok (real COBOL: RC 16 + exact SYSOUT tail) |
| CLI smoke (`java -jar target/cbact04c-1.0.0.jar ...`, stock inputs) | rc 0; TRANSACT, ACCTFILE and SYSOUT `cmp`-identical to `golden/CBACT04C` |

## Equivalence (byte-identical, plus RC 0)
| Scenario | TRANSACT | ACCTFILE | SYSOUT |
|---|---|---|---|
| `CBACT04C` | 50 records | 50 records | 152 lines |
| `CBACT04C-after-CBTRN02C` | 50 records | 50 records | 302 lines |

## Coverage against acceptance criteria
- AC1: only JUnit 5 (test scope) + Surefire/jar plugins.
- AC2-4: `GoldenTest.stock`, `afterCbtrn02c`.
- AC5: `InterestTest` truncation for positive and negative balances. It includes cases where rounding would differ (0.99916 → 0.99; −0.99). It also checks that −14.56 is written as `0000000145O`.
- AC6: `DefectTest`
  - `lastAccountNotRewritten` (D1): 49/50 accounts changed, acct 50 equals `golden/CBTRN02C`, yet its 18.77 transaction exists.
  - `feesStepIsNoOp` (D2).
  - `fallsBackToDefaultGroup` (D3): 100 MISSING + 100 RETRY lines.
  - `discgrpOpenErrorSaysDalyRejects` (D5).
  - `allRowsOfAccountUseHeldAccountRecordAndCard` (D7).
  - Also `existingGroupIsUsedWithoutFallback` and `zeroRateWritesNothingAndSuffixDoesNotAdvance`.
- AC7: `AbendTest` missing account / xref / DEFAULT group → rc 16, and the stdout tail equals the fixtures captured from the real COBOL (`legacy/tests/cbact04c-abend/*.expected`). It also covers no output files on abend and the empty-TCATBALF clean run.
- AC8: `RecordLayoutTest.unsetBytesAreSpaces` (desc tail, merchant name/city/zip, FILLER 330-349).
- R1: `ZonedCodecTest`, covering all overpunch chars, −0, zero as `{`, and wrap at 11 and 12 digits.

## Iterations
1 fix iteration. It was a test-fixture error of mine (malformed rate/balance field widths in one test), not a program defect. No production code changed in this stage.

## Notes for humans
- The COBOL abend script confirmed the spec's abend text exactly (A, X, D cases), so there was no guess to correct.
- D6 (suffix wrap) has no end-to-end test, because it would need 1,000,000 transactions. The arithmetic is `% 1_000_000` in the code, and `Zoned.wrap` is unit-tested.
- Duplicate xref alternate keys use first-match, as the plan assumed. The sample data has none, and this is untested against BDB ordering.
- The CI hook picks up `legacy/tests/cbact04c-abend.sh`. Its fixtures are in `legacy/tests/cbact04c-abend/`.
