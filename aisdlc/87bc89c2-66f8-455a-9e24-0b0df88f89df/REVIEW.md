# CR-1 Review: `CBACT04C` → Java 21

**Verdict: ready to merge.** All golden outputs (TRANSACT, ACCTFILE, SYSOUT, RC) match byte for byte in both
scenarios, both in JUnit and in a live comparison against COBOL. The findings below are should-fix/nit only and
affect paths that are not golden-tested.

## Evidence (run by reviewer)

| Check | Result |
|---|---|
| `mvn -q verify` (modernized/cbact04c) | exit 0. 16 tests: Characterization 8, Golden 2, Interest 2, Zoned 4. 0 failures |
| `legacy/tests/cbact04c_java_vs_cobol.sh` | 8/8 `OK` (stock + after-CBTRN02C × TRANSACT, ACCTFILE, SYSOUT, RC) |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden`, two runs byte-identical |
| `git diff --stat baseline -- legacy/golden legacy/harness .github` | empty |
| pom dependencies | `junit-jupiter` 5.10.2 (test), surefire 3.2.5 and jar plugin only |

The tests are real. `GoldenEquivalenceTest` compares against the frozen files in `legacy/golden/` with `assertArrayEquals`
and does not regenerate expectations. The characterization fixtures are small and asserted by hand. D1 is pinned:
`CharacterizationTest.java:29` asserts the second account is byte-identical to its input. D2 is pinned by a case
where rounding would differ: 79.99 → `0.99`, not `1.00` (`InterestTest.java:27`).

Money handling is sound: `BigDecimal` throughout, one `RoundingMode.DOWN` at scale 2, mod 10^9 / 10^10 wraps, and
no `-0` (`Zoned.java:30-47`).

## Findings

1. **should-fix**: open-error texts differ from legacy, against spec rule 1 ("prints the legacy open-error text").
   `Cbact04c.java:67,73,86` print `…TRANSACTION CATEGORY BALANCE FILE`, `…XREF FILE` and `…ACCOUNT FILE`. Legacy
   prints `ERROR OPENING TRANSACTION CATEGORY BALANCE` (`CBACT04C.cbl:245`), `ERROR OPENING CROSS REF FILE` plus the
   status on the same line (`:263`), and `ERROR OPENING ACCOUNT MASTER FILE` (`:300`). These paths are not
   golden-tested, but log scrapers were the reason for approving SYSOUT parity. Fix: use the exact literals and add
   one test per open error.
2. **should-fix**: output I/O failures do not abend with rc 16. TRANSACT is never "opened" up front. An unwritable
   `DD_TRANSACT`/`DD_ACCTFILE` throws `UncheckedIOException` from `Cbact04c.java:60`, so the JVM exits with 1, after
   all processing and with no `ABENDING PROGRAM`. If the ACCTFILE write fails after TRANSACT was written
   (`:53-54`), the output is partial, which breaks the "no partial success" intent of AC7. Fix: check the output
   paths are writable in the open phase (legacy text `ERROR OPENING TRANSACTION FILE`, `:318`). Write both files
   to temp files, then move them into place, and map `IOException` to `Abend`.
3. **should-fix**: a malformed `CARDDEMO_CURRENT_DATE` (wrong length or non-numeric) throws from `Main.java:32` and
   exits with 1 and a stack trace. Fix: validate it and fail with a clear message, or document the behaviour.
4. **nit**: when the env var is unset, the default clock is UTC (`Main.java:30`), as the spec says. The mainframe
   `CURRENT-DATE` is local time, so timestamps will shift by the container's TZ offset in production. A human should
   confirm the intended zone (see below).
5. **nit**: `Records.load` skips empty lines (`Records.java:27`). That is fine for the golden data, but a blank
   record in the middle of a file would be dropped silently, not padded. Fix: add a comment or a test that pins
   this as matching `cdfiles LOAD`.
6. **nit**: `Cbact04c.java:124-127` uses a `for … break` loop to take the first xref entry. It is clearer as
   `xrefByAcct.ceilingEntry(acctId)` plus a prefix check. `card = null` on `:123` is then redundant.
7. **nit**: there are inline fully-qualified names (`java.io.PrintStream`, `java.nio.file.Path`,
   `java.io.UncheckedIOException`) in `Cbact04c.java:25,60,91`, while the other types are imported. Use imports.
8. **nit**: the D3 counter wrap (999999 → 000000) is implemented (`Cbact04c.java:170`) but not tested. `ZonedTest`
   covers only the amount wrap. Fix: make the counter start value injectable, or add a package-private test.

## What a human should look at before merging

- **Accept or fix findings 1–2 before cut-over.** They only affect failure paths, but operations will see those
  paths. Each is a few lines of change.
- **Time zone for the production clock** (finding 4): should it be UTC or the mainframe's local zone?
- **Defects that are preserved on purpose**, each needing its own CR:
  - D1: the last account is never rewritten. This is the most material: the account misses its nightly interest
    posting and keeps stale cycle totals.
  - D2: interest is truncated, not rounded.
  - D4: the blank group-id means every account uses `DEFAULT` rates.
  - D7: credit balances produce negative interest.
- **R1:** the TRANSACT filler is spaces, matching golden. A real mainframe may leave `x'00'` there. Keep this in
  mind for any parallel run against production output.
