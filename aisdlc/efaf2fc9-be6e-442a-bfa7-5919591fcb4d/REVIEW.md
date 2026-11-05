# CR-1 Review — `modernized/cbact04c/`

**Verdict: changes needed.** The behaviour is correct and proven against real COBOL. But the change would commit build output (`modernized/cbact04c/target/`, 30+ files, including a jar and .class files). That is a 2-minute fix. Nothing else blocks the merge.

## What I ran (reviewer, fresh session)

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | exit 0. All tests pass. The `CEE3ABD` lines on stderr come from the abend tests and are expected |
| `legacy/tests/cbact04c-differential.sh` | 6/6 PASS against GnuCOBOL: `mixed`, `negative`, `overflow` (all DDs compared), and `missing-account/-xref/-default` (SYSOUT + RC 16) |
| `legacy/harness/golden.sh --check` | OK. Goldens match, two runs byte-identical |
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty, so nothing was touched |

## Do the tests prove equivalence?

Yes.
- `GoldenTest` (`GoldenTest.java:15-24`) reads the expected bytes straight from `legacy/golden/…` for TRANSACT, ACCTFILE, SYSOUT and RC. The inputs are the raw `app/data/ASCII` files, CRLF included. Nothing is regenerated.
- The unit tests compare against hand-derived constants, not against output the code produced:
  - `0000000292D` = 29.24 for acct 2
  - `0000000292M` = −29.24 for a negative balance
  - `0000001666F` = 16.66 at a 20% rate
- The differential script runs real COBOL on crafted inputs. It covers negatives, overflow and the abend paths, which the goldens do not exercise.

## Money, truncation, sign

- `monthly`: `bal × rate` (scale 4), then `divide(1200, 2, DOWN)`. This is exact truncation toward zero, then mod 10^9 (`Cbact04c.java:194-196`). Correct.
- Balance update: mod 10^10, then the cycle fields are set to `00000000000{` (`:205-212`).
- The offsets follow `CVACT01Y.cpy`: cycle fields at 79-102, group at 113-122. That is correct. **The spec table (179-190 / 191-202 / 213-222) is wrong**, see finding 5.
- Overpunch codec (`Zoned.java`): positive/zero → `{A-I`, negative → `}J-R`. High-order digits are dropped. Verified by the `overflow` and `negative` differentials.
- D1 (last account not rewritten) is implemented by simply not calling `updateAccount` after the loop (`:202`). It is pinned by `DefectTest`: 49 of 50 records change, acct 50 is billed `0000000187G` but its record is unchanged.

## Findings

1. **blocker** — `modernized/cbact04c/target/**`: build output is in the change set (jar, classes, surefire XML, `maven-status`). The repo `.gitignore` does not cover it. *Fix:* delete `target/` and add `modernized/**/target/` (or `target/`) to `.gitignore`.
2. **should-fix** — `Cbact04c.java:153-158, 167-172`: the account and xref lookups are linear scans run on every account break, so cost grows as O(accounts × accounts). That is fine for 50 records and slow at production volume. *Fix:* build a `Map<acctId,index>` and a `Map<acctId,card>` once, using `putIfAbsent` to keep the first match. Behaviour does not change.
3. **should-fix** — `Cbact04c.java:111-120`:
   - If opening TRANSACT fails (`:99-103`), the final `Datasets.write(dd("TRANSACT"), …)` fails again and throws `UncheckedIOException`. The result is a stack trace and exit 1, not RC 16.
   - The second `if (accounts != null)` guarding the TRANSACT write is a copy-paste. It should be a flag meaning "TRANSACT opened".
   - *Fix:* track `boolean tranOpened`, and only write TRANSACT when it is set.
4. **should-fix** — no RC-16 path for bad data. `Zoned.parse` (`Zoned.java:300`) throws `NumberFormatException` on non-numeric bytes, and `clock()` (`:215-218`) throws on a `CARDDEMO_CURRENT_DATE` shorter than 16 characters. Both end in an uncaught exception (exit 1). COBOL would not abend this way. *Fix (minimum):* validate the clock env at startup and fail with a clear message. Document that malformed zoned data is out of scope, or treat it as a decision for a separate CR.
5. **should-fix (docs)** — `aisdlc/…/spec.md` "Fields used" table: the `ACCT-CURR-CYC-CREDIT/DEBIT` and `ACCT-GROUP-ID` columns are off by 100. The code and the goldens are right. *Fix:* correct the spec to 79-90 / 91-102 / 113-122 so the next reader is not misled.
6. **nit** — `AbendTest.inputUnreadable` (`AbendTest.java:43-50`) only checks the `NNNN0035` tail, not the `ERROR OPENING TRANSACTION CATEGORY BALANCE` line. No open-failure case is differential-tested against COBOL. *Fix:* assert the full tail.
7. **nit** — `Datasets.java` `read`: `l.replace("\r", "")` removes CRs anywhere in the line, not just at the end, and lines longer than LRECL are silently truncated. *Fix:* strip only a trailing `\r`. Consider rejecting over-long lines.
8. **nit** — `pom.xml`: `maven-jar-plugin` has no version, so the build is not reproducible and Maven warns. Pin it, e.g. `3.4.1`.
9. **nit** — the plan listed a `Records.java` for the column constants, but the offsets are inline magic numbers in `Cbact04c.java`. Named constants would make the copybook mapping easier to review.

## What a human should look at before merging

- **Finding 1** must be fixed. Findings 2-4 can go in this PR or a quick follow-up. None of them change golden behaviour.
- **Untested against COBOL:**
  - Duplicate xref acct ids: Java takes the first in file order. VSAM AIX order was not reproducible in the harness.
  - The partial TRANSACT/ACCTFILE state after an abend: the harness does not export datasets on abend.
  - Treat these two as accepted risk, or verify them on the mainframe.
- **Decisions for separate CRs (preserved on purpose here):**
  - D1, last account billed but not updated. This is customer-impacting, so it should be prioritised.
  - D2, no fees.
  - D3, zero-amount transactions.
  - D5, TRAN-ID wraps at 999,999.
  - D6, group id is blank, so every lookup falls back to DEFAULT.
- `TranRecordTest.suffixWrapsAfter999999_D5` writes about 1M records, which takes about 6 s. Acceptable, but it is the slowest test.
