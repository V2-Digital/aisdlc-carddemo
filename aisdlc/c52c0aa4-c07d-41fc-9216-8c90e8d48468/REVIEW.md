# CR-1 Review: CBACT04C in Java 21

**Verdict: `ready to merge` once finding 1 is done.** The Java output is byte-identical to the COBOL output on both golden scenarios and on the abend paths. Protected paths are untouched. Build outputs (`target/`) must be removed from the change set before commit.

## What I ran (on a clean copy of the working tree)

| Check | Result |
|---|---|
| `rm -rf target && mvn -q verify` (Java 21, JUnit 5.11.4 + Surefire only) | exit 0, **26 tests, 0 failures** (Abend 7, Golden 2, Logic 14, Overpunch 3) |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` |
| `legacy/tests/cbact04c-abends.sh` (re-runs real COBOL against saved references) | `cbact04c-abends: OK` |
| CLI jar on after-CBTRN02C inputs, `CARDDEMO_CURRENT_DATE=2022071800000000` | RC 0. `cmp` shows TRANSACT, ACCTFILE and SYSOUT identical to golden |
| Extra probe: balance −0.01 (`0000000000J`), rate 15, COBOL vs Java | Both write amount `0000000000{` (truncates to +0, not `}`). All 3 outputs identical |
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty |

**Are the tests real?**
- The golden tests compare raw `byte[]` against `legacy/golden/*`, which the change does not touch.
- The abend expectations come from real COBOL runs, and the script re-checks them.
- Unit cases use independent hand-computed values: 14.56, −0.12 / `…1K`, and 1.25 where HALF_UP gives 1.26.
- I found no tautologies.

**Spec compliance:**
- Rules 1–8 are implemented in `Cbact04c.java`: key-ordered TCATBAL, the account/xref break, DEFAULT fallback, `divide(1200, 2, DOWN)`, high-order truncation in `fit`, D1 (no final rewrite), and no outputs on abend.
- Fields not in the spec are carried through untouched (`substring` patching).

## Findings

1. **should-fix (do before commit)**, `modernized/cbact04c/target/**`. The diff includes the jar, `.class` files and surefire reports (about 25 files) because there is no ignore rule. Add `modernized/*/target/` to `.gitignore` and leave `target/` out of the commit.
2. **should-fix**, `Cbact04c.java:128-135`, `:201-210`. Only a *missing* file maps to RC 16. An unreadable file (`IOException`) or a malformed zoned decimal (`IllegalArgumentException` from `Overpunch.decode`, `Overpunch.java:24`) escapes `main` as a stack trace with exit code 1. AC 4 and spec rule 1 say "missing or unreadable" should abend. Suggestion: in `main`, catch `IOException` and `RuntimeException` and return 16 after printing `ABENDING PROGRAM` / `CEE3ABD: ABEND U0999`. Don't try to copy every COBOL data-exception behaviour.
3. **nit**, `LogicTest.java:253-257`. The D4 counter wrap (`% 1_000_000`, `Cbact04c.java:166`) is never tested; the test name says so. I did not check against COBOL what `ADD 1` does to `PIC 9(06)` at 999999 without `ON SIZE ERROR` (GnuCOBOL and IBM may differ). This is low risk: it needs more than a million rows in one run. Record it as an open item for the D4 decision instead of claiming it is preserved.
4. **nit**, `AbendTest.java:42-49`. The four missing-input expectations (status 35) are hard-coded strings, not COBOL-captured fixtures. The test report says they were checked against COBOL by hand. Note that `INPUT_DIR` without a DD falls back to sample data (my probe with DISCGRP removed ran RC 0), so the harness can't capture these directly. Add a comment saying so.
5. **nit**, `LogicTest.java:153`. The comment is a leftover scratch calculation ("…use 100.47…"). Replace it with `100.47*15/1200 = 1.255875 → 1.25 (HALF_UP 1.26)`.
6. **nit**, `AbendTest.java:21`. The `assertEquals` arguments are swapped (actual, expected).
7. **nit**, `Cbact04c.java:190-192`. A malformed `CARDDEMO_CURRENT_DATE` shorter than 16 chars silently uses the system clock. Consider failing fast, since determinism is the reason this variable exists.
8. **nit**, structure. The spec lists `InterestTest`, `TranIdTest`, `DefaultFallbackTest` and `CharacterizationTest`. These are merged into `LogicTest` with section comments, and the coverage is equivalent. Update the spec or plan acceptance table so the traceability still holds.

Checked and fine:
- Sign handling: negative zero encodes as `{`, as COBOL does.
- `fit` keeps the sign with `remainder`.
- Account balance uses 10 integer digits and the total uses 9, matching `WS-TOTAL-INT S9(09)V99` (`CBACT04C.cbl:169`).
- Lowest card wins on duplicate xref.
- CRLF and short-line input are handled.
- ISO-8859-1 I/O is used throughout.

## What a human should look at before merging

- **Commit hygiene:** make sure `target/` is not in the PR (finding 1).
- **Decide on finding 2.** Today, corrupt or unreadable input exits with 1, not 16. A scheduler that only checks for non-zero still catches it, but run books keyed on 16 would not.
- **Defects D1–D4 are preserved, not fixed, by design.** D1 means account `00000000050` gets an 18.77 interest transaction but its master balance is not updated. This happens every night for one account. It needs its own CR (intent Q3).
- **AIX duplicate-card rule** (lowest card number) is an inference. The goldens don't exercise it. Confirm it against real VSAM AIX behaviour before go-live.
