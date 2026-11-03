# CR-1 Plan: `CBACT04C` → Java 21

Contract: [spec.md](spec.md). Context: [intent.md](intent.md).

## Files (in order)
All Java code goes under `modernized/cbact04c/`, package `com.v2.carddemo.cbact04c`.
1. `pom.xml`: Java 21, JUnit Jupiter 5.x (test scope), Surefire 3.x. Builds a runnable jar (`Main-Class` in the manifest). No other deps.
2. `src/main/java/.../Zoned.java`: overpunch encode/decode for `S9(n)V99`, with high-order truncation.
3. `.../Records.java`: fixed-width read/pad helpers and field slices for the 5 layouts (offsets from spec).
4. `.../InterestCalc.java`: the rules 1–8 loop. `Clock`, PARM and the I/O dirs are injected. Returns RC. Abends are thrown as an internal `Abend` exception → RC 16.
5. `.../Main.java`: CLI (`--in --out --parm [--now]`).
6. `src/test/java/...`: `GoldenStockTest`, `GoldenAfterTrn02Test`, `InterestTest`, `ZonedTest`, `DefectR1Test`, `AbendTest`.
7. `modernized/cbact04c/README.md`: how to run, plus the list of preserved defects (short).

## Test strategy
- **Equivalence (golden):** tests stage the inputs in a temp dir, using the same DD→file mapping as `legacy/harness/run.sh:50-52`:
  - `TCATBALF`←`tcatbal.txt`, `XREFFILE`←`cardxref.txt`, `ACCTFILE`←`acctdata.txt`, `DISCGRP`←`discgrp.txt`.
  - Overlay for after-CBTRN02C: `legacy/golden/CBTRN02C/{ACCTFILE,TCATBALF}.txt`.
  - Run with the clock `2022-07-18T00:00:00Z`, then compare `TRANSACT`, `ACCTFILE`, `SYSOUT` and `RC` with `Files.mismatch` against `legacy/golden/CBACT04C{,-after-CBTRN02C}/`.
- **Characterization:** truncation 14.560875→14.56, negative truncation, -0 encoding, R1 (acct 50), and overflow wrap (suffix 999999→000000, balance mod 10^10).
- **Abend paths:** synthetic inputs in a temp dir, one each for a missing account, a missing XREF, and a missing DEFAULT. Each expects RC 16 and the spec'd SYSOUT tail. Parity with COBOL is checked once by hand in the harness via `INPUT_DIR` and quoted in the test report. No new `legacy/tests/` script is needed: golden already covers the COBOL side.
- **Commands:**
  ```
  mvn -q -f modernized/cbact04c test
  mvn -q -f modernized/cbact04c package && java -jar modernized/cbact04c/target/cbact04c.jar --in <staged> --out /tmp/j --parm 2022071800 --now 2022071800000000
  diff -r /tmp/j legacy/golden/CBACT04C   # plus RC
  legacy/harness/golden.sh --check
  git diff --stat baseline -- legacy/golden legacy/harness .github   # must be empty
  ```

## Risks
| Risk | Mitigation |
|---|---|
| BigDecimal scale/rounding drift vs COBOL intermediate precision | Exact `multiply` then `divide(1200, 2, DOWN)`. The golden after-CBTRN02C run checks 50 non-zero amounts |
| Overpunch sign of zero / negatives | `ZonedTest` round-trips every input value in the golden files |
| CRLF / short lines in sample inputs (`tcatbal.txt`, 36-byte `cardxref.txt`) | Strip CR, right-pad to LRECL on read |
| SYSOUT drift (DISPLAY of the raw record, blank lines) | Byte-compare `SYSOUT.txt` in both golden tests |
| Golden tests depend on the repo layout | Resolve the repo root from `${basedir}/../..`. Fail clearly if `legacy/golden` is missing |

## Effort
About 20 min build + 10 min test and evidence, for roughly 30 min agent time.
