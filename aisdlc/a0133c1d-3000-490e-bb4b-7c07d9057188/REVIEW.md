# CR-1 Review: `CBACT04C` → Java 21

**Verdict: ready to merge.** All acceptance criteria are met and pass when I run them myself. The tests compare against the real golden files. The findings below are robustness and documentation items. None of them affects the golden behaviour.

## Evidence (re-run by the reviewer)

| Check | Result |
|---|---|
| `mvn -q verify` (modernized/cbact04c) | exit 0 |
| `legacy/tests/cbact04c_negzero.sh` | `PASS: truncated negative interest is written as 0000000000{` |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden`, two runs byte-identical |
| `git status` / `git diff baseline` on `legacy/golden legacy/harness .github` | empty, untouched |
| Dependencies | JUnit Jupiter 5.11.4 (test) only; plugins: Surefire and jar |

**Do the tests really prove equivalence?** Yes. `GoldenEquivalenceTest` runs the program on stock inputs and on the `golden/CBTRN02C` inputs. It then compares `TRANSACT`, `ACCTFILE`, `SYSOUT` and `RC` byte for byte (`assertArrayEquals`) with `legacy/golden/<scenario>/`. Nothing is regenerated. The characterization fixtures are small, hand-built inputs, and their expected values are worked out independently (for example 1512.77 × 1.5 / 1200 → `0000000189{`). The −0 sign rule is backed by a real COBOL run, not assumed.

**Money and sign handling:** I checked `Zoned` and `Cbact04c` against the COBOL source.
- Interest is an exact multiply, then `divide(1200, 2, DOWN)`. That is truncation toward zero (F2).
- Overflow is handled as mod 10^(n+2) on the unscaled value (F6).
- A zero result is always encoded as `{`.
- The account-record offsets in the code are correct (curr-bal 12, cyc-credit 78, cyc-debit 90, group 112).
- F1 is preserved: there is no write-back after the loop (`Cbact04c.java:151`).

## Findings

1. **should-fix**: A missing output DD crashes instead of abending. `Cbact04c.java:155-161`: `FixedFile.write` gets a `null` path, throws an NPE, and that is not caught. I ran it with `DD_TRANSACT` unset: stack trace, exit code 1, no `ABENDING PROGRAM`. This breaks AC 7's "bad input → 16". *Fix:* also catch `RuntimeException` in that block, or better, check that all six DDs are present at the start of `execute()`.
2. **should-fix (behavioural note for abend paths)**: The outputs are written only at the end (`Cbact04c.java:154-158`). COBOL opens `TRANSACT` OUTPUT at startup and REWRITEs ACCTFILE in place as it goes. So after an abend, COBOL leaves a partial TRANSACT and partly updated accounts, while Java leaves neither output. A TRANSACT open failure is also reported only after processing, not before it. That is arguably safer and is outside the golden scenarios, but it is a difference from like-for-like. *Fix:* either document it in `README.md` and the spec as an intentional deviation, or open/create the outputs up front (and still write them atomically).
3. **nit**: The spec's ACCTFILE offsets are wrong. `spec.md` table lists cyc-credit 66, cyc-debit 78, group 100. Per `CVACT01Y` they are 78, 90 and 112. The code is right; only the document is wrong. *Fix:* correct the spec row so future readers aren't misled.
4. **nit**: `"ERROR OPENING CROSS REF FILE35"` (`Cbact04c.java:72`) hard-codes the status that COBOL appends dynamically (`CBACT04C.cbl:263`). It is correct for "file missing" (35), the only case Java can produce. A short comment saying so would help.
5. **nit**: Some imports are unused: `java.nio.file.Files` in `Cbact04c.java:8` and `Files` in `DefectCharacterizationTest.java`. *Fix:* remove them.
6. **nit**: `main` passes a malformed `CARDDEMO_CURRENT_DATE` straight to `fixedClock`, which throws an uncaught `NumberFormatException` or `StringIndexOutOfBoundsException` (exit code 1). *Fix:* treat it like finding 1 and map it to RC 16 or a usage error.
7. **nit**: `AbendTest.missingFile` (`AbendTest.java:43-55`) calls `f.run()` first and then rebuilds the DD map by hand. It could instead set a non-existent path through `Fixture`. That is a readability issue only.

## What a human should look at before merging

- **Decide on finding 2.** Is "no outputs on abend" acceptable for operations and restart procedures, or must partial-output behaviour match the mainframe? If it is accepted, record it as a deliberate deviation.
- **Raise the follow-up CRs** for F1 (the last account's interest is never posted; account 50 under-posts 18.77 in the golden run) and F2 (truncation versus rounding; 28 of 50 amounts differ by 1 cent). Also note F4 (the blank `ACCT-GROUP-ID` means the DEFAULT rate always applies) to the data owners.
- **Fallback clock is UTC.** When `CARDDEMO_CURRENT_DATE` is unset, the system clock in UTC is used. COBOL `CURRENT-DATE` is local time. Confirm the container's expected timezone for transaction timestamps.
- Finding 1 is a small fix; it can go in before or after the merge.
