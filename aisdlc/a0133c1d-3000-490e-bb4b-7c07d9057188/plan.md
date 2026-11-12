# CR-1 Plan: `CBACT04C` in Java 21

Spec: [spec.md](spec.md). Intent: [intent.md](intent.md).

## Files (in order)

1. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter 5.x, Surefire. The jar's main class is `Cbact04c`. No other dependencies.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch decode/encode and mod-10^n truncation.
3. `.../FixedFile.java`: reads records (strips CR, pads to LRECL) and writes them (LRECL + LF, ISO-8859-1).
4. `.../Cbact04c.java`: rules 1–9. Constructor takes `(Map<String,Path> dds, String parm, Clock clock, PrintStream sysout)` and `run()` returns an RC. `Abend` is an exception that maps to 16. `main` reads the `DD_*` and `CARDDEMO_CURRENT_DATE` env vars.
5. `src/test/java/...`: `ZonedTest`, `GoldenEquivalenceTest`, `DefectCharacterizationTest`, `AbendTest`, `ClockInjectionTest`. Golden paths resolve to `../../legacy/golden`. Stock inputs come from `../../app/data/ASCII`.
6. `legacy/tests/cbact04c_negzero.sh`: runs the harness with a crafted TCATBALF (a small negative balance and a non-zero rate). It asserts the COBOL output sign of a truncated −0.00 amount and records it. The Java test copies that result.
7. `modernized/cbact04c/README.md`: how to run it, plus the env contract (short).

## Test strategy

- **Equivalence:** golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Byte compare of TRANSACT, ACCTFILE and SYSOUT, plus RC. Clock `2022071800000000`, PARM `2022071800`.
- **Characterization:** F1, F2, F3 and the DEFAULT fallback/abend, using small in-memory fixtures in `@TempDir`.
- **Legacy check:** the new script above confirms the zero-sign rule against real COBOL. No golden changes.

Commands:
```sh
cd modernized/cbact04c && mvn -q verify
legacy/tests/cbact04c_negzero.sh
legacy/harness/golden.sh --check
git diff --exit-code -- legacy/golden legacy/harness .github
```

## Risks

| Risk | Mitigation |
|---|---|
| Sign of a zero/negative result differs from COBOL (`{` vs `}`) | Legacy test 6 fixes the truth. `ZonedTest` covers every sign digit. |
| SYSOUT drift (DISPLAY of the raw record, error-text spacing) | Byte-compare the full golden SYSOUT. Copy the error strings verbatim from the `.cbl`. |
| Truncation versus BigDecimal scale | Exact multiply and divide (`MathContext.UNLIMITED` then `setScale(2, DOWN)`). The F2 test pins acct 48. |
| Input quirks (36-byte xref, CRLF) | Pad and strip in `FixedFile`. Stock inputs go through the equivalence test. |
| Key ordering of TCATBAL and ACCTFILE | Sort by raw key bytes (TreeMap). Golden ACCTFILE order is checked. |

## Effort

Build about 15 min, tests and legacy check about 10 min. **About 25 min in total.**

## Decisions for the approver

Confirm the intent Q1–Q3 defaults: canonical text I/O through `DD_*` env vars with a separate `DD_ACCTFILE_OUT`, SYSOUT/RC parity, and F1/F2 preserved and raised as follow-up CRs.
