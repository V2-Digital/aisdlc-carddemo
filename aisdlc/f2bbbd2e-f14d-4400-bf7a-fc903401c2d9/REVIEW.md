# CR-1 Review: `CBACT04C` → Java 21

**Verdict: ready to merge.** Both golden scenarios are byte-identical (TRANSACT, ACCTFILE, SYSOUT, RC). The defects
are preserved and pinned, and the protected dirs are untouched. The findings below are should-fix/nit only.

## What I ran (reviewer, fresh session)

| Check | Result |
|---|---|
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty: untouched |
| `cd modernized/cbact04c && mvn -q verify` | PASS (19 tests). The 4 `CEE3ABD` stderr lines come from the intended abend tests |
| `legacy/tests/cbact04c-abends.sh` | PASS: real COBOL, 3 cases, RC 16, SYSOUT exact |
| `legacy/harness/golden.sh --check` | OK: COBOL still matches goldens |
| `java -jar target/cbact04c.jar 2022071800` with `DD_*` env, after-posting inputs | rc 0; TRANSACT, ACCTFILE, SYSOUT `cmp`-identical to `golden/CBACT04C-after-CBTRN02C/` |
| pom deps | JUnit Jupiter 5.11.4 (test) + Surefire 3.5.2 + jar plugin only |

## Do the tests prove equivalence?

- **Yes, for the goldens.** `GoldenTest` reads the expected outputs straight from `legacy/golden/`. Nothing is
  regenerated or copied into test resources.
- **Abend parity.** The `SYSOUT.expected` files come from running the real COBOL. The script re-checks them on every
  run, and `UPDATE_EXPECTED` is opt-in.
- **D1** checks account `…050` against its input, the amount `0000000187G`, and exactly 49 changed accounts.
- **D2** checks every golden row where truncating and rounding differ, and fails if no such row exists.
- **No tautologies found.** The tests independently compute the expected value with `HALF_UP`/`DOWN` and compare it
  with the program output, not with itself.

## Findings

1. **should-fix: spec/intent column errors.** `aisdlc/…/spec.md:39-40` (and intent "Byte columns") give CYC-CREDIT
   83-94, CYC-DEBIT 95-106 and GROUP-ID 117-126. Per `app/cpy/CVACT01Y.cpy`, the correct columns are **79-90 /
   91-102 / 113-122**. The code is correct (`Records.java:32`, `:39`), and the goldens prove the CYC fields. The
   test report flags only the CYC columns. GROUP-ID is wrong in the docs too. F1's conclusion still holds, because
   cols 113-122 are blank in `acctdata.txt`. *Fix:* correct the spec table so CR-2 does not inherit the error.
2. **should-fix: an output I/O error does not give RC 16.** `Cbact04c.java:66-72` rethrows a write failure as
   `UncheckedIOException`, which means a stack trace and JVM exit 1. The COBOL abends with U0999 / RC 16 on any I/O
   error. The `TRANSACT` DD is also only null-checked at open (`:116`), so an unwritable path fails only at the end.
   *Fix:* catch `IOException` in `run()`, print the COBOL-style message, and return 16. Optionally, probe that
   TRANSACT is writable in `open()`.
3. **should-fix: no test for partial persistence after a mid-run abend.** The spec says "accounts already rewritten
   are flushed". All 3 abend fixtures fail on the first account, so `AbendTest` only proves the "nothing written"
   case. *Fix:* add a fixture where account 1 succeeds and account 2 is missing. Expect 1 TRAN plus account 1
   rewritten, and capture the COBOL output via the abend script.
4. **nit: bad clock value is silently ignored.** `Main.java:26` falls back to the system clock when
   `CARDDEMO_CURRENT_DATE` is set but malformed. Fail fast instead. A typo there silently changes every timestamp.
5. **nit: bad numeric field crashes with exit 1.** `Zoned.java:26` throws `IllegalArgumentException` on a
   non-numeric sign byte, which also gives exit 1 and a stack trace. This is acceptable, but document it or map it
   to RC 16.
6. **nit: dead constant.** `Records.java:11`: `TRAN_LEN` is unused. Use it in an assertion in `tran()`, or delete it.
7. **nit: brittle reflection in a test.** `CharacterizationTest.java:178-180`: `suffixWraps_F4` sets the private
   `tranIdSuffix` by reflection. A package-private setter or constructor parameter would be less brittle.
8. **nit: stale comment.** `CharacterizationTest.java:42`: the comment's example arithmetic does not match what
   the test does. Remove it. The type `01` filter and hard-coded rate `15` (`:52-53`) also deserve a one-line note
   that they come from `discgrp.txt` DEFAULT/01/0001.
9. **nit: duplicate account ids.** `Cbact04c.java:113-114`: a duplicate ACCT id in the input silently keeps the last
   record. A KSDS load would reject it. This is not reachable with the goldens.

## Money / sign / truncation: checked, OK

- Everything is `BigDecimal`, with no `double`.
- `monthly = bal×rate / 1200` is computed with `divide(…, 2, DOWN)` on the exact product, then mod 10⁹ (S9(9)V99).
- The total is truncated to S9(9)V99 and the balance to S9(10)V99 (`Zoned.truncate`, mod on the magnitude, sign
  kept).
- Zero is always written as `{`, including -0.00 (`Zoned.java:42`).
- A negative balance truncates toward zero (test: -100.01 → -1.25).
- Open order and messages match `CBACT04C.cbl:234-323`, including F5 (`DALY REJECTS`).

## What a human should look at before merging

- **Accept or defer findings 2 and 3.** Both are about abend/I-O fidelity outside the golden scenarios. Neither
  affects the acceptance criteria as written.
- **Decide on D1 (last account not rewritten) and D2 (truncation).** Both are preserved as required. They need
  separate CRs: D1 makes TRANSACT and the balances drift apart by one account's interest every night.
- **Confirm the ACCTFILE contract.** The file is rewritten in full via temp file + atomic rename in the same
  directory, so the container needs that directory to be writable.
- **Edge cases with no COBOL reference.** Suffix wrap, negative balances and multi-card XREF are pinned only by
  Java tests that follow COBOL semantics. No COBOL run backs them.
