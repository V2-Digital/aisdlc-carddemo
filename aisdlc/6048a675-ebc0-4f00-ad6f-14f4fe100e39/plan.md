# CR-1 Plan: `modernized/cbact04c/`

Implements [spec.md](spec.md). No changes outside `modernized/cbact04c/` and `legacy/tests/`.

## Files (in order)

1. `modernized/cbact04c/pom.xml`: Java 21. JUnit Jupiter (test) and Surefire only. The jar manifest's main class is `carddemo.cbact04c.Main`.
2. `src/main/java/carddemo/cbact04c/`:
   - `Zoned.java`: overpunch decode/encode, plus modulo-10^n truncation.
   - `RecordFile.java`: reads lines (strips CR, pads to LRECL) and writes fixed-width LF output.
   - `InterestCalc.java`: R2-R9 over in-memory files, with an injected `Clock` and a `PrintStream` for SYSOUT. Returns the RC. An `Abend` exception carries RC 16.
   - `Main.java`: CLI `<PARM> <IN_DIR> <OUT_DIR>`. Builds the clock from `CARDDEMO_CURRENT_DATE`.
3. `src/test/java/carddemo/cbact04c/`:
   - `GoldenTest`: stages the inputs into a temp dir from `app/data/ASCII` (via the harness DD→file map) and `legacy/golden/CBTRN02C`.
   - `ZonedTest`, `RecordIoTest`, `InterestTest`, `DefectsTest`, `AbendTest`.
4. `legacy/tests/cbact04c-diff.sh`: builds crafted inputs, runs `legacy/harness/run.sh CBACT04C` with `INPUT_DIR`, runs the Java jar on the same inputs, then diffs TRANSACT, ACCTFILE, SYSOUT and RC.
   - Case A: negative balance, a real group hit and multiple categories.
   - Case B: missing DEFAULT, which abends.
5. `modernized/cbact04c/README.md`: how to run it, plus the preserved-defect list (F1-F6).

## Test strategy

- Equivalence proof: golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`, byte-compared for TRANSACT, ACCTFILE and SYSOUT, with RC 0.
- Characterization: unit tests pin F1-F5 (spec "Acceptance tests"). The diff script settles any COBOL semantics the golden data does not exercise: negative overpunch, real group, abend output and partial files. Its observed output becomes the unit-test expectations.
- Commands:
  ```
  cd modernized/cbact04c && mvn -q verify
  legacy/tests/cbact04c-diff.sh
  legacy/harness/golden.sh --check      # unchanged legacy still matches
  ```

## Risks

- **Timestamp source.** The clock must give `00.00.00.000000` under the frozen date. Mitigation: inject `Clock`; the golden test uses a fixed 2022-07-18T00:00Z.
- **Overpunch on output.** Java might emit `+0` differently, e.g. `0` vs `{`. Mitigation: always encode with an overpunch; the golden byte-compare catches any slip.
- **Abend partial-file state.** Unknown how the harness unloads after CEE3ABD. Mitigation: observe it in diff-script case B and match it; if it can't be matched, flag it, since the CR only requires the success paths.
- **Overflow semantics (F3).** GnuCOBOL could differ from the documented mainframe truncation. Mitigation: pin the GnuCOBOL behaviour in the diff script, using a high rate on a large balance.
- **Input path coupling.** The tests read `../../app` and `../../legacy`. Mitigation: resolve paths from `${project.basedir}`, and fail clearly if they are missing.

## Effort (agent)

Build ~15 min, tests + diff script ~15 min, fix-up loop ~10 min: **~40 min**.
