# CR-1 Plan: INTCALC (`CBACT04C`) in Java 21

Implements [spec.md](spec.md). No changes to `app/`, `legacy/golden/`, `legacy/harness/` or `.github/`.

## Files, in order

1. `modernized/cbact04c/pom.xml`
   - Java 21, JUnit Jupiter 5 (test scope) and Surefire only. The jar's main class is `carddemo.cbact04c.Cbact04c`.
   - Surefire sets `golden.dir=${project.basedir}/../../legacy/golden` and `data.dir=…/../../app/data/ASCII`.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch parse and format, `BigDecimal` with scale 2, truncating to field width.
3. `…/FixedFile.java`: reads lines (strip CR, pad to LRECL) and writes LRECL+LF records as ISO-8859-1.
4. `…/Records.java`: field offsets from the copybooks (spec table). Accounts are patched in place on the raw `byte[]`/`String`.
5. `…/Cbact04c.java`: `run(dataDir, parm, clock, sysout)` (R1-R9) and a `main` that reads the args and env and calls `System.exit(rc)`.
6. Tests in `src/test/java/carddemo/cbact04c/`, as listed in the spec's table. The golden tests do this:
   - copy the inputs into a `@TempDir` (stock: the `app/data/ASCII` files renamed to DD names; after-post: ACCTFILE/TCATBALF taken from `golden/CBTRN02C`);
   - run with PARM `2022071800` and clock `2022071800000000`;
   - `assertArrayEquals` on the bytes of each file, plus RC.
7. `modernized/cbact04c/README.md`: how to run, env var, preserved defects D1-D8 (one line each).

## Test strategy

- **Equivalence (AC2-4).** Golden scenarios `CBACT04C` (50 tran, 152 SYSOUT lines) and `CBACT04C-after-CBTRN02C` (50 tran, 49/50 accounts changed, 302 lines).
- **Unit and characterization (AC5-9).** Built with small inline datasets:
  - truncation of ±values;
  - a DISCGRP hit;
  - the rate-0 path;
  - layout;
  - D1;
  - the four abend paths.
- **No new COBOL tests.** The golden files already pin the legacy behaviour, so `legacy/tests/` stays empty.
- Commands:
  ```sh
  mvn -q -f modernized/cbact04c test
  legacy/harness/golden.sh --check          # sanity: legacy still matches golden
  mvn -q -f modernized/cbact04c package && \
    CARDDEMO_CURRENT_DATE=2022071800000000 java -jar modernized/cbact04c/target/cbact04c.jar 2022071800 <dir>
  ```
  The last command is a CLI smoke test. Diff `<dir>` against `legacy/golden/CBACT04C`.

## Top risks

| Risk | Mitigation |
|---|---|
| Overpunch or `-0` encoding drift | `Zoned` round-trip test over every value in all golden files |
| Key-order mismatch (TCATBAL, ACCTFILE unload) | `TreeMap` on the raw key bytes; the golden byte diff catches any mismatch |
| Truncation done wrong (`setScale` vs `divide` scale) | Multiply exactly, then `divide(1200, 2, DOWN)`; check against all 50 golden amounts |
| Abend partial-output semantics differ from COBOL | Documented in the spec (R9) and covered only by an RC/SYSOUT check; the approver decides |
| Charset or trailing-space loss | Byte-level I/O with ISO-8859-1, never trimming |

## Effort estimate

Build about 15 min and tests about 10 min, so **about 25 min** of agent time.

## Decisions for the approver

- Is "no output files on abend" acceptable (spec R9)? The alternative is to mimic partial TRANSACT and ACCTFILE writes.
- Is the CLI shape `<PARM> [DATA_DIR]` with DD-named `.txt` files OK for Deploy?
