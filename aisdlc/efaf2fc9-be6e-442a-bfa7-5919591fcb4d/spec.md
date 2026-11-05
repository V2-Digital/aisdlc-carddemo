# CR-1 Spec — `CBACT04C` in Java 21 (behaviour-preserving)

Legacy behaviour, file table and defects: [intent.md](intent.md). This spec only pins down what the Java must reproduce. Q1–Q3 are taken at their defaults: SYSOUT/RC parity, text datasets, and no R1 fix.

## Interface

`java -cp target/classes carddemo.cbact04c.Cbact04c <PARM>`. PARM is the 10-byte run date, e.g. `2022071800`.

- **Datasets** come from env `DD_<ddname>` (same binding as the harness): `TCATBALF`, `XREFFILE`, `DISCGRP`, `ACCTFILE` (read, then rewritten in place), `TRANSACT` (created or truncated).
- **Text form:** one record per line, LF. On read, strip `\r` and right-pad with spaces to LRECL (`cardxref.txt` lines are 36 bytes). On write, emit exactly LRECL bytes + `\n`, ISO-8859-1.
- **Clock:** env `CARDDEMO_CURRENT_DATE` = `YYYYMMDDHHMMSSss` (16 digits, as in the harness). If unset, use the system clock in UTC.
- **SYSOUT** → stdout. **RC** → process exit code (0, or 16 on abend).

## Fields used (1-based columns)

| Copybook | Field | Cols | PIC | Java |
|---|---|---|---|---|
| [`CVTRA01Y`](../../app/cpy/CVTRA01Y.cpy) TCATBAL | `TRANCAT-ACCT-ID` / `TYPE-CD` / `CD` | 1-11 / 12-13 / 14-17 | 9(11) / X(2) / 9(4) | String |
| | `TRAN-CAT-BAL` | 18-28 | S9(9)V99 | BigDecimal |
| [`CVACT03Y`](../../app/cpy/CVACT03Y.cpy) XREF | `XREF-CARD-NUM` / `XREF-ACCT-ID` (lookup key) | 1-16 / 26-36 | X(16) / 9(11) | String |
| [`CVTRA02Y`](../../app/cpy/CVTRA02Y.cpy) DISCGRP | key group+type+cat | 1-16 | X(10)X(2)9(4) | String |
| | `DIS-INT-RATE` | 17-22 | S9(4)V99 | BigDecimal |
| [`CVACT01Y`](../../app/cpy/CVACT01Y.cpy) ACCT | `ACCT-ID` | 1-11 | 9(11) | String key |
| | `ACCT-CURR-BAL` | 13-24 | S9(10)V99 | BigDecimal, rewritten |
| | `ACCT-CURR-CYC-CREDIT` / `DEBIT` | 179-190 / 191-202 | S9(10)V99 | set to `+0` (`00000000000{`) |
| | `ACCT-GROUP-ID` | 213-222 | X(10) | String |
| [`CVTRA05Y`](../../app/cpy/CVTRA05Y.cpy) TRAN (out, 350) | built from spaces. All columns are listed in rule 6 | | | |

All other bytes of the account record are copied through unchanged.

**Zoned codec.** The last digit carries the sign: `{`/`A`-`I` = +0..9 and `}`/`J`-`R` = −0..9. On write, positive or zero uses `{A-I` and negative uses `}J-R`. On read, accept plain digits as positive.

