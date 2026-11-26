# CR-1 Test report

**Result: all green.** 2 fix iterations of my own tests (limit 5); no defects found in the Java.

## Commands and results

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | 23 tests, 0 failures (Java 21, JUnit 5 only) |
| `legacy/tests/cbact04c-diff.sh` | COBOL vs Java: all compared files identical (6 comparisons), exit 0 |
| `legacy/harness/golden.sh --check` | OK, legacy outputs match `legacy/golden` |

## Golden equivalence (`GoldenTest`)

| Scenario | Compared | Result |
|---|---|---|
| `CBACT04C` (stock) | TRANSACT 50×350, ACCTFILE 50×300, SYSOUT 152 lines, RC 0 | byte-identical |
| `CBACT04C-after-CBTRN02C` | TRANSACT 50×350, ACCTFILE 50×300, SYSOUT 302 lines, RC 0 | byte-identical |

## Characterization (pinned legacy behaviour)

- **F1** `DefectsTest.F1_lastAccountNotPosted`: account 50 is identical to its input, while its 18.77 interest transaction exists. Exactly 49 of 50 accounts change.
- **F2** `InterestTest`: 116.48 → 1.45 (not 1.46); −116.48 → −1.45 (not FLOOR's −1.46).
- **F3** `InterestTest.overflowWrapsSilently`: high-order digits dropped. The COBOL diff script also confirms this (account 4, see below).
- **F4** `F4_zeroBalanceNonZeroRateWritesZeroTransaction`. A zero rate writes no transaction, yet the account is still rewritten with its cycle fields zeroed.
- **F5** Real group rate is used (12% vs DEFAULT 15%), with only the blank-group account falling back.
- **F6** Wrong DISCGRP open message is preserved. A short PARM is space-padded.
- Also covered: CRLF/36-byte records (`RecordIoTest`), overpunch codec (`ZonedTest`), and an empty TCATBALF.
- `AbendTest`: missing DEFAULT, account, XREF and input file each give the messages plus RC 16.

## COBOL-vs-Java differential (`legacy/tests/cbact04c-diff.sh`, fixtures in `legacy/tests/cbact04c-diff/`)

- **caseA** (5 accounts, RC 0): TRANSACT, ACCTFILE, SYSOUT and RC identical. It covers:
  - a real group hit (GRP1 12%) and a multi-category account;
  - a zero-rate category and a negative balance (−1.45);
  - overflow (balance 999999999.99 at 9999.99%: both programs wrap to `124999999I`);
  - the last account left unposted.
- **caseB** (missing DEFAULT for 09/0009): SYSOUT and RC 16 identical.

## Limitations / for humans

- **Abend partial files are not verified.** On abend the harness exports no datasets (only SYSOUT, RC 16), so Java's "write partial output" (spec R9) cannot be compared with COBOL. It is unproven either way. The CR only requires the success paths.
- The script builds the jar if `target/cbact04c.jar` is missing. CI runs it from the repo root and needs Maven, Java and GnuCOBOL there.
- Defects F1–F3 are preserved and need separate tickets. F1 is customer-impacting.
- Fix iterations: two test-authoring slips (an off-by-one slice and a wrong expectation about the blank-group fallback). No production code changed.
