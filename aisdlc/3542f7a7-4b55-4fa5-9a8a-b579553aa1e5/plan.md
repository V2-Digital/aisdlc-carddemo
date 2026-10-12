# Plan: CR-1, `CBACT04C` in Java 21

Implements `spec.md`. The work touches only new files under `modernized/cbact04c/` plus this run's artifacts
directory. Nothing under `app/`, `legacy/` or `.github/` changes.

## Files, in build order

| # | Path (under `modernized/cbact04c/`) | Purpose |
|---|---|---|
| 1 | `pom.xml` | Java 21 (`maven.compiler.release=21`) and JUnit Jupiter 5.x as a test dependency. Surefire 3.x, with `repo.root` set to `${project.basedir}/../..` so tests can find `app/` and `legacy/golden/`. The jar manifest's Main-Class is the only plugin config. |
| 2 | `src/main/java/carddemo/cbact04c/Zoned.java` | Overpunch decode/encode and `store(value, intDigits)`: truncate toward 0 to 2 dp, then take cents mod 10^n. |
| 3 | `…/FixedFile.java` | Reads lines: strips CR, pads to LRECL, rejects lines that are too long. Writes LRECL bytes + LF in ISO-8859-1, so the mapping is byte-exact. |
| 4 | `…/Records.java` | Records for TranCatBal, Xref, DisclosureGroup and Account (Account keeps its raw 300 bytes and patches the 3 fields), plus a builder for Transaction. |
| 5 | `…/Abend.java` | Exception that carries the SYSOUT lines already printed. `main` maps it to `ABENDING PROGRAM`, stderr output and exit 16. |
| 6 | `…/InterestCalc.java` | The program logic of spec §3, R0-R7. It takes in-memory inputs (sorted `TreeMap`s), a `PrintStream` for SYSOUT and a `Clock`, and returns the TRANSACT records and the account map. There is no file I/O in here, so tests stay fast. |
| 7 | `…/Main.java` | CLI parsing (`--DD=path`, `--PARM`, `--CURRENT-DATE`). Loads files, runs the logic, then writes the outputs: temp file + atomic move, only on RC 0 (D1). |
| 8 | `src/test/java/…/ZonedTest.java` | All 20 sign characters, `-0`, and truncation/overflow cases. |
| 9 | `…/GoldenEquivalenceTest.java` | Both scenarios run end-to-end through `Main` on temp dirs, with byte comparison of TRANSACT, ACCTFILE, SYSOUT and RC. |
| 10 | `…/CharacterizationTest.java` | F1-F7 and the probe values in spec §5, built from small crafted inputs. |
| 11 | `…/AbendTest.java`, `InputFormatTest.java`, `NoFloatingPointTest.java` | AC 6, AC 7 and AC 8. |
| 12 | `modernized/cbact04c/README.md` | Short: how to build and run, the CLI, and the preserved defects F1-F7 linked to the spec. |

## Test strategy

- **Equivalence (the proof):**
  - `legacy/golden/CBACT04C`: TRANSACT 50, ACCTFILE 50, SYSOUT 152, RC 0.
  - `legacy/golden/CBACT04C-after-CBTRN02C`: inputs TCATBALF and ACCTFILE from `golden/CBTRN02C`. Expected
    TRANSACT 50 (Σ 1,279.16), ACCTFILE 50, SYSOUT 302, RC 0.
  - Both run with PARM `2022071800` and clock `2022071800000000`.
- **Characterization:** expected values come from the COBOL probes run in Design (spec §5), so they reflect
  real legacy behaviour, not assumptions. These are -1.543625 → -1.54, the 1.255 → 1.25 truncation, monthly
  and total overflow, F1 with a single account, F6 double charge, and the F7 message.
- **Cross-check against the COBOL for new cases.** While building, any crafted characterization input whose
  expected value is not already in spec §5 is run through `INPUT_DIR=<dir> legacy/harness/run.sh CBACT04C <out>`
  first. Its output becomes the expected value, which removes guesswork.

Commands:
```sh
cd modernized/cbact04c && mvn -B verify                 # AC 1-8
legacy/harness/golden.sh --check                         # AC 9
git status --porcelain legacy/ .github/ app/             # must be empty
```

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Overpunch errors, especially `-0`/`}` and encoding zero as `{` | Table-driven `ZonedTest` covering all 20 characters, plus golden bytes (the after-posting TCATBAL contains `}` signs). |
| Truncation done as `RoundingMode.DOWN` on the final result only | The spec requires a separate store per statement (monthly, then total, then balance). The overflow tests catch a merged computation. |
| Charset or line-ending drift (platform default, CRLF on write) | ISO-8859-1 and `\n` set explicitly. Golden comparison is done on raw bytes. |
| Key order differs from KSDS order | `TreeMap<String,…>` sorted by the raw key bytes. All keys are ASCII, so byte order equals String order. |
| Timestamp source | `Clock` is injected. `--CURRENT-DATE` uses the same format as the harness. hh = hundredths, followed by `0000`. |
| Tests depend on the repo layout | Paths are resolved from the `repo.root` system property and fail clearly when it is missing. |
| D1 (abend atomicity) gets decided the other way | Writes are isolated in `Main`. Switching to "flush partial outputs" is a small, local change. |

## Effort estimate (agent)

| Step | Minutes |
|---|---|
| POM, Zoned and FixedFile, with tests | 8 |
| Records and InterestCalc | 10 |
| Main and golden equivalence green | 7 |
| Characterization, abend and format tests | 10 |
| README, `golden.sh --check`, final verify | 5 |
| **Total build + test** | **≈ 40** |

## Decisions for the approver

1. **D1, abend atomicity** (spec §6): when the program abends, should it leave no outputs at all (proposed),
   or keep what it wrote before the abend, as z/OS VSAM does?
2. **Rate erratum:** DEFAULT 01/0001 is 15.00 % per year, not 1.50 %. This only corrects the description in
   the intent; no behaviour changes.
3. **Input errors** (duplicate keys, overlong lines, bad zoned data): exit 16 with an `INPUT ERROR` line. The
   legacy load would fail before the program even ran. OK?
