# CR-1 Review: `CBACT04C` → Java 21

**Verdict: ready to merge.** All 9 ACs are met and I re-ran every check myself. Equivalence is proven against the real golden files and against the COBOL itself on the edge cases. None of the findings below is a blocker.

## What I ran

| Command | Result |
|---|---|
| `mvn -q -f modernized/cbact04c test` | exit 0; 6 classes, **17 tests**, 0 failures/errors/skips |
| `legacy/tests/cbact04c-edge.sh` (COBOL vs Java, same inputs) | `ok` e1/e2/e3 (rc 16), grp/neg (rc 0) |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` |
| `java -jar target/cbact04c-1.0.0.jar 2022071800 <stock> <out>` | rc 0; ACCTFILE `cmp`-identical to golden; TRANSACT identical through col 277 (the rest is the timestamp) |
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty, so the protected paths are untouched |

## Correctness vs spec

- **R1–R8** match `CBACT04C.cbl`:
  - Open order is TCATBALF, XREF, DISCGRP, ACCT (`Cbact04c.java:72-82`).
  - The rewrite happens only on an account break, so F1 is preserved (`:107-110`). There is no rewrite after the loop (`:154`).
  - The rate is used only when ≠ 0, and the suffix is incremented only when a record is written (`:144-151`).
- **Money:**
  - All arithmetic is `BigDecimal` and there is no `double` anywhere.
  - Interest is `divide(1200, 2, DOWN)`, which truncates toward zero (F2, F9).
  - Amounts wrap at 11 unscaled digits, i.e. S9(9)V99. Balances wrap at 12 on encode, i.e. S9(10)V99.
  - A result of −0 is written as `{`, which matches the COBOL (`InterestTest:241`, edge case `neg`).
- **Tests are real, not tautological:**
  - `GoldenTest` compares whole files byte for byte against `legacy/golden/*` (TRANSACT, ACCTFILE, SYSOUT), so expected values were not regenerated.
  - `DefectTest.F1` checks row 50 against the *input* file and also checks that row 49 *did* change.
  - The edge script diffs Java against a live GnuCOBOL run.

## Findings

1. **should-fix: `Cbact04c.java:45-46`.** An `IOException` escapes as `UncheckedIOException`, so the JVM exits 1 with a stack trace instead of 16. An unwritable or missing `OUT_DIR` would do this. In the COBOL that case is `ERROR OPENING TRANSACTION FILE` + abend (`CBACT04C.cbl` 0400-TRANFILE-OPEN). *Suggestion:* check `outDir` up front. Print `ERROR OPENING TRANSACTION FILE`, then status, then `abend()`. This matters for container ops, because exit 16 is the abend contract.
2. **should-fix: E4 (missing input file) has no test** (`AbendTest`). The spec lists E4 but AC7 only covers E1–E3, so this is not an AC gap. The edge script cannot cover it either: when I removed `DISCGRP.txt` from `INPUT_DIR`, the harness fell back to stock data and the COBOL returned rc 0. The Java does print the expected message (`ERROR OPENING DALY REJECTS FILE` / `NNNN0035`, rc 16). *Suggestion:* add one JUnit case per DD.
3. **should-fix: `InterestTest.java:247-252`, the overflow test is mostly self-referential.** It compares `Zoned.wrap(x)` with `x.mod(10^11)`, which restates the implementation. It covers only a positive value and does not go through `run()`. *Suggestion:* add a negative wrap case (e.g. `-1000000000.01` → `0000000000J`) and a balance wrap at width 12. Ideally, also run one end-to-end overflow case through the edge script so the COBOL confirms it.
4. **nit: `Zoned.java:215-217`.** `encode` assumes scale 2 because it uses `unscaledValue()`, but it does not enforce that. Every caller currently passes scale 2. *Suggestion:* add `v = v.setScale(2)` (no rounding needed) or an assert.
5. **nit: `Zoned.java:205`.** Only leading and trailing spaces are tolerated. A field with embedded spaces or other garbage throws `NumberFormatException`, whereas the COBOL would carry on with garbage. This is acceptable for clean data. Just be aware that a bad input now fails fast instead of silently.
6. **nit: `Cbact04c.java:73-80`.** The XREF open duplicates the `open()` helper only because its message has the status appended (`...FILE35`). *Suggestion:* pass the full message into `open()` to remove the special case.
7. **nit: `GoldenTest.java:187`.** The `assertEquals` arguments are reversed (actual, expected), so a failure message would read backwards.
8. **nit: `legacy/tests/cbact04c-edge.sh:26`.** The comment says "mask timestamps", but on that line it only drops blank SYSOUT lines. The masking happens at line 29.

## What a human should look at before merging

- **F1 decision.** The last account's interest (acct `00000000050`, 18.77) is posted as a transaction but never added to the balance. This is preserved as the CR requires. Confirm that CR-3 is raised to fix it.
- **Finding 1 (exit code on I/O failure).** Decide whether it should be fixed before this goes into a container schedule, or accepted for now.
- **Timestamp source.** `Main` uses `Clock.systemDefaultZone()`, so the container's TZ must match the mainframe's local time, or the TRANSACT timestamps will shift.
- **`legacy/tests/` is a new top-level test location.** Confirm whether CI should run `cbact04c-edge.sh` (it needs GnuCOBOL plus a JDK). `.github/` was deliberately left unchanged.
