# CR-1 Plan: `CBACT04C` → Java 21

Specification: [spec.md](spec.md). Package `carddemo.cbact04c`; no dependencies except JUnit 5.

## Files, in build order

1. `modernized/cbact04c/pom.xml`
   - Java 21, JUnit Jupiter, Surefire. No other plugins except `maven-jar-plugin` (Main-Class).
2. `src/main/java/carddemo/cbact04c/Zoned.java`
   - Overpunch decode/encode for `S9(n)V99`, including the high-order truncation (D3).
3. `.../FixedFile.java`
   - Reads canonical text (strip CR, pad to LRECL) and writes fixed-width lines with LF.
4. `.../Cbact04c.java`
   - The main loop, lookups, interest, transaction build, account update and abend.
   - The PARM, the clock and stdout are injected.
5. `.../Main.java`
   - CLI `<PARM> <IN_DIR> <OUT_DIR>` and `CARDDEMO_CURRENT_DATE`. Turns an abend into exit code 16.
6. Tests in `src/test/java/carddemo/cbact04c/`:
   - `ZonedTest`
   - `InterestTest`
   - `GoldenTest`
   - `AbendTest`
   - `DefectTest`
7. `legacy/tests/cbact04c_missing_default.sh`
   - Runs the legacy program with `INPUT_DIR` holding a DISCGRP that has no `DEFAULT` rows.
   - Asserts RC 16 and the SYSOUT lines, so the Java abend contract is proven against COBOL.
8. `modernized/cbact04c/README.md`
   - Usage and the D1–D5 list (5 lines).

## Test strategy

- **Equivalence.** `GoldenTest` stages an input directory, runs in-process with PARM `2022071800` and clock
  `2022071800000000`, and byte-compares `TRANSACT.txt`, `ACCTFILE.txt`, stdout and the exit code:
  - `CBACT04C` takes its inputs from `app/data/ASCII/{tcatbal,cardxref,acctdata,discgrp}.txt`.
  - `CBACT04C-after-CBTRN02C` takes `ACCTFILE` and `TCATBALF` from `legacy/golden/CBTRN02C/`.
- **Characterization.** These use small inline fixtures:
  - D1 last account
  - D2 truncation
  - D3 wrap
  - D4 zeroing when the rate is 0
  - DEFAULT fallback
  - the 3 abend causes
  - an empty TCATBALF
  - unsorted TCATBALF gets sorted
- **Commands:**
  ```sh
  cd modernized/cbact04c && mvn -q verify
  legacy/tests/cbact04c_missing_default.sh
  legacy/harness/golden.sh --check
  ```
  Plus a CLI smoke test, compared against the golden files:
  `java -jar target/cbact04c-*.jar 2022071800 <in> <out> && diff <out>/TRANSACT.txt ...`.

## Top risks → mitigation

- **Overpunch/sign errors (e.g. `-0`, the zero sign).** `ZonedTest` round-trips every sign digit, and the
  golden diff catches the rest.
- **Exact decimal semantics drifting from GnuCOBOL** (intermediate precision). Use `BigDecimal` exact
  multiply, then `divide(1200, 2, DOWN)`. All 50 after-scenario amounts are checked.
- **D1 "fixed" by accident through an idiomatic loop.** The `DefectTest.d1` and after-scenario ACCTFILE
  diffs fail if account 50 changes.
- **Timestamp/clock drift.** The injected clock, the frozen value in tests, and the `ss0000` format are
  pinned in `GoldenTest`.
- **ACCTFILE output order and untouched bytes.** Write the whole master sorted by key, as raw byte arrays.
  Only the 3 fields are overwritten.

## Estimate

About 20 min of build and about 10 min of testing (agent time).
