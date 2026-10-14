# Review — CR-1 (`CBACT04C` → Java 21)

**Verdict: changes needed.** The port is correct and proven byte-identical on both golden scenarios. But the working tree adds Maven build output (`target/`, including a jar and `.class` files), and `spec.md` has wrong field offsets. Both are small fixes. No code logic needs to change.

## What I ran
| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | Passes. 12 tests, 0 failures (Abend 3, D1 1, Golden 2, Interest 1, Layout 1, ZeroRate 1, Zoned 3) |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden`, two runs byte-identical |
| `java -jar target/cbact04c.jar 2022071800 <CBTRN02C ACCTFILE+TCATBALF, stock xref/discgrp> out` | RC 0. `cmp` identical to `golden/CBACT04C-after-CBTRN02C/` for TRANSACT, ACCTFILE **and** SYSOUT |
| `git diff baseline --stat -- legacy .github app` | Empty. Protected paths untouched |

## Correctness vs spec (checked against `CBACT04C.cbl`)
- Control flow matches cbl:188-222. The rewrite happens on account change, and there is no rewrite at EOF (D1). The rate key uses the buffered account's group, and DEFAULT is the fallback. Fees are a no-op hook.
- Money is all `BigDecimal`. Interest uses `divide(1200, 2, DOWN)` = truncation toward zero, with no float anywhere. `Zoned.truncate` uses `remainder`, which keeps the sign, so S9(n) overflow is mirrored (D4). Zero always encodes as `{`.
- Account offsets in the code (`Records.java:16`: BAL 13, CREDIT 79, DEBIT 91, GROUP 113, 1-based) match `CVACT01Y`. TRANSACT offsets (`InterestCalc.java:121-130`: card 263, ts 279/305, filler 331) match `CVTRA05Y`.
- The tests are real. The golden tests compare bytes of committed COBOL outputs (`assertArrayEquals` on file bytes), not regenerated expectations. Fixtures come from `legacy/golden/` and `app/data/ASCII/`, which are read-only inputs.

## Findings

1. **should-fix**: `modernized/cbact04c/target/` (jar, classes, surefire reports, 30+ files) is in the working tree, and nothing ignores it. Committed as is, the PR would carry binaries and machine-specific reports. **Fix:** add `modernized/cbact04c/.gitignore` with `target/`, or don't stage it.
2. **should-fix**: Offsets in `spec.md` (Files table and rule 5) disagree with the copybooks and with the code:
   - ACCTFILE: CYC-CREDIT @83→**79**, CYC-DEBIT @95→**91**, GROUP-ID @117→**113**.
   - TRANSACT: merchant spaces @153–222→**153–262**, card @223→**263**, timestamps @239/@265→**279/305**, filler @291→**331**.

   The code follows the copybooks, and golden proves it right. `test-report.md` flagged only the TRANSACT part of this. **Fix:** correct `spec.md` so the next CR (fees, CR-2) doesn't inherit bad offsets.
3. **should-fix**: `Main.java:14`. A missing or unreadable input file throws `IOException`, which gives a stack trace and JVM exit 1. COBOL abends at OPEN, which the harness reports as RC 16. A scheduler that expects 16 for every failure will see 1. **Fix:** catch `IOException` in `main`, print `ABENDING PROGRAM`, and `exit(16)`. Pin it with one test. This is outside the listed ACs, so a human can defer it.
4. **nit**: `TransactionLayoutTest.java:23` checks blanks only at 0-based `152..222`, but the merchant name, city and zip span 153–262 (1-based). **Fix:** use `substring(152, 262)`. Golden already covers this byte-wise, so there is no risk.
5. **nit**: The group-specific rate path (spec edge case "Group-specific rate present → used") has no test. All golden lookups go through DEFAULT (D2). **Fix:** add one inline fixture with a non-blank `ACCT-GROUP-ID` at @113 and a matching `DISCGRP` row. Assert the rate is used and that no `DISCLOSURE GROUP RECORD MISSING` line is printed.
6. **nit**: `Records.java:27` drops empty lines anywhere in the input. A KSDS can't hold an all-blank keyless record, so this is harmless, but a comment should say it is intentional (trailing newline handling).
7. **nit**: `Main.java:12` silently falls back to the default date when `CARDDEMO_CURRENT_DATE` is shorter than 16 chars. The harness does the same for *unset*, but not for a malformed value. **Fix:** reject a malformed value with exit 2.
8. **nit**: Dead or duplicate test code:
   - `Fx.s()` and the `Fx.out` field are unused.
   - `GoldenEquivalenceTest` duplicates `Fx.ROOT/GOLDEN/ASCII` and the input copying.
   - `ZonedTest.java:26` compares `"0000000000{".length()` to the encoded length. That assertion tests only the width; the line below it already pins `-0.00 → {`. Delete it.

## What a human should look at before merging
- **Exclude `target/`** (finding 1) before you commit or merge.
- **Exit code on missing input files** (finding 3): decide whether ops needs 16 here too.
- **D1 is revenue-impacting**: every night, the last account in key order gets interest transactions but no balance posting. Approve the follow-up CR (intent Q3). D2 (group id loaded into the zip field) is a data fix and also needs an owner.
- Equivalence is proven only on the two golden scenarios. None of them exercise negative balances, group-specific rates, overflow or the TRAN-ID wrap. If you want more assurance before cut-over, run one parallel night on production-shaped data and compare it with the COBOL run.
