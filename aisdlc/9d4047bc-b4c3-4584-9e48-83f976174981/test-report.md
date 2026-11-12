# CR-1 Test report — `CBACT04C` Java port

**Result: green on the first run of the real suite. Fix iterations: 0 production changes** (3 of my own test-fixture
offsets were wrong and corrected; no change to `src/main`).

## Commands and results
| Command | Result |
|---|---|
| `mvn -f modernized/cbact04c verify` | 26 tests, 0 failures (Golden 2, ZonedDecimal 4, Interest 5, Characterization 15). Only JUnit 5 + Surefire. |
| `legacy/harness/golden.sh --check` | OK, golden outputs unchanged, two runs byte-identical |
| `bash legacy/tests/cbact04c_abend.sh` | OK: COBOL RC=16, SYSOUT tail `ABENDING PROGRAM` |

## Golden equivalence (AC 2-4)
| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` | 50 records, byte-identical | 50, byte-identical | 152 lines, identical | 0 = 0 |
| `CBACT04C-after-CBTRN02C` | 50, byte-identical | 50, byte-identical | 302 lines, identical | 0 = 0 |

Total 100 TRANSACT + 100 ACCTFILE records and 454 SYSOUT lines compared with `assertArrayEquals` on file bytes: 0 diffs.

## Acceptance criteria 5-8 / defects
- AC5 `InterestTest.truncatesNotRounds`: 233997 cents × 15.00 → `0000000292D`. Also negative (`…292M`), negative sub-cent (`…000}`), interest overflow wrap, balance overflow wrap (D2, D4).
- AC6/D1 `lastAccountNotRewritten_D1` (inline) and `…InGolden_D1` (acct 50 identical to the CBTRN02C input, acct 49 changed).
- AC7/D3 `defaultFallback_D3`, `directGroupHit_D3`, `abendMissingDefault/Account/Xref`, `missingInputFileAbends`.
- AC8/D6 `zeroRateNoTx`, `cycleZeroedWhenInterestZero_D6`, `untouchedAccountVerbatim`; plus empty TCATBALF, duplicate xref (first wins), tran-id/timestamp, CRLF/short-line normalisation.
- D5 (suffix wrap at 10^6) and D7 (fees no-op) are not covered by a dedicated test: D5 would need 1M records; D7 is implicit (no code).

## Cross-checks against real COBOL (not in golden data; run by hand)
- **Abend:** COBOL (`cbact04c_abend.sh`, DISCGRP without `DEFAULT`) vs Java on the same inputs: `SYSOUT.txt` identical, Java exit 16. The harness leaves no TRANSACT/ACCTFILE after an abend, so those weren't compared. Java's spec'd "partial outputs after abend" is untested against COBOL.
- **Direct group hit:** `ACCT-GROUP-ID=A000000000` on account 1, scenario-3 TCATBALF, full `discgrp.txt`: COBOL vs Java `TRANSACT`, `ACCTFILE`, `SYSOUT` byte-identical (98 MISSING lines instead of 100).

## Left failing / for a human
- Nothing failing.
- Gap: `legacy/tests/cbact04c_abend.sh` is the only script under `legacy/tests`; the direct-hit COBOL comparison above is manual, not in CI.
- Defects D1-D7 remain preserved and flagged for a separate decision (see spec).
