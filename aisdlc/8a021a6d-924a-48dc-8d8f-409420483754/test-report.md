# Test report — CR-1 (`CBACT04C` → Java 21)

**Result: green.** 12 JUnit tests pass, golden harness check OK, CLI output matches golden. Fix iterations: 1 (a wrong offset in my own test; no production code changed).

## Commands and results
| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | 12 tests, 0 failures (JUnit 5 + Surefire only) |
| `legacy/harness/golden.sh --check` | OK, legacy outputs unchanged, two runs byte-identical |
| `java -jar target/cbact04c.jar 2022071800 <stock in> /tmp/j1` | RC 0. `TRANSACT` and `ACCTFILE` `cmp`-identical to `golden/CBACT04C`, stdout identical to `SYSOUT.txt` |

## Coverage vs acceptance criteria
| AC | Test | Evidence |
|---|---|---|
| 1 | `mvn -q verify` | passes |
| 2 | `GoldenEquivalenceTest.stock` | TRANSACT (50×350), ACCTFILE (50×300), SYSOUT byte-identical |
| 3 | `GoldenEquivalenceTest.afterCbtrn02c` | same, 50 txns, 50 accounts, 302 SYSOUT lines |
| 4 | `AbendTest` (3) | missing account / xref / DEFAULT row → RC 16, exact SYSOUT tail; no TRANSACT written |
| 5 | `InterestTest` | acct 1 → `0000000145F` (14.56), acct 2 → `0000000292D` (29.24, not 29.25) |
| 6 | `DefectD1Test` | acct 50 identical to input; txn `…00000050` with `0000000187G` written; acct 49 rewritten |
| 7 | `ZeroRateTest` | 03/0001 acct 1: no txn, cycle credit/debit `00000000000{`, balance unchanged |
| 8 | `TransactionLayoutTest` | all 50 txns: ID = PARM + seq 1..50, spaces in desc tail, merchant fields and filler, both timestamps equal |
| R1 | `ZonedTest` | overpunch round trip ±0..9, zero is `{`, high-order truncation |

Records compared byte for byte: 400 (2 scenarios × (50 TRANSACT + 50 ACCTFILE)) plus both SYSOUT files.

## Findings
- **Spec offset error (not a code defect).** `spec.md` rule 5 says timestamps at @239/@265 and spaces from @291. The golden bytes, which the code matches, have them at 1-based @279 and @305, with spaces from @331 (the copybook layout). I wrote the layout test against the golden offsets. The spec text should be corrected.
- D2 (blank group id → every lookup falls back to DEFAULT) is pinned only indirectly, via the golden SYSOUT, which has the `DISCLOSURE GROUP RECORD MISSING` lines. There is no separate test, and no group-specific rate fixture exists.
- D4 (TRAN-ID wrap, PIC overflow) is covered only at the `Zoned.truncate` unit level, not end to end. The golden data cannot reach it.
- D5 (running twice posts interest twice) is not tested.

## Left failing
Nothing.

## For a human
Decide whether to raise D1 (revenue-impacting) and D2 as separate CRs, as proposed in intent Q3.
