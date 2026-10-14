# CR-1 Review — `CBACT04C` → Java 21

**Verdict: ready to merge.** Both golden scenarios are byte-identical, real COBOL agrees on the edge and abend
cases, and the protected paths are untouched. The findings below are follow-ups. None of them blocks the merge.

## What I ran (this session)
| Command | Result |
|---|---|
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty: untouched |
| `cd modernized/cbact04c && mvn -q test` | exit 0 (17 tests) |
| `legacy/tests/cbact04c_edge.sh` | exit 0: `main`, `nodefault`, `noacct`, `noxref` COBOL vs Java `diff -r` clean |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` |

## Correctness against spec
- R1–R7 match `CBACT04C.cbl`. Totals are reset on an account break, the DEFAULT fallback lines are there, and
  `WS-TRANID-SUFFIX` 9(6) wraps (`% 1_000_000`). Credit and debit are zeroed, and all other bytes are kept on
  REWRITE.
- D1 is preserved the right way. There is no update after the loop (`Cbact04c.java:119`), and
  `DefectCharacterizationTest.d1_…` pins it.
- Money handling: `BigDecimal` throughout. `divide(…, 2, DOWN)` truncates toward zero (D3/D5). S9(9)V99 and
  S9(10)V99 wrap through `ZonedDecimal.truncate`/`encode` (D6). A zero result is always written as `{`.
- The tests are real. `GoldenEquivalenceTest` compares against the committed `legacy/golden/` bytes, not against
  expectations it generates itself. The edge script runs the real GnuCOBOL through `run.sh` with `INPUT_DIR`.

## Findings
1. **should-fix**: overflow of the monthly interest (D6) is never checked against COBOL.
   `InterestTest.java:16-22` derives its expected value from the same formula the code uses, so it is close to a
   tautology. None of the edge rows overflows S9(9)V99: the largest is 999999999.99 × 15.00 / 1200 ≈ 12.5M.
   *Suggestion:* add an edge `TCATBALF` row with balance 999999999.99 on a 9999.99 rate (e.g.
   `DEFAULT 02 0001`), so COBOL decides the wrap. GnuCOBOL's behaviour on COMPUTE without SIZE ERROR depends on
   the dialect.
2. **should-fix**: the open-failure path (D7, `Cbact04c.java:54-57`, `:161-165`) is never checked against
   COBOL. `FILE STATUS IS: NNNN0035` and the `35` suffix on the XREF text are guesses (build.md says so). The
   harness falls back to the sample file when a DD is missing from `INPUT_DIR`, so the edge script cannot reach
   this path. *Suggestion:* add one JUnit test that pins the current output (e.g. `discgrp == null` →
   `ERROR OPENING DALY REJECTS FILE`, RC 16) and record the status code as unproven in the README.
3. **should-fix**: bad numeric data crashes the job. `ZonedDecimal.decode` (`ZonedDecimal.java:28-30`) throws
   on spaces or a non-digit and is not caught. Java then exits with a stack trace and no `RC.txt`; COBOL would
   carry on with garbage. Golden data never does this. *Suggestion:* treat it as an abend (RC 16 plus SYSOUT
   line), or at least write it down as a known divergence.
4. **nit**: D8 (TRAN-ID repeats on rerun) has no explicit test, although the spec says every defect is pinned.
   Two runs with the same PARM should assert equal IDs.
5. **nit**: `Records.TRAN_LEN` (`Records.java:11`) is never used. Use it in `TranRecordTest` or delete it.
6. **nit**: `Cbact04c.java:101`, `rec.substring(11, 13) + rec.substring(13, 17)` is just `rec.substring(11, 17)`.
7. **nit**: `Fx.acct` (`Fx.java:24-25`), `" ".repeat(12 * 3 + 30).substring(0, 54)` should simply be
   `" ".repeat(54)`. The offsets are correct: credit 78, debit 90, group 112.
8. **nit**: the README links `aisdlc/*/intent.md` as a glob. Link the run directory, and list D1–D8 inline as
   plan item 8 asked.

## What a human should look at before merging
- **D1 money impact.** Every night the last account is charged interest in `TRANSACT`, but its balance and
  cycle totals are never updated. It is preserved here as instructed. Raise the separate CR (intent Q3).
- **Abend semantics differ from z/OS.** On abend, Java writes only SYSOUT and RC. On the mainframe, REWRITEs
  already done to the VSAM `ACCTFILE` stick, and `TRANSACT` may be partly written. The spec chose to follow the
  harness. Operations need a restart/rerun procedure before cut-over.
- **Unproven paths:** monthly-interest overflow (#1), open-failure texts and status (#2), and multiple cards per
  account (Java takes the first by card number, no data covers it). D2 also means non-DEFAULT rates only ever
  run in synthetic tests.
- **D3 truncation** and **D5 negative interest** need a Finance decision, outside this CR.
