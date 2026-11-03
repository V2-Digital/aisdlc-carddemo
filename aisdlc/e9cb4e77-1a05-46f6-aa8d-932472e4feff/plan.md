# CR-1 Plan: `CBACT04C` in Java 21

Behaviour: [spec.md](spec.md). Background: [intent.md](intent.md).

## Files (in order)

1. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter + Surefire only, jar with `Main-Class`.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch decode/encode, PIC-width modulo truncation.
3. `…/Records.java`: fixed-offset field access for TCATBAL, XREF, DISCGRP, ACCOUNT, and the TRAN record builder.
4. `…/Clock.java`: `--now`, then `CARDDEMO_CURRENT_DATE`, then `COB_CURRENT_DATE`, then the UTC system clock. Formats the DB2 timestamp.
5. `…/Cbact04c.java`: main loop (spec rules 1–7), including the D1 EOF quirk. `Abend` exception → RC 16.
6. `…/Main.java`: CLI arg parsing, stdout/stderr, `System.exit(rc)`.
7. `src/test/java/…`: `ZonedTest`, `InterestTest`, `ClockTest`, `DefectTest`, `AbendTest`, `GoldenTest`.
8. `modernized/cbact04c/README.md`: build/run in a few lines.

## Test strategy

- **Equivalence (golden):** `GoldenTest` runs in-process on
  - `CBACT04C`: inputs `app/data/ASCII/{tcatbal,cardxref,discgrp,acctdata}.txt`, PARM `2022071800`, now `2022071800000000`;
  - `CBACT04C-after-CBTRN02C`: same, but ACCTFILE and TCATBALF come from `legacy/golden/CBTRN02C/`.
  It asserts byte equality of TRANSACT/ACCTFILE, line equality of SYSOUT, and RC = `RC.txt`.
- **Characterization:** D1 (acct 50), D2 (a golden amount where HALF_UP differs), D5 (0.00 txn), D7 (overflow wraps).
  It also checks zero rate → no suffix, the abend paths (small synthetic inputs in `@TempDir`), and the clock format.
- **COBOL-side cross-check:** `legacy/tests/cbact04c_abend.sh`. It runs `run.sh` with `INPUT_DIR` holding a TCATBALF that has an
  unknown account, and asserts RC 16 plus the message lines. This proves the AC-8 texts against real COBOL, not just my reading.
- **Commands:**
  ```sh
  mvn -q -f modernized/cbact04c test
  legacy/harness/golden.sh --check          # golden untouched, still reproducible
  bash legacy/tests/cbact04c_abend.sh
  # CLI smoke: diff against golden
  java -jar modernized/cbact04c/target/cbact04c.jar --parm=2022071800 --now=2022071800000000 \
    --in=<staged dir> --out=/tmp/j && diff /tmp/j/TRANSACT.txt legacy/golden/CBACT04C/TRANSACT.txt
  ```

## Risks

| Risk | Mitigation |
|---|---|
| Truncation vs rounding, or wrong scale in the intermediate | exact `BigDecimal` multiply/divide (`scale 2`, `DOWN`). The golden after-CBTRN02C case catches it (28 would differ). |
| Overpunch encoding of ±0 / negatives | `ZonedTest` round-trips every golden numeric field. Negative balances already exist in the CBTRN02C TCATBALF. |
| SYSOUT drift (trailing spaces, DISPLAY widths) | line-exact comparison against both golden SYSOUTs (152 / 302 lines) |
| D1 accidentally "fixed" | `DefectTest` pins account 50 unchanged |
| Abend message/status text guessed wrong | shell test runs the real COBOL for one abend path |
| Golden test paths fragile in CI | resolve the repo root from `${basedir}/../..` via a Surefire system property |

## Effort (agent)

Build ~15 min, tests + verification ~10 min. **Total ≈ 25 min.**
