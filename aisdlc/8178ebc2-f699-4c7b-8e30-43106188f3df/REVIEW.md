# CR-3 Review: CBTRN02C → Java 21

**Verdict: changes needed.** Equivalence is proven: golden and all 5 COBOL edge fixtures match byte for byte. But an I/O failure at close/flush crashes with **exit 1** and a Java stack trace. AC9 requires the abend lines and **exit 16**. It's a small fix.

## What I ran (reviewer, fresh session)

| Check | Result |
|---|---|
| `git diff baseline --stat -- legacy .github` | empty: golden, harness and CI untouched |
| `mvn -q verify` (modernized/cbtrn02c) | exit 0, **32 tests**, 0 failures (7 Abend, 5 Edge, 1 Golden, 16 Rules, 3 Zoned) |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` |
| Re-captured all 5 `edge/*/in` through the real COBOL (`INPUT_DIR=… run.sh CBTRN02C`) and `cmp` against committed `expected/` | **all 26 files identical**, so the fixtures really are COBOL output and weren't regenerated from Java |
| `java -jar target/cbtrn02c.jar` on stock data, `CARDDEMO_CURRENT_DATE=2022071800000000` | RC 4; TRANFILE, DALYREJS, ACCTFILE, TCATBALF and SYSOUT `cmp`-identical to golden |
| Same run with ACCTFILE `chmod 444` | **rc=1**, `Exception in thread "main" …FileStatusException: file status 30 at Datasets.write … Cbtrn02c.flush(Cbtrn02c.java:137)`. No `ERROR CLOSING ACCOUNT FILE` line |

Tests compare against real oracles: `legacy/golden` is read in place through `repo.root`, and edge fixtures are COBOL output. None of them is a tautology. Money is held as `long` cents. Left truncation and the zero/−0 signs match the probes.

## Findings

1. **should-fix**: `Cbtrn02c.java:96,130-139`. `close()`/`flush()` run outside `step()`, so a write failure there (disk full, read-only volume) escapes `run()`. The JVM exits 1 with a stack trace, which breaks AC9 and the RC contract a scheduler depends on. The abend path (`:102`) can also throw the same way. **Fix:** wrap each write in `close()` in `step()` with the spec §3.4 messages (`ERROR CLOSING TRANSACTION FILE`, `… DAILY REJECTS FILE`, `… ACCOUNT FILE`, `… TRANSACTION BALANCE FILE`). Make `flush()` on the abend path best-effort (catch and ignore), so it always returns 16. Add an `AbendTest` case with a read-only ACCTFILE. Note D6: the DALYREJS close error should show the XREFFILE status (`00`) to stay like-for-like, so that defect becomes observable and testable.
2. **should-fix**: `Zoned.java:21,26`, called from `Cbtrn02c.java:75-77`. Non-numeric zoned data throws `NumberFormatException`, which is also uncaught (exit 1). Spec §6 says "treated as a read error". **Fix:** catch it in `run()` and abend (16) with `ERROR READING …` for the dataset concerned, or at least make sure the exit is 16.
3. **should-fix (behaviour gap, document)**: `Cbtrn02c.java:119-126`. ACCTFILE/TCATBALF are "opened I-O" just by reading them, so a read-only master gets through open and fails only at the end, after all the work. In COBOL it fails at OPEN (status 37). Fixing #1 covers the RC. Optionally check `Files.isWritable` at open and use status `37`.
4. **nit**: `Cbtrn02c.java:6`: unused `import java.nio.file.Files`. `Records.java:21-22,33`: `T_PROC_TS`, `T_FILLER` and `REJ_LEN` are unused. Use them or remove them.
5. **nit**: `RulesTest.java:294-299`: the D1 test comment is muddled ("existing debit 20.00" doesn't match the data). The test itself is correct (payment −100 then purchase 100 → temp 200 > 150 → 102). Reword it to: "payment −100 → CYC-DEBIT −100; next purchase sees 0 − (−100) + 100 = 200".
6. **nit**: `Main.java:15`: a malformed `CARDDEMO_CURRENT_DATE` (shorter than 16 chars) silently falls back to the system clock, which gives non-deterministic PROC-TS without any warning. Fail fast or warn on stderr.
7. **nit**: `Datasets.java:43`: duplicate keys in a KSDS input silently keep the last one. That's acceptable (VSAM can't hold duplicates), but say so in a comment.
8. **nit (doc drift)**: spec §8/plan name `OverlimitTest`, `TcatbalTest`, `TruncationTest`, `TranfileReplacedTest`, `PostingTest`, but these were merged into `RulesTest`/`AbendTest`/`EdgeScenarioTest`. The package is `com.v2.carddemo.cbtrn02c`, not the plan's `com.carddemo.cbtrn02c`. Coverage is there. Add a short "defect → test" map to the README for AC10.
9. **nit**: `EdgeScenarioTest.java:78` resolves `src/test/resources/edge` relative to the cwd. Use `repo.root` or the classpath so it works from an IDE too.

## What a human should look at before merging

- **D4 on abend has no COBOL oracle.** The harness doesn't unload files after an abend. `AbendTest.duplicateTranIdLeavesBalancesPosted_D4` checks the spec's "flush on abend" decision (plan decision 2), not observed COBOL bytes. Confirm that decision.
- **D9 (FILLER inheritance) and D5 (TRANFILE replaced each run)** are kept on purpose. The golden data depends on D9. Raise follow-up CRs if the business wants them fixed.
- **Docker image never built** (no Docker in the Build or Test environment). `Dockerfile` needs `target/cbtrn02c.jar` to be built first. Run `docker build` once in CI before relying on it.
- Approve or reject finding 1 (and 2). All other findings are cosmetic.
