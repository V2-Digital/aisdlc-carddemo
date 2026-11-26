# CR-1 Review: `modernized/cbact04c`

**Verdict: changes needed.** All success paths are byte-identical to COBOL and every acceptance criterion passes. One failure path breaks spec R9: when a file fails to open, the Java writes an **empty `ACCTFILE`**. The fix is small (finding 1).

## What I ran (reviewer, fresh session)

| Command | Result |
|---|---|
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty, so the protected paths are untouched |
| `cd modernized/cbact04c && mvn -q verify` (OpenJDK 21.0.12) | 23 tests, 0 failures. JUnit Jupiter 5.10.2 is the only dependency |
| `legacy/tests/cbact04c-diff.sh` | 6/6 comparisons identical (caseA: TRANSACT, ACCTFILE, SYSOUT, RC; caseB: SYSOUT, RC) |
| `legacy/harness/golden.sh --check` | OK |
| Java run with `DISCGRP.txt` removed | RC 16, but `ACCTFILE.txt` comes out as **0 bytes** (and so does `TRANSACT.txt`) |

The equivalence tests are genuine. `GoldenTest` reads the expected bytes straight from `legacy/golden/` and compares TRANSACT (50×351 incl. LF), ACCTFILE (50×301), SYSOUT and RC. No expected output was regenerated. Money handling is right: `BigDecimal` with `RoundingMode.DOWN` (toward zero, not FLOOR), modulo 10^11 for S9(9)V99 and 10^12 for S9(10)V99 with the sign kept, and `+0` encoded as `{`. The caseA differential test confirms overflow and negative balances against GnuCOBOL.

## Findings

1. **should-fix (blocks merge, in my view): an open failure truncates the account master.** `InterestCalc.java:52-60` throws before `accounts` is loaded (`:82-84`). `run()` (`:46-48`) then writes an empty ACCTFILE. COBOL never rewrites ACCTFILE in this case, and spec R9 says ACCTFILE must be written "with the R7 updates applied so far", which here means unchanged. If a deployment points OUT_DIR at the master, or a later step promotes the output, the account master is wiped.
   *Fix:* load ACCTFILE before opening anything else, or on abend write ACCTFILE only once it has been loaded and otherwise skip it or copy the input. Add an `AbendTest` case: input DISCGRP missing → output ACCTFILE equals the input or is absent.
2. **should-fix: timezone of the timestamp.** `InterestCalc.java:155` and `Main.java:29` always use UTC. COBOL `CURRENT-DATE` is local time. That gives identical results under the frozen harness clock, but in production a container on UTC would stamp `ORIG-TS`/`PROC-TS` hours away from the mainframe. *Suggest:* use the system default zone (set by `TZ` in the container), or have a human confirm that UTC is acceptable and document it in the README.
3. **should-fix: the diff script can test a stale jar.** `legacy/tests/cbact04c-diff.sh:11-13` builds only when `target/cbact04c.jar` is missing. After a source edit it silently compares the old jar. *Suggest:* always run `mvn -q -DskipTests package`, which is quick and incremental.
4. **nit: the overflow test is partly circular.** `InterestTest.java:46-54` computes its expected value with the same truncate-and-mod algorithm as production. The COBOL cross-check exists only in the diff script. *Suggest:* hardcode the value GnuCOBOL produces (`124999999I` from caseA, or the equivalent for this input).
5. **nit: obfuscated literal.** `InterestTest.java:31` has `"00120{".replace("{", "0")`, which is just `"001200"`. Write the literal.
6. **nit: missing status in the XREF open message.** COBOL prints `'ERROR OPENING CROSS REF FILE' XREFFILE-STATUS` on one line (`CBACT04C.cbl:263`). `InterestCalc.java:70` omits the status. This doesn't affect any AC, since the harness can't produce an open failure (it falls back to the sample files), but it is a SYSOUT parity gap.
7. **nit: silent fallback on a bad clock env.** `Main.java:29`: if `CARDDEMO_CURRENT_DATE` is set but shorter than 16 chars, the program silently uses the system clock. Failing fast is safer for reruns.
8. **nit: input checks are lax.** `RecordFile.java:25` silently truncates over-length lines. `InterestCalc.java:80` lets a duplicate DISCGRP key take the last value, where a KSDS would reject it. Neither occurs in the data. Consider rejecting both.
9. **nit: the package differs from the plan.** The plan has `carddemo.cbact04c`; the code uses `com.v2.carddemo.cbact04c`. This is harmless, but it should be noted so the plan and the code agree.
10. **nit: duplicated staging code.** The CBTRN02C input staging is duplicated in `GoldenTest.afterCbtrn02c` and `DefectsTest.F1_lastAccountNotPosted`. Move it into `Support`.

## What a human should look at before merging

- **Finding 1**: decide whether it must be fixed before merge. I recommend fixing it, because it is a data-loss path.
- **Finding 2**: is UTC correct for production transaction timestamps?
- **Abend partial outputs are unverified against COBOL.** The harness exports no datasets on abend, so spec R9 rests only on the Java side (see test-report.md).
- **Separate tickets for the preserved defects F1–F6.** F1 matters most: customers are charged interest that never reaches the balance (e.g. account 50, 18.77).
- The golden data only exercises categories 01/0001 and 03/0001 with the DEFAULT group. Other paths rely on unit tests plus the two crafted diff cases.
