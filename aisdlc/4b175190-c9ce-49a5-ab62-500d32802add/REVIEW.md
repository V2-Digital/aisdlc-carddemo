# CR-1 Review: INTCALC (`CBACT04C`) in Java 21

**Verdict: ready to merge.** All 9 acceptance criteria are met. I checked both golden scenarios byte-for-byte myself, through the tests and through the jar. The findings below harden failure paths and do not affect equivalence.

## What I checked (evidence)

| Check | Result |
|---|---|
| `git diff baseline --stat` | Only `modernized/cbact04c/` (8 files) and `aisdlc/<run>/` changed. `legacy/golden/`, `legacy/harness/`, `.github/` and `app/` are **untouched**. |
| `mvn -q test` | 17 tests, 0 failures (`GoldenTest` 2, `BehaviourTest` 15). Dependencies: JUnit Jupiter 5.10.2 (test) and Surefire 3.2.5 only. |
| `legacy/harness/golden.sh --check` | OK. The legacy code still matches golden. |
| Jar smoke test, after-post scenario (not run by the Build stage) | RC 0. `TRANSACT`, `ACCTFILE` and stdout are `cmp`-identical to `golden/CBACT04C-after-CBTRN02C`. |
| **Overflow probe** (my own input: acct 1 bal `999999999.99`, TCATBAL bal `9999999999.99`), run on both COBOL (`INPUT_DIR`) and the jar | Both RC 0. Monthly `124999999.99` (high-order digit lost) and new balance `1249999.98` (wrapped). All 3 outputs are byte-identical between COBOL and Java. This confirms spec R7 and the `Zoned.fit` truncation. |
| Tests are genuine | `GoldenTest` reads the expected bytes straight from `legacy/golden/` (`assertArrayEquals`), not from regenerated files. Stock inputs come from `app/data/ASCII`, which has CRLF line endings, so CR stripping is exercised. |
| Money handling | `BigDecimal` throughout. `divide(1200, 2, DOWN)` truncates toward zero. `WS-MONTHLY-INT` and `WS-TOTAL-INT` are fitted to S9(9)V99, and the balance to S9(10)V99. A zero result is written as `{`, never `}`. |
| Control flow vs COBOL `:188-221` | Open order, control break, the first-time flag, D1 (no final rewrite), and the rate-0 skip all match. |

## Findings

1. **should-fix: non-`Abend` failures exit 1 with a stack trace, not RC 16.** `Cbact04c.java:53` catches only `Abend`.
   - A malformed `CARDDEMO_CURRENT_DATE` (I tried `2022`) throws `StringIndexOutOfBoundsException` in `timestamp()` (`:193`), and the process exits with **rc=1**.
   - A non-numeric zoned field makes `Zoned.parse` (`Zoned.java:22`) throw `IllegalArgumentException`. Legacy COBOL would S0C7-abend in that case.
   - *Suggestion:* validate the env var in `systemClock16()` (`:199-207`): it must be exactly 16 digits, otherwise usage error and exit 2. In `run`, also catch `RuntimeException`: print `ABENDING PROGRAM`, write `CEE3ABD: ABEND U0999` to stderr, and return 16. Schedulers then see a single abend code.
2. **should-fix: the `ACCTFILE.txt` rewrite is not atomic.** `Cbact04c.java:133` writes `TRANSACT` and then overwrites the account master in place (`FixedFile.java:42` `Files.write`). If the process crashes or the disk fills partway through, the master file is truncated, and `TRANSACT` already exists. *Suggestion:* write each output to `<name>.tmp`, then `Files.move(..., ATOMIC_MOVE, REPLACE_EXISTING)`, with ACCTFILE last. Byte output does not change.
3. **should-fix (or human decision): after an abend, an old `TRANSACT.txt` stays in place.** Legacy opens `TRANSACT` as OUTPUT at start-up (`CBACT04C.cbl:309`), so an abend leaves it empty or partial. The Java leaves *yesterday's* file untouched (spec R9 "no output written"). A downstream step that ignores the RC would post stale interest again. *Suggestion:* delete `TRANSACT.txt` (or truncate it) right after the opens succeed. This is closer to legacy, and it is still "no new output".
4. **nit: tautological assertion.** `BehaviourTest.java:214` asserts `ACCTFILE.txt.new` does not exist, but no code ever creates that file. Replace it with `assertArrayEquals(original ACCTFILE bytes, Files.readAllBytes(ACCTFILE.txt))`, which proves that an abend does not touch the master.
5. **nit: weak status assertion.** `BehaviourTest.java:188` checks `NNNN00` only. Pin `FILE STATUS IS: NNNN0035` exactly. 35 is GnuCOBOL's status for a missing file.
6. **nit: test coverage gaps.**
   - `sourceHasNoFloatingPoint` (`BehaviourTest.java:88`) scans `Cbact04c.java` only. Scan `src/main/java/**` so that `Zoned`/`FixedFile` are covered too.
   - D7 (counter wrap at 999,999) is untested. Expose the counter step as a static helper, or document the gap.
7. **nit: path logic is hard to read.** `GoldenTest.java:243-246` uses a ternary plus an existence fallback to choose input paths. A small explicit map per scenario would make clear which files come from `golden/CBTRN02C`.
8. **nit: duplicate primary keys.** In `Cbact04c.java:160`, `TreeMap.put` keeps the *last* record with a duplicate key, but a VSAM load would reject it. This cannot happen with the sample data. Either fail on duplicates or add a comment.
9. **nit:** the plan listed `Records.java`. Its offsets are inlined as `substring` calls in `Cbact04c.java`, which is fine at this size. Named constants for the field offsets (e.g. `ACCT_GROUP = 112..122`) would help the next reader.

## What a human should look at before merging

- **Two decisions are still open from the plan:**
  - "no output files on abend" (spec R9);
  - the CLI shape `<PARM> [DATA_DIR]`.
  - Finding 3 is a concrete argument to at least clear `TRANSACT` on abend.
- **Whether to fix findings 1-3 in this PR or in a follow-up.** None of them changes golden output, and each one is small (a few lines).
- **Raise the defect CRs flagged in intent.md:**
  - D1 (lost update of the last account; in the after-post golden, `00000000050` is charged 18.77 that is never posted);
  - D3 (blank `ACCT-GROUP-ID`).
- I did not compare the abend paths against real COBOL partial-file output. They are checked only for RC and SYSOUT, as the plan says.
