# CR-1 Plan: `CBACT04C` → `modernized/cbact04c/`

Implements [spec.md](spec.md). Nothing under `legacy/`, `.github/` or `app/` changes.

## Files (in build order)

1. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter 5.x (test scope), Surefire, jar with `Main-Class`. No other dependencies.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch decode/encode and high-order truncation (spec "Zoned codec").
3. `.../RecordFiles.java`: load (strip CR, pad to LRECL, ISO-8859-1), write (LRECL + LF), and the DD-to-sample-file resolution.
4. `.../AbendException.java`: carries the SYSOUT lines. `Main` maps it to exit 16.
5. `.../InterestCalc.java`: the main loop (spec rules 1-8). TreeMaps for ACCTFILE and DISCGRP, alt-key map for XREF (first card wins), injected `Clock`, SYSOUT collector.
6. `.../Main.java`: CLI parsing (`--data --input-dir --out --parm --clock`) and exit code.
7. Tests in `src/test/java/carddemo/cbact04c/`:
   - `ZonedCodecTest`
   - `InterestTest`
   - `GoldenTest` (stock + after-CBTRN02C, byte compare of TRANSACT/ACCTFILE/SYSOUT/RC)
   - `DefectTest` (D1, D6, D9 text)
   - `DiscGrpTest`
   - `AbendTest`
   - `RateZeroTest`
   - Small fixtures are built in-test from golden lines; no new data files.
8. `modernized/cbact04c/README.md`: how to run, plus the preserved-defect list linking to spec.

No new `legacy/tests/*.sh` is needed. The COBOL side is already pinned by `legacy/golden/` (D1 evidence included), so no new COBOL behaviour needs recording.

## Test strategy

- **Equivalence.** `GoldenTest` runs the Java program in-process, with clock `2022071800000000` and PARM `2022071800`:
  - Stock: `--data app/data/ASCII`. Compared against `legacy/golden/CBACT04C/`.
  - Nightly order: `--input-dir legacy/golden/CBTRN02C`. Compared against `legacy/golden/CBACT04C-after-CBTRN02C/`.
  - Golden paths resolve relative to the repo root (`../../`).
- **Characterization.** Unit tests pin AC4-AC8 and each preserved defect in the spec.
- **Commands:**
  ```
  cd modernized/cbact04c && mvn -q verify
  java -jar target/cbact04c.jar --data ../../app/data/ASCII --out /tmp/j1 --parm 2022071800 --clock 2022071800000000
  diff -r /tmp/j1 ../../legacy/golden/CBACT04C            # RC.txt written too
  java -jar target/cbact04c.jar --data ../../app/data/ASCII --input-dir ../../legacy/golden/CBTRN02C \
       --out /tmp/j2 --parm 2022071800 --clock 2022071800000000
  diff -r /tmp/j2 ../../legacy/golden/CBACT04C-after-CBTRN02C
  ../../legacy/harness/golden.sh --check
  git diff --stat baseline -- legacy .github             # must be empty
  ```

## Risks

| Risk | Mitigation |
|---|---|
| Overpunch or -0 encoding off by one char | `ZonedCodecTest` covers all 20 sign chars; golden compare covers non-zero amounts (after-CBTRN02C) |
| BigDecimal scale/rounding drift | Single `divide(…, 2, DOWN)`, then truncate to field width; test where half-up differs |
| SYSOUT byte mismatch (padding, banner, line endings) | Byte compare with golden `SYSOUT.txt` in both scenarios |
| Abend output written partially | Write data files only on success; tests assert exit 16 and that only SYSOUT and RC are written |
| Repo-relative golden paths break in CI | Resolve via `basedir` system property set by Surefire |

## Effort

Build ≈ 15 min, tests + golden verification ≈ 10 min. **Total ≈ 25 min.**
