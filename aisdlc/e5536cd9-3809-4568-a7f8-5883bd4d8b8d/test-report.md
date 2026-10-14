# CR-1 Test report: CBACT04C Java rewrite

**Result: green. 19 JUnit tests pass, golden check OK, live COBOL vs Java identical in both scenarios.**

## Commands and results
| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn test` | 19 tests, 0 failures, 0 errors (GoldenTest 3, UnitTest 16) |
| `legacy/harness/golden.sh --check` | OK, legacy outputs match `legacy/golden/` (two runs byte-identical) |
| `legacy/tests/cbact04c-java-equiv.sh` | 6 of 6 identical (TRANSACT, ACCTFILE, SYSOUT × stock and after-CBTRN02C), Java vs live GnuCOBOL |

## Evidence per acceptance criterion
| AC | Test | Evidence |
|---|---|---|
| 1 | build | Only JUnit 5.11.4 (test scope) and Surefire. Java 21 |
| 2 | `GoldenTest.stock` | TRANSACT (50 records) and ACCTFILE (50) byte-equal to `golden/CBACT04C` (`Files.mismatch == -1`). SYSOUT (302 lines) also equal. RC 0 |
| 3 | `GoldenTest.afterCbtrn02c` | TRANSACT (50) and ACCTFILE (50) byte-equal to `golden/CBACT04C-after-CBTRN02C`. SYSOUT equal. 49 of 50 accounts changed |
| 4 | `goldenAmountsAreTruncationNotRounding`, `interestTruncatedNotRounded_D2`, `negativeInterestTruncatesTowardZero` | All 50 golden amounts equal truncation. 28 would differ under HALF_UP. 999.00 @15% gives 12.48, not 12.49. Negative truncates toward zero |
| 5 | `lastAccountNotRewritten_D1`, `goldenAccount50UnchangedAndTranAmount` | Acct 50 is byte-identical to the CBTRN02C input. Its tran is `2022071800000050`, amount `0000000187G` |
| 6 | `defaultFallbackAndSysout`, `specificGroupHitNoFallback`, `rateZeroNoTransactionNoSuffixButAccountRewritten` | DEFAULT fallback lines present. Specific group hit has no fallback. Rate 0 gives no tran, no suffix use, and the account is rewritten with cycle fields zeroed |
| 7 | `abendMissingAccount`, `abendMissingXrefStopsProcessing`, `abendMissingDefaultGroup`, `missingInputFileAbends_D6MessageText` | RC 16, legacy messages, `ABENDING PROGRAM`, no later records processed. D6 text `DALY REJECTS FILE` preserved |
| 8 | `tranRecordLayoutAndCounterAcrossAccounts`, `suffixWrapsAt999999_D3`, `amountOverflowTruncatesHighOrder_D3` | Layout offsets checked (350 bytes, spaces at 330-349, desc tail). Counter runs across accounts. Suffix goes 999999 then 000000 (D3). High-order overflow truncated (D3) |
| 9 | SYSOUT compares above | Identical in both scenarios, and in the live COBOL cross-check |

Also covered: empty TCATBALF (accounts unchanged, no trans), zoned encode/decode round-trips (`-0` written as `{`).

## Iterations
0 fix iterations on production code. The first test runs failed 5 and then 2 tests. Each was an error in my own fixtures or expected values (the implied-decimal offset, a mis-sized field, TRAN field offsets), fixed in the tests. No product defect was found.

## Gaps, for the reviewer
- The plan said `cbact04c-java-equiv.sh` would also run a synthetic fixture with a negative balance and a non-DEFAULT group against live COBOL. **It does not.** It only runs the two golden scenarios. Those branches are covered by JUnit tests only, with expected values derived by hand, not by the COBOL program.
- Abend-time partial outputs (ACCTFILE and TRANSACT after an abend) are asserted only for transaction counts. They are not golden-checked, per spec.
- Spec offset correction (from build): ACCTFILE cycle-credit at 78, cycle-debit at 90 and group-id at 112, not 76/88/110. Tests use the corrected offsets and the golden files agree.
- `suffixWrapsAt999999_D3` builds 1,000,001 records, which accounts for most of the roughly 8 s of test time.

## Defects pinned (not fixed)
D1 (last account not rewritten), D2 (truncation), D3 (suffix wrap and overflow), D6 (message text). D4 and D5 are data and scope notes and are not pinned in code. Both are visible in the golden SYSOUT.
