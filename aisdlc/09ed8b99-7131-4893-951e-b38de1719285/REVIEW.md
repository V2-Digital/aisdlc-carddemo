# CR-1 Review: `CBACT04C` to Java 21

**Verdict: ready to merge.** Both golden scenarios are byte-identical, the edge cases are cross-checked against real COBOL, and the protected paths are untouched. The findings below are should-fix/nit only and none of them changes output.

## What I ran (this session)
| Command | Result |
|---|---|
| `mvn -q -f modernized/cbact04c test` | green, 27 tests (Abend 6, Defect 7, Golden 2, Interest 5, Overpunch 7), 0 failures |
| `bash legacy/tests/cbact04c_edge.sh` | 6 cases, 18/18 files `cmp`-identical COBOL vs Java (negative, rate0only, empty, nodefault, noxref, noacct) |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` |
| `git status -- legacy/golden legacy/harness .github app` | no changes |

## Checked and OK
- **Equivalence is real.** `GoldenEquivalenceTest` runs `Files.mismatch` against the committed `legacy/golden/` files, not against regenerated ones. It compares `TRANSACT`, `ACCTFILE`, `SYSOUT` and `RC` for both scenarios (AC 2, 3).
- **Money handling.** BigDecimal throughout, `divide(1200, 2, DOWN)`, then mod 10^9 / 10^10 high-order truncation (`Cbact04c.java:222-229`, `:166`). There is no float anywhere. The edge fixture's negative balances (`-1.00` as `J`, `-0.00` as `}`), the 99.99 truncation and the 999.99 boundary all match COBOL byte for byte.
- **Sign encoding.** Negative zero is decoded, and zero is always encoded as `{` (`Overpunch.java:306-313`). The `}` input case matches COBOL in the edge `negative` case.
- **D1 is preserved** (`Cbact04c.java:150`) and pinned with golden bytes (`DefectCharacterizationTest` d1). D2 and D3 are also pinned with golden evidence.
- **Abend messages.** The messages for account, xref and DEFAULT misses, plus RC 16, are verified against COBOL by the edge script, not guessed.

## Findings
1. **should-fix**: `Main.java:413`, `Cbact04c.java:72-73`. An `IOException` (for example, an unwritable `--transact` or `--acctfile-out` path) escapes `main`, so the JVM exits **1** with a stack trace instead of 16 with `ABENDING PROGRAM`. The legacy `0400-TRANFILE-OPEN` failure path is not modelled. Suggestion: catch `IOException` in `main` (or in `openFiles` for the output paths), display the legacy open message and exit 16.
2. **should-fix**: `DefectCharacterizationTest.java:62-66` (D4 counter wrap). The test only exercises `Overpunch.encodeUnsigned`. It never reaches the wrap at `Cbact04c.java:236` (`% 1_000_000`), so deleting that line would still pass. Suggestion: make the counter's start value injectable (package-private), or extract a `nextTranId()` helper and test the 999999 → 000000 step on it.
3. **should-fix**: `Cbact04c.java:102-106`. The open-error status is hard-coded to `NNNN0035` for every DD, and XREF gets a `35` suffix. This looks right from the COBOL (`:263`, `:636-646`), but no open-error case is cross-checked against COBOL: `AbendTest.openErrorForDiscgrp…` only asserts that the line is present, and the edge script has no missing-file case. Suggestion: add one edge case if `run.sh` can simulate a missing DD; if it can't, note in the README that this path is unverified.
4. **nit**: `Cbact04c.java:165`. The account-record non-numeric message passes `recNo` `0`, which gives `ACCTFILE RECORD 0`. Pass the account id instead.
5. **nit**: `DefectCharacterizationTest.java:69`. The comment says `9,999,999,999.00` but the code uses `999999999.99`. Use `assertTrue` instead of `assertEquals(true, …)` (lines 71-72).
6. **nit**: `Overpunch.java:272`. The validity condition is convoluted: `lastDigit < 0 || (…) && p < 0 && n < 0`. Use `if (lastDigit < 0)`, since `Character.digit` already handles the plain-digit case.
7. **nit**: `Overpunch.decodeUnsigned` is used only by tests (dead in main code). Either remove it, or use it for the 9(11)/9(4) key fields.
8. **nit**: the record name `Cbact04c.Files_` (`:28`) reads oddly. `DataSets` or `Dds` would avoid the clash with `java.nio.file.Files` more cleanly.
9. **nit**: `Main.java:415` silently ignores unknown or odd-count arguments, and a missing argument exits 2, which the spec doesn't define. Acceptable, but document it in the README.

## What a human should look at before merging
- **Partial outputs on abend** (spec rule 10, plan "Decision"). Java writes `TRANSACT`/`ACCTFILE` after an RC 16, and this cannot be compared with COBOL. Confirm that ops will treat any RC 16 output as invalid.
- **Finding 1**: decide whether an I/O failure exiting 1 instead of 16 matters for the scheduler's RC handling (Control-M/CA7 conditions on RC).
- **TCATBALF ordering**: Java sorts in ASCII/ISO-8859-1 order. A real EBCDIC KSDS sorts letters before digits. This is irrelevant while the key is all digits (acct, type `01`/`03`, cat), but it matters if non-numeric type codes ever appear.
- **Defects D1-D4** are deliberately preserved. D1 (the last account's interest is never posted to its balance) has a real financial impact and needs its own CR.
