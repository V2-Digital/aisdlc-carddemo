# CR-1 Test report — CBACT04C → Java 21

**Result: green. 30 JUnit tests pass, `golden.sh --check` passes. Fix iterations on production code: 0.**

## Commands
| Command | Result |
|---|---|
| `mvn -q test` in `modernized/cbact04c` | 30 tests, 0 failures, BUILD SUCCESS |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` |
| `git diff --stat baseline -- legacy/golden legacy/harness .github` | empty |

## Evidence by acceptance criterion
| AC | Test | Result |
|---|---|---|
| 1 | `InterestTest.pomHasOnlyJunit`, build | 1 dependency (junit-jupiter), Java 21 |
| 2, 4 | `GoldenEquivalenceTest.stock` | TRANSACT 50, ACCTFILE 50, SYSOUT 152 byte-identical; rc 0 |
| 3, 4 | `GoldenEquivalenceTest.afterCbtrn02c` | TRANSACT 50, ACCTFILE 50, SYSOUT 302 byte-identical; rc 0 |
| 5 | `InterestTest` | 2339.97@15% → 29.24; −10.01 → −0.12; 999,999,999.99×9999.99 → 333,324,999.91; no `double`/`float` in `src/main` |
| 6 | `DefectCharacterizationTest` (8) | D1 single and last account unchanged (and golden acct 50); D2 truncation; D3 miss lines and group hit; D4 suffix/id wrap, balance wrap |
| 7 | `ErrorPathTest` (9) | E1 (incl. F1 `DALY REJECTS`, F1b `FILE35`), E2, E3, E4, E5, E7, E9, E12 (exit 3), usage (exit 2) |
| 8 | `ZonedTest` (5) | all 20 overpunch chars, ±0 → `{`, ±max, malformed input |
| 9 | golden check and diff above | clean |
| R2 | `DifferentialEdgeTest` | crafted dataset run through the **COBOL harness** (expected in `src/test/resources/edge/expected`) and Java: TRANSACT 5, ACCTFILE 4, SYSOUT 18, byte-identical. Covers negative balance, group hit (GRP1, 12.50%), miss→DEFAULT, zero rate, last-account D1. |

Test code that reads `legacy/golden` and `app/data/ASCII` does so in place; ACCTFILE inputs are copied to temp dirs so the repo is never rewritten.

## Iterations
Three red runs, all bugs in my new tests (wrong expected overpunch value, a mis-typed fixture amount, a helper that failed on re-copy). The production code needed no change.

## Not proven / for humans
- **Error-path status codes** `0035`, `0030`, `0034` come from the spec and were **not verified** with a `cobc` probe (not reachable via `run.sh`). Only `0023` (E3) is harness-verified. The E3 SYSOUT is tested against the spec text, not freshly captured.
- E6 (write/rewrite I/O failure) has no test; it cannot be provoked portably. E2 is covered.
- D4 suffix wrap is pinned as the arithmetic (`(999999+1) % 10^6 → 000000`), not by generating 1M rows.
- Byte-identity is proven on the canonical text form only (R1).
- Decisions still open: Q2 (separate CRs for D1/D2) and E9 as a deliberate deviation.
