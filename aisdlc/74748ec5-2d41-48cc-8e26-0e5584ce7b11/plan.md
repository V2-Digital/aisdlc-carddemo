# CR-1 Plan: `CBACT04C` in Java 21

Run `74748ec5-2d41-48cc-8e26-0e5584ce7b11` · Stage: Design · Behaviour: [spec.md](spec.md)

## Files, in build order (`modernized/cbact04c/`)

1. `pom.xml`: Java 21, `junit-jupiter` (test scope), Surefire, and a jar with `Main-Class`. Nothing else.
2. `src/main/java/carddemo/cbact04c/Overpunch.java`: zoned-decimal decode and encode (`BigDecimal`, scale,
   length) with modulo truncation.
3. `.../RecordFiles.java`: reads lines (strip CR, pad to LRECL) and writes LRECL+LF records. Also loads the
   keyed files into a `TreeMap`.
4. `.../Clock.java`: parses `YYYYMMDDHHMMSSss` and formats it as a DB2 timestamp.
5. `.../InterestBatch.java`: the main loop (rules 2–8), with output written through a `Consumer<String>`
   sysout. Fatal paths throw `AbendException(messages)`.
6. `.../Main.java`: parses the CLI arguments, runs the batch, maps an abend to stderr plus exit 16, and
   otherwise exits 0.
7. Tests in `src/test/java/carddemo/cbact04c/`: `OverpunchTest`, `InterestTest`, `InterestBatchTest`,
   `DefectCharacterizationTest`, `AbendTest`, `GoldenEquivalenceTest`.
8. `modernized/cbact04c/README.md`: how to build and run, plus the list of preserved defects (D1–D7) with links
   to spec.md.

## Test strategy

- **Equivalence, which is the proof.** `GoldenEquivalenceTest` runs the batch in-process with PARM `2022071800`
  and clock `2022071800000000`, then compares bytes with `legacy/golden/` for:
  - `CBACT04C`, with inputs from `app/data/ASCII/{tcatbal,cardxref,acctdata,discgrp}.txt`;
  - `CBACT04C-after-CBTRN02C`, with ACCTFILE and TCATBALF from `legacy/golden/CBTRN02C/`.

  The test compares TRANSACT.txt, ACCTFILE.txt, the SYSOUT lines and RC. Paths are resolved from the repo root
  (`../..`, overridable with `-Drepo.root`).
- **Characterization.** Covers D1 (account 50 unchanged, tran 50 = 18.77) and D2 (1164.87 @ 15.00 → 14.56).
  Each test is named after its defect id.
- **Unit tests.** Cover the 20 overpunch characters, the DEFAULT fallback, the rate-0 skip, negative and
  negative-zero amounts, and an empty TCATBALF.
- **Abend.** Small in-memory fixtures for a missing account, a missing xref and a missing DEFAULT rate. Each
  asserts exit 16 and the exact legacy lines.
- **CLI smoke test.** Run the jar against the after-CBTRN02C inputs and `diff` with the golden files.
- **No new COBOL tests.** The legacy behaviour is already pinned by the golden scenarios.

Commands:

```sh
mvn -q -f modernized/cbact04c verify
legacy/harness/golden.sh --check
java -jar modernized/cbact04c/target/cbact04c.jar --parm 2022071800 --clock 2022071800000000 --out /tmp/j \
  TCATBALF=legacy/golden/CBTRN02C/TCATBALF.txt ACCTFILE=legacy/golden/CBTRN02C/ACCTFILE.txt \
  XREFFILE=app/data/ASCII/cardxref.txt DISCGRP=app/data/ASCII/discgrp.txt
for f in TRANSACT ACCTFILE; do diff /tmp/j/$f.txt legacy/golden/CBACT04C-after-CBTRN02C/$f.txt; done
git diff --stat baseline -- legacy .github   # must be empty
```

## Top risks

| Risk | Mitigation |
|---|---|
| The stock scenario is all zeros, so it proves little about arithmetic | The after-CBTRN02C scenario and the unit tests cover non-zero and negative amounts |
| Reading the 36-byte xref lines or CRLF inputs wrongly shifts fields | Pad/strip on load, with a unit test on `cardxref.txt` |
| A "helpful" fix of D1 or rounding slips in | Characterization tests fail if D1 or D2 change |
| SYSOUT drift (trailing spaces, line order) | Compare line by line against golden SYSOUT, including the raw 50-byte record lines |
| Encoding: non-ASCII bytes in the data | I/O uses ISO-8859-1, so each byte maps to one char and back |

## Effort (agent)

Build ~15 min, tests and verification ~10 min. **Total ~25 min.**
