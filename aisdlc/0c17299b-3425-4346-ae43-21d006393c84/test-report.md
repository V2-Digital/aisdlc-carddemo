# CR-1 Test report

**Result: all green, 0 fix iterations.** Nothing left failing.

## Commands and results
| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | pass: 25 tests, 0 failures (Abend 6, Defect 9, Golden 2, Interest 3, Zoned 5) |
| `legacy/tests/cbact04c_missing_default.sh` | PASS (legacy COBOL: RC 16 + the 4 SYSOUT lines) |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden`; legacy untouched |
| CLI smoke: `java -jar target/cbact04c-1.0.jar 2022071800 in out` (clock `2022071800000000`), after-CBTRN02C inputs | rc 0; TRANSACT, ACCTFILE and stdout `cmp`-identical to golden |

## Equivalence (GoldenTest, byte comparison)
| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` (stock) | 50 records identical | 50 identical | identical | 0 = RC.txt |
| `CBACT04C-after-CBTRN02C` | 50 identical | 50 identical | identical (302 lines) | 0 = RC.txt |

Total 200 output records compared, 0 diffs; stderr empty on success.

## Acceptance criteria
- AC1 `mvn -q verify`: pass; deps are JUnit 5 + Surefire (+ jar plugin).
- AC2/3/4: GoldenTest above.
- AC5 `InterestTest`: 1000.00×15 → 12.50; 1234.56×15 → 15.43; 1239.99×15 → 15.49 (rounding would give 15.50, D2); −99.13×24 → −1.98.
- AC6 `AbendTest`: DEFAULT fallback, exact group (no fallback), missing DEFAULT / account / xref → 16 with the spec'd lines and `CEE3ABD: ABEND U0999` on stderr; absent file → 16 with D5 text and status 0035.
- AC7 `DefectTest.d1_*`: last account byte-unchanged while its interest is charged.
- AC8: golden check OK.

## Characterization tests (`DefectTest`)
D1 (two variants), D3 (monthly wrap, total wrap at 10⁹, balance wrap at 10¹⁰), D4 (cycle zeroed at rate 0), D5 (in AbendTest). Also: zero rate writes no tran and no counter increment, unsorted TCATBALF is processed in key order, empty TCATBALF.

## Notes for reviewers
- The missing-DEFAULT abend is proven against real COBOL (`legacy/tests/cbact04c_missing_default.sh`, fixture `DISCGRP.txt` = stock minus 17 `DEFAULT` rows). The other abend paths and D3 wrap are verified only against the spec, not run on COBOL.
- The build stage noted that spec ACCTFILE offsets (84/96/118) are wrong; the code uses the copybook offsets (78/90/112) and the golden diff confirms them. `spec.md` still needs correcting.
- Account-missing/xref-missing abend text and D3 wrap semantics are not golden-covered; a human may want a COBOL cross-check.
