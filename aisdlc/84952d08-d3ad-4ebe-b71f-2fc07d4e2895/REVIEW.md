# CR-1 Review: `CBACT04C` → `modernized/cbact04c/`

**Verdict: changes needed.** The equivalence work is solid: it is byte-identical on both golden scenarios and on 7 COBOL oracle cases. But if the `TRANSACT` path is missing, the job exits 0 and writes nothing, not even the ACCTFILE update. This breaks spec rule 1, and a nightly ledger job must not do it silently. The fix is small.

## Evidence (run by reviewer)

| Check | Result |
|---|---|
| `mvn -q test` in `modernized/cbact04c` | exit 0 (13 tests) |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` |
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty, untouched |
| pom dependencies | JUnit Jupiter 5.10.2 (test scope), Surefire and jar plugins only |
| Tests vs. expectations | Golden tests read `legacy/golden/*` directly. Oracle fixtures are COBOL-generated and reproducible (per test-report). No tautologies found. |
| Probe: jar run without `--TRANSACT` | stdout ends `END OF EXECUTION…`, **rc=0**, no TRANSACT, ACCTFILE **not rewritten** |

## Findings

1. **blocker** `Main.java:56-58` (the `finally` guard `transact != null && acctOut != null`) and `InterestCalc.java:34-37`. TRANSACT is never "opened". When its path is missing or unwritable, the run returns RC 0 and silently drops all outputs. The COBOL program would display `ERROR OPENING TRANSACTION FILE`, `FILE STATUS IS: NNNN00xx` and `ABENDING PROGRAM`, then end with RC 16 (`CBACT04C.cbl:318`). Spec rule 1 requires this too.
   **Fix:** validate the TRANSACT and ACCTFILE-OUT paths before `run()`, for example by checking the parent dir is writable or by opening the writer up front. Emit the COBOL text and abend. Add a unit test.

2. **should-fix** `Records.java:43` and `Main.java:46-49`. An over-long input line throws `Abend` inside `Records.read`, before `InterestCalc` exists. Stdout then has no `START OF EXECUTION` banner and no `ABENDING PROGRAM` line, so operators see a different log shape from every other abend.
   **Fix:** print the banner and the abend line in `Main` on this path, or load the files inside the constructor.

3. **should-fix** Spec drift. `spec.md` still lists ACCTFILE cycle credit at 77-88, cycle debit at 89-100 and group-id at 111-120. The code correctly uses 79-90, 91-102 and 113-122 (`InterestCalc.java:103,133-134`), which the golden diff and the `group-found` oracle confirm. `build.md` records the correction.
   **Fix:** amend `spec.md` so the approved spec matches what ships.

4. **should-fix** Gap: no oracle checks partial outputs after an abend. On abend, `Main` writes partial TRANSACT/ACCTFILE, and by default (`ACCTFILE-OUT` = input) it **overwrites the account master in place**. The behaviour is plausible (COBOL REWRITEs in place too), but the harness exports nothing at RC 16, so nothing proves it.
   **Fix:** either document that the platform must restore from backup on RC 16, or write ACCTFILE only on RC 0 to a temp file and rename it. That second option needs a human decision because it is a behaviour choice.

5. **nit** `Zoned.java:54`. The javadoc says "low `digits` digits of the integer part", but `digits` is the total digit count including the decimals. Usage (`truncate(x, 11, 2)` for `S9(9)V99`) is correct. Fix the comment.

6. **nit** `InterestCalc.java:47`. `open` has an unused type parameter `<T>`. Remove it.

7. **nit** `Records.java:39`. `text.split("\n", -1)` runs again inside the loop. The `isEmpty()` short-circuit keeps the cost low, but hoist it, or check `i == lines.length - 1`.

8. **nit** `OracleCasesTest.java:21-36`. All cases run in one `@Test`, so the first failure hides the rest. Use `@TestFactory` / `DynamicTest` per case.

9. **nit** `InterestCalc.java:39`. TCATBAL records are sorted by key, matching KSDS sequential order. Duplicate keys, which a KSDS cannot hold, are processed rather than rejected. That is acceptable but undocumented; one line in the README would cover it.

Money, truncation and sign handling look correct:
- `BigDecimal` exact product, then `RoundingMode.DOWN` at scale 2, then low-11-digit truncation, matching the F3 and F6 oracles (`overflow`).
- `-0.00` encodes as `{`, matching the `negative` oracle.
- Negative values truncate toward zero (`-1234.52 × 15 / 1200` → `0000000154L`).
- Balance truncates to 12 digits through `encode`.

## What a human should look at before merging

- **Finding 1** must be fixed. Re-run `mvn test` and `golden.sh --check` afterwards.
- **R2 (multi-card accounts)** is not oracle-proven. The XREF AIX has no `WITH DUPLICATES`, so the COBOL cannot load such data. Accept "first card by key" as the stated rule.
- **Partial outputs on abend (finding 4):** decide whether the in-place ACCTFILE overwrite on RC 16 is acceptable for the container runbook.
- **CI:** nothing in `.github/` runs `mvn test`, and `legacy/tests/cbact04c-oracle.sh` always exits 0. Today only the developer's run proves this change, so decide whether to wire `mvn test` into CI.
- **Legacy defects F1-F6** are preserved as intended. F1 is a customer-impacting ledger mismatch: the last account is charged interest but its balance is not updated. Raise it as a separate CR.
