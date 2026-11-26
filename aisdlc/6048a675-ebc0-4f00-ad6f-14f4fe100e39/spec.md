# CR-1 Spec: `CBACT04C` in Java 21 (behaviour-preserving)

Legacy behaviour, file table and findings F1-F6: see [intent.md](intent.md). This spec fixes only what the Java must do.
The answers assumed for Q1-Q3 are the intent's defaults: SYSOUT parity, exit 16 with no rollback, preserve defects.

## Interface

- `java -jar cbact04c.jar <PARM> <IN_DIR> <OUT_DIR>`. Reads `<IN_DIR>/{TCATBALF,XREFFILE,ACCTFILE,DISCGRP}.txt` and writes `<OUT_DIR>/{TRANSACT,ACCTFILE}.txt`. SYSOUT goes to stdout. The exit code is the RC.
- PARM: the first 10 chars, space-padded if shorter. F6 note: COBOL takes the bytes after a short PARM, which we cannot replicate. Only 10-char PARMs are supported, and others are pinned as "pad with spaces".
- Clock: `java.time.Clock` is injected. The CLI uses env `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`, the same as the harness) if set, otherwise the system clock in UTC.

## Record I/O (canonical text, `legacy/README.md` "Outputs")

- Read: split on LF and strip a trailing CR (AC4). Right-pad each line with spaces to its LRECL, so XREF's 36 bytes become 50. Skip a final empty line.
- Write: every record exactly LRECL bytes, each followed by `\n`. Encoding: ISO-8859-1, byte-for-byte.
- Zoned S9(n)V99 codec: the last byte carries the sign (`{`/`A`-`I` positive, `}`/`J`-`R` negative). Values are decoded to `BigDecimal` scale 2. Encoding always emits overpunch, `{` for +0 (matches golden `0000000000{`).

| File / copybook | Fields read or written (1-based col, len, PIC → Java) |
|---|---|
| `TCATBALF` [`app/cpy/CVTRA01Y.cpy`](../../app/cpy/CVTRA01Y.cpy) | acct 1/11 X; type 12/2 X; cat 13/4 X; bal 18/11 S9(9)V99 → BigDecimal |
| `XREFFILE` [`app/cpy/CVACT03Y.cpy`](../../app/cpy/CVACT03Y.cpy) | card 1/16 X; acct 26/11 X (alt key, first record in file order wins) |
| `ACCTFILE` [`app/cpy/CVACT01Y.cpy`](../../app/cpy/CVACT01Y.cpy) | id 1/11; curr-bal 13/12 S9(10)V99; cyc-credit 79/12, cyc-debit 91/12 S9(10)V99; group 113/10 X |
| `DISCGRP` [`app/cpy/CVTRA02Y.cpy`](../../app/cpy/CVTRA02Y.cpy) | key 1/16 (group+type+cat); rate 17/6 S9(4)V99 |
| `TRANSACT` [`app/cpy/CVTRA05Y.cpy`](../../app/cpy/CVTRA05Y.cpy) | written in full: see R6 |

ACCTFILE is held as an ordered map keyed by id, with each record kept as its raw 300 bytes. A rewrite splices in only the 3 changed fields. Output is in key order and every other byte is untouched.

## Processing rules (in order)

- R1. Print `START OF EXECUTION OF PROGRAM CBACT04C`. Load the inputs. If a file is missing or unreadable, print the matching open message from `CBACT04C.cbl:245-318`, verbatim (including `DALY REJECTS` for DISCGRP), then abend (R9).
- R2. For each TCATBAL record in file (key) order, print the 50-byte record.
- R3. Account break, when acct ≠ last acct:
  - If this is not the first account, apply R7 to the previous account.
  - Then `total = 0`, look up ACCTFILE by id, then XREF by acct.
  - Not found: print `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE` (or `XREF FILE`), `FILE STATUS IS: NNNN0023`, then abend.
