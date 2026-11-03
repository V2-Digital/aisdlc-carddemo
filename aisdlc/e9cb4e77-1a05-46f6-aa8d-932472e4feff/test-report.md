# CR-1 Test report

**Result: all green.** 21 JUnit tests pass, golden check passes, COBOL abend test passes. Java code was not changed in this stage.

## Commands and results

| Command | Result |
|---|---|
| `mvn -q -f modernized/cbact04c test` | 21 run, 0 failures (Golden 2, Interest 5, Defect 5, Abend 3, Clock 4, Zoned 2) |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden`, two runs byte-identical |
| `bash legacy/tests/cbact04c_abend.sh` | 3/3 ok, exit 0 (real GnuCOBOL) |
| CLI smoke: `java -jar target/cbact04c.jar --parm=2022071800 --now=2022071800000000` on stock data | exit 0; TRANSACT, ACCTFILE and stdout identical to `golden/CBACT04C` |

## Equivalence (compared in `GoldenTest`)

| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` | 50 records byte-identical | 50 byte-identical | 152 lines identical | 0 |
| `CBACT04C-after-CBTRN02C` | 50 byte-identical | 50 byte-identical | 302 lines identical | 0 |

## Acceptance criteria

| AC | Evidence |
|---|---|
| 1 | `mvn test` green; only JUnit Jupiter + Surefire (+ jar plugin for packaging) |
| 2, 3, 4 | `GoldenTest.stock`, `afterCbtrn02c` |
| 5 | `InterestTest.truncatesNotRounds`: all 50 golden amounts equal truncation; HALF_UP would differ on 28. Plus negative case (-0.125 → -0.12) |
| 6 | `DefectTest.d1_…`: acct 50 equals input (bal `00000019458G`), txn `…0050` amount `0000000187G`; exactly 49 of 50 accounts changed |
| 7 | `InterestTest.zeroRateWritesNothingAndKeepsSuffix` (rate-0 → none, next suffix 000001, 0.00 txn written) |
| 8 | `AbendTest` ×3 (Java) and `legacy/tests/cbact04c_abend.sh` (real COBOL: RC 16, same lines) |
| 9 | `ClockTest`: `2023010203040506` → `2023-01-02-03.04.05.060000` in both TS fields; precedence `--now` > `CARDDEMO_CURRENT_DATE` > `COB_CURRENT_DATE` |

Defects also pinned: D4 (50 fallbacks), D5 (all 50 stock txns are 0.00), D6 (DISCGRP open text), D7 (overflow wraps, no exception; `ZonedTest`, `DefectTest.d7`).

## Iterations

0 fix iterations on the Java. The first test runs failed only because of mistakes in my new tests (wrong amount offset, wrong fixture account, wrong expected overpunch digits). I corrected those in the tests. The production code is unchanged.

## Not verified / for a human

- **Missing input file** (open failure, status `NNNN0035` and the `…XREFFILE35` text in `Cbact04c.open`) was guessed in the Build stage and is **not checked against COBOL**. It is not in the AC. `DefectTest.d6` only pins the DISCGRP text. Consider removing the guessed status or checking it in a later change.
- The spec's ACCTFILE offsets (77/89/111) are off by 2. The code uses the copybook (79/91/113), and the goldens confirm that. Fix spec.md if it matters.
- The PIC 9(6) suffix wrap is implemented, but the test is arithmetic only (a real run would need 1M records).
- The abend tests check the SYSOUT/RC messages, not output files after an abend (outside the contract).
- Defect decisions (D1 money impact first) remain open for a separate CR.
