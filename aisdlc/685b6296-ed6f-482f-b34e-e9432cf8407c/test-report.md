# CR-1 Test report: CBACT04C Java 21

**Result: all green. 23 JUnit tests, 4 cross-check scenarios, golden check OK.**

## Commands and results

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn verify` | BUILD SUCCESS, 23 tests, 0 failures (JUnit 5 only) |
| `bash legacy/tests/cbact04c_java_equiv.sh` | PASS: Java == real COBOL on 4 scenarios |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` |
| `git diff --stat baseline -- legacy/golden legacy/harness .github` | empty |

## Golden equivalence (AC2-4)
`GoldenEquivalenceTest`, byte-for-byte with `assertArrayEquals`:

| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` | 50 recs identical | 50 recs identical | 152 lines identical | 0 |
| `CBACT04C-after-CBTRN02C` | 50 recs identical | 50 recs identical | 302 lines identical | 0 |

## Characterization and rule tests
| Test | AC | Pins |
|---|---|---|
| `DefectF1Test` | 5 | Acct `00000000050` unchanged (`00000019458G`, cycle not zeroed) while txn `2022071800000050` (`0000000187G`) is written. Also: a single-account run never rewrites it. |
| `InterestTruncationTest` | 6, 9 | 2339.97 × 1.50 → `0000000292D`; negative balance and negative rate → `0000000292M` (toward zero); balance = 100.00 + 29.24 |
| `RulesTest` | 7 | default fallback, 3 SYSOUT lines per record, specific group hit, rate 0 → no txn but account rewritten (cycle zeroed, balance unchanged, other bytes kept), PARM pad/cut, hundredths timestamp, F4 wrap formula |
| `AbendTest` | 8 | missing account / xref / DEFAULT group → legacy lines + RC 16, no outputs written; F5 text `DALY REJECTS FILE` |
| `ZonedDecimalTest` | 9 | all 20 sign characters decode and re-encode; `}` (−0) re-encodes as `{`; high-order truncation |

## COBOL cross-check (`legacy/tests/cbact04c_java_equiv.sh`)
Runs the real COBOL via `harness/run.sh` and the jar on the same inputs, then `cmp`s TRANSACT, ACCTFILE, SYSOUT and RC. Exits non-zero on any difference.
- `stock`: identical. `after` (CBTRN02C outputs): identical.
- `edge` (`legacy/tests/cbact04c_java_equiv/`): negative balance, negative account balance, rate 0, non-DEFAULT group. Identical.
- `missing` (account not in ACCTFILE): both RC 16, SYSOUT identical. Outputs are not compared, because the harness does not export them after an abend.

## Iterations
Fixes needed: 2, within the limit of 5.
1. My own test fixtures had wrong expected values (rate PIC scale) and an invalid F5 setup. These were test errors, not product bugs.
2. The cross-check showed the jar used the wall clock where the harness freezes it. I added a `CARDDEMO_CURRENT_DATE` (YYYYMMDDHHMMSSss) override in `Main.clock()`, the same variable the harness uses. The default is still the system UTC clock.

## Notes for the reviewer
- `Main.java` has a small addition beyond the spec: the `CARDDEMO_CURRENT_DATE` override above. Please confirm you want it kept.
- Not covered by golden or tests: several xref rows per account (R3, first in file order), and the real 999,999 suffix wrap (only the formula is tested).
- Nothing is failing.
