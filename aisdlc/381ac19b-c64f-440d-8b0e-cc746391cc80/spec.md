# CR-1 Spec — `CBACT04C` in Java 21 (behaviour-preserving)

Legacy behaviour, file table and defects D1–D8: see [intent.md](intent.md). This spec only pins down what the Java code must reproduce.

## Interface

`java -jar cbact04c.jar <PARM-DATE> [--TCATBALF=f] [--XREFFILE=f] [--DISCGRP=f] [--ACCTFILE=f] [--out=dir]`

- Each input defaults to `./<DD>.txt`. The outputs `TRANSACT.txt` and `ACCTFILE.txt` go to `--out` (default `.`). The input `ACCTFILE` is never overwritten.
- SYSOUT goes to stdout, one DISPLAY per line, LF line endings.
- Exit code: 0 on success, 16 on abend. On abend, `CEE3ABD: ABEND U0999` goes to stderr (as the harness stub does).
- Clock: `CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss` freezes it. Otherwise the system clock is used, in UTC. The clock is read once per transaction written.
- PARM: the first 10 characters, right-padded with spaces (COBOL `PIC X(10)`).

## Record I/O

- **Read:** strip a trailing `\r`, then right-pad with spaces to the LRECL (`cardxref.txt` lines are 36 bytes; the LRECL is 50).
- **Write:** exactly the LRECL in bytes, ASCII, `\n` after every record (including the last).
- **Overpunch:** the last byte of a signed zoned field carries the sign. `{`,`A`–`I` mean +0..9 and `}`,`J`–`R` mean −0..9. On write, positive (and zero) values use `{`/`A`–`I` and negative values use `}`/`J`–`R`. Use a single codec class with `BigDecimal` and a fixed scale.

| File (copybook) | Fields used | Offset / len (1-based) | PIC → Java |
|---|---|---|---|
| TCATBALF [`CVTRA01Y`](../../app/cpy/CVTRA01Y.cpy) | acct-id, type, cat, `TRAN-CAT-BAL` | 1/11, 12/2, 14/4, 18/11 | 9(11) String; X(2); 9(4) String; S9(9)V99 BigDecimal |
| XREFFILE [`CVACT03Y`](../../app/cpy/CVACT03Y.cpy) | card-num, acct-id (alt key) | 1/16, 26/11 | X(16); 9(11) String |
| DISCGRP [`CVTRA02Y`](../../app/cpy/CVTRA02Y.cpy) | key group+type+cat, `DIS-INT-RATE` | 1/16, 17/6 | String; S9(4)V99 BigDecimal |
| ACCTFILE [`CVACT01Y`](../../app/cpy/CVACT01Y.cpy) | acct-id, `CURR-BAL`, `CURR-CYC-CREDIT`, `CURR-CYC-DEBIT`, `GROUP-ID` | 1/11, 13/12, 80/12, 92/12, 114/10 | String; S9(10)V99 ×3; X(10) |
| TRANSACT [`CVTRA05Y`](../../app/cpy/CVTRA05Y.cpy) | all fields, see intent step 5 | 350 bytes | `TRAN-AMT` S9(9)V99 at 133/11 |

- ACCTFILE is held as raw 300-byte records keyed by acct-id. The update overwrites only bytes 13–24, 80–91 and 92–103; every other byte is copied through unchanged. Output is the whole file in ascending key order.
- XREFFILE alt-key index: acct-id → the **first** record in card-key (file) order (intent R2).

## Processing rules (in order)

1. DISPLAY `START OF EXECUTION OF PROGRAM CBACT04C`.
2. For each TCATBALF record, in file (key) order: DISPLAY the 50-byte padded record.
3. If the acct-id ≠ the last acct-id: unless this is the first record, **rewrite the previous account** (rule 6). Then set total-int = 0, look up the account and then the xref.
   - Account missing: DISPLAY `ACCOUNT NOT FOUND: <acct>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM`; exit 16.
   - Xref missing: the same lines, with `ERROR READING XREF FILE`.
