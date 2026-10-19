# CR-1 Plan: CBACT04C → Java 21

Contract: [spec.md](spec.md). Background: [intent.md](intent.md).

## Files (in order)

1. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter 5 (test scope), Surefire, and a jar with `Main-Class`. No other dependencies.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch decode/encode and fixed-width modulo truncation.
3. `.../Records.java`: column slices for TCATBAL, XREF, ACCOUNT, DISCGRP, and the TRAN builder.
4. `.../FileStore.java`: loads the canonical text (strips CR, right-pads to LRECL) and writes LF output.
5. `.../InterestCalc.java`: the main loop (spec rules 1-7). It takes the `Clock`, PARM and a SYSOUT sink, and throws `AbendException` on failure.
6. `.../Main.java`: the CLI (args, `CARDDEMO_CURRENT_DATE`, per-DD path overrides). Exits 0 or 16.
7. `src/test/java/...`: the tests listed below.
8. `modernized/cbact04c/README.md`: how to run, plus the preserved-defect list D1-D6.

## Test strategy

- **Equivalence (gate):**
  - `GoldenTest` runs the Java program in-process with PARM `2022071800` and clock `2022071800000000`.
    - Stock inputs: `app/data/ASCII/{tcatbal,cardxref,acctdata,discgrp}.txt`, compared byte for byte with `legacy/golden/CBACT04C/`.
    - After CBTRN02C: `golden/CBTRN02C/{ACCTFILE,TCATBALF}.txt`, compared with `legacy/golden/CBACT04C-after-CBTRN02C/`.
    - Both check `TRANSACT.txt`, `ACCTFILE.txt` and `RC.txt`. SYSOUT is diffed but only reported, not asserted as a gate.
- **Characterization / unit tests:** `Zoned`, truncation vs rounding, negatives, D1, D3 overflow, rate fallback / zero rate / direct hit, the counter and clock, pass-through, and the abends (exit 16). These use small in-memory fixtures.
- **COBOL cross-check (new):** `legacy/tests/cbact04c_edge.sh`. It builds an `INPUT_DIR` with a negative balance, a direct `A000000000` group hit and a rate that rounding would change. It then runs `legacy/harness/run.sh CBACT04C` and the Java jar on the same inputs and diffs TRANSACT/ACCTFILE/RC. This proves that the unit-test expectations match real COBOL, not my reading of it.
- **Commands:**
  ```
  mvn -q -f modernized/cbact04c test
  mvn -q -f modernized/cbact04c package && legacy/tests/cbact04c_edge.sh
  legacy/harness/golden.sh --check     # legacy untouched
  ```

## Top risks → mitigation

- **Overpunch / -0 encoding wrong** → round-trip test on every golden zoned field. A zero result is encoded as `{`.
- **Truncation direction for negatives and overflow semantics** → `BigDecimal` with `RoundingMode.DOWN`, then modulo. The edge cases are confirmed against GnuCOBOL in the edge script.
- **XREF alternate key with duplicate acct-ids** → take the first match in file order. There are no duplicates in the sample data (checked); the rule is documented.
- **Timestamp drift between records** → one injected `Clock`. The golden data uses a frozen clock.
- **Test paths relative to the repo** → resolve the repo root from `${basedir}/../..` via a Surefire system property.

## Effort (agent)

About 25 min build and about 15 min test (including the COBOL edge script).

## For the approver

- The CLI shape (`<PARM> <IN_DIR> <OUT_DIR>`, env clock) follows the intent's Q3 default.
- SYSOUT is informational (Q1) and the abend exit code is 16 (Q2).
