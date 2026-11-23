# CR-1 Review — `CBACT04C` → `modernized/cbact04c/`

**Verdict: ready to merge.** Both golden scenarios match byte for byte (TRANSACT, ACCTFILE, SYSOUT, RC 0), and the protected paths are untouched. Before merging, a human must decide on D7 (finding 1). The remaining findings are should-fix or nit, and none of them blocks a like-for-like merge.

## What I ran
| Command | Result |
|---|---|
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty (nothing touched) |
| `mvn -q -f modernized/cbact04c verify` | rc 0. 18 tests: Golden 2, ZonedDecimal 3, Behaviour 13. 0 failures. |
| CLI jar, scenario `CBACT04C-after-CBTRN02C`, `CARDDEMO_CURRENT_DATE=2022071800000000` | rc 0. `cmp` shows TRANSACT, ACCTFILE and SYSOUT identical to golden. |
| `bash legacy/tests/neg_balance.sh` | PASS. COBOL gives −1512.77 @ 15% → `0000000189}` (−18.90), the same as Java. |
| `bash legacy/tests/xref_dup.sh` | rc 0. Harness LOAD fails with status 22, so CBACT04C never runs. |
| CLI with a missing input file | `ABENDING PROGRAM`, rc 16 ✔ |
| CLI with `CARDDEMO_CURRENT_DATE=2022x71800000000` | uncaught `NumberFormatException`, rc 1 (finding 4) |

Spec rules 1–9 match the code. I checked the loop structure against `CBACT04C.cbl:188-221` (D1), the truncation (`RoundingMode.DOWN` followed by the mod 10^n wrap), the overpunch sign handling (−0 is written as `{`), and the xref status-02 path, which prints no `ACCOUNT NOT FOUND`, the same as COBOL because 02 is not INVALID KEY.

## Findings

1. **should-fix (needs a human decision): D7 behaviour was not verified against COBOL.** `InterestCalc.java:89-92` abends when an account has more than one xref card. Intent D7 assumed "first card". The build changed this to "abend" based on z/OS AIX semantics (status 02 ≠ `00` at `CBACT04C.cbl:400`). The source supports that reasoning, but nothing has run it: `legacy/tests/xref_dup.sh` only proves that the harness cannot load duplicates. *Suggestion:* the approver confirms "abend". If the AIX is `UNIQUEKEY` in production, the branch is unreachable and either choice is safe. Record the decision in the README.

2. **should-fix: `LastAccountDefectTest` does not exercise Java.** `BehaviourTest.java:117-131` only compares two golden files with each other, so it would pass with any implementation. Java's D1 behaviour is still proven, by `GoldenTest.afterCbtrn02c` and `lastAccountDefect_syntheticTwoAccounts`. *Suggestion:* run `Main.run` on the CBTRN02C inputs in this test and assert on the Java output for acct 50 and tran `…000050`.

3. **should-fix: timezone of the system clock.** `Main.java:58` and `InterestCalc.java:153` use UTC when `CARDDEMO_CURRENT_DATE` is unset. COBOL `CURRENT-DATE` uses the host's local time. This follows the spec, but container timestamps will differ from mainframe local time. *Suggestion:* ops confirm UTC, or use `Clock.systemDefaultZone()` with the container TZ set.

4. **should-fix: an invalid `CARDDEMO_CURRENT_DATE` is handled badly.** A value shorter than 16 characters silently falls back to the system clock (`Main.java:57`). A non-numeric value throws an uncaught exception and exits with 1, not 16. *Suggestion:* fail with exit 16 (or 2) and a clear message.

5. **nit: stale outputs after an abend.** Outputs are only written at the end of a successful run (`InterestCalc.java:119-120`). After an abend, any files left from an earlier run stay in place, with rc 16. The spec says to discard outputs on abend, so this is acceptable. Mention it in the README for ops.

6. **nit: silent data acceptance.** `FixedWidthFiles.pad` truncates lines longer than the LRECL (`FixedWidthFiles.java:44-46`). Duplicate ACCTFILE keys collapse in the `TreeMap` (`InterestCalc.java:56-58`). Unknown or odd CLI args are ignored (`Main.java:30`). The golden data reaches none of these. Consider abending on them.

7. **nit: dead code.** `TRAN_LRECL` (`InterestCalc.java:23`) is unused. Use it to assert the record length in `tranRecord`, or remove it.

8. **nit: test layout differs from the spec.** The spec lists `InterestTest`, `AbendTest` and others, but they are combined in `BehaviourTest`. The coverage is equivalent. `interestIsTruncatedNotRounded_acct48` uses synthetic acct 1 with acct 48's figures. That is fine, because the golden test covers the real acct 48.

9. **nit: new legacy tests are not in CI.** The test report calls `legacy/tests/*.sh` "new CI tests", but `.github/` is unchanged (correctly), so CI does not run them. Wire them in separately if they are wanted.

## What a human should look at before merging
- **D7:** accept "abend on duplicate xref cards" (finding 1), or ask for "first card".
- **D1:** the account-50 balance is understated in every run. Raise the follow-up CR (intent Q3).
- **Timezone** for the production clock (finding 3).
- **Not modelled or not cross-checked:** D5 (open-error text; Java has no open-failure path) and D6 overflow (Java unit test only; no COBOL run).
