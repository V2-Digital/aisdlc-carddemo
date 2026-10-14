# CR-1 Review: `modernized/cbact04c/`

**Verdict: ready to merge.** The Java output is byte-identical to golden on both scenarios (TRANSACT, ACCTFILE, SYSOUT, RC), and the money, truncation and sign rules match the COBOL. None of the findings below is a blocker.

## What I ran

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | exit 0. 22 tests: Golden 3, Program 16, Zoned 3. 0 failures |
| `legacy/tests/cbact04c_java_equiv.sh` | 8/8 `OK` (both scenarios × TRANSACT/ACCTFILE/SYSOUT/RC) |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` |
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty, so these are untouched |

## Correctness checks (pass)

- **The equivalence tests are real.** `GoldenEquivalenceTest` runs the Java program on the copied inputs and compares the bytes with the `legacy/golden/` files in the repo. The inputs are wired the same way as `golden.sh` (scenario 2 uses `golden/CBTRN02C` ACCTFILE+TCATBALF). No expectations are regenerated.
- **Interest:** `bal × rate / 1200` is computed at scale 10 with `DOWN`, then truncated to 2 decimals with `DOWN`. This equals exact truncation toward zero, because the exact quotient has a finite expansion (×1/1200 adds at most 4 more decimals to a scale-4 product). `ROUNDED` is not used, which matches `CBACT04C.cbl:464-465`.
- **Zoned encoding:** `-0` encodes as `{`. Negative values use `}`/`J`-`R`. High-order digits are dropped mod 10^n. Every overpunch character round-trips (`ZonedDecimalTest`).
- **D1:** after EOF there is no rewrite (`Cbact04c.java:180`). `lastAccountNotRewritten_D1` pins this on the Java side.
- **ACCTFILE offsets:** CYC-CREDIT 78, CYC-DEBIT 90, GROUP-ID 112 (`Cbact04c.java:25-27`) are **correct** per `CVACT01Y.cpy`. See finding 1: the spec is the part that is wrong.
- **Order:** `START` is printed before the opens, as in COBOL `:181-186`. The spec's rule 1/2 order is reversed, but the code follows COBOL.

## Findings

1. **should-fix (doc). The spec's offset table is wrong:** `spec.md:24-25`. It says cycle credit/debit are at 76/88 and the group id at 110. Per the copybook they are 78/90 and 112. The code is right; golden would fail otherwise. Fix the spec so CR-2 doesn't copy the error.
2. **should-fix. The equivalence script can test a stale jar:** `legacy/tests/cbact04c_java_equiv.sh:6` builds only `[ -f "$JAR" ] ||`. If someone edits the source and doesn't rebuild, the script still says OK. Always run `mvn -q package -DskipTests`, or depend on `mvn verify` first in CI.
3. **should-fix. Some I/O errors don't exit 16:** `Cbact04c.java:76-77` and `:102`. The TRANSACT writability check only looks at the parent directory. A read-only existing TRANSACT file, or any `IOException` in `persist`, becomes `UncheckedIOException`, and the JVM exits with 1, not 16, and prints no `ABENDING PROGRAM`. Suggestion: map `IOException` to the matching `ERROR WRITING…` / `ERROR RE-WRITING ACCOUNT FILE` message and return 16. This is outside the AC8 cases, so it isn't a blocker.
4. **should-fix (test). The AC4 golden test only checks golden data:** `GoldenEquivalenceTest.java:44-54` (`lastAccountOfGoldenRunIsUnchanged_D1`) reads only `legacy/golden` files and never runs Java, so it describes the legacy output, not the rewrite. It also skips the spec's `0000000187G` amount assert. Run Java on scenario 2 and assert on its output: account 50 out = in, and tx `2022071800000050` has amount `0000000187G`.
5. **nit. The XREF open-error line is missing the status:** `Cbact04c.java:98`. COBOL `:263` prints `'ERROR OPENING CROSS REF FILE' XREFFILE-STATUS` on one line. Java prints the text only. Open-error status is also a fixed `0035` (`:90`), which build.md notes. No golden covers this; append the status when parity matters.
6. **nit. TRANSACT may not be truncated:** `Cbact04c.java:227`. `persist` returns early when ACCTFILE is empty, so TRANSACT is never truncated and a stale pre-existing file would survive. COBOL `OPEN OUTPUT` always empties it. Write TRANSACT unconditionally.
7. **nit. The xref index is built twice:** `Cbact04c.java:113` and `:186-192`. It builds a `HashMap`, copies it into a `TreeMap`, then builds a third map. Build a `TreeMap` once in `xrefByCard`.
8. **nit. Test readability:** `ProgramTest.java:19-21`. `truncatesNotRounds` passes the dummy rates `"001599"` and then overwrites DISCGRP, and the comment is confusing. `:63` `"000000000000".substring(0, 11) + "{"` and `:123` `"00000000999{".substring(0, 0) + …` are leftover noise; use plain literals.
9. **nit. Plan drift:** the package is `com.v2.carddemo.cbact04c`, not `carddemo.cbact04c` (build.md explains why). The tests are merged into `ProgramTest`, not the `InterestTest`/`DefectTest`/`AbendTest`/`TransactLayoutTest` names in the spec. The coverage is the same; update the spec's test-name table or accept the change.

## What a human should look at before merging

- **The defects stay in on purpose (D1-D8).** The most important is D1: account 50's 18.77 of interest is posted as a transaction but never added to the balance. This needs a separate business decision or CR.
- **Untested paths, because golden has no data for them:** the D6 suffix wrap, R2 duplicate-xref handling, and partial output on abend (spec rule 8, best effort).
- **CI:** the equivalence script needs Maven and network access to build the jar (finding 2).
- **Finding 1:** correct the spec's offsets, since later CRs will build on this spec.
