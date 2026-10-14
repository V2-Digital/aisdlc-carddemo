# CR-1 Plan: `CBACT04C` → Java 21

Implements [spec.md](spec.md). There is no change to `app/`, `legacy/golden/`, `legacy/harness/` or `.github/`.

## Files (in order)

All paths below are under `modernized/cbact04c/`.

1. `pom.xml`: Java 21, JUnit Jupiter + Surefire only. It builds a runnable jar (`Main-Class` via the jar plugin manifest).
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch decode/encode and `truncInt(n)` (R1, D5).
3. `…/Records.java`: fixed-width line I/O (strip CR, pad to LRECL) and field slicing/patching by column.
4. `…/InterestCalc.java`: R2-R7 plus the error paths E1-E4. Takes an injectable `now` and a `PrintStream` sysout. Abends throw `AbendException(16)`.
5. `…/Main.java`: CLI (`--parm --in --out --now`). Maps the result to the exit code.
6. `src/test/java/…/GoldenEquivalenceTest.java`: covers both scenarios. It stages inputs into a temp `--in` dir (stock files renamed to DD names; scenario 3 overlays `legacy/golden/CBTRN02C/{ACCTFILE,TCATBALF}.txt`). It then byte-compares the 3 files and RC against `legacy/golden/…`.
7. `…/ZonedTest.java`, `…/InterestTest.java`: unit tests for AC5.
8. `…/DefectCharacterizationTest.java`: D1 on scenario 3 (AC6). Small synthetic inputs cover the DEFAULT fallback, E1/E2/E3 (AC7) and the zero rate (AC8).
9. `modernized/cbact04c/README.md`: how to run it, plus the preserved defects list (links to the spec).

No new `legacy/tests/*.sh` is needed: the Java tests read the goldens directly.

## Test strategy and commands

- **Equivalence** is proven by the goldens `CBACT04C` (50 TRANSACT / 50 ACCT / 152 SYSOUT lines) and `CBACT04C-after-CBTRN02C` (50 / 50 / 302).
- **Characterization**: synthetic fixtures are built in-test, so there are no extra data files.
- **Commands:**
  - `mvn -q -f modernized/cbact04c verify`
  - `legacy/harness/golden.sh --check` (unchanged harness still green)
  - The CLI smoke test diffs against the golden:

    ```
    java -jar modernized/cbact04c/target/cbact04c.jar --parm 2022071800 --in <staged> --out /tmp/o --now 2022071800000000 && diff -r …
    ```

  - `git diff --stat -- legacy .github`, which must be empty.

## Top risks

| Risk | Mitigation |
|---|---|
| Overpunch or negative-zero encoding off by one character | `ZonedTest` round-trips every value in the golden files. Zero is always `{`. |
| Trailing spaces lost (desc tail, FILLER, padded XREF lines) | Pad on read and write at fixed LRECL. Use a byte-level `assertArrayEquals` on whole files. |
| Truncation done with `double` or the wrong scale | Use only `BigDecimal` with `RoundingMode.DOWN`. AC5 pins 29.249625 → 29.24. |
| ACCTFILE output order or untouched records | Write the whole `TreeMap` in key order. Diff 50/50 records. |
| The test's working dir differs under `mvn -f` | Resolve the repo root from `${project.basedir}/../..` via a Surefire system property. |

## Effort estimate (agent)

- Build: about 15 minutes.
- Tests and green run: about 10 minutes.
- **Total: about 25 minutes.**
