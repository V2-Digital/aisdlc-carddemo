# CR-1 Plan — `CBACT04C` in Java 21

Implements [spec.md](spec.md). Target is `modernized/cbact04c/`, package `carddemo.cbact04c`.

## Files (in order)

1. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter 5.x (test scope), Surefire 3.x, and an
   executable jar via the `maven-jar-plugin` manifest. No other dependencies.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch decode and encode for `S9(n)V99`, plus mod-10^n
   truncation.
3. `.../Records.java`: fixed-width read, pad and write (ISO-8859-1, LF). Field slices per the copybooks.
4. `.../IntCalc.java`: the rules from spec §1–8, with the clock and the PARM injected. `Abend` is an
   exception that carries the SYSOUT lines.
5. `.../Main.java`: CLI (`--parm --in --out --now`). Maps the result to stdout/stderr and the exit code.
6. Tests in `src/test/java/carddemo/cbact04c/`: `ZonedTest`, `InterestTest`, `RulesTest`, `AbendTest`,
   `CharacterizationTest`, `GoldenEquivalenceTest`. The golden test reads `../../legacy/golden` and
   `../../app/data/ASCII` directly, so nothing is copied.
7. `legacy/tests/cbact04c-edge.sh`: builds a crafted `INPUT_DIR` (negative balance, a group-specific hit,
   rate 0, fractional remainder) and runs `legacy/harness/run.sh CBACT04C` and the Java jar on it. Diffs
   TRANSACT, ACCTFILE and SYSOUT. It also runs the abend cases (missing DEFAULT, account, xref) and compares
   RC and SYSOUT.
8. `modernized/cbact04c/README.md`: how to run it, and the list of preserved defects (a short list that
   links to the spec).

## Test strategy

- **Equivalence:** goldens `CBACT04C` and `CBACT04C-after-CBTRN02C`. TRANSACT, ACCTFILE and SYSOUT must match
  byte for byte, with RC 0 and `--now 2022071800000000`.
- **Differential:** the new edge script covers what the goldens do not: negative balances, group hits and
  abends. The COBOL output is the oracle, so no expected values are hand-derived.
- **Unit and characterization:** the tests listed in the spec's acceptance table, pinning D1 to D4.

```sh
cd modernized/cbact04c && mvn -q verify
legacy/tests/cbact04c-edge.sh                 # COBOL vs Java on crafted inputs
legacy/harness/golden.sh --check              # harness/goldens untouched
```

## Risks

| # | Risk | Mitigation |
|---|---|---|
| 1 | The sign of a truncated −0 result (e.g. −0.01 × 15/1200) could be `{` or `}` | Settled by the edge script against GnuCOBOL. Java matches whatever COBOL emits |
| 2 | The precision of COBOL's intermediate in `bal × rate / 1200` | Exact BigDecimal, truncated once. Fractional cases are diff-tested against COBOL |
| 3 | The SYSOUT wording or order of abend messages drifts | Strings are copied from `CBACT04C.cbl`. The edge script diffs the abend SYSOUT |
| 4 | Input quirks (CRLF, 36-byte xref lines) | Normalise on load as `run.sh` does. Stock golden test covers it |
| 5 | Edge cases in the overpunch codec (`}` input, zero output) | `ZonedTest` round-trips every sign char and checks against golden bytes |

## Effort

Build about 15 min, test and differential about 10 min. **Total about 25 min.**