- R4. Rate lookup:
  - Look up DISCGRP with key `group(10)+type(2)+cat(4)`.
  - On a miss, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with group `DEFAULT` padded to 10.
  - If the retry also misses, print `ERROR READING DEFAULT DISCLOSURE GROUP` and `FILE STATUS IS: NNNN0023`, then abend.
- R5. Only when rate ≠ 0:
  - `monthly = trunc9_2(bal × rate / 1200)`. Compute it exactly (BigDecimal), then `setScale(2, RoundingMode.DOWN)`, then keep the value modulo 10^9 with its sign (F3).
  - `total = trunc9_2(total + monthly)`.
  - Write a transaction (R6), even when monthly = 0 (F4).
- R6. TRANSACT record:
  - `id = PARM10 + %06d(++suffix)`, then `01`, `0005`, `System` padded to 10.
  - desc = `Int. for a/c ` + acct id (11), space-padded to 100.
  - amt = zoned 11 bytes; merchant id `000000000`; name, city and zip are spaces (50/50/10); card number (16).
  - orig-ts = proc-ts = `yyyy-MM-dd-HH.mm.ss.hh0000`, read from the clock at write time.
  - 20 spaces.
- R7. Account update:
  - `curr-bal = mod10^10(curr-bal + total)`, keeping the sign (F3).
  - Cycle credit and debit are set to `+0`. The record is then replaced in the map.
- R8. At EOF, stop. The last account is **not** updated (F1). Print `END OF EXECUTION OF PROGRAM CBACT04C`, write the outputs, exit 0.
- R9. Abend: print `ABENDING PROGRAM`. Write TRANSACT so far and ACCTFILE with the R7 updates applied so far (the same as COBOL's in-place REWRITE, with no rollback). Exit 16.

## Edge cases

| Case | Handling |
|---|---|
| Negative balance | Truncates toward zero (−1.456 → −1.45), overpunch `}`/`J`-`R` |
| Group present in DISCGRP | Uses that rate. No MISSING lines (golden data never hits this; unit test) |
| Rate 0 (incl. DEFAULT 03/0001) | No transaction and no total change. Account still rewritten at the next break |
| Several categories per account | Totals accumulate. A single rewrite at the break |
| Duplicate XREF acct | First record in primary-key (file) order |
| Empty TCATBALF | Banners only. No rewrite. Empty TRANSACT. ACCTFILE unchanged. RC 0 |

## Preserved defects (fix later, deliberately)

- **F1** The last account's interest is never posted to ACCTFILE (R8).
- **F2** Interest is truncated, not rounded (R5).
- **F3** Monthly, total and balance overflow wrap silently (R5, R7).
- **F4** Zero-amount interest transactions are written (R5).
- **F5** The lookup uses blank `ACCT-GROUP-ID`, so the sample data always falls back to DEFAULT (R4).
- **F6** Wrong DISCGRP open message. PARM length is ignored. Timestamps are hundredths only.

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -q verify` on Java 21, JUnit 5 only | 1 |
| `GoldenTest.stock`: byte-compare TRANSACT, ACCTFILE and SYSOUT with `legacy/golden/CBACT04C/`; RC 0 | 2, 9 |
| `GoldenTest.afterCbtrn02c`: ACCTFILE and TCATBALF from `legacy/golden/CBTRN02C/`; compare with `…/CBACT04C-after-CBTRN02C/` | 3, 9 |
| `RecordIoTest`: CRLF input and 36-byte XREF are padded and accepted | 4 |
| `InterestTest`: 116.48 × 15 → 1.45; −116.48 → −1.45; overflow wraps | 5, F3 |
| `DefectsTest.lastAccountNotPosted`: account `00000000050` identical to input | 6 |
| `DefectsTest.zeroBalance` / `zeroRate` | 7 |
| `AbendTest`: missing group → DEFAULT; missing DEFAULT / account / XREF → messages and RC 16 | 8 |
| `legacy/tests/cbact04c-diff.sh`: COBOL vs Java on crafted inputs (negative, real group, multi-cat, abend) | 5, 7, 8 |
