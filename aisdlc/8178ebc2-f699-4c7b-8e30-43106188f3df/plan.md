# CR-3 Plan: CBTRN02C to Java 21

The spec is `spec.md`. Target: `modernized/cbtrn02c/`. Package `com.carddemo.cbtrn02c`. No frameworks.

## Files (in build order)

| # | Path | Purpose |
|---|---|---|
| 1 | `pom.xml` | Java 21, JUnit 5 (`junit-jupiter`), Surefire; jar with `Main-Class` |
| 2 | `src/main/java/.../Zoned.java` | Overpunch decode/encode, left truncation to n digits, −0 handling |
| 3 | `.../Datasets.java` | Load: strip CR, pad/truncate to LRECL. KSDS as `TreeMap<String,String>`, written in key order. Sequential writer. Status codes 35/30/22 via `FileStatusException` |
| 4 | `.../Records.java` | Field offsets for tran, xref, account, tcatbal and reject (§2), as small accessors over a `char[]`/`String`, so untouched bytes survive |
| 5 | `.../Cbtrn02c.java` | The program: open order, loop, validate, post, close, counts, RC. Takes `Clock`, `PrintStream out/err`, `Function<String,Path> dd`. A `run()` method returns the RC (no `System.exit`) so tests can call it |
| 6 | `.../Main.java` | Reads the `DD_*` and `CARDDEMO_CURRENT_DATE` env vars and calls `System.exit(run())` |
| 7 | `src/test/java/...` | Tests below |
| 8 | `src/test/resources/edge/<case>/{in,expected}/` | Fixtures captured from COBOL with `INPUT_DIR` (the probe scenarios from Design) |
| 9 | `Dockerfile`, `run.sh` | `eclipse-temurin:21-jre` image runs the jar; DD paths through env vars |
| 10 | `README.md` | Usage, env vars, defects D1-D9 with code refs |

The tests read golden data from `../../legacy/golden/CBTRN02C` and `../../app/data/ASCII`, resolved from `${project.basedir}` through a Surefire system property. Nothing is copied, so the tests can't drift from the golden data.

## Test strategy

- **Equivalence (AC2-4):** `GoldenEquivalenceTest` copies the stock inputs to a temp dir and runs with `Clock.fixed(2022-07-18T00:00Z)`. It asserts byte equality for `TRANFILE`, `DALYREJS`, `ACCTFILE`, `TCATBALF`, stdout vs `SYSOUT.txt`, and RC vs `RC.txt`.
- **Edge equivalence (D7, D9, −0, abend):** `EdgeScenarioTest` covers 5 COBOL-captured cases: `tcatbal-filler`, `zero-sign`, `temp-overflow`, `bal-overflow`, `dup-tranid`. Build regenerates them with:
  ```sh
  INPUT_DIR=src/test/resources/edge/<case>/in ../../legacy/harness/run.sh CBTRN02C src/test/resources/edge/<case>/expected
  ```
  For `dup-tranid` only SYSOUT and RC are produced, because the harness doesn't unload outputs after an abend.
- **Characterization unit tests:** `ZonedTest`, `ReasonCodeTest` (100/101/102/103/102+103), `OverlimitTest` (D1 + running totals), `TcatbalTest` (create, update, D9), `TruncationTest`, `AbendTest` (35, 22; exact lines, RC 16), `TranfileReplacedTest` (D5), and `PostingTest` (PROC-TS from the clock, FILLER spaces, RC 0 with no rejects).
- **Commands:**
  ```sh
  cd modernized/cbtrn02c && mvn -q verify          # AC1: all tests
  ../../legacy/harness/golden.sh --check            # harness still green (unchanged)
  ```
  Then a manual cross-check: `java -jar target/cbtrn02c.jar` with `DD_*` set to the stock files, then `diff` against golden.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| TCATBAL FILLER on create: the intent said spaces, but golden shows zeros (D9) | Model "last record read" state; covered by golden + the `tcatbal-filler` edge case |
| Sign/zero/truncation encoding differs from GnuCOBOL | Rules confirmed by probe; `zero-sign`, `bal-overflow` and `temp-overflow` fixtures come from real COBOL output |
| Rewriting a field that COBOL left untouched (e.g. −0 TRAN-AMT) | Records are byte buffers; only arithmetic targets are re-encoded |
| Key order / charset | ISO-8859-1 throughout; `TreeMap` natural `String` order = byte order for this charset |
| Clock precision / zone | `CARDDEMO_CURRENT_DATE` parsed as local in `TZ`; hundredths truncation; tests use a fixed clock |
| Abend leaves partial files | Spec §4: flush KSDS state on abend; pinned by `AbendTest` (D4) |
| Path coupling to `legacy/` | Golden path set through a Surefire system property; fails loudly if missing |

## Effort estimate (agent)

| Stage | Minutes |
|---|---|
| Build: code (~600 LOC main), capture fixtures | 25 |
| Test: unit + equivalence tests, fix diffs | 20 |
| **Total** | **~45** |

## Decisions for the approver

1. **D9 (new):** keep the FILLER inheritance like-for-like? *Default: yes, golden depends on it.*
2. **Partial outputs on abend:** flush them (mirrors VSAM durability, keeps D4 visible), or leave the files untouched? *Default: flush.*
3. **Q1/Q2 from the intent** are taken at their defaults: SYSOUT is part of the contract; keep D5.
