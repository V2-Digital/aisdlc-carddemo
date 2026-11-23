# CR-1 Test report

**Result: green, 0 fix iterations needed** (Build left everything passing; Test re-verified, no code changed).

## Commands and results

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q test` | exit 0, **13 tests, 0 failures/errors/skips** (Characterization 7, GoldenEquivalence 2, OracleCases 1, Zoned 3) |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` (two runs byte-identical) |
| `legacy/tests/cbact04c-oracle.sh` (re-run COBOL oracle) | RC per case: abend-acct/default/xref 16; empty, group-found, negative, overflow 0. Regenerated `expected/` is **identical** to the committed copy (`diff -r` clean), so the fixtures are reproducible |
| `git diff --stat baseline -- legacy/golden legacy/harness .github` | empty (unchanged) |

## What is proven (acceptance criteria)

- **AC2/AC3 equivalence:** `GoldenEquivalenceTest` compares Java output with `legacy/golden/CBACT04C` and `CBACT04C-after-CBTRN02C`. It checks TRANSACT (50 records each), ACCTFILE (50 each), SYSOUT (152 / 302 lines) and RC 0. All are byte-identical.
- **AC4/AC7 oracle cases** (expected output produced by the real COBOL): `negative`, `group-found`, `overflow`, `empty`, `abend-acct`, `abend-xref`, `abend-default`. Java matches RC, SYSOUT and output files. Abend cases are RC 16.
- **Characterization tests pin the legacy quirks:**
  - F1: last account not rewritten.
  - F3: truncation vs rounding.
  - F5: `DALY REJECTS FILE` text.
  - F6: high-order digit loss and tran-id suffix wrap.
  - Rate 0: no transaction, cycle fields still reset.
  - Timestamp and id format.
  - Bad input leads to an abend.

## Gaps / notes for the reviewer

1. **Multi-card accounts (R2) are not oracle-proven.** The COBOL AIX has no duplicates, so the harness load fails with status 22. Java takes the first card by key. Nothing in COBOL can contradict this.
2. **Partial outputs after an abend are not oracle-checked.** `run.sh` exports no datasets at RC 16, so only SYSOUT and RC are compared.
3. **Spec offsets were corrected in Build** (see `build.md`): cycle credit 79-90, cycle debit 91-102, group-id 113-122. They follow the copybook, and the golden diff confirms them.
4. **`legacy/tests/cbact04c-oracle.sh` is a fixture generator, not an assertion.** It always exits 0 and rewrites the committed fixtures. In CI it runs but can't fail. The assertions are in the Java `OracleCasesTest`, so CI must also run `mvn test` to cover this change. A human should decide whether to wire that in.
5. Nothing is left failing.

## Decisions for a human

- Decide on F1 (customer-impacting ledger mismatch), F3 (truncation) and F4 (blank group-id) in separate CRs.
- Decide whether CI should run `mvn test` for `modernized/`.
