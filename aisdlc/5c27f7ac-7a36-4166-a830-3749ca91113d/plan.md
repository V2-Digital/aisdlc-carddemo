# CR-1 Plan — `CBACT04C` → `modernized/cbact04c/`

Run `5c27f7ac-7a36-4166-a830-3749ca91113d` · Stage: Design · Behaviour: [spec.md](spec.md)

## Files, in build order

1. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter 5.x (test scope), Surefire 3.x, jar with `Main-Class`.
   No other dependencies.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch decode/encode, scale 2, mod 10^n (spec "Fields used").
3. `.../Records.java`: fixed-width field slicing and splicing for the 5 layouts, pad to LRECL, ISO-8859-1 I/O.
4. `.../InterestCalc.java`: rules 1-9 as one class over injected `parm`, `clock`, SYSOUT `PrintStream`. Returns RC
   and holds output buffers, so it can flush partial output on abend.
5. `.../Main.java`: CLI args (`--in --out --parm --clock`), exit code.
6. `src/test/java/.../GoldenEquivalenceTest.java`: builds an input dir per scenario from `app/data/ASCII`
   (renamed to DD names) + `legacy/golden/CBTRN02C`, runs the program, and byte-compares against `legacy/golden/...`.
7. `.../ZonedTest.java`, `InterestTest.java`, `CharacterizationTest.java`: AC5-9 on small synthetic inputs.
8. `legacy/tests/cbact04c-abend.sh` (new): runs the real COBOL via `INPUT_DIR` with a TCATBALF naming a
   missing account (and one missing xref), and saves the SYSOUT + RC that the Java tests assert against.
   It only reads the harness and never modifies it.
9. `modernized/cbact04c/README.md`: how to run (CLI + `docker run eclipse-temurin:21-jre`). Short.

## Test strategy

- **Equivalence:** golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Byte-equal TRANSACT, ACCTFILE,
  SYSOUT, RC. The second scenario is the real proof: non-zero amounts, negative balances, D1 visible.
- **Characterization:** truncation, DEFAULT fallback/abend, zero rate, last-account (D1), missing acct/xref (RC 16),
  overflow mod 10^n (D6), first xref wins (D7).
- **Cross-check of the synthetic abend cases against the real COBOL** via `legacy/tests/cbact04c-abend.sh`.
- Commands:
  ```sh
  mvn -q -f modernized/cbact04c test
  mvn -q -f modernized/cbact04c package && java -jar modernized/cbact04c/target/cbact04c.jar \
      --in <dir> --out /tmp/j --parm 2022071800 --clock 2022071800000000
  bash legacy/tests/cbact04c-abend.sh
  legacy/harness/golden.sh --check
  ```

## Top risks

| Risk | Mitigation |
|---|---|
| Zoned/sign edge cases (`}` -0, negative truncation) | Unit tests per sign char. Golden after-CBTRN02C has negative TCATBALs. |
| Intermediate precision ≠ COBOL | The exact product (scale 4), then a single DOWN divide to scale 2. The 50 golden amounts confirm it. |
| Abend SYSOUT wording/status drift | Assert against real COBOL output captured by `legacy/tests/cbact04c-abend.sh`. |
| Input padding (36-byte xref lines, CRLF) | Pad/strip at load, same as `cdfiles`. Covered by the stock scenario. |
| Tests depend on repo paths | Resolve repo root from `${basedir}/../..`, and fail clearly if it's missing. |

## Effort (agent)

Build ≈ 15 min, tests + legacy abend script ≈ 10 min, verification ≈ 5 min. **Total ≈ 30 min.**
