# CR-1 Review — `CBACT04C` → `modernized/cbact04c/`

Run `5c27f7ac-7a36-4166-a830-3749ca91113d` · Stage: Deploy (independent review)

**Verdict: changes needed.** Both golden scenarios match byte for byte. But overflow truncation keeps 2 integer
digits too few, so when amounts are large the output no longer matches the COBOL. I reproduced this against the
real program. The fix is two numbers.

## What I ran

| Check | Result |
|---|---|
| `mvn -o test` in `modernized/cbact04c` | 20 tests, 0 failures (Characterization 11, Golden 2, Interest 3, Zoned 4) |
| `bash legacy/tests/cbact04c-abend.sh` | `ok noacct` / `ok noxref`: COBOL and Java agree, RC 16 |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` |
| `git diff baseline -- legacy/golden legacy/harness .github` | empty (untouched) |
| Ad-hoc check against COBOL: 3 accts, negative balances (`…1004}`, `…0000M`), 0% row | TRANSACT, ACCTFILE, SYSOUT byte-equal, RC 0 |
| Ad-hoc check against COBOL: TCAT bal `9999999999.99`, ACCT bal `9999999999.99` | **differs**: see F1 |

The golden test is a real proof. It compares raw bytes with `legacy/golden/*` (which was not regenerated) and checks
TRANSACT, ACCTFILE, SYSOUT and RC.

## Findings

1. **Blocker: overflow truncation drops 2 integer digits too many (D6 not preserved).**
   `InterestCalc.java:125-126` uses `Zoned.fit(…, 9)` and `:147` uses `Zoned.fit(…, 10)`. But `fit` counts the
   *total* digits, decimals included (`ZonedTest.java:44`: `fit(12345.67, 4) = 45.67`). So interest is held as
   S9(7)V99 and the balance as S9(8)V99. The COBOL fields are `WS-MONTHLY-INT`/`WS-TOTAL-INT PIC S9(09)V99`
   (`CBACT04C.cbl:168-169`) and `ACCT-CURR-BAL S9(10)V99`.
   Evidence from the real COBOL with TCAT bal 9,999,999,999.99 at 15%:
   - TRAN-AMT: COBOL `0124999999I`, Java `0024999999I`
   - ACCT-CURR-BAL: COBOL `00124999999H`, Java `00024999999H`

   The trigger is monthly interest ≥ 100,000.00 or a balance ≥ 100,000,000.00. That's unlikely in production, but
   it's money and it breaks the "byte-identical" requirement.
   **Fix:** use `fit(…, 11)` at `:125-126` and `fit(…, 12)` at `:147`. Or change `fit` to take integer digits, so
   it reads like the PIC.

2. **Should-fix: the D6 test passes with the bug.** `CharacterizationTest.java:119-126` uses
   9999999999.90 + 1.25. The digits that overflow there are all zero, so 8-digit and 10-digit truncation give the
   same result. Add a case with non-zero high-order digits: interest > 99,999.99, and a balance whose digits 9–10
   are non-zero. Take the expected values from the real COBOL above (`0124999999I` / `00124999999H`). Ideally add
   it as a third case in `legacy/tests/cbact04c-abend.sh`, which would then also cover a non-abend scenario.

3. **Should-fix: build output is in the change set.** `modernized/cbact04c/target/` holds 23 files (jar, classes,
   surefire reports) and isn't ignored. Add `modernized/cbact04c/.gitignore` with `target/` and remove it from
   the PR.

4. **Should-fix: no TRANSACT open/write error path.** The COBOL abends with `ERROR OPENING TRANSACTION FILE`
   (`CBACT04C.cbl:318`). The Java writes outputs only at the end (`InterestCalc.java:82-83`), so a missing or
   unwritable `--out` gives an `IOException` stack trace and exit 1, not RC 16. Either map it to the COBOL
   message + RC 16, or record the deviation in the spec.

5. **Nit: dead code.** `Support.fixture` (`Support.java:303-309`) is unused, and its Javadoc describes something
   else. Delete it.

6. **Nit: `Main` silently ignores a trailing option with no value** (`Main.java:210`, `i + 1 < args.length`), e.g.
   `--clock` at the end falls back to the system clock. Reject it with the usage message.

7. **Nit: a non-numeric zoned field throws `NumberFormatException`** (`Zoned.java:254`, e.g. a blank balance).
   The COBOL would not stop there. This is outside the golden data, so documenting it is enough.

8. **Nit: stderr noise.** Every abend test prints `CEE3ABD: ABEND U0999` (`InterestCalc.java:179`, hard-wired
   to `System.err`). You could inject it like `sysout`.

Checked and correct: truncation toward zero (`RoundingMode.DOWN`, single divide after the exact product), `-0`
decoded and encoded as `{`, negative overpunch, a counter that runs across the whole run and wraps at 10^6
(PIC 9(06)), first xref wins (`putIfAbsent`), D1 (no rewrite at EOF), D4 (partial outputs flushed on abend),
D5 wording, `ERROR OPENING CROSS REF FILE35` (the COBOL appends the status), and that only the 3 fields are
re-spliced on rewrite.

## What a human should look at before merging

- **F1.** Confirm the fix and that the new D6 test compares against real COBOL output. Then re-run
  `mvn test` and `legacy/tests/cbact04c-abend.sh`.
- **The `legacy/tests/` addition.** It's a new directory under `legacy/`, but outside the protected paths.
  Decide whether it belongs there or under `modernized/cbact04c/`.
- **Defect follow-ups** (still open from Plan): raise D1 (last account's interest never written back) as its own
  CR, and decide whether D2 (group id in the wrong field) is a data fix.
- **F4.** Decide whether an unwritable output dir has to follow the COBOL abend contract (RC 16) for operations.
