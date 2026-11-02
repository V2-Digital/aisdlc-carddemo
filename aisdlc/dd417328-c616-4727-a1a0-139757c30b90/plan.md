# CR-1 Plan: `CBACT04C` in Java 21

The behaviour is defined in [spec.md](spec.md). The background is in [intent.md](intent.md).

## Files (in build order)

All Java files live under `modernized/cbact04c/`, in the `src/main/java/com/v2/carddemo/cbact04c/` package.

1. `pom.xml`: Java 21, JUnit Jupiter 5 and Surefire, no other dependencies. Builds an executable jar.
2. `Zoned.java`: decodes and encodes overpunch to and from `BigDecimal`, with mod-10^n truncation.
3. `Records.java`: field offsets for the 5 layouts and the TRANSACT record builder.
4. `Datasets.java`: loads `<DD>.txt` into a `TreeMap` (KSDS semantics, padding, CR stripping) and writes outputs in key order.
5. `Cbact04c.java`: the main loop, the control break, the rate lookup and fallback, and the interest calculation. `Abend` exits with code 16, and an injectable `Clock` provides the time.
6. `Main.java`: parses the CLI arguments listed in the spec.
7. `src/test/java/...`:
   - `GoldenEquivalenceTest`
   - `InterestTest` (with `ZonedTest`)
   - `DefectCharacterizationTest`
   - `ErrorPathTest`

   Inputs come from `../../app/data/ASCII` and `../../legacy/golden`.
8. `legacy/tests/cbact04c-diff.sh`: crafted inputs (multiple categories, negative balance with a non-zero rate, a real group hit, missing account, missing DEFAULT). It runs each through `legacy/harness/run.sh CBACT04C` with `INPUT_DIR` and through the Java jar, then diffs all outputs.
9. `modernized/cbact04c/README.md`: how to run it, plus the list of preserved defects.

## Test strategy

- **Equivalence (AC2-4):** the `CBACT04C` and `CBACT04C-after-CBTRN02C` golden scenarios. Each compares 4 files byte for byte: TRANSACT, ACCTFILE, SYSOUT and RC.
- **Characterization (AC5-7):** unit tests on in-memory datasets for D1, D2, F1 and F2, the fallback, and the abends.
- **COBOL vs Java on new edge inputs (R1):** `cbact04c-diff.sh`. This turns the spec's claims about paths outside the golden data into facts.

Commands:

```sh
cd modernized/cbact04c && mvn -q verify
legacy/tests/cbact04c-diff.sh            # needs GnuCOBOL; exit 0 = identical
legacy/harness/golden.sh --check         # legacy is untouched
```

## Risks

| Risk | Mitigation |
|---|---|
| Overpunch sign of zero or negative results (`{` vs `}`) | Covered by `ZonedTest` and the negative case in the diff script against real GnuCOBOL |
| The order of SYSOUT lines on error paths differs from COBOL | Diff the script's SYSOUT/RC against the harness |
| KSDS order vs line order (TCATBALF, XREF AIX duplicates) | Use a `TreeMap`; the AIX lookup returns the first card. Crafted unsorted input in the diff script |
| BigDecimal precision differs from COBOL intermediate precision | Compute exactly, then truncate. The values fit easily (11+6 digits) |
| Golden paths are relative to the module | Resolve them from `${project.basedir}/../..` through a Surefire system property |

**Estimate:** about 25 min to build and about 15 min to test (including the diff script). About 40 min in total.
