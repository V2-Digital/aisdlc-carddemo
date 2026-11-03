# CR-1 Plan — build `modernized/cbact04c/`

Spec: [spec.md](spec.md). Intent: [intent.md](intent.md).

## Files (in order)

1. `modernized/cbact04c/pom.xml`
   - Java 21, JUnit Jupiter 5.x and maven-surefire-plugin only.
   - Builds a runnable jar (manifest `Main-Class`).
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch decode/encode and mod-10^n truncation.
3. `Records.java`: field offsets for the five copybooks (TCATBAL, XREF, DISCGRP, ACCOUNT, TRAN).
4. `Datasets.java`: loads and writes canonical text files.
   - Indexed files load into a `TreeMap` by key; XREF also gets a first-match alt-key map.
   - TRANSACT is written as appended lines.
5. `Cbact04c.java`: main loop R1–R9.
   - Constructor takes `(Path dir/DD map, String parm, Clock, PrintStream sysout)`.
   - `Abend` exception → exit 16.
6. `Main.java`: reads args/env (`DD_*`, `CARDDEMO_CURRENT_DATE`) and calls `System.exit(rc)`.
7. `src/test/java/...`: `ZonedTest`, `InterestTest`, `ZeroRateTest`, `RateFallbackTest`,
   `LastAccountDefectTest`, `TransactLayoutTest`, `GoldenTest`.
   - Tests read `../../legacy/golden` and `../../app/data/ASCII` directly; no copied fixtures.
   - Synthetic cases build small in-memory/temp datasets.
8. `modernized/cbact04c/README.md`: how to run, env vars, and the D1–D7 list.

## Test strategy

- **Equivalence:** the golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Compare TRANSACT, ACCTFILE,
  SYSOUT and RC byte for byte with clock `2022071800000000`.
- **Characterization:** these cover the cases the golden data never exercises (R1/R2 of intent):
  - negative sign
  - zero sign
  - overflow
  - group-specific rate
  - DEFAULT missing
  - acct/xref missing
- **Cross-check vs COBOL:** `legacy/tests/cbact04c_edge.sh` runs both legacy and Java on one synthetic
  `INPUT_DIR` and diffs them. The input has negative balances, a group-specific DISCGRP row and an ACCTFILE
  with `ACCT-GROUP-ID` set. This turns the unit-test assumptions into harness evidence.
- Commands:
  ```sh
  cd modernized/cbact04c && mvn -q test
  mvn -q package && legacy/tests/cbact04c_edge.sh
  legacy/harness/golden.sh --check     # legacy unchanged
  ```

## Risks

| Risk | Mitigation |
|---|---|
| Abend output (partial TRANSACT/ACCTFILE, stderr text) differs from harness | Run one abend case (missing xref) in `cbact04c_edge.sh` and match its outputs |
| Overpunch encoding of negative zero / overflow wrap | `long` cents with explicit mod. Harness-verified −0.01 → `{` |
| Timestamp hundredths or TZ drift | Inject `Clock` and parse `CARDDEMO_CURRENT_DATE`, UTC only |
| SYSOUT line ordering vs DISPLAY | Golden SYSOUT diff in `GoldenTest` (152 / 302 lines) |
| Trailing-space / final-newline handling | Byte compare whole files, not lines |

## Effort (agent)

About 20 min build and 10 min test/cross-check.
