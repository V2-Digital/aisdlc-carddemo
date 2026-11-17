# CR-1 Test report

**Result: all green. 21 JUnit tests, 1 differential script, `golden.sh --check` OK.** Fix iterations: 0 on the Java code. 4 rounds fixing my own test fixtures (see below).

## Commands and results
| Command | Result |
|---|---|
| `mvn -q -f modernized/cbact04c test` | 21 tests, 0 failures (Abend 4, DefectCharacterization 4, GoldenEquivalence 2, Interest 6, Zoned 5) |
| `mvn -q -f modernized/cbact04c package -DskipTests` | builds `target/cbact04c.jar` |
| `legacy/tests/cbact04c-diff.sh` | ok: TRANSACT, ACCTFILE, SYSOUT, RC identical between GnuCOBOL and the Java jar |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` |
| `git diff --stat baseline -- legacy/golden legacy/harness .github` | empty |

## Equivalence (AC2, AC3)
`GoldenEquivalenceTest` byte-compares against `legacy/golden/`, with PARM `2022071800` and a frozen clock:
- `CBACT04C`: TRANSACT (50 records), ACCTFILE (50), SYSOUT (152 lines) and RC `0`: **0 diffs**.
- `CBACT04C-after-CBTRN02C`: TRANSACT (50), ACCTFILE (50), SYSOUT (302 lines) and RC `0`: **0 diffs**.

## Differential vs real COBOL (plan T5): `legacy/tests/cbact04c-diff.sh`
Synthetic 5-account dataset: a real `ACCT-GROUP-ID` (GOLD) with a direct DISCGRP hit, negative balances, a `-0.00x` result, a near-overflow balance (999999999999), a 0% account mid-run, and an unused-category zero rate. It runs the COBOL harness (`INPUT_DIR`) and the jar on the same inputs, then compares every output. The spec's assumptions on sign of zero, negative truncation and overflow are **confirmed against GnuCOBOL**. The script exits non-zero on any diff, and CI picks it up from `legacy/tests/`.

## Tests by acceptance criterion
- AC4: `AbendTest` covers A1, A2, A3 and a missing input. Each checks exit 16, the exact SYSOUT tail and `CEE3ABD: ABEND U0999` on stderr (A1 only).
- AC5: `InterestTest.truncates` pins 1164.87 × 15.00 / 1200 = 14.56.
- AC6: `DefectCharacterizationTest.D1_lastAccountNotRewritten`. Account 50 is unchanged, txn `2022071800000050` is written, and accounts 1-49 are all rewritten.
- AC7: `InterestTest.zeroRateAndAllZeroAccountStillZeroed`.
- AC8: `InterestTest.defaultFallback` and `directHit`.
- AC9: `InterestTest.tranIdAndClock`.
- D2, D3, D7: characterization tests in `DefectCharacterizationTest`.
- D4, R2: `ZonedTest`, which covers overpunch round trip, −0, negative truncation and overflow.
- Empty TCATBALF: `InterestTest.emptyTcatbal`.

## Iterations
The Java implementation needed no changes. The test fixtures needed fixes: a duplicate key in the differential data, over-long ACCTFILE lines, 11-char balances in a 12-byte field, and wrong expected amounts (1000.00 × 15% = 12.50, not 1.25). The program output was right each time.

## Gaps (not failing)
- Abend paths A1-A3 and open-error texts are checked against the **spec**, not against GnuCOBOL. The differential script only covers the success path.
- D5 (sequence wrap at 10^6) has no test, because it needs about 1M transactions. It is implemented as `% 1_000_000`.
- Output-file I/O errors (unwritable output) are untested, and the spec does not pin their status.

## For humans to decide
D1 (last account never rewritten, so interest is lost nightly) is preserved and pinned. It needs a separate fix CR. D2 (truncation versus rounding) needs business confirmation.