4. Rate: key = `ACCT-GROUP-ID` (10 bytes, as stored) + type + cat.
   - If it is missing: DISPLAY `DISCLOSURE GROUP RECORD MISSING`, `TRY WITH DEFAULT GROUP CODE`, then retry with `DEFAULT   ` + the same type + cat.
   - If that is also missing: DISPLAY `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM`; exit 16.
5. If rate ≠ 0:
   - interest = `bal × rate / 1200`, computed exactly. Scale to 2 decimals with `RoundingMode.DOWN` (toward zero), then reduce modulo 10^9 keeping the sign (S9(9)V99 high-order truncation).
   - total-int = (total-int + interest), with the same truncation.
   - suffix = (suffix + 1) mod 10^6. Write the TRANSACT record: id = PARM + 6-digit zero-padded suffix, plus the fields in intent step 5. The filler is spaces. Timestamp format: `YYYY-MM-DD-HH.MM.SS.hh0000`.
6. Rewrite of an account:
   - bal = (bal + total-int), reduced modulo 10^10 keeping the sign (S9(10)V99, no SIZE ERROR).
   - cyc-credit = 0 and cyc-debit = 0.
7. At EOF, **do not** rewrite the last account (D1). DISPLAY `END OF EXECUTION OF PROGRAM CBACT04C`; exit 0.

## Edge cases

| Case | Handling |
|---|---|
| Empty TCATBALF | Only the START and END lines. TRANSACT is empty, ACCTFILE is unchanged, RC 0. |
| Negative balance | Truncate toward zero: −0.005 → 0.00, written as `{`. A harness test confirms this (plan T3). |
| Rate 0 (e.g. `DEFAULT/03/0001`) | Nothing is written and total-int is unchanged. |
| Balance 0, rate ≠ 0 | A zero-amount transaction is written (D8). |
| Account appears again later, non-contiguously | Treated as a new break, so it is rewritten again (follows the code). |
| Malformed numeric field | Not defined by the COBOL. Java DISPLAYs `ERROR READING <DD>` and exits 16. This is the only deliberate divergence. |

## Preserved defects (one line each, fix later)

- **D1** The last account's interest is never rewritten.
- **D2** `ACCT-GROUP-ID` is blank, so every lookup falls back to DEFAULT.
- **D3** Interest is truncated, not rounded.
- **D4** No fees.
- **D5** Timestamps use the clock, not the PARM.
- **D6** The suffix and the balances wrap silently.
- **D7** The DISCGRP open error says `DALY REJECTS` (Java opens files up front and prints the same text when the open fails).
- **D8** Zero-amount transactions are written.

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -q verify` on Java 21, JUnit 5 only | 1, 8 |
| `GoldenTest.stock`: `app/data/ASCII` inputs, PARM `2022071800`, clock `2022071800000000`. TRANSACT, ACCTFILE and SYSOUT are byte-equal to `legacy/golden/CBACT04C/`, RC 0 | 2, 4 |
| `GoldenTest.afterCbtrn02c`: ACCTFILE/TCATBALF from `golden/CBTRN02C/`. Byte-equal to `golden/CBACT04C-after-CBTRN02C/`, RC 0 | 3, 4 |
| `InterestTest`: 1164.87 × 15.00 → 14.56; negative values truncate toward zero | 5 |
| `DefectTest.d1`: account `00000000050` is byte-equal to its input record, and TRANSACT #50 amount is `0000000187G` | 6 |
| `RateTest`: rate 0 writes nothing; DEFAULT fallback; missing DEFAULT/account/xref → exit 16 + exact SYSOUT lines | 7 |
| `OverpunchTest`: round-trip for every sign byte; −0 is written as `{` | R1 |
| `CliTest`: runs the shaded jar in a separate process with paths given on the command line | 8 |
