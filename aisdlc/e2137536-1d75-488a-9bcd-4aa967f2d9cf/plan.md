# CR-1 Plan: `CBACT04C` → Java 21

Run `e2137536-1d75-488a-9bcd-4aa967f2d9cf` · Stage: Design · Implements [spec.md](spec.md)

## Files (in order)

1. `modernized/cbact04c/pom.xml` — Java 21, JUnit 5 (`junit-jupiter`), Surefire, jar with `Main-Class`.
2. `src/main/java/carddemo/cbact04c/Overpunch.java` — zoned decimal ⇄ `BigDecimal`, with high-order truncation.
3. `.../Records.java` — field offsets for the 5 layouts (copybook paths in javadoc), fixed-width line I/O.
4. `.../Datasets.java` — load (strip CR, pad, sort into `TreeMap`, XREF alt index), write LF files.
5. `.../Cbact04c.java` — rules R2–R8, SYSOUT buffer, `AbendException` → RC 16; injectable `Clock`.
6. `.../Main.java` — CLI (`--parm --in --dd --out`, `CARDDEMO_CURRENT_DATE`).
7. `src/test/java/...` — `OverpunchTest`, `InterestTest`, `RulesTest`, `DefectTest`, `AbendTest`, `GoldenEquivalenceTest`.
8. `src/test/resources/fixtures/<case>/` — small `<DD>.txt` sets: negative balance, truncation vs half-up,
   real group hit, zero rate, missing account, missing xref, missing DEFAULT.
9. `legacy/tests/cbact04c-diff.sh` — runs COBOL (`INPUT_DIR=<fixture> legacy/harness/run.sh CBACT04C`) and Java on
   each fixture, diffs the 4 output files. Proves the fixtures' expected values come from COBOL, not from me.
10. `modernized/cbact04c/README.md` — how to run, defect list → spec.

## Test strategy

- **Equivalence (the proof):** `GoldenEquivalenceTest` runs Java in-process on
  - `CBACT04C`: `app/data/ASCII` (`tcatbal.txt`, `cardxref.txt`, `acctdata.txt`, `discgrp.txt`) → `legacy/golden/CBACT04C/`
  - `CBACT04C-after-CBTRN02C`: same, with `ACCTFILE`/`TCATBALF` from `legacy/golden/CBTRN02C/` → that golden dir
  - PARM `2022071800`, clock `2022071800000000`; byte compare of `TRANSACT`, `ACCTFILE`, `SYSOUT`, `RC`.
- **Characterization:** unit tests per spec table. Expected values for fixtures are taken from COBOL runs via step 9.
- **Commands**
  ```sh
  cd modernized/cbact04c && mvn -q verify
  legacy/tests/cbact04c-diff.sh            # COBOL vs Java on edge fixtures
  legacy/harness/golden.sh --check         # golden still reproduces from COBOL (unchanged)
  ```

## Risks

| Risk | Mitigation |
|---|---|
| Decimal drift (`double`, wrong rounding) | `BigDecimal` only, `RoundingMode.DOWN`; truncation test + 28 discriminating golden records |
| XREF duplicate acct-id order differs from BDB | Spec R1 (first in file order); fixture with 2 cards per account in diff script |
| Abend-path partial outputs differ from harness unload | Diff script compares COBOL vs Java on every abend fixture |
| −0 / overpunch sign edge cases | Exhaustive `OverpunchTest`; negative-balance fixture diffed against COBOL |
| Clock/timezone nondeterminism | `CARDDEMO_CURRENT_DATE` frozen in tests; same format as harness |

## Effort (agent)

Build ≈ 15 min, tests + diff script + fixing mismatches ≈ 15 min. **Total ≈ 30 min.**
