# Review: CR-1, `CBACT04C` → Java 21

**Verdict: changes needed.** Golden equivalence is proven and the money logic is right. Two input-error paths
break spec §5: one exits 1 instead of 16, and one returns RC 0 on bad input. Both are small fixes.

## What I ran

| Check | Result |
|---|---|
| `mvn -B verify` (`modernized/cbact04c`) | **pass**. 39 tests, 0 failures: Golden 3, Characterization 19, Abend 5, InputFormat 5, Zoned 6, NoFloat 1 |
| `legacy/harness/golden.sh --check` | **pass**: "outputs match legacy/golden", two runs byte-identical |
| `git status --porcelain legacy/ .github/ app/` | **empty**. The diff adds only `modernized/cbact04c/**` and `aisdlc/<run>/**` |
| POM dependencies | JUnit Jupiter 5.10.2 (test scope), Surefire 3.2.5, and jar plugin for the manifest only. Meets AC 1 |
| Probe: bad zoned value in TCATBALF | **exit 1** with a Java stack trace (finding 1) |
| Probe: TCATBALF with the same 17-byte key twice | **RC 0**, 2 transactions (finding 2) |

## What is solid

- **Equivalence is real.** `GoldenEquivalenceTest` runs `Main.run` end to end and compares raw bytes of
  TRANSACT, ACCTFILE and SYSOUT against `legacy/golden/*`, plus RC from `RC.txt`. The expected values are read
  from golden at test time, not regenerated.
- **Arithmetic.** It uses only `BigDecimal`. Each COBOL statement is stored separately:
  - monthly: `InterestCalc.java:88-89`, `Zoned.store(…, 9)`
  - total: `:90`
  - balance: `Zoned.encode(…, 10)` → `store`
  - Truncation goes toward zero (`RoundingMode.DOWN`), and high-order digits are dropped with
    `remainder(10^n)`, which keeps the sign.
  - The divide at scale 10 with DOWN is exact enough: `bal×rate/1200` has at most 4 dp + 3 more, so nothing
    below scale 10 can change the 2-dp result.
- **Signs.** -0 decodes as zero, so `signum()==0` (tested). Zero is always encoded with `{`.
- **F1-F7 are all pinned.** The probe values from spec §5 are asserted: `3333249999A`, -1.54, 1.25, `{` for -0.002.
- **D1 is implemented as proposed.** Outputs are written only on RC 0, via temp file + atomic move. The
  in-place ACCTFILE is untouched on abend (`AbendTest.abendMidRun_…`).

## Findings

1. **should-fix: bad zoned data exits 1 with a stack trace instead of `INPUT ERROR` + 16.**
   - Where: `InterestCalc.java:87` and `:113` (`Zoned.decode` of TCAT-BAL and DIS-INT-RATE) and `:118` (ACCT-CURR-BAL).
   - Why: these values are decoded lazily inside `run()`. `Main.java:54` catches only `Abend`, so the
     `IllegalArgumentException` escapes `main`. Spec §5 says: message and exit 16.
   - Reproduce: a TCATBALF line `00000000001010001ABCDEFGHIJK` gives `exit=1`.
   - Impact: a scheduler keyed on RC 16 would miss this. Also, a blank line in any input pads to spaces and
     hits the same path.
   - Fix: in `Main.run`, catch `IllegalArgumentException` around `InterestCalc.run`, print `INPUT ERROR: <msg>`
     and return 16. Better still, validate zoned fields in `load()`. Add a test to `AbendTest`.
2. **should-fix: duplicate TCATBALF keys are not rejected.**
   - Where: `Main.java:87`. TCATBALF is read as a list, and the `dup` check runs only for XREF, DISCGRP and
     ACCTFILE.
   - Why: spec §5 says a duplicate primary key in *any* keyed input is an input error. TCATBALF is a KSDS
     keyed on cols 1-17, and the harness load would fail.
   - Probe: two identical `00000000001010001…` rows give RC 0 and 2 interest transactions, so the account is
     charged twice.
   - Fix: check that the 17-byte keys are unique after the sort (`InterestCalc.java:53-54`) or in `load()`,
     then add a test.
3. **nit: the comment on the sort is wrong.**
   - Where: `InterestCalc.java:54` ("stable tail keeps duplicates deterministic").
   - Why: the code sorts whole 50-byte records, not keys. With finding 2 fixed, sorting by the full record
     equals sorting by key, so the comment can simply say that.
4. **nit: tautological assertion.**
   - Where: `GoldenEquivalenceTest.java:50`. `.replace("0000000187", "0000000187")` does nothing.
   - Fix: `assertEquals("0000000187G", Fx.amt(tr.get(49)))`. This doesn't weaken the test, since the
     byte-compare already covers it.
5. **nit: dead code in the suffix-wrap test.**
   - Where: `CharacterizationTest.java:119-120`. `Fx f` and `f.parm` are unused.
   - The test also builds 1,000,001 records in memory and takes about 10 s of the ~11 s suite. That is
     acceptable, but worth knowing.
6. **nit: a malformed `--CURRENT-DATE` throws an uncaught `NumberFormatException` or
   `StringIndexOutOfBounds` (exit 1).**
   - Where: `Main.java:73-84`.
   - Fix: wrap the parse and return 16 with a usage message.
7. **nit: the two outputs are not written atomically as a pair.**
   - Where: `Main.java:62-64`. If the ACCTFILE write fails after TRANSACT has been renamed, TRANSACT is
     already replaced.
   - This is outside the golden tests and acceptable under D1, but say so in the README if D1 is meant as
     "all or nothing".
8. **nit: errors in later input files fire before `START OF EXECUTION`.**
   - Where: `Main.load`. Input-error checks (overlong lines, duplicates) run for all files before the
     START line, and before the open-order abends.
   - No legacy behaviour exists for this case, so it is fine. Just note that the order differs from the open
     sequence.

## What a human should look at before merging

- **Decide findings 1-2:** fix them now (about 10 lines + 2 tests, recommended) or accept them as known gaps.
  Both concern only invalid input. Golden behaviour is unaffected.
- **Sign off D1** (no outputs on abend). This is a deliberate difference from z/OS VSAM, where REWRITEs
  done before the abend stay.
- **Raise F1-F7 as follow-up CRs.** F1 matters most for the money: the last account in every run never gets
  its interest posted. In the after-posting golden run, account `00000000050` is charged 18.77 that is never
  added to its balance.
- **Note the rate erratum** in spec.md: the rate is 15.00 %, not 1.50 %. Only the description changes.
