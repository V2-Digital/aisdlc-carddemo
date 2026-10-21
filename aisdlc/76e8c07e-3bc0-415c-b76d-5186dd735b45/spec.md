# CR-1 Spec — `CBACT04C` in Java 21 (behaviour-preserving)

The legacy behaviour, with file:line refs, is in [intent.md](intent.md). This spec pins down what the Java code must reproduce **byte for byte**.

## Interface

`java -jar cbact04c.jar --in <dir> --out <dir> [--parm 2022071800] [--clock 2022071800000000]`

- It reads `<in>/{TCATBALF,XREFFILE,ACCTFILE,DISCGRP}.txt`.
- It writes `<out>/{TRANSACT,ACCTFILE}.txt`.
- DISPLAY lines go to **stdout**. The abend line `CEE3ABD: ABEND U0999` goes to **stderr**, as in `legacy/harness/cee3abd.cbl`.
- The exit code is 0, or 16 on abend.
- `--clock` uses the `COB_CURRENT_DATE` form `YYYYMMDDHHMMSSss`. If it is absent, the program uses `CARDDEMO_CURRENT_DATE` and then the system clock (UTC).
- The core API is `Cbact04c.run(in, out, parm, Supplier<String> clock, PrintStream sysout, PrintStream syserr) → int`, so tests can inject everything.

## Files (canonical text: one fixed-width record per line, LF, trailing spaces kept)

| DD | Copybook | Use | Fields read / written (1-based col, len, PIC → Java) |
|---|---|---|---|
| TCATBALF | `app/cpy/CVTRA01Y.cpy` | read, file order | acct 1/11 `9(11)`→String; type 12/2 `X`; cat 14/4 `9(4)`; bal 18/11 `S9(9)V99`→BigDecimal |
| XREFFILE | `app/cpy/CVACT03Y.cpy` | map acct→card | card 1/16 `X`; acct 26/11 `9(11)`. The **first** line per acct wins. |
| ACCTFILE | `app/cpy/CVACT01Y.cpy` | map id→record, rewritten | id 1/11; curr-bal 13/12 `S9(10)V99`; cyc-credit 71/12, cyc-debit 83/12 `S9(10)V99`; group-id 105/10 `X` |
| DISCGRP | `app/cpy/CVTRA02Y.cpy` | map key(1/16)→rate | group 1/10, type 11/2, cat 13/4; rate 17/6 `S9(4)V99`→BigDecimal |
| TRANSACT | `app/cpy/CVTRA05Y.cpy` | written, 350 bytes | all fields, see R5 |

Zoned decimal: the last byte carries the sign.
- `{`=+0, `A`–`I`=+1..9, `}`=-0, `J`–`R`=-1..9.
- A plain digit is read as positive.
- On write, use `{`/`A`–`I` for values ≥ 0 and `}`/`J`–`R` for values < 0. Zero is always `{`.
- On write, digits are **mod 10^n**: the high-order digits are dropped (COBOL truncation without SIZE ERROR).

Records are kept as `byte[]`/`String` and patched in place. Any byte the program does not write (FILLER, address, dates) is copied through unchanged. The output ACCTFILE contains every input record in **primary-key order**, untouched records included.

## Processing rules (in order)

- **R1 Open.** Each input must exist and be readable, checked in the COBOL order TCATBALF, XREFFILE, DISCGRP, ACCTFILE, TRANSACT. On failure the program prints the legacy line and abends. The lines are:
  - `ERROR OPENING TRANSACTION CATEGORY BALANCE`
  - `ERROR OPENING CROSS REF FILE` + status
  - `ERROR OPENING DALY REJECTS FILE` (D5)
  - `ERROR OPENING ACCOUNT MASTER FILE`
  - `ERROR OPENING TRANSACTION FILE`

  The status line is `FILE STATUS IS: NNNN0035`. Then `ABENDING PROGRAM` and exit 16.
- **R2** Print `START OF EXECUTION OF PROGRAM CBACT04C`.
- **R3** For each TCATBAL line in file order, print the 50-byte record exactly as read.
- **R4 Control break.** When the acct differs from the last one:
  - If this is not the first account, apply R7 to the *previous* account.
  - Set total to 0.
  - Look up the account. If it is missing, print `ACCOUNT NOT FOUND: <11-digit id>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`, then abend.
  - Look up XREF by acct. If it is missing, print the same three lines with `ERROR READING XREF FILE`, then abend.
