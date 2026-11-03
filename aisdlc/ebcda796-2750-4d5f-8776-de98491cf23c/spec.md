# CR-1 Spec: `CBACT04C` in Java 21 (behaviour-preserving)

Source of truth for legacy behaviour: [intent.md](intent.md). This spec only adds the details the Java code must get exactly right.
Approver defaults are taken as accepted: Q1 SYSOUT parity **yes**, Q2 text I/O **yes**, Q3 preserve D1/D2.

## Invocation

`java -jar cbact04c.jar --data <dir> [--input-dir <dir>] --out <dir> --parm 2022071800 [--clock 2022071800000000]`

- Input resolution mirrors `legacy/harness/run.sh:48-51,96-99`. If `<input-dir>/<DD>.txt` exists, it is used. Otherwise the sample in `--data` is used: `tcatbal.txt`, `cardxref.txt`, `acctdata.txt`, `discgrp.txt`.
- `--out` receives `TRANSACT.txt`, `ACCTFILE.txt`, `SYSOUT.txt` and `RC.txt`, the same layout as `run.sh`. SYSOUT also goes to stdout.
- `--clock` is `YYYYMMDDHHMMSSss`, the same form as the harness's `COB_CURRENT_DATE`. It defaults to the system clock in UTC. Internally this is a `java.time.Clock`.
- Exit codes: `0` for success, `16` for abend (after `ABENDING PROGRAM` is printed; `CEE3ABD: ABEND U0999` goes to stderr).

## Record I/O

- Load: split on LF, strip a trailing CR, right-pad to LRECL with spaces, and ignore a final empty line. Write: exactly LRECL bytes, then LF.
- Charset is ISO-8859-1 (1 char = 1 byte).
- KSDS files are loaded into a `TreeMap` keyed by the primary-key bytes. `ACCTFILE` is written back in full, in key order.

| File | Copybook | Fields used (0-based offset, length, PIC → Java) |
|---|---|---|
| TCATBALF (50) | `app/cpy/CVTRA01Y.cpy` | acct-id 0,11 `9(11)`→String; type 11,2 `X`; cat 13,4 `9(4)`; `TRAN-CAT-BAL` 17,11 `S9(9)V99`→BigDecimal |
| XREFFILE (50) | `app/cpy/CVACT03Y.cpy` | card-num 0,16 `X`; acct-id 25,11 `9(11)` (alt key) |
| ACCTFILE (300) | `app/cpy/CVACT01Y.cpy` | acct-id 0,11; `CURR-BAL` 12,12 `S9(10)V99`; `CYC-CREDIT` 81,12; `CYC-DEBIT` 93,12; `GROUP-ID` 115,10 `X` |
| DISCGRP (50) | `app/cpy/CVTRA02Y.cpy` | key 0,16 = group(10)+type(2)+cat(4); `DIS-INT-RATE` 16,6 `S9(4)V99`→BigDecimal |
| TRANSACT (350) | `app/cpy/CVTRA05Y.cpy` | written in full (rule 6) |

**Zoned codec.** The last byte carries both digit and sign: `{`/`A-I` = +0..9, `}`/`J-R` = -0..9. Plain digits `0-9` are also accepted as +. Encode: positive or zero → `{A-I`, negative → `}J-R`. Zero always encodes as `{`. Values that do not fit are truncated at the high-order end (mod 10^n), as COBOL `MOVE`/`ADD` does without `ON SIZE ERROR`.

## Processing rules (in order, mirrors `CBACT04C.cbl:184-232`)

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`. A missing or unreadable input file → open error abend: message text from the COBOL paragraph (incl. D9 text), then `FILE STATUS IS: NNNN00xx`, then `ABENDING PROGRAM`, exit 16.
2. For each TCATBALF record in file order: print the full 50-byte record, untrimmed (golden `SYSOUT.txt` rows are 50 bytes).
3. If acct-id ≠ last acct-id:
   - Unless this is the first record, rewrite the previous account (rule 7).
   - Set total = 0.
   - Look up ACCTFILE by acct-id. If missing: print `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`, then abend.
   - Look up the xref by alt key: the first match in card-num order. If missing: print `ACCOUNT NOT FOUND: <id>`, `ERROR READING XREF FILE`, `FILE STATUS IS: NNNN0023`, then abend.
4. Rate lookup key = `ACCT-GROUP-ID` + type + cat.
   - If missing: print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with `DEFAULT` padded to 10.
   - If `DEFAULT` is also missing: print `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, then abend.
