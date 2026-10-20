# CR-3 Review: CBTRN02C → Java 21

**Verdict: ready to merge.** All 4 data files, SYSOUT and RC are byte-identical to golden and to independent COBOL references on 5 extra fixtures. The CBACT04C chain also matches. Every finding below is on an error path or in test quality. None changes a data output.

## What I ran (reviewer, fresh session)
| Command | Result |
|---|---|
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty. Protected paths are untouched |
| `(cd modernized/cbtrn02c && mvn -q verify)` | exit 0. 33 tests: Golden 2, CobolReference 5, Rules 17, Zoned 7, ErrorPath 2 |
| `modernized/cbtrn02c/scripts/compare.sh` | TRANFILE, DALYREJS, ACCTFILE, TCATBALF, SYSOUT, RC all `ok`. CBACT04C chain: TRANSACT, ACCTFILE, SYSOUT, RC all `ok` (AC 2-5, 7) |
| `legacy/tests/cbtrn02c_reasons.sh` | COBOL matches the checked-in refs (5 cases). Java matches COBOL. Reasons are `0100 0101 0102 0103 0103 0103`. exit 0 |
| `legacy/harness/golden.sh --check` | OK (AC 8) |

**Are the tests real?** Yes for equivalence. `GoldenEquivalenceTest` reads `legacy/golden/` directly. `CobolReferenceTest` compares against outputs from the unmodified COBOL, and `cbtrn02c_reasons.sh` re-runs that COBOL to regenerate them, so the refs can be re-verified. The expectations do not come from the Java. The money paths are pinned against COBOL: truncation (`overflow`, `overflow_acct`), `}` pass-through, `-0`→`{`, and the D2 precedence.

## Findings

1. **should-fix**: `Cbtrn02c.java:81-91`. Open order and checks don't match COBOL. COBOL opens DALYTRAN, TRANFILE, XREF, DALYREJS, ACCT, TCATBAL (`CBTRN02C.cbl:247-338`) and abends at open. The Java never opens TRANFILE/DALYREJS. A missing DD for either one is only noticed at unload, after the whole run. SYSOUT then holds "Creating" lines and the message is `ERROR CLOSING …` instead of `ERROR OPENING TRANSACTION FILE` / `DALY REJECTS FILE`. That breaks the spec edge case "Missing DD → ERROR OPENING <file>". The comment on line 91 says writability is checked, but nothing checks it. *Suggestion:* check both DDs at startup in COBOL order, using the COBOL open messages.
2. **should-fix**: `Zoned.java:251,259` with `Cbtrn02c.java:114-117,161`. A bad sign or digit byte throws `IllegalArgumentException`, and `run()` doesn't catch it. The JVM exits 1 with a stack trace, but the spec says the job abends (RC 16). *Suggestion:* catch it in `run()` and route it to the abend path. Also confirm the COBOL behaviour, because GnuCOBOL may not abend on bad zoned data. If it doesn't, write that down.
3. **should-fix**: `Cbtrn02c.java:222`. The comment says D5 is mirrored, but the code prints `ERROR CLOSING DAILY REJECTS FILE / NNNN0035`, which is the generic status, not XREFFILE's status. The test report admits this (`RulesTest.java:383-399`). *Suggestion:* reword the comment to "D5 not reproducible with text I/O". Then either update the spec, or make the DALYREJS branch print `NNNN0000`, which is the XREFFILE status after a successful close.
4. **should-fix**: `Cbtrn02c.java:49,157`. A TCATBAL record created before any successful READ gets 22 **spaces** of filler. After a READ it gets the last-read filler. Every fixture and the stock data use zero filler and update an existing record before the first create. So the "before any read" branch has no COBOL reference. In COBOL the FD area starts as whatever GnuCOBOL initialises it to. *Suggestion:* add a fixture with an empty `TCATBALF` to `cbtrn02c_reasons.sh` and pin the result. Also fix the spec line "key + 0 + 22 spaces" (Build already flagged it).
5. **nit**: `Cbtrn02c.java:208-216`. Outputs are written one file after another, and ACCTFILE/TCATBALF overwrite their inputs in place (I-O). An I/O failure in the middle leaves a mix of old and new files. This matches D4 in spirit, but operators in a container should know about it. *Suggestion:* write to temp files and rename them. Leave it as is if like-for-like matters more.
6. **nit**: `RulesTest.java:370`. `assertFalse(getDeclaredFields().length == 0)` asserts nothing about D3. Same at `RulesTest.java:380`: `exists && isEmpty` is always false here, because no ACCTFILE is written on abend. *Suggestion:* delete line 370. Change line 380 to `assertFalse(exists())` for ACCTFILE and TCATBALF.
7. **nit**: `Harness.java:168`. The `fallback` varargs parameter is never used. Delete it.
8. **nit**: `Cbtrn02c.java:65-67`. If `CARDDEMO_CURRENT_DATE` is set but malformed, the clock silently falls back to the system time and skips `COB_CURRENT_DATE`. A non-digit value throws `NumberFormatException`. This is low risk.
9. **nit**: The Dockerfile was never built or run (the test report says so). It is trivial and looks correct.

## What a human should look at before merging
- **Decide on findings 1-3**: error-path messages and RCs that differ from the spec. They don't affect the golden outputs. Either fix them before merge or take them as a follow-up with a spec amendment.
- **Spec correction**: TCATBAL create filler is "last-read filler", not spaces (finding 4).
- **D1 (over-limit formula)**: 20 of the 300 stock decisions would flip if it were fixed. Raise it as a follow-up CR for the Head of Cards.
- **D3/D5 are only documented**, not proven against COBOL. They can't be triggered with text I/O.
- `legacy/tests/` is new and outside the protected paths. Check that CI is supposed to run `cbtrn02c_reasons.sh`. It needs GnuCOBOL.
