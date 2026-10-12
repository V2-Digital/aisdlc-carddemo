# CR-1 Plan — `CBACT04C` → `modernized/cbact04c/`

## Files (build in this order)

| # | Path | Content |
|---|---|---|
| 1 | `modernized/cbact04c/pom.xml` | Java 21, `junit-jupiter` (test scope) and `maven-surefire-plugin`. No other dependencies. |
| 2 | `src/main/java/cbact04c/Zoned.java` | Overpunch decode/encode for `S9(n)V99` → `BigDecimal`; COBOL truncate and wrap helpers (`trunc2`, `wrap(int digits)`) |
| 3 | `.../FixedText.java` | Read lines (strip CR, pad to LRECL, reject over-length) and write fixed-width + LF; ISO-8859-1 |
| 4 | `.../Records.java` | Field offsets per spec §2 (Account, TcatBal, Xref, DiscGrp) as small records and views. Accounts keep the raw 300 bytes and patch cols 13-24 and 79-102 in place. |
| 5 | `.../Ksds.java` | `TreeMap` load with a duplicate-key check (exit 3); primary key plus the XREF AIX map |
| 6 | `.../Abend.java` | Unchecked exception carrying the SYSOUT lines; `Main` maps it to stderr `CEE3ABD…` and exit 16 |
| 7 | `.../InterestCalc.java` | The main loop of spec §3, a line-for-line mirror of the paragraphs (comments cite `CBACT04C.cbl` line numbers). Takes a `Clock`-like supplier and a `PrintStream` for SYSOUT. |
| 8 | `.../Main.java` | CLI parsing (spec §1); clock from `--clock` / `COB_CURRENT_DATE` / system |
| 9 | `src/test/java/cbact04c/*Test.java` | Tests listed below |
| 10 | `src/test/resources/edge/` | Crafted edge inputs and the COBOL outputs captured from `run.sh` |
| 11 | `modernized/cbact04c/README.md` | How to run; links the defects list |
| 12 | `aisdlc/<run>/` build and test notes | Stage hand-off |

The golden tests read `../../legacy/golden` and `../../app/data/ASCII` in place, resolved from `basedir`, so nothing is copied.

## Test strategy

**Equivalence: the goal.** `GoldenEquivalenceTest` runs `Main` in-process into a `@TempDir` and compares bytes:

- `CBACT04C`: TRANSACT 50, ACCTFILE 50, SYSOUT 152, RC 0.
- `CBACT04C-after-CBTRN02C`: TCATBALF/ACCTFILE from `golden/CBTRN02C`; 50 / 50 / 302, RC 0.

**New tests:**

| Class | Covers |
|---|---|
| `ZonedTest` | AC 8: all 20 overpunch chars, ±0 → `{`, max/min, malformed digit |
| `InterestTest` | AC 5, D2: acct 2 → 29.24; −10.01@15 → −0.12; 999,999,999.99 × 9999.99 → 333,324,999.91; a scan of `src/main` that fails on `double`/`float` |
| `DefectCharacterizationTest` | AC 6: D1 single-account and last-account unchanged; D3 blank group → DEFAULT plus 2 miss lines; D4 suffix 999,999 → `000000`, total/balance wrap |
| `ErrorPathTest` | AC 7: E1–E6 SYSOUT lines and exit 16; E7 empty input; E12 duplicate key → 3 |
| `DifferentialEdgeTest` | R2: negative balance, group hit (`A000000000` set in cols 113-122), zero rate, two categories, compared against outputs captured from COBOL |

**Capturing the edge expectations** (done once in Build; read-only use of the harness):

```sh
INPUT_DIR=modernized/cbact04c/src/test/resources/edge/in \
  legacy/harness/run.sh CBACT04C modernized/cbact04c/src/test/resources/edge/expected
```

Build also runs a throwaway `cobc` probe in `/tmp` to confirm the E1/E2/E6 status codes. Those paths cannot be reached through `run.sh`.

**Commands (AC 1, 9):**

```sh
mvn -q -f modernized/cbact04c test
legacy/harness/golden.sh --check
git diff --stat baseline -- legacy/golden legacy/harness .github   # must be empty
```

## Risks

| Risk | Mitigation |
|---|---|
| Arithmetic differs from COBOL (rounding, sign of zero, overflow) | `BigDecimal` with `RoundingMode.DOWN` and explicit mod-10^n wrap. Semantics already probed on GnuCOBOL 3.2 (spec §3). Differential edge test. |
| Byte drift: padding, CRLF, charset, trailing newline | ISO-8859-1 bytes; byte-level asserts with the first-diff offset in the failure message |
| Key order differs from VSAM/BDB | Byte-wise `String` compare on ASCII keys, same as BDB memcmp. Golden ACCTFILE/TRANSACT order checks it. |
| The D1 "fix" sneaks in | Golden after-CBTRN02C fails if acct 50 changes; an explicit D1 test |
| Clock nondeterminism | Clock injected; the tests always freeze it |
| Error paths drift from the legacy text | Messages copied verbatim from the source. E3 verified on the harness; the other codes confirmed by probe. |
| Text-only proof (R1) | Out of scope; recorded for Deploy and platform |

## Effort (agent)

| Step | Minutes |
|---|---|
| Build: files 1–8 | ~15 |
| Tests 9–10, including edge capture | ~15 |
| Run, fix, `golden.sh --check`, notes | ~10 |
| **Total** | **~40** |

## Decisions for the approver

- **Q1:** SYSOUT and exit code parity. Assumed **yes**, as in the intent default.
- **Q2:** D1 and D2 to become separate CRs. Assumed **yes**.
- **New:** E9 (abend on non-numeric zoned data) is a deliberate, invalid-data-only deviation from GnuCOBOL. GnuCOBOL computes on garbage. Approve, or ask for garbage-in parity.
