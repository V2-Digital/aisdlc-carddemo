# CR-1 Review — `CBACT04C` → Java 21

**Verdict: ready to merge.** Both golden scenarios match byte for byte, all D1–D5 are pinned, and a real COBOL-vs-Java run covers the non-golden paths. No blockers. One should-fix: the README that the plan promised is missing.

## What I ran (reviewer, fresh session)

| Check | Result |
|---|---|
| `mvn -q -f modernized/cbact04c test` | **19 tests, 0 failures, 0 errors** (Defect 5, Golden 3, Interest 1, Lookup 6, Zoned 4) |
| `legacy/tests/cbact04c_equiv.sh` | exit 0. `in_group_hit` RC 0/0, `in_negative` RC 0/0, `in_no_default` RC 16/16. SYSOUT, TRANSACT and ACCTFILE identical in every case |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden`, two runs byte-identical |
| `git diff --stat baseline -- legacy/golden legacy/harness .github` | **empty** |
| pom dependencies | only `junit-jupiter` (test scope), plus the Surefire and jar plugins ✅ |

## The tests prove equivalence; they are not tautologies

- `GoldenTest` reads its expected output straight from `../../legacy/golden/<scenario>/`. Nothing is regenerated. It compares TRANSACT and ACCTFILE with `assertArrayEquals` on the raw bytes, and SYSOUT and RC as well (AC 2–4).
- `InterestTest` uses 3577.41, which gives `0000000447A`. That only passes if the amount is truncated (D2, AC 5).
- `DefectTest.d1_*` asserts that the last account's record is byte-equal to its input **and** that its transaction is written (D1, AC 6).
- The D3 test checks its own fixture: it asserts the cycle fields were non-zero before the run.
- The equivalence script fails if the COBOL output for the negative case has no negative sign. That guards against a test that passes vacuously. Its result is real COBOL evidence for D4: truncation toward zero, written with a `J`–`R` overpunch.
- `groupHit` uses `ZEROAPR` (rate 0, while DEFAULT is 15.00). That is a better choice than the spec's `A000000000`, which has the same 15.00 rate as DEFAULT and so would not tell the two paths apart.

## Findings

1. **should-fix** — `modernized/cbact04c/README.md` is missing (plan item 10). Operators need one place that lists how to run it (`--in/--out/--parm/--clock`, exit codes) and the preserved defects D1–D5. *Add a ~20-line README.*
2. **nit** — `Main.java:16-19`: it also reads `COB_CURRENT_DATE` before `CARDDEMO_CURRENT_DATE`. The spec only names `--clock`, then `CARDDEMO_CURRENT_DATE`, then the system clock. This is harmless, but in a container a stray `COB_CURRENT_DATE` would silently freeze the clock. *Drop it, or document it in the spec and README.*
3. **nit** — `Cbact04c.java:160-173` (`open()`): only `NoSuchFileException` becomes the legacy open-error path with RC 16. Other `IOException`s (permissions, a directory instead of a file) escape, so the JVM exits 1 with no legacy message. *Catch `IOException` and map it to status `35` (or `37`).*
4. **nit** — `Zoned.java:13` (`parse`): a non-numeric field, such as spaces in a corrupt record, throws `NumberFormatException` and crashes with RC 1. COBOL behaves differently here, though this is outside the golden scope. *Leave as is, but list it in the README under known differences.*
5. **nit** — `Main.java:20` (`i < args.length - 1`): a trailing flag without a value is ignored without any message, and a short `--clock` value causes a `StringIndexOutOfBoundsException` in `timestamp()`. *Check that `--clock` matches `\d{16}`.*
6. **nit** — The plan listed `Records.java`, `Datasets.java` and `ZonedTest`-only offsets. Instead, the offsets are inline `substring` literals in `Cbact04c.java` (e.g. `112, 122`, `24, 78`). I checked them against `CVACT01Y.cpy` and they are correct: group id at col 113, cycle credit/debit at col 79/91. Named constants would make them easier to read. **Note: the spec table says cycle credit/debit are at col 71/83, which is wrong. The code is right.** *Correct the spec, or add named constants.*
7. **nit** — Dedup is inconsistent: DISCGRP uses `put` (the last row wins), while ACCTFILE and XREF use `putIfAbsent` (the first row wins). Keys are unique in a KSDS, so this does not change behaviour. *Use `putIfAbsent` everywhere for consistency.*
8. **nit** — Untested edge case: the same account appearing on non-adjacent TCATBAL lines, where the re-read should come from the updated map. The code handles it (`accounts.get` after `updateAccount`), but no test pins it. *Add a small test, or remove the row from the spec.*

Money handling looks right. `BigDecimal` is used throughout, with `divide(1200, 2, DOWN)`. Results are taken mod 10^n into `S9(9)V99` / `S9(10)V99`, and -0 is written as `{`. I found no dead code beyond the intentional empty fees hook.

## What a human should look at before merging

- **D1 is a live financial defect that this change carries over on purpose.** In each run, the last account in TCATBAL gets its interest transaction but no balance update (golden: account 50, 18.77). Confirm that the follow-up CR is opened and prioritised before cut-over (intent Q3).
- **D2 under-charges** by up to 0.01 per category. 28 of 50 golden amounts would differ if rounded.
- **I/O format:** the program reads and writes the harness's text unload format, not VSAM (intent Q2). The data conversion still has to be owned by the deploy / data-migration work.
- Findings 1 and 6 (README, spec column typo) are documentation fixes and can go in before or straight after the merge.
