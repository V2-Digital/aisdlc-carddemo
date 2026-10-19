# CR-1 Test report: `CBACT04C` Java 21

**Result: all green, 0 fix iterations needed on the Java code** (4 mistakes in my new tests fixed, see below).

## Commands and results
| Command | Result |
|---|---|
| `mvn -q -f modernized/cbact04c test` | 27 tests, 0 failures (Abend 6, DefectCharacterization 7, GoldenEquivalence 2, Interest 5, Overpunch 7) |
| `bash legacy/tests/cbact04c_edge.sh` | 6 cases, all identical COBOL vs Java |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` (legacy untouched) |

## Golden equivalence (`GoldenEquivalenceTest`, `Files.mismatch == -1`)
PARM `2022071800`, clock `2022071800000000` injected.
| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` (stock) | 50 recs identical | 50 recs identical | 152 lines identical | 0 |
| `CBACT04C-after-CBTRN02C` | 50 recs identical | 50 recs identical | 302 lines identical | 0 |

Total 200 records and 454 SYSOUT lines compared, 0 diffs. Covers AC 1-4, 8.

## Defect characterization (AC 5)
- **D1**: acct 50 txn `2022071800000050` = `0000000187G`, but ACCTFILE rec 50 equals `golden/CBTRN02C` input. Exactly 49 of 50 records changed.
- **D2**: 100 `DISCLOSURE GROUP RECORD MISSING` and 100 `TRY WITH DEFAULT GROUP CODE` lines.
- **D3**: 2339.97 × 15.00 → 29.24 (unit and golden `0000000292D`). Negative: -10.01 → -0.12 (`0000000001K`).
- **D4**: counter 999999 → 000000 → 000001. Interest overflow is high-order truncated, no error (unit level).
- Trailing 20 bytes of each txn are spaces.

## Codec and arithmetic (AC 6)
`OverpunchTest`: all 20 sign chars (`{A-I}J-R`) on S9(9)V99, S9(10)V99 and S9(4)V99 round-trip. Zero encodes as `{`, `}` decodes to zero.
Invalid input is rejected. `InterestTest` uses BigDecimal only.

## Abend paths (AC 7)
`AbendTest`: missing account, xref and DEFAULT group each give RC 16 and the exact SYSOUT lines. DISCGRP open error keeps `ERROR OPENING DALY REJECTS FILE`.
Empty TCATBALF gives START/END only, no txns, ACCTFILE unchanged.

## Cross-check against real COBOL: `legacy/tests/cbact04c_edge.sh`
Same inputs through `harness/run.sh` (GnuCOBOL) and the Java jar. Fixtures in `legacy/tests/cbact04c_edge/`. The script exits non-zero on any difference.
| Case | Compared |
|---|---|
| `negative`: balances -1.00 and -0.00 (`}`), 0.99, 99999.99 negative, type 03 rate 0, last account | SYSOUT, RC, TRANSACT, ACCTFILE |
| `rate0only`: accounts whose only category has rate 0 | all four |
| `empty` TCATBALF | all four |
| `nodefault`, `noxref`, `noacct` (abends, RC 16) | SYSOUT, RC (`run.sh` exports no datasets on abend) |

## Iterations
Java code needed none. Four of my first-draft tests failed: wrong amount offset (133-143), a wrong truncation expectation, and my helper wrote a blank line for an empty file (which exposed no program bug). Fixed in the tests.

## Not covered / for a human
- **Partial outputs after abend** (spec rule 10) are not verifiable against COBOL, as planned. Treat any RC 16 output as invalid.
- Open-error status `NNNN0035`, duplicate-key and non-numeric messages are Java-only (legacy undefined). Not compared.
- Multiple cards per account (xref choice) and duplicate keys are untested against COBOL.
- Defects D1-D4 and the empty `1400-COMPUTE-FEES` are preserved and need a separate decision (CR-2).