5. If rate ≠ 0:
   - `monthly = (bal × rate) / 1200`, using BigDecimal `divide(1200, 2, RoundingMode.DOWN)`, i.e. truncated toward zero. No double anywhere.
   - Truncate `monthly` to `S9(9)V99`. Add it to `total`, also `S9(9)V99`.
   - Write a transaction. Fees are a no-op (D4).
6. TRANSACT record: `PARM(10)` + suffix `%06d` (counter starting at 1, kept mod 10^6) | `01` | `0005` | `System` padded to 10 | `Int. for a/c ` + acct-id(11), padded to 100 | amount zoned 11 | `000000000` | spaces 110 | card-num 16 | ts 26 | ts 26 | spaces 20.
   - `ts = yyyy-MM-dd-HH.mm.ss.hh0000`. The clock is read once per transaction.
7. Account rewrite: `CURR-BAL = trunc12(CURR-BAL + total)`. Set `CYC-CREDIT` and `CYC-DEBIT` to zero (`00000000000{`). Every other byte stays as read.
8. At EOF: **do not** rewrite the last account (D1). Write the files and print `END OF EXECUTION OF PROGRAM CBACT04C`. Exit 0.

If an abend happens, only `SYSOUT.txt` and `RC.txt` (`16`) are written, as with `run.sh:120`.

## Edge cases

| Case | Handling |
|---|---|
| Empty TCATBALF | Only the 2 banner lines. ACCTFILE unchanged, TRANSACT empty, exit 0. |
| Rate 0 | No transaction, total unchanged, no suffix increment. |
| Balance 0, rate ≠ 0 | Writes a `0000000000{` transaction (D6). |
| Negative balance | Negative interest, truncated toward zero (e.g. -0.009 → -0.00, encoded `{`) (D7). |
| Same account in non-adjacent TCATBAL rows | Treated as a new break each time, as in COBOL (key order makes this impossible in practice). |
| CRLF or short input lines | Strip and pad (R2). |

## Preserved defects (fix deliberately later)

- **D1** The last account is never rewritten: its interest is in TRANSACT but not in ACCTFILE.
- **D2** Interest is truncated, not rounded.
- **D3** Blank `ACCT-GROUP-ID` means every rate comes from `DEFAULT`, with 2 SYSOUT lines per row.
- **D4** Fees are not computed.
- **D5** Timestamps come from the clock, not from PARM.
- **D6** Zero-amount transactions are written.
- **D7** No negative-balance guard. The tran-ID suffix and amounts wrap silently.
- **D8** For an account with several cards, the first card is used.
- **D9** The DISCGRP open error says "DALY REJECTS FILE".

## Acceptance tests (→ intent AC)

| AC | Test |
|---|---|
| 1 | `mvn -q verify` (JUnit 5 only) |
| 2 | `GoldenTest.stock`: `app/data/ASCII` → byte-compare TRANSACT, ACCTFILE, SYSOUT with `legacy/golden/CBACT04C/` |
| 3 | `GoldenTest.afterCbtrn02c`: `--input-dir legacy/golden/CBTRN02C` → `legacy/golden/CBACT04C-after-CBTRN02C/` |
| 4 | `InterestTest`: 0.40 × 15.00 / 1200 = 0.005 → `0.00` (half-up: 0.01); -0.40 → `-0.00` encoded `{`; 1945.87 → 24.32 |
| 5 | `DefectTest.lastAccountNotRewritten`: account 50 bytes in = out, its transaction amount `0000000187G` |
| 6 | `DiscGrpTest`: group present → used; group missing → DEFAULT + 2 lines; DEFAULT missing → exit 16 |
| 7 | `AbendTest`: account missing / xref missing → exit 16, SYSOUT messages as rule 3 |
| 8 | `RateZeroTest`: no transaction, balance += 0 |
| 9 | covered by AC2/AC3 SYSOUT compares + exit codes |
| 10 | `legacy/harness/golden.sh --check`; `git diff --stat baseline -- legacy .github` empty |
| R1 | `ZonedCodecTest`: all 20 sign chars, round-trip, -0 → `{`, overflow truncation |
