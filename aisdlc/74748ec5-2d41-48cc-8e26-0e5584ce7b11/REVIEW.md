# CR-1 Review: `CBACT04C` in Java 21

Run `74748ec5-2d41-48cc-8e26-0e5584ce7b11` · Stage: Deploy · Reviewer: independent agent session

## Verdict

**Ready to merge.** The equivalence contract is proven against the real golden bytes, on both scenarios. D1–D7 are preserved and pinned. Two should-fix items remain on abend paths that the golden files don't cover. Neither one affects TRANSACT or ACCTFILE on a successful run.

## What I ran

| Check | Result |
|---|---|
| `mvn -f modernized/cbact04c clean verify` | BUILD SUCCESS, **20 tests, 0 failures** (Abend 4, Defect 3, Golden 2, Interest 8, Overpunch 3) |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden`, two runs byte-identical |
| `git diff baseline --stat -- legacy .github app` | **empty**: the judges are untouched |
| CLI jar, after-CBTRN02C inputs, `cmp` vs golden | TRANSACT, ACCTFILE and SYSOUT (stdout) identical, RC 0 |
| CLI, empty TCATBALF | RC 0, empty TRANSACT (0 bytes), ACCTFILE unchanged |
| CLI, missing XREFFILE | `ERROR OPENING CROSS REF FILE` / `FILE STATUS IS: NNNN0035` / `ABENDING PROGRAM`, stderr `CEE3ABD: ABEND U0999`, RC 16 |
| CLI, `--out` not writable (`/proc/nope`) | **Java stack trace, RC 1** (see F1) |

The tests are not tautologies. `GoldenEquivalenceTest` uses `assertArrayEquals` to compare the program's output with the files in `legacy/golden/`. Nothing is regenerated. The D1 test checks a hard-coded `00000019458G` and `0000000187G`, and also asserts that exactly 49 of 50 accounts changed.

## Findings

1. **should-fix**: `InterestBatch.java:50-52` and `Main.java:41-46`. An output write failure (or an `IOException` from `createDirectories`) escapes as an uncaught exception, which gives exit 1 and a stack trace. AC8 asks for a non-zero exit (met) *and the legacy message text* (not met). COBOL opens TRANSACT before processing and shows `ERROR OPENING TRANSACTION FILE` + status → U0999 (`CBACT04C.cbl:309-320`). Fix: wrap the output step in a try/catch, display `ERROR OPENING TRANSACTION FILE` (or the ACCTFILE equivalent) and the status line, and throw `abend()` so the run exits 16. Add an `AbendTest` case.
2. **should-fix**: `InterestBatch.java:46`. The COBOL XREF open error puts the status on the same line: `DISPLAY 'ERROR OPENING CROSS REF FILE' XREFFILE-STATUS` (`CBACT04C.cbl:263`). Java shows the text without the status, so its SYSOUT line differs from the legacy one. Fix: pass a per-file message suffix, so the line becomes `ERROR OPENING CROSS REF FILE35`. The other three open errors have no suffix.
3. **nit**: `Overpunch.java:15`. A bad sign byte, or a non-numeric amount in the input, throws `IllegalArgumentException` / `NumberFormatException` → RC 1. COBOL behaviour on bad data is a data exception, which isn't in the contract either. You could map these to the abend path, or leave them and document it.
4. **nit**: `InterestBatch.java:38,40` (`transactions()`, `accounts()`) are unused dead code. The Javadoc on `process()` (`:65`, "Loads in-memory datasets; used by tests") is wrong: tests go through `Main.run`. Remove the accessors and fix the comment.
5. **nit**: `Main.java:21-23`. `--parm`/`--clock`/`--out` given as the last argument, with no value, throws `ArrayIndexOutOfBoundsException` where it should print the usage text.
6. **nit**: `InterestBatch.java:57`. `TreeMap.put` silently keeps the *last* duplicate primary key. A real KSDS can't contain duplicate keys, so this is harmless, but rejecting duplicates on load would make bad unloads visible.
7. **nit**: Small drifts from plan.md: the package is `com.v2.carddemo.cbact04c`, not `carddemo.cbact04c`. `InterestBatchTest` became `InterestTest`. README links `aisdlc/<run>/spec.md` as a placeholder, not the real path. None of these is a problem in behaviour.

Money handling: interest uses `BigDecimal` from end to end. It is truncated with `RoundingMode.DOWN` at scale 2 (D2). The amount and total wrap mod 10^9 (`S9(9)V99`), and the balance wraps mod 10^10 via `encode(…,12,2)` (D6). Negative results are encoded `J`–`R`/`}`, and zero is always `{`. ISO-8859-1 I/O keeps one byte per char. The code matches the spec and is covered by tests.

## What a human should look at before merging

- **D1 (last account never rewritten) and D2 (truncation) are still live business defects.** In production, the last account in each run loses its interest and keeps its cycle totals. Open the follow-up CRs.
- **Decide F1/F2** now or as a follow-up. They only affect abend diagnostics, not the golden outputs.
- **Clock timezone:** without `--clock`, Java uses **UTC** (spec-approved). COBOL `CURRENT-DATE` is local LPAR time, so TRANSACT timestamps will shift unless the container runs on UTC or ops pass `--clock`.
- **Not covered by tests:** the system clock path, the tran-counter wrap at 10^6, and D7 duplicate cards. The sample data has none of these cases.
- The test report corrects the intent: the stock scenario has 50 `MISSING` messages, not 100. That is only a documentation fix.
