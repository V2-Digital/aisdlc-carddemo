# CR-1 Plan: `CBACT04C` in Java 21

Implements [spec.md](spec.md). Package `com.carddemo.cbact04c`, no frameworks.

## Files (in order)
1. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter 5 (test scope), Surefire, jar with `Main-Class`.
2. `src/main/java/.../Zoned.java`: overpunch decode/encode, `long` cents, digit-width wrap.
3. `src/main/java/.../FixedRecords.java`: read (strip CR, pad/cut to LRECL) and write (LRECL + LF).
4. `src/main/java/.../Cbact04c.java`: the main loop (spec rules 1-7). Lookups go into in-memory maps (account `TreeMap`, xref by acct with the first match kept, discgrp by 16-byte key). Includes `computeFees()` no-op, the abend helper (prints lines, exit 16) and the CLI parser.
5. `src/test/java/...`: `ZonedCodecTest`, `InterestTest`, `RecordLayoutTest`, `DefectTest`, `AbendTest`, `GoldenTest`. GoldenTest reads `../../legacy/golden` and `../../app/data/ASCII` relative to the module, and runs `Cbact04c.run(...)` in-process with captured stdout and a returned exit code. `main` calls `System.exit` only at the outer edge.
6. `legacy/tests/cbact04c-abend.sh`: runs the COBOL via `run.sh` with `INPUT_DIR` holding a TCATBALF that has an unknown account. It asserts RC 16 and the SYSOUT tail, so the abend lines in the spec come from the real program, not from a guess.
7. `modernized/cbact04c/README.md`: how to run it (a few lines).

## Test strategy
- Equivalence: golden scenarios `CBACT04C` (50 tx / 50 acct / 152 SYSOUT lines) and `CBACT04C-after-CBTRN02C` (50 / 50 / 302). TRANSACT, ACCTFILE and stdout are compared byte for byte, and RC must be 0.
- Defects D1-D7 are pinned by name in `DefectTest`. D1 is checked against acct `00000000050` in golden after-CBTRN02C, which must stay byte-equal to `golden/CBTRN02C`.
- Commands:
  ```
  cd modernized/cbact04c && mvn -q test
  legacy/tests/cbact04c-abend.sh
  legacy/harness/golden.sh --check          # legacy goldens unchanged
  # CLI smoke, diffed against golden:
  java -jar target/cbact04c-*.jar --parm 2022071800 --timestamp 2022-07-18-00.00.00.000000 \
    --TCATBALF app/data/ASCII/tcatbal.txt --XREFFILE app/data/ASCII/cardxref.txt \
    --DISCGRP app/data/ASCII/discgrp.txt --ACCTFILE app/data/ASCII/acctdata.txt --out /tmp/j \
    > /tmp/j/SYSOUT.txt; diff -r /tmp/j legacy/golden/CBACT04C   # (RC.txt aside)
  ```

## Risks
| Risk | Mitigation |
|---|---|
| Decimal or overpunch mistakes (sign of zero, truncation) | `ZonedCodecTest` + both goldens. Use exact BigDecimal, then `RoundingMode.DOWN` |
| Abend SYSOUT text or file status guessed wrong | `legacy/tests/cbact04c-abend.sh` captures the real COBOL output, and `AbendTest` mirrors it |
| Xref with duplicate alt keys: order differs from BDB | Keep the first match in file order. The sample data has no duplicates; note it as an assumption |
| Overflow wrap untested by goldens | Unit tests on the wrap at 9 and 10 integer digits |
| Golden paths break when Maven runs from another dir | Resolve them from `basedir` via a Surefire system property |

## Effort
Build ~15 min, tests + abend script ~15 min, so **~30 min** in total.