- **R5 Rate.** Look up the key `ACCT-GROUP-ID` (as stored, often blank) + type + cat.
  - Missing → print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with `DEFAULT` padded to 10 characters.
  - The retry is also missing → print `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, then abend.
- **R6 Interest** (only when rate ≠ 0):
  - `int = (bal × rate / 1200)` with `setScale(2, RoundingMode.DOWN)`, which truncates toward zero, then mod 10^9 (S9(9)V99).
  - `total += int`, mod 10^9.
  - `suffix += 1`. The suffix is global across the run, `%06d`.
  - Write a TRANSACT record with these fields:
    - id = parm(10) + suffix
    - type `01`, cat `0005`
    - source `System` padded to 10
    - desc `Int. for a/c ` + acct id(11), padded to 100
    - amt = int (zoned 11)
    - merchant id `000000000`
    - name/city/zip spaces (50/50/10)
    - card(16) from XREF
    - orig-ts = proc-ts = `YYYY-MM-DD-HH.MM.SS.hh0000`, taken from the clock **for each record**
    - 20 spaces
  - Fees: no-op.
- **R7 Update account.**
  - `curr-bal += total` (mod 10^10, scale 2).
  - Set cyc-credit and cyc-debit to 0.
  - Store the record.
- **R8 End of file.** **Do not apply R7 to the last account (D1).**
  - Print `END OF EXECUTION OF PROGRAM CBACT04C`.
  - Write `TRANSACT.txt` and `ACCTFILE.txt`, then exit 0.
  - On abend, nothing is written to `<out>`. This matches the harness, which exports nothing on RC 16.

## Edge cases

| Case | Handling |
|---|---|
| Empty TCATBALF | Prints only the banners. TRANSACT is empty and ACCTFILE is unchanged. RC 0. |
| Same acct on non-adjacent TCATBAL lines | Each new run of the acct triggers a break. The account is re-read from the **updated** map (the same as a VSAM re-read after REWRITE), so updates add up. Golden input is sorted, so this never happens there. |
| Rate = 0, e.g. DEFAULT `03/0001` | No txn and no suffix bump. The account is still rewritten (D3). |
| Negative balance or rate | Negative interest, truncated toward zero (D4). The sign is written as `}`/`J`–`R`. |
| A result that truncates to -0.00 | Written as `0000000000{`. |
| XREF has two cards for one acct | The first in file (key) order wins. Golden has 0 duplicates. |
| Line shorter than LRECL / CRLF | The CR is stripped and the line padded with spaces to LRECL, as the harness load normalises. |

## Preserved defects (fix later, deliberately)

- **D1** The last TCATBAL account is never rewritten, but its interest txn is written.
- **D2** Interest is truncated, not rounded. The customer is under-charged by up to 0.01 per category.
- **D3** Cycle credit/debit are zeroed for every account in TCATBAL, even when no interest is charged.
- **D4** A negative balance gives a negative interest txn (a credit).
- **D5** A DISCGRP open failure prints `ERROR OPENING DALY REJECTS FILE`.
- Fees are not implemented (CR-2).

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -f modernized/cbact04c test` green; the pom has only JUnit 5 + Surefire | 1 |
| `GoldenTest.stock`: stock inputs, compared byte-for-byte with `legacy/golden/CBACT04C/{TRANSACT,ACCTFILE,SYSOUT,RC}.txt` | 2, 4 |
| `GoldenTest.afterCbtrn02c`: ACCTFILE + TCATBALF from `legacy/golden/CBTRN02C/`, compared with `legacy/golden/CBACT04C-after-CBTRN02C/` | 3, 4 |
| `InterestTest.truncatesNotRounds`: 3577.41 × 15 / 1200 → `0000000447A`, i.e. 44.71 | 5 (D2) |
| `DefectTest.lastAccountNotRewritten`: acct 50 is byte-equal to its input; its txn exists | 6 (D1) |
| `DefectTest.cycleZeroedWithoutInterest` (D3), `negativeBalanceCredit` (D4) | — |
| `LookupTest.groupHit` (ACCT-GROUP-ID `A000000000`, no fallback lines), `defaultFallback`, `defaultMissing→16`, `accountMissing→16`, `xrefMissing→16`, `discgrpOpenFails→D5 text` | 7 |
| All tests inject `--parm`/clock; with the same inputs, two runs give identical output | 8 |
| `legacy/harness/golden.sh --check` passes; `git diff --stat baseline -- legacy/golden legacy/harness .github` is empty | 9 |
| `legacy/tests/cbact04c_equiv.sh`: COBOL vs Java on the non-golden cases (group hit, negative balance, missing DEFAULT) | 4, 7 (D4 evidence) |
