# CR-1 Plan: CBACT04C → Java 21

Behaviour: [spec.md](spec.md). Context: [intent.md](intent.md).

## Files (in build order), under `modernized/cbact04c/`
1. `pom.xml`: Java 21, JUnit Jupiter 5 (test scope), Surefire, and a jar with `Main-Class`. No other dependencies.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch decode/encode and fixed-width field helpers (pad, slice, mod-10^n truncation).
3. `.../Records.java`: offset constants and accessors for TCATBAL, XREF, DISCGRP, ACCOUNT, TRAN (spec field table).
4. `.../FileIo.java`: reads LF text (strips `\r`, pads to LRECL) and writes LF text with a trailing newline.
5. `.../InterestCalc.java`: the main loop, rules 1–9, D1 preserved. Takes a `Clock` and a `PrintStream` sysout. Abends throw `AbendException(16)`.
6. `.../Main.java`: CLI parsing (`--parm`, 5 DD paths, `--clock`), env `CARDDEMO_CURRENT_DATE`, `System.exit(rc)`.
7. `src/test/java/...`: `ZonedTest`, `InterestTest`, `RateLookupTest`, `DefectCharacterizationTest`, `GoldenEquivalenceTest`, `CliTest`.
8. `modernized/cbact04c/README.md`: how to build and run, plus the preserved-defect list (pointer to spec).
9. `legacy/tests/cbact04c-java-vs-cobol.sh`: differential test. It builds crafted inputs into a temp dir, runs `legacy/harness/run.sh CBACT04C` with `INPUT_DIR`, runs the jar on the same files, and diffs all four outputs.

## Test strategy
- **Equivalence (the proof):** `GoldenEquivalenceTest` reads `../../app/data/ASCII` and `../../legacy/golden/` relative to the module. It covers `CBACT04C` and `CBACT04C-after-CBTRN02C` with parm `2022071800` and clock `2022071800000000`. TRANSACT, ACCTFILE, SYSOUT and RC must be byte-equal.
- **Unit and characterization:** in-memory fixtures for each spec edge case and for D1–D4 (named `d1_…` to `d4_…`).
- **Differential vs real COBOL:** `legacy/tests/cbact04c-java-vs-cobol.sh`. Its crafted data covers a negative balance, a group-specific rate (blank `ACCT-GROUP-ID` replaced by a real group), a rounding-sensitive amount, and a 2-account file so D1 is visible. These cover R1/F1, which golden can't.
- Commands:
  ```sh
  cd modernized/cbact04c && mvn -q test && mvn -q package -DskipTests
  legacy/tests/cbact04c-java-vs-cobol.sh
  legacy/harness/golden.sh --check   # harness untouched, still green
  ```

## Risks
| Risk | Mitigation |
|---|---|
| Sign of zero, or a negative zero written as `}` | Always encode zero as `{`. A unit test plus a COBOL diff on a negative balance that truncates to 0.00 |
| Truncation semantics differ from COBOL for negatives | `RoundingMode.DOWN` (toward zero), checked against COBOL in the differential script |
| Missing trailing newline or `\r` in output breaks byte equality | `FileIo` is tested directly. The golden diff compares raw bytes |
| SYSOUT DISPLAY of a TCATBAL record re-encodes it | Print the raw input line as read, never re-serialized |
| XREF duplicate order (F3) unverifiable on sample data | Documented assumption. The differential script includes a duplicate to see what COBOL/BDB picks, and Java is aligned to it |
| Abend outputs differ (partial files) | Out of contract (harness doesn't export them). Only SYSOUT and RC are asserted |

## Effort (agent)
About 25 min build, 10 min test and differential, 35 min total.
