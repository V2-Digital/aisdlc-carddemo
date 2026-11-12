# CR-1 Review: `CBACT04C` → Java 21

Reviewer: independent session · Run `685b6296-ed6f-482f-b34e-e9432cf8407c`

**Verdict: `ready to merge`.** The output is byte-identical to golden and to the live COBOL on 4 scenarios. Money handling is exact and truncates correctly. I found no blockers. The should-fix items below are test hygiene and untested error paths.

## What I ran (this session)

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | exit 0. 23 tests, 0 failures (Abend 5, F1 2, Golden 2, Truncation 4, Rules 6, Zoned 4) |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` |
| `legacy/tests/cbact04c_java_equiv.sh` | `PASS: Java == COBOL`. stock / after / edge: RC 0, SYSOUT+RC+TRANSACT+ACCTFILE identical. missing: RC 16 on both, SYSOUT identical |
| `git diff --stat baseline -- legacy/golden legacy/harness .github` | empty |

## Correctness vs spec

- **Golden equivalence is real.** `GoldenEquivalenceTest` reads the expected bytes from `legacy/golden/*` and checks them with `assertArrayEquals`. Expectations are not regenerated. The cross-check script also runs the actual COBOL.
- **F1 is preserved.** No rewrite after the loop (`InterestCalc.java:106`). Acct 50 stays `00000019458G` and the txn `0000000187G` is posted. Pinned in `DefectF1Test`.
- **Money:** `BigDecimal` only. `RoundingMode.DOWN` truncates toward zero (`:166-167`). High-order truncation goes through `ZonedDecimal.wrap/encode`. The negative cases (`}J-R`) pass, and so does the live-COBOL edge fixture with a negative balance.
- **ACCTFILE:** only the 3 fields are overlaid (`:193-196`), so the other bytes are kept. The offsets 12/78/90/112 match `CVACT01Y` (see finding 6).
- Open order (TCATBALF, XREF, DISCGRP, ACCTFILE, TRANSACT) matches `CBACT04C.cbl:182-186`.

## Findings

1. **should-fix** · `RulesTest.java:48,50`: these assertions can never fail. `assertEquals("00000000001", "".substring… + "00000000001")` and `assertEquals("0000000002{".length(), 11)` test nothing. The real check is on line 51. **Fix:** delete both lines.
2. **should-fix** · `RulesTest.java:98-101` `txnIdSuffixWrapsAtMillionF4` recomputes `(999_999+1) % 1_000_000` inside the test, so it never touches production code. F4 is not actually pinned. **Fix:** make the suffix start package-private or injectable, or extract `nextSuffix(int)` in `InterestCalc`, and test the real wrap to `000000`.
3. **should-fix** · `InterestCalc.java:71` vs `CBACT04C.cbl:263`: COBOL prints `'ERROR OPENING CROSS REF FILE' XREFFILE-STATUS`, which puts the status straight after the text (e.g. `ERROR OPENING CROSS REF FILE35`). Java prints the text with no status. This is a SYSOUT mismatch on an abend path, and no test or golden covers it. **Fix:** append the status (`"35"`) and add an `AbendTest` case for a missing XREFFILE.
4. **should-fix** · `InterestCalc.java:108-109`: if writing `TRANSACT`/`ACCTFILE` fails, the `IOException` escapes `run()`. The JVM then exits with code 1 and a stack trace, not RC 16. The same happens if `ZonedDecimal.decode` throws `NumberFormatException` on garbage digits (`:301`). Spec rule 1 says an unwritable output must abend with RC 16. **Fix:** catch these in `run()` and map them to the abend path. At minimum, make sure the exit code is non-zero, which it already is.
5. **should-fix (decide)** · `Main.java:57-68`: the `CARDDEMO_CURRENT_DATE` env override is not in the spec. It is useful for the cross-check, but a stray env var in production would freeze every timestamp. A bad value throws an unhandled `NumberFormatException`. **Fix:** keep it but document it in the README and validate it, or replace it with an explicit `--clock` argument. A human should decide.
6. **nit** · `spec.md` (Files table): the ACCTFILE offsets are listed as cyc-credit 188, cyc-debit 200 and group-id 212. Those are wrong. The code is right (78/90/112 per `CVACT01Y`, proven by golden). **Fix:** correct the spec so it does not mislead CR-2.
7. **nit** · `ZonedDecimalTest.java:182-183`: the first `expect` assignment is overwritten straight away (dead code). For `}`, only the length is asserted (`:192`), and `:202` already covers it. **Fix:** remove the dead lines.
8. **nit** · The package is `com.v2.carddemo.cbact04c`, but the plan says `com.carddemo.cbact04c`. The plan also says "no changes outside `modernized/cbact04c/`", yet `legacy/tests/` was added. Both are harmless. Accept them or align the plan.
9. **nit** · `InterestCalc.java:135-141`: the xref lookup is a linear scan for each account change (O(accounts × xrefs)), and every file is loaded whole into memory. That is fine for 50 records, but production volumes would need a `Map` built at load time (`putIfAbsent` keeps R3 first-match semantics).
10. **nit** · `Main.java:45`: `--sysout` as the last argument throws `ArrayIndexOutOfBoundsException`, not a usage message.

## What a human should look at before merging

- **Q2 / F1 money defect:** the last account's interest is posted as a transaction but never added to its balance. This is preserved deliberately. Raise the follow-up CR.
- **Finding 5:** keep or drop the `CARDDEMO_CURRENT_DATE` override.
- **Finding 3:** a small SYSOUT divergence on the XREF open-failure path. Decide whether it must be fixed before merge. It is outside the golden scope.
- **Not proven by any test:** several xref rows per account (R3), the real 999,999 suffix wrap (F4), and open-failure paths other than DISCGRP.
- **Production readiness:** `ACCTFILE` is rewritten in place and whole (README says "copy it first"). Confirm the operational procedure for backup and restart before the first container run.
