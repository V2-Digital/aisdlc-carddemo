# CR-1 Plan: CBACT04C in Java 21

Spec: [spec.md](spec.md). Intent: [intent.md](intent.md).

## Files (in order)

1. `modernized/cbact04c/pom.xml`: Java 21, JUnit 5, Surefire, jar with `Main-Class`. No other dependencies.
2. `src/main/java/carddemo/cbact04c/ZonedDecimal.java`: overpunch decode and encode, with modulo truncation.
3. `.../FixedFile.java`: reads lines (strip CR, pad to LRECL) and writes LF output atomically (temp file, then rename).
4. `.../Cbact04c.java`: the main loop, lookups, interest, TRANSACT building, update, and abend (an `AbendException` carries the exit code 16). Uses an injected `Clock` and a `PrintStream`.
5. `.../Main.java`: parses the CLI and the clock override, then calls `System.exit(rc)`.
6. `src/test/java/...`: `ZonedDecimalTest`, `InterestTest`, `GoldenEquivalenceTest`, `DefectTest`, `AbendTest`. Small inputs are built in `@TempDir`.
7. `legacy/tests/cbact04c-edge.sh`: builds crafted inputs (a negative balance, a real group hit, a rate-0 case, a missing account, a missing XREF, a missing DEFAULT). It runs `legacy/harness/run.sh CBACT04C` with `INPUT_DIR` and then the Java jar on the same inputs, and diffs TRANSACT, ACCTFILE, SYSOUT and RC.
8. `modernized/cbact04c/README.md`: how to run it, plus the preserved defects D1-D3 and R2.

## Test strategy

- Equivalence: golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Clock `2022071800000000`, PARM `2022071800`.
- Gaps the golden files do not cover (negative amounts, a non-DEFAULT rate, abends) are checked against real COBOL through `cbact04c-edge.sh`. The results are then pinned as JUnit cases.

```
cd modernized/cbact04c && mvn -q verify
legacy/tests/cbact04c-edge.sh          # needs the jar from mvn package
legacy/harness/golden.sh --check
git diff --stat baseline -- legacy/golden legacy/harness .github   # must be empty
```

## Risks

| Risk | Mitigation |
|---|---|
| Interest off by a cent (rounding) | `RoundingMode.DOWN`, plus a COBOL cross-check on a negative value |
| Wrong overpunch byte for a negative or zero value | Round-trip tests, plus COBOL diff in the edge script |
| SYSOUT drift (trailing spaces, line order) | Byte comparison against both golden SYSOUT files |
| Abend leaves different partial outputs than COBOL | Edge script diffs the abend-path outputs against the harness unload |
| XREF alt-key duplicates resolved differently | Not present in the data; documented rule (first in key order) and not tested further |

## Effort

About 25 min build and 10 min test (including the edge script).
