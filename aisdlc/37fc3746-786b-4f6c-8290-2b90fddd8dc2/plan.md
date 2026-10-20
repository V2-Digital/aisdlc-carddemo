# CR-3 Plan: CBTRN02C → Java 21

Spec: [spec.md](spec.md). Intent: [intent.md](intent.md). Answers to the intent's questions are taken as the defaults: SYSOUT is in the contract, D1 is logged as a follow-up, and a minimal Dockerfile is in scope.

## Files (in order)

1. `modernized/cbtrn02c/pom.xml`: Java 21, JUnit Jupiter, Surefire, jar with `Main-Class`.
2. `src/main/java/carddemo/cbtrn02c/Zoned.java`: overpunch decode, and encode with PIC-width truncation.
3. `.../Datasets.java`: DD env lookup, load (strip `\r`, pad to LRECL), unload (LF, key order or write order).
4. `.../Cbtrn02c.java`: validation + posting rules 1-8, SYSOUT, RC, `AbendException` → 16. Injectable `Clock`/env.
5. `src/test/java/...`: `ZonedTest`, `GoldenEquivalenceTest`, `ValidationRulesTest` (100/101/102/103, precedence), `PostingTest` (TCATBAL create/update, negative amt), `DefectCharacterizationTest` (D1-D6), `ErrorPathTest` (dup id, missing DD → 16).
6. `modernized/cbtrn02c/Dockerfile`: `eclipse-temurin:21-jre`, copies the jar, DD paths from env.
7. `legacy/tests/cbtrn02c_reasons.sh`: builds small inputs for 100/101/103/103-over-102 and runs them through `run.sh` with `INPUT_DIR`. Its output is the COBOL reference that the Java tests compare against (it reads nothing from `golden/`).
8. `modernized/cbtrn02c/scripts/compare.sh`: runs the jar on stock data, diffs it with golden, then feeds the result to CBACT04C (AC 7).

## Test strategy

- **Equivalence:** golden `CBTRN02C` (4 data files + SYSOUT + RC). Downstream: golden `CBACT04C-after-CBTRN02C` via `INPUT_DIR`.
- **COBOL references for paths the golden data misses:** `legacy/tests/cbtrn02c_reasons.sh`. Its outputs are checked into `modernized/cbtrn02c/src/test/resources/reasons/` and the Java must match them byte-for-byte.
- Commands:
  ```sh
  (cd modernized/cbtrn02c && mvn -q verify)
  legacy/tests/cbtrn02c_reasons.sh /tmp/cr3-ref
  modernized/cbtrn02c/scripts/compare.sh        # golden + CBACT04C chain
  legacy/harness/golden.sh --check
  ```

## Risks

| Risk | Mitigation |
|---|---|
| Overpunch/`-0` re-encoding differs from GnuCOBOL | Copy amt bytes raw; `ZonedTest` checks against values in the golden data; golden diff |
| Truncation semantics (D6) are guessed, not observed | Add an overflow case to `cbtrn02c_reasons.sh` so the COBOL result is the reference |
| KSDS unload order ≠ Java string order | All keys are digits/ASCII; the golden diff on 3 KSDS files proves it |
| Abend output/RC on error paths | `ErrorPathTest` + dup-id case in the reasons script (RC 16, no outputs) |
| Clock/TZ drift | Clock is injected; the env format is the same 16 digits as the harness |

## Effort (agent)

Build ~15 min, tests + reference script ~15 min, compare/Docker ~5 min. **Total ≈ 35 min.**
