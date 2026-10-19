# CR-1 Review: CBACT04C → Java 21

**Verdict: ready to merge.** Java output is byte-identical to the COBOL on both golden scenarios, and the protected paths are untouched. Nothing blocks the merge. Three should-fix items are listed below. They concern test evidence and the CLI exit code, not the interest logic.

## What I ran
| Command | Result |
|---|---|
| `mvn -q -f modernized/cbact04c test` | green. `SYSOUT … identical=true` for both scenarios |
| `legacy/tests/cbact04c_edge.sh` | PASS for TRANSACT, ACCTFILE and RC |
| `legacy/harness/golden.sh --check` | OK. Two runs byte-identical and matching golden |
| `git status legacy/golden legacy/harness .github` | clean, no changes |
| `run.sh CBACT04C` on the edge fixtures (by hand) | RC 0, 4 TRANs, **6 × `DISCLOSURE GROUP RECORD MISSING`** |

## Correctness (checked against spec and COBOL)
- Golden gates (AC2–4). `GoldenTest` byte-compares TRANSACT and ACCTFILE with `legacy/golden/` (50 / 50 records). The expected files are read from `legacy/golden/`, not regenerated.
- Money handling is integer cents in `long` throughout. Java `/` truncates toward zero, which matches COBOL with no ROUNDED. The largest intermediate is 1e11 × 1e6 = 1e17, well under `Long.MAX_VALUE`. `Zoned.truncate` keeps the low digits and the sign. A `-0` result becomes `{`.
- The TRAN layout adds up to 350 bytes, and the field order matches CVTRA05Y. Cycle credit/debit sit at cols 79-102 and the group at 113-122. That agrees with the CVACT01Y copybook and with the golden data.
- D1 (`InterestCalc.java:74`), D2, D3, D5 and D6 are preserved and pinned. Abend messages match COBOL wording (`cbl:386,407,431,455`).

## Findings

1. **should-fix**: the COBOL cross-check never exercises a direct group hit. Location: `legacy/tests/cbact04c_edge/ACCTFILE.txt:1-5`.
   - `A000000000` is in cols 103-112 (ACCT-ADDR-ZIP). ACCT-GROUP-ID (113-122) is blank, or `        A0` on account 2.
   - The real COBOL run shows 6 of 6 lookups falling back to DEFAULT.
   - So `test-report.md` is wrong to say the script covers "a direct `A000000000` group hit". AC7's direct hit is proven only by the Java unit test `BehaviourTest.directGroupHit`, which checks the Java code against its own reading of the spec.
   - *Fix:* shift `A000000000` 10 bytes right in at least one fixture account. Add a check to the script, e.g. that the COBOL SYSOUT has fewer `MISSING` lines than TCATBAL rows.
2. **should-fix**: non-abend failures exit 1, not 16. Location: `Main.java:28-38`.
   - Only `AbendException` is mapped to 16.
   - A missing or unreadable input (`NoSuchFileException`) or a malformed zoned field (`IllegalArgumentException` from `Zoned.java:25`) escapes `main` and gives JVM exit 1.
   - Approver Q2 says "exits 16 on any I/O or lookup failure". Legacy abends U0999 on OPEN errors (`cbl:245-318`).
   - *Fix:* catch the I/O and parse exceptions around both loading and `run()`. Print the `ERROR …` / `ABENDING PROGRAM` lines, then exit 16.
3. **should-fix**: the CLI exit-code path has no test (the test report says so too). Location: `Main.java`.
   - AC4 and AC10 are proven only in-process; nothing checks the actual process exit code.
   - *Fix:* refactor to `static int run(String[] args)` called from `main`, and assert 0 and 16 on it.
4. **should-fix (doc)**: the cycle-credit/debit and group column numbers in `spec.md` are wrong. Location: the `spec.md` field table.
   - The spec says 87-110 and 121-130. The copybook says 79-102 and 113-122.
   - The code is right and the deviation is noted in `modernized/cbact04c/README.md:23`.
   - *Fix:* correct the approved spec so later stages (CR-2) don't inherit the wrong columns.
5. **nit**: `Zoned.decode` doesn't validate the leading bytes. Location: `Zoned.java:28-30`. A space or letter decodes to garbage without any error. COBOL would also misbehave here, so this is low priority. *Fix:* throw on a non-digit, which feeds into finding 2.
6. **nit**: a leftover draft comment. Location: `BehaviourTest.java:27` ("rounding gives 0.16/0.16? use 10.97 …"). *Fix:* keep only the line-28 explanation.
7. **nit**: `abendMissingXref` asserts only that an abend happened. Location: `BehaviourTest.java:127-130`. *Fix:* also assert `ACCOUNT NOT FOUND` and `ERROR READING XREF FILE`, as `abendMissingAccount` does.
8. **nit**: the counter test is slow and memory-heavy. Location: `BehaviourTest.java:154-165`. `counterWrapsAt999999_D6` builds 1,000,001 rows and the same number of 350-byte TRAN strings, which is several hundred MB of heap. That is fine for now, but it could cause OOMs on small CI runners. *Fix:* make the counter seedable through a package-private constructor.

## What a human should look at before merging
- **D1 and D2 have real money impact.** The last account's interest is posted as a transaction but never added to its balance. Interest is truncated every night. Both are preserved by design. Decide whether to raise follow-up CRs.
- **Approve the spec correction** in finding 4 (ACCTFILE column numbers).
- **Decide whether findings 1–2 must be fixed before merge.** I recommend fixing finding 1 at least: the "proven against real COBOL" claim for the direct group hit is currently not true.
- The system clock defaults to **UTC** (spec), while the mainframe uses local time. Check that this is acceptable for timestamps in production containers.
