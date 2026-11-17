# CR-1 Plan: `CBACT04C` → `modernized/cbact04c/`

Implements [spec.md](spec.md). No changes outside `modernized/cbact04c/` and `legacy/tests/`.

## Files (in order)

1. `modernized/cbact04c/pom.xml`: Java 21, `junit-jupiter` (test scope) and `maven-surefire-plugin` only. Builds a
   runnable jar (`Main-Class`) with the standard jar plugin. `golden.dir` system property = `../../legacy/golden`.
2. `src/main/java/carddemo/cbact04c/Zoned.java`: overpunch decode/encode to `BigDecimal` and back, with mod-10^n
   truncation.
3. `…/Records.java`: field offsets from the spec table; fixed-width load (CR strip, pad) and write.
4. `…/Cbact04c.java`: rules 1–8. Uses an `Abend` exception → rc 16. `Config` record holds PARM, DD paths, `Clock`,
   SYSOUT.
5. `…/Main.java`: reads env `DD_*` and `CARDDEMO_CURRENT_DATE`, sets `System.exit(rc)`.
6. `src/test/java/…/ZonedTest`, `InterestTest`, `CharacterizationTest`, `GoldenEquivalenceTest`. Each test runs in a
   `@TempDir` with small synthetic fixtures built inline.
7. `legacy/tests/cbact04c_java_vs_cobol.sh`: runs `harness/run.sh CBACT04C` and the Java jar on the same inputs (stock
   and `INPUT_DIR=legacy/golden/CBTRN02C`), then `cmp`s TRANSACT, ACCTFILE, SYSOUT and RC. This is the live COBOL
   cross-check, beyond the frozen golden files.
8. `modernized/cbact04c/README.md`: how to run, the env vars, and a list of preserved defects pointing to intent.md.

## Test strategy

- **Equivalence:** golden scenarios `CBACT04C` (stock inputs from `app/data/ASCII/`: tcatbal, cardxref, acctdata,
  discgrp) and `CBACT04C-after-CBTRN02C` (ACCTFILE + TCATBALF from `legacy/golden/CBTRN02C/`). Compare TRANSACT,
  ACCTFILE and SYSOUT byte for byte, and rc against `RC.txt`. Clock `2022071800000000`, PARM `2022071800`.
- **Characterization:** D1 last account, D2 truncation, D3 wrap, D7 negative, zero rate, DEFAULT fallback, and the
  three abend paths (spec acceptance table).
- **Commands:**
  ```sh
  cd modernized/cbact04c && mvn -q verify
  legacy/tests/cbact04c_java_vs_cobol.sh
  legacy/harness/golden.sh --check
  git diff --stat baseline -- legacy/golden legacy/harness .github   # must be empty
  ```

## Risks

| Risk | Mitigation |
|---|---|
| Sign or zero encoding mismatch (`}` vs `{` for zero results) | `ZonedTest` covers every sign char. Golden data has negative balances (`P`, `Q`, `}` in SYSOUT) |
| Intermediate precision differs from COBOL `COMPUTE` | Exact `BigDecimal` multiply then divide, one `DOWN` at the end. Pinned by 50/50 golden amounts |
| SYSOUT parity: the DISPLAY of the raw record, or line order with stderr | Only stdout goes into SYSOUT. The CEE3ABD text goes to stderr, as the harness stub does |
| Input loading differs from `cdfiles` (CRLF, short lines) | Same strip/pad rules. The stock scenario exercises both (tcatbal CRLF, cardxref 36 bytes) |
| Duplicate xref acct-ids are untested (R4) | First match in card-num order; documented in README |

## Effort (agent)

Build ≈ 15 min, tests + golden/cross-check ≈ 10 min. **Total ≈ 25 min.**
