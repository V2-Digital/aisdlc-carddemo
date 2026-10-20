# CR-1 Review — `CBACT04C` → Java 21

**Verdict: ready to merge.** Java output is byte-identical to the COBOL golden outputs in both scenarios and to all 8 COBOL-generated edge fixtures. No blockers. The findings below are small test-quality and CLI fixes.

## What I ran (reviewer session)
| Command | Result |
|---|---|
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | **empty**: the judges are untouched |
| `cd modernized/cbact04c && mvn -q verify` | exit 0 (16 tests) |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden`, two runs byte-identical |
| `legacy/tests/cbact04c-edge.sh --check` | PASS ×8 (empty, missing-acct/-default/-xref, negative, negrate, negzero, overflow) |
| CLI smoke: `CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar 2022071800 --in <stock> --out <o>` | rc 0. TRANSACT, ACCTFILE and stdout `cmp`-identical to `legacy/golden/CBACT04C` |
| CLI with `DISCGRP.txt` removed | `ERROR OPENING DALY REJECTS FILE`, `NNNN0035`, `ABENDING PROGRAM`, stderr `CEE3ABD: ABEND U0999`, rc 16 |

## Correctness against the spec
- **Money:** `BigDecimal` only, with no `double` anywhere. `divide(1200, 2, DOWN)` gives truncation toward zero (F2). `remainder(10^n)` gives sign-preserving high-order truncation (F5). The `overflow` fixture is real COBOL output (`3333249999A` tran, balance `03333249999{`), and Java matches it.
- **Sign:** a -0 result encodes as `{`, because `signum()` of -0.00 is 0. The `negzero` fixture confirms COBOL does the same. Negatives use `}J-R`, confirmed by the `negative` and `negrate` fixtures.
- **Control flow:** R4 order (ACCT, then XREF), DEFAULT fallback, the rate≠0 gate, F1 (no update after EOF) and outputs written only on RC 0 all match `CBACT04C.cbl:188-222`.
- **The tests are real.** Golden tests read `legacy/golden` directly. Edge expectations come from `run.sh` on the real COBOL, and CI re-checks them (`ci.yml:39` runs `legacy/tests/*.sh`). Nothing is regenerated from Java.

## Findings
1. **should-fix**: tautological or library-only assertions in `CharacterizationTest.java`:
   - `:110-111` compare string constants with themselves.
   - `:82` duplicates `:83`.
   - `:89-90` (`defectF2_F5_arithmeticUnits`) re-states the formula against `BigDecimal` and never calls production code.

   Suggestion: delete `:110-111` and `:82`. Have `:89` exercise `Cbact04c` through a tiny fixture, or remove it, since the `negative`/`overflow` fixtures already prove this behaviour.
2. **should-fix**: `Main.java:261-262`. `--out` with no value throws `ArrayIndexOutOfBoundsException` with a stack trace, and the exit code is 1. Suggestion: bounds-check and fall through to the usage message, exit 2.
3. **should-fix**: E4 (missing input file) has no test. I verified it by hand (above). The `XREFFILE` message is hard-coded as `"ERROR OPENING CROSS REF FILE35"` (`Cbact04c.java:68`). COBOL prints the actual status there (`CBACT04C.cbl:263`), so other statuses would differ. Suggestion: add an `EdgeFixtureTest` case with a missing DD, or a unit test.
4. **nit**: `Cbact04c.java:150-155`. A non-numeric overpunch abends with no display line before `ABENDING PROGRAM`. The spec allows this ("abend"), but an operator gets no clue which record caused it. Suggestion: show the field or record first. It is unreachable on golden data.
5. **nit**: `CharacterizationTest.java:63`. `assertTrue(!a.equals(b))` should be `assertNotEquals`.
6. **nit**: the plan said package `carddemo.cbact04c`, a separate `Records.java`, and a `--dd NAME=path` CLI override. The build uses `com.v2.carddemo.cbact04c`, has no `Records.java`, and no `--dd`. These are harmless deviations, but the plan/README should say so.
7. **nit**: `Datasets.java:218`. `replace("\r", "")` is redundant, because `readAllLines` already splits on CRLF. Over-long lines are silently truncated to LRECL. Consider abending instead.

## What a human should look at before merging
- **F1 is a billing defect, preserved on purpose.** The last account's interest is written to TRANSACT but never posted to ACCTFILE (acct `00000000050`, 18.77). Raise the follow-up CR before this goes live.
- **F7 (duplicate XREF → first in file order) is unverified against COBOL.** My quick duplicate-card fixture failed to load in the harness (`CDFILES: LOAD XREFFILE file status 22`), so neither side has evidence. The sample data has no duplicates.
- **Fixture location:** the fixtures are in `legacy/tests/cbact04c-edge/`, which is the plan's stated alternative, not the spec's `modernized/…/edge/`. Accept this or ask for a move. CI already runs them.
- **R2/R3 still stand:** trailing bytes are spaces (z/OS may use `x'00'`). Operations needs a VSAM unload/load step around the Java job, and a mainframe parallel run is recommended.
- CI runs `mvn test`, not `verify`. That is equivalent today because there is no `verify`-phase plugin.
