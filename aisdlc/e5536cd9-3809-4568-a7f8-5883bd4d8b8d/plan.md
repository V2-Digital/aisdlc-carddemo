# CR-1 Plan: `CBACT04C` → `modernized/cbact04c/`

Implements [spec.md](spec.md). There are no changes outside `modernized/cbact04c/` and `legacy/tests/`.

## Files (in order)

1. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter 5.x (test scope), Surefire, and a jar manifest (`Main-Class`). No other dependencies.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch decode/encode for `S9(n)V99` ↔ `BigDecimal`, with high-order truncation.
3. `.../FixedRecords.java`: read lines (strip `\r`, pad to length) and write fixed-length lines ending in `\n`.
4. `.../Records.java`: field offsets from the spec table (record types for TCATBAL, XREF, ACCT, DISCGRP, TRAN).
5. `.../InterestCalc.java`: the R1–R9 loop. Takes `PrintStream` (SYSOUT) and `Clock` as arguments, and returns RC 0/16 through an `Abend` exception.
6. `.../Main.java`: CLI `<PARM> <IN_DIR> <OUT_DIR>`. Clock from `CARDDEMO_CURRENT_DATE`, else system UTC. Calls `System.exit(rc)`.
7. `src/test/java/...`: `GoldenTest`, `InterestTest`, `DefectTest`, `RateTest`, `AbendTest`, `TranRecordTest`, `ZonedTest`.
8. `legacy/tests/cbact04c-java-equiv.sh`: builds the jar, then runs `harness/run.sh CBACT04C` and the Java jar on the same staged inputs (both scenarios), and runs `diff` on TRANSACT/ACCTFILE/SYSOUT.
9. `modernized/cbact04c/README.md`: how to run, plus the D1–D6 list.

## Test strategy

- **Equivalence:** `GoldenTest` covers both golden scenarios, `CBACT04C` and `CBACT04C-after-CBTRN02C`.
  - Inputs are staged into a temp dir under DD names, mirroring `run.sh:50-51`. Stock inputs come from `app/data/ASCII/{tcatbal,cardxref,acctdata,discgrp}.txt`. For after-CBTRN02C, `ACCTFILE.txt` and `TCATBALF.txt` come from `legacy/golden/CBTRN02C/`.
  - Clock frozen at `2022071800000000`.
  - Asserts byte equality (`Files.mismatch == -1`) for TRANSACT, ACCTFILE and SYSOUT, and checks RC 0.
- **Characterization and unit tests:** small synthetic fixtures built in code, one per AC 4–8 (see the spec table). Covers D1, D2 and D3 (suffix wrap, overflow truncation).
- **Cross-check against live COBOL:** `legacy/tests/cbact04c-java-equiv.sh`. It also runs one synthetic fixture with a negative balance and a non-DEFAULT group hit, to cover branches the golden data never reaches.
- **Commands:**
  ```
  cd modernized/cbact04c && mvn -q test
  legacy/harness/golden.sh --check          # legacy untouched
  legacy/tests/cbact04c-java-equiv.sh
  ```

## Top risks

| Risk | Mitigation |
|---|---|
| Overpunch sign on zero or negative values differs from GnuCOBOL | `ZonedTest` round-trips every golden amount. The live-COBOL script exercises negative values |
| Division scale: COBOL keeps intermediate precision before truncating | Compute `bal*rate` exactly, then `divide(1200, 2, DOWN)`. Pinned by `InterestTest` on all 50 golden amounts |
| SYSOUT drift (trailing spaces, the raw record echo) | Byte-compare SYSOUT in `GoldenTest` |
| ACCTFILE output order / non-touched records | Use an ordered map keyed by acct-id. Every non-touched byte is copied through |
| Abend partial-output semantics differ from VSAM | The spec defines them, and they are not golden-checked. Flagged in the README |

**Estimate:** about 25 min build + 10 min test and cross-check.
