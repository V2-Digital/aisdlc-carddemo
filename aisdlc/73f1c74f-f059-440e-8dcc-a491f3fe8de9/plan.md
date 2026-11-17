# CR-1 Plan: `CBACT04C` to Java 21

Rules R1–R9 are defined in [spec.md](spec.md).

## Files, in order
1. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter (test scope), Surefire, and a jar with `Main-Class`. Nothing else.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch parse/format and `S9(n)V99` high-order truncation.
3. `.../Records.java`: fixed-width read (CR strip, pad) and write (LRECL + LF, ISO-8859-1), plus field slicing by offset.
4. `.../InterestCalc.java`: R1–R9 as a pure function over input lines, parm and clock. Returns the outputs, the stdout
   lines and the RC (or throws `Abend`).
5. `.../Main.java`: CLI (`--parm --clock --in --out`), atomic temp-file and rename on success, exit 16 on `Abend`.
6. `src/test/java/...`: `ZonedTest`, `InterestTest`, `GroupFallbackTest`, `ZeroRateTest`, `SuffixTest`, `ClockTest`,
   `DefectTest`, `AbendTest`, `GoldenTest`.
7. `legacy/tests/cbact04c-negative.sh`: runs the real COBOL (`INPUT_DIR`) on a crafted TCATBALF with
   negative `01/0001` balances (−1164.87, −0.01, −0.99). Asserts the amounts (−14.56, and the −0.00 sign byte) so the
   Java negative-truncation test is pinned to legacy facts, not to assumptions.
8. `modernized/cbact04c/README.md`: how to run it, plus the preserved defects D1–D6 in one line each.

## Test strategy
- **Equivalence:** `GoldenTest` reads `legacy/golden/` and `app/data/ASCII/` in place (repo-relative path) and
  byte-compares `TRANSACT.txt` and `ACCTFILE.txt` for both scenarios (stock: 50+50; after-CBTRN02C: 50+50, 49 changed).
  It also compares stdout with `SYSOUT.txt` (152 / 302 lines) as a bonus.
- **Characterization:** one small in-memory fixture per quirk (AC4–AC6). The negative case gets its expected values
  from step 7.
- Commands:
  ```
  cd modernized/cbact04c && mvn -q test
  bash legacy/tests/cbact04c-negative.sh
  legacy/harness/golden.sh --check
  # CLI smoke: java -jar target/cbact04c-*.jar --parm 2022071800 --clock 2022071800000000 --in <dir> --out /tmp/o
  ```

## Risks
| Risk | Mitigation |
|---|---|
| Intermediate precision of `bal × rate / 1200` differs from GnuCOBOL | Exact `BigDecimal` multiply, then a single `DOWN` divide. Verify with 50 non-zero golden amounts and the step-7 COBOL run |
| Sign of a −0.00 result (`{` vs `}`) | Taken from the step-7 legacy run, not guessed |
| Input quirks (36-byte xref lines, CRLF tcatbal) break the fixed-width slicing | `Records` pads and strips. `GoldenTest` uses the raw `app/data/ASCII` files |
| Duplicate xref alt keys | None in the sample data (checked). Keep the first match by file order and document it |
| Off-by-one on D1 (rewriting the last account by accident) | `DefectTest` plus a golden byte-compare of account 50 |

## Effort
Build ~15 min, tests + legacy negative script ~15 min, so **~30 min** in total.
