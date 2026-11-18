# CR-1 Review — `modernized/cbact04c` (independent reviewer)

**Verdict: ready to merge.** Both golden scenarios are byte-identical to the stored golden and to live COBOL, and AC 1-10 are met. The remaining findings are should-fix or nit, mostly in paths the golden does not exercise (live clock, open errors, bad input).

## Evidence (re-run by me)

| Check | Result |
|---|---|
| `mvn -q verify` (modernized/cbact04c) | 23 tests, 0 failures (Zoned 6, Behaviour 15, Golden 2), ~6 s. Only junit-jupiter (test scope) + Surefire/jar plugins. |
| `legacy/tests/cbact04c-java-vs-cobol.sh` | 8/8 `ok`: stock and post-CBTRN02C × TRANSACT/ACCTFILE/SYSOUT/RC. TRANSACT 50, ACCTFILE 50, SYSOUT 302 lines for post. |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden`. |
| `git status`/`git diff baseline -- legacy/golden legacy/harness .github` | Empty. Untouched. |

The tests are real. `GoldenEquivalenceTest` reads the committed `legacy/golden/*` files directly and nothing regenerates them. `lastAccountNotRewritten_D1` checks that both the balance and the non-zero cycle fields (5.00) survive on the last account. Interest uses `RoundingMode.DOWN` at every step, and high-order truncation (`Zoned.truncate` 9/10 digits) matches the PICs. Negative zero is encoded as `{`. I checked the offsets against `CVACT01Y.cpy` (group id at 112, cycle credit/debit at 78/90).

## Findings

1. **should-fix**: one timestamp for the whole run. `Cbact04c.java:150` (`timestamp()` formats the fixed `clock` string). COBOL calls `FUNCTION CURRENT-DATE` for each transaction (`CBACT04C.cbl:496,614`), so in production the hundredths/seconds change from one transaction to the next. Java stamps every transaction with the start-of-run time. The golden cannot catch this because its clock is frozen. *Suggestion:* inject a `Supplier<String>` / `java.time.Clock` and read it per transaction. A frozen supplier keeps the tests identical. Or record this as accepted deviation D10.
2. **should-fix**: the clock zone is UTC. `Main.java:29`. `CURRENT-DATE` is local mainframe time. Batch jobs that run near midnight would get a different date in `TRAN-ORIG-TS`/`PROC-TS`. *Suggestion:* use `ZoneId.systemDefault()` or a `--tz` option, and document it. This needs a human decision (see below).
3. **should-fix**: the XREF open-error text differs from COBOL. `Cbact04c.java:68`. COBOL displays `'ERROR OPENING CROSS REF FILE' XREFFILE-STATUS` (`CBACT04C.cbl:263`), so the line ends with the 2-char status, e.g. `ERROR OPENING CROSS REF FILE35`. Java prints it without the status. The test `missingInputFileAbends` covers DISCGRP only. *Suggestion:* append the status for XREFFILE and add a test for it.
4. **should-fix**: invalid `--clock` or bad data crashes with a stack trace and exit 1, not 16. `Main.java:26-38`. A `--clock` that is not 16 digits throws `StringIndexOutOfBoundsException` at the first transaction. A bad sign char throws `IllegalArgumentException` from `Zoned.decode`. In both cases no outputs are written. *Suggestion:* validate `--clock` (`\d{16}`) and `--parm` (length 10) in `Main` before running, and exit 2 with the usage message. Leave bad-data behaviour as is, but list it in the README as a known difference (COBOL would S0C7).
5. **nit**: blank input lines are silently skipped. `Records.java:28-30`. To COBOL a blank line is a record of spaces, and it would fail. This is harmless with real unloads, but it could hide a truncated file. *Suggestion:* skip only the final empty element after the last `\n`.
6. **nit**: the xref "first in primary-key order" rule (D9) relies on file order. `Cbact04c.java:48-50`. `cardxref.txt` is sorted today (I checked it with `sort -c`). *Suggestion:* sort by card number before `putIfAbsent`, or put the assumption in a comment.
7. **nit**: dead code. `Records.TRAN_LEN` (`Records.java:17`) is unused. Either use it in an `assert t.length()==TRAN_LEN` inside `transaction()` or delete it.
8. **nit**: test readability. `BehaviourTest.java:146-155` has a comment ("use only first account instead") that contradicts what the code does. `ZonedTest.java:28` uses `"99999999999".substring(0,10)+"I"`, which is just `"9999999999I"`. `BehaviourTest.java:21-22` has a double blank line. The spec names `InterestTest`/`ZeroRateTest`/`CharacterizationTest`, but these were merged into `BehaviourTest` with `// ----` section comments. That's fine, but update the spec table or the test report for traceability.
9. **nit**: `suffixWraps_D7` (`BehaviourTest.java:128`) feeds 1,000,001 TCATBAL records that share one key. A KSDS can't hold duplicate keys, so this is an artificial fixture. The fixture is acceptable for pinning the wrap, but say so in the comment.
10. **nit**: `README.md:3` links `aisdlc/<run>/intent.md` with a placeholder. Use the real path. Package is `com.v2.carddemo.cbact04c` vs plan's `carddemo.cbact04c`, which is harmless.

## What a human should look at before merging

- **Decide on findings 1-2** (per-transaction timestamp, time zone). These are the only differences from COBOL that a production run would show, and the golden cannot detect them. Either fix them before cut-over or accept them as a documented deviation.
- **Abend-path outputs** (partial TRANSACT/ACCTFILE on RC 16) are not proven against COBOL. The spec flags this. Confirm that ops does not rely on partial outputs, or add a harness scenario for it.
- **Preserved defects D1-D3** still need fix CRs. D1 (last account never rewritten, e.g. acct 50 misses 18.77 per run) costs money every night.
- I did not review `intent.md`/`spec.md` prose beyond checking the code against them. One small doc slip: intent gives `ACCT-GROUP-ID` as "cols 102-111", but the copybook puts it at 1-based cols 113-122 (0-based 112). The code is correct.
