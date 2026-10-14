# CR-1 Review: CBACT04C → Java 21

**Verdict: ready to merge.** Both golden scenarios are byte-equal on TRANSACT, ACCTFILE, SYSOUT and RC. The proof checks raw bytes against `legacy/golden/`, and a COBOL-vs-Java differential test covers the paths that golden doesn't reach. No blockers. The should-fix items are about error-path parity and fixing the docs.

## What I ran
| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q test` | 25 tests, 0 failures (Cli 2, Defect 4, Golden 2, Interest 7, RateLookup 6, Zoned 4) |
| `bash legacy/tests/cbact04c-java-vs-cobol.sh` | `PASS: Java == COBOL on crafted inputs (6 transactions)` |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden`, two runs byte-identical |
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty: protected paths untouched |

## Is the proof real?
- `GoldenEquivalenceTest` reads the expected bytes straight from `legacy/golden/<scenario>/` (`assertArrayEquals` on raw file bytes). The expectations are not regenerated or copied into the module. Its inputs are stock `app/data/ASCII` and `golden/CBTRN02C` ACCTFILE+TCATBALF, which matches AC 2 and AC 3.
- The differential script runs the real COBOL through the harness (`INPUT_DIR`) and the jar on the same fixtures, then `cmp`s all four outputs. It also guards against a vacuous pass by requiring ≥4 TRANs and at least one negative amount. The fixtures cover a group-specific rate (`GRPX`), DEFAULT fallback, rate 0, negative balances, a rounding-sensitive amount (1164.67 @ 15%) and a near-max balance. The test report says a `HALF_UP` mutation makes it fail.
- Money: `BigDecimal` everywhere and `RoundingMode.DOWN` (toward zero, matching COBOL truncation). A source scan test bans `double`/`float`. Zero always encodes as `{`, including a negative value that truncates to 0.00, and COBOL agrees (differential).
- D1–D4 have tests named after them in `DefectCharacterizationTest`. D1 asserts that the last ACCTFILE line is unchanged even though a TRAN was written for that account.

## Findings
1. **should-fix**: `InterestCalc.java:128-129`, `:159-165`. Output-side I/O errors don't follow the COBOL abend contract. Only `Files.isReadable` is checked at open. If TRANSACT can't be created, or ACCTFILE is read-only (COBOL opens it I-O), the program gets past `START OF EXECUTION`, processes everything, and then dies from an `UncheckedIOException` with **exit 1** and a stack trace. COBOL would print `ERROR OPENING TRANSACTION FILE` / `ERROR OPENING ACCOUNT MASTER FILE`, then `FILE STATUS IS: NNNN00xx` and `ABENDING PROGRAM`, with RC 16. Spec rule 1 asks for each file's open message. *Suggestion:* at open time, check `Files.isWritable` for ACCTFILE and that TRANSACT's parent directory is writable, using the existing messages and status `35`. Map any later `IOException` to `abend(…)` so it exits 16. Add one test.
2. **should-fix**: `spec.md` field table. The ACCOUNT offsets for cycle credit/debit (75/87) and group id (109) are wrong. The code correctly uses 78/90/112 from `CVACT01Y.cpy`, and golden plus the differential confirm it. Both build.md and test-report.md flag this, but the approved spec is still wrong. *Suggestion:* correct spec.md in this PR so the CR-2/D1 fixes don't inherit the error.
3. **should-fix (decision)**: F3 (several cards per account) is unverified. The harness loader rejects duplicate AIX keys, so Java's "lowest card number wins" (`InterestCalc.java:62-66`) is an assumption. This is harmless on today's data (no duplicates), but say so explicitly in `modernized/cbact04c/README.md`. Production VSAM returns the AIX duplicates in insertion order, not card order.
4. **nit**: `Main.java:23-26`. Argument parsing silently ignores unknown options and a trailing odd argument, and a missing `--parm` quietly becomes 10 spaces in every TRAN-ID. For a container entrypoint, fail with usage (rc 2) on unknown keys and on a missing `--parm`.
5. **nit**: `Main.java:42-49`. A malformed `--clock` or `CARDDEMO_CURRENT_DATE` throws `NumberFormatException` or `StringIndexOutOfBounds`, which exits 1 with a trace. Validate the 16 digits and return 2.
6. **nit**: `Zoned.java:27`. A non-digit, non-overpunch last character gives `Character.digit = -1`, which later fails with an opaque `NumberFormatException`. Throw an `IllegalArgumentException` that names the field value instead. COBOL behaviour on bad data is undefined anyway.
7. **nit**: `InterestCalc.java:57`. `"ERROR OPENING CROSS REF FILE35"` hard-codes the status that COBOL concatenates (`'…FILE' XREFFILE-STATUS`, `cbl:263`). It is correct for the only status Java produces, but a short comment would save the next reader a double-take.
8. **nit**: `GoldenEquivalenceTest.java:41-44`. Stripping `\r` from `tcatbal.txt` (49 CRLF lines) in the test is redundant, because `FileIo.read` already strips it. Removing it would make the test also prove that the CRLF handling works on the real file.
9. **nit**: `AbendException(int)` is always constructed with 16. You could make it a constant, but it's fine as it is.

## What a human should look at before merging
- **CI dependency:** `legacy/tests/cbact04c-java-vs-cobol.sh` runs `mvn package`, so the CI runner that loops over `legacy/tests/*.sh` needs Maven and Maven Central access.
- **Finding 1:** decide whether exit-1-on-write-failure is acceptable for go-live monitoring, or should become RC 16 first. I recommend fixing it now, since it's small.
- **Behaviour you are knowingly keeping (spec "Preserved defects"):** D1 means the last account's interest is **never posted**. In the after-CBTRN02C scenario, acct `00000000050` loses +18.77 every run. Also D2 (no fees), D3 (truncation), D4 (wrong text), and F2 (silent wrap, verified against Java only, not COBOL).
- **Ops, R2:** `PARM=2022071800` is fixed in the JCL, so every nightly run reuses the same TRAN-IDs. The container wrapper should pass the real business date.
- **F3:** the duplicate-card assumption (finding 3).