## Processing rules (in order)

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`. Load all inputs, then open/truncate TRANSACT. An unreadable input → abend.
2. For each TCATBALF line in file order: count it and print the 50-byte record verbatim.
3. **Account break** (acct id ≠ last). If this is not the first record, apply rule 7 to the *previous* account. Then: total = 0, look up ACCT by id, look up XREF by acct id (first match in file order).
4. **Rate.** Key = `ACCT-GROUP-ID`+type+cat.
   - On a miss, print `DISCLOSURE GROUP RECORD MISSING`, then `TRY WITH DEFAULT GROUP CODE`, and retry with `DEFAULT` padded to 10.
   - A miss on the retry → abend.
5. **If rate ≠ 0:**
   - `monthly = trunc(bal × rate / 1200)`: exact BigDecimal, `setScale(2, DOWN)`, then drop high-order digits to 9 integer digits (mod 10^9, sign kept).
   - `total += monthly`, also mod 10^9.
   - Write a transaction. Fees: no-op.
6. **Transaction record.** Suffix = `(suffix+1) mod 10^6`, 6 digits.

   | Cols | Value |
   |---|---|
   | 1-16 | PARM + suffix |
   | 17-18 | `01` |
   | 19-22 | `0005` |
   | 23-32 | `System` |
   | 33-132 | `Int. for a/c ` + ACCT-ID |
   | 133-143 | monthly, zoned |
   | 144-152 | `000000000` |
   | 153-262 | spaces |
   | 263-278 | card number |
   | 279-304, 305-330 | timestamp `YYYY-MM-DD-HH.MM.SS.hh0000` |
   | 331-350 | spaces |

   The timestamp is read from the clock once per transaction.
7. **Account update.** `CURR-BAL = (CURR-BAL + total)` mod 10^10 integer digits. Both cycle fields = 0. Replace the record in memory.
8. **EOF:** do **not** apply rule 7 to the last account (R1). Write ACCTFILE (all records, input order = key order). Print `END OF EXECUTION OF PROGRAM CBACT04C`. Exit 0.

## Error paths (abend = print the lines, flush files, print `CEE3ABD: ABEND U0999` to stderr, exit 16)

| Trigger | SYSOUT lines before `ABENDING PROGRAM` |
|---|---|
| Account missing | `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023` |
| Xref missing | `ACCOUNT NOT FOUND: <id>`, `ERROR READING XREF FILE`, `FILE STATUS IS: NNNN0023` |
| DEFAULT missing | `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023` |
| Input unreadable | the matching `ERROR OPENING …` text from `CBACT04C.cbl:245-318`, `FILE STATUS IS: NNNN0035` |

The rewrites and writes done before the abend persist, as they would on VSAM (R8). The `differential` test checks this against COBOL.

## Preserved defects (fix later, deliberately)

- **D1** The last account's interest is never added and its cycle fields are not reset (R1).
- **D2** Fees are not computed (R2).
- **D3** A transaction with amount 0 is written when the rate ≠ 0 and the balance is 0 (R3).
- **D4** Truncation, not rounding. Overflow silently drops high-order digits (R5).
- **D5** The TRAN-ID suffix wraps at 999,999, so IDs can repeat (R5).
- **D6** `ACCT-GROUP-ID` is used as stored: blank in the sample data, so every lookup falls back to DEFAULT (R4).

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -q verify`, only JUnit 5 + Surefire | 1 |
| `GoldenTest.stock`: TRANSACT/ACCTFILE/SYSOUT/RC byte-equal to `legacy/golden/CBACT04C/` | 2, 9 |
| `GoldenTest.afterCbtrn02c`: same against `legacy/golden/CBACT04C-after-CBTRN02C/` | 3, 9 |
| `InterestTest`: acct 2: 2339.97 @ 15.00 → `0000000292D`; negative balance → `}`/`J-R` sign | 4 |
| `RateLookupTest`: direct hit, no messages; miss → DEFAULT, 2 messages; rate 0 → no tran, total unchanged | 5 |
| `DefectTest.lastAccountNotRewritten`: acct 50 has a transaction, its record is unchanged | 6 |
| `TranRecordTest`: id, suffix wrap, timestamp format, spaces at 153-262 and 331-350 | 7 |
| `AbendTest`: each trigger → exit 16 and exact SYSOUT tail | 8 |
| `legacy/tests/cbact04c-differential.sh`: COBOL vs Java on crafted inputs | 4, 5, 8 (R5, R8 evidence) |
