# CR-1 Test report — CBACT04C → Java 21

**Result: all green. 0 fix iterations needed for the Java program** (only my own test fixtures needed correcting).

## Commands and results

| Command | Result |
|---|---|
| `mvn -q test` (in `modernized/cbact04c`) | 19 tests, 0 failures |
| `bash legacy/tests/cbact04c_equiv.sh` | exit 0; 3/3 cases match COBOL |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` |
| `git diff --stat baseline -- legacy/golden legacy/harness .github` | empty |

## Java tests (19)

| Class | Tests | Covers (AC) |
|---|---|---|
| `GoldenTest` | `stock`, `afterCbtrn02c`, `deterministic` | AC 2, 3, 4, 8: TRANSACT (50 recs) and ACCTFILE (50 recs) byte-identical (`assertArrayEquals`) in both scenarios; SYSOUT and RC equal to golden; two runs identical |
| `InterestTest` | `truncatesNotRounds` | AC 5 / D2: 3577.41 → `0000000447A` (44.71; rounding would give 44.72) |
| `DefectTest` | `d1_lastAccountNotRewritten_butItsTxnIsWritten`, `d1_singleAccountRunRewritesNothing`, `d3_cycleZeroedEvenWithoutInterest`, `d4_negativeBalanceGivesNegativeInterest`, `d5_discgrpOpenFailureText` | AC 6, D1, D3, D4, D5 |
| `LookupTest` | `groupHit`, `defaultFallback`, `defaultMissing_16`, `accountMissing_16`, `xrefMissing_16`, `emptyTcatbal` | AC 7; abends write nothing to `<out>` |
| `ZonedTest` | 4 | overpunch round-trip, signs, -0 → `{`, mod 10^n |

## COBOL vs Java (`legacy/tests/cbact04c_equiv.sh`)

Runs the real COBOL through the harness and the Java jar on the same inputs, with the same frozen clock.

| Case | COBOL RC / Java RC | Compared |
|---|---|---|
| Group hit (accts 1–3 in `ZEROAPR`) | 0 / 0 | TRANSACT, ACCTFILE, SYSOUT identical |
| Negative balances (D4: -1000.01, -3577.41) | 0 / 0 | TRANSACT, ACCTFILE, SYSOUT identical; the script also checks that COBOL really wrote a negative amount |
| `DEFAULT 01/0001` removed | 16 / 16 | SYSOUT identical |

D4 is now confirmed against real COBOL: negative balance → negative interest txn, sign written as overpunch `J`–`R`.

## Notes / left open

- Nothing failing.
- The test fixtures that failed on the first run were mine: wrong column assumptions and the wrong ZEROAPR/DEFAULT rates. Program code was not changed.
- Group-hit test uses `ZEROAPR` (rate 0, so no txn) as the observable proof of a hit, because the stock account data has a blank `ACCT-GROUP-ID`.
- The equivalence script builds the jar (`mvn package -DskipTests`) itself, so CI needs Maven and a JDK 21 for it.
- Human decision still pending: D1–D5 follow-up CRs (D1 is the material financial defect).
