# CR-1 Spec — `CBACT04C` in Java 21 (behaviour-preserving)

Run `5c27f7ac-7a36-4166-a830-3749ca91113d` · Stage: Design · Legacy behaviour, files and defects: [intent.md](intent.md)

Approver defaults are taken as decided: **preserve D1–D7**, **match SYSOUT and RC**, **canonical text I/O**.

## Interface

`java -jar cbact04c.jar --in <dir> --out <dir> [--parm 2022071800] [--clock 2022071800000000]`

- `--in` holds `TCATBALF.txt`, `XREFFILE.txt`, `ACCTFILE.txt`, `DISCGRP.txt` (JCL DD names). `--out` receives
  `TRANSACT.txt` and `ACCTFILE.txt`. SYSOUT (DISPLAY lines) goes to stdout. Exit code = RC (0, or 16 on abend).
- `--clock` uses the harness format `YYYYMMDDHHMMSSss`. Defaults: the PARM above and the system clock (UTC).
- Input load works like `cdfiles LOAD`: strip `\r`, then right-pad each line with spaces to its LRECL
  (`cardxref.txt` lines are 36 bytes). Output: one record per line, full LRECL, LF, ISO-8859-1 (1 char = 1 byte).

## Fields used (1-based cols; everything else is carried through as bytes)

| File / copybook | Field | Cols | PIC → Java |
|---|---|---|---|
| TCATBALF [`CVTRA01Y`](../../app/cpy/CVTRA01Y.cpy) | `TRANCAT-ACCT-ID` / `TYPE-CD` / `CD` | 1-11 / 12-13 / 14-17 | 9(11) / X(2) / 9(4) → `String` |
| | `TRAN-CAT-BAL` | 18-28 | S9(9)V99 zoned → `BigDecimal` scale 2 |
| XREFFILE [`CVACT03Y`](../../app/cpy/CVACT03Y.cpy) | `XREF-CARD-NUM` / `XREF-ACCT-ID` (alt key) | 1-16 / 26-36 | X(16) / 9(11) → `String` |
| ACCTFILE [`CVACT01Y`](../../app/cpy/CVACT01Y.cpy) | `ACCT-ID` (key) | 1-11 | 9(11) → `String` |
| | `ACCT-CURR-BAL` | 13-24 | S9(10)V99 → `BigDecimal` |
| | `ACCT-CURR-CYC-CREDIT` / `-DEBIT` | 179-190 / 191-202 | S9(10)V99 → `BigDecimal` |
| | `ACCT-GROUP-ID` | 213-222 | X(10) → `String` (not trimmed) |
| DISCGRP [`CVTRA02Y`](../../app/cpy/CVTRA02Y.cpy) | key group+type+cat | 1-16 | X(10)+X(2)+9(4) → `String` |
| | `DIS-INT-RATE` | 17-22 | S9(4)V99 → `BigDecimal` |
| TRANSACT [`CVTRA05Y`](../../app/cpy/CVTRA05Y.cpy) | all 14 fields written | 1-350 | see rule 6 |

Zoned decode/encode: last byte holds digit + sign (`{A-I` = +0..9, `}J-R` = -0..9). Decoding accepts both
`{` and `}` for zero. Encoding writes a zero result as `{` (positive). Values are stored **modulo 10^n**:
high-order digits are dropped, sign kept (COBOL MOVE/ADD without SIZE ERROR, D6).

## Processing rules (in order)

1. Write `START OF EXECUTION OF PROGRAM CBACT04C`. Load the inputs. ACCTFILE goes into a key-ordered map
   (raw 300-byte records). XREFFILE is indexed by alt key, and the **first record in file order** wins (D7).
2. For each TCATBALF record, in file order: write the raw 50-byte record to SYSOUT.
3. If acct-id ≠ last acct-id: if this is not the first account, **rewrite** the held account (rule 7). Then
   total = 0, read the account (rule 9 on a miss), and read xref by alt key (rule 9 on a miss).
4. Rate: key = held `ACCT-GROUP-ID` + type + cat. On a miss, write `DISCLOSURE GROUP RECORD MISSING`,
   `TRY WITH DEFAULT GROUP CODE`, and retry with `DEFAULT   ` (padded to 10). On a second miss, write
   `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, then abend.
5. If rate ≠ 0: `monthly = (bal × rate) / 1200` → `divide(1200, 2, RoundingMode.DOWN)` (truncate toward
   zero), then keep it mod 10^9. `total += monthly` (mod 10^9). Write a transaction (rule 6). Fees: no-op (D3).
6. Transaction: counter += 1 (global, 6 digits). `TRAN-ID` = PARM(10) + counter `%06d`. Type `01`.
   Cat `0005`. Source `System` (pad 10). Desc `Int. for a/c ` + ACCT-ID, padded to 100. Amt = zoned(monthly, 11).
   Merchant id `000000000`. Name/city/zip = spaces (50/50/10). Card = `XREF-CARD-NUM`.
   Orig-ts and proc-ts = `YYYY-MM-DD-HH.MM.SS.ss0000` from the clock. Cols 331-350 = spaces.
7. Rewrite: `CURR-BAL += total` (mod 10^10), set cycle credit and debit to `+0`. Only those 3 fields are
   re-encoded; the other 264 bytes are unchanged.
8. EOF: **no rewrite of the held account (D1)**. Write `END OF EXECUTION OF PROGRAM CBACT04C` and write the
   outputs. ACCTFILE is written in key order and includes accounts no TCATBAL record touched. RC 0.
9. Abend: write the error lines, then `ABENDING PROGRAM`, and print `CEE3ABD: ABEND U0999` to stderr.
   **Flush partial outputs** (TRANSACT written so far, ACCTFILE with rewrites done so far; D4). Exit 16.
   - Account miss: `ACCOUNT NOT FOUND: <acct>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`.
   - Xref miss: `ACCOUNT NOT FOUND: <acct>`, `ERROR READING XREF FILE`, `FILE STATUS IS: NNNN0023`.
   - Missing or unreadable input file: `ERROR OPENING <...>` (wording per `:234-323`, incl. D5) +
     `FILE STATUS IS: NNNN0035`.

## Edge cases

| Case | Handling |
|---|---|
| Negative balance | Truncate toward zero (−0.005 → 0.00, encoded `{`). Zoned `}J-R`. |
| Rate 0 (e.g. DEFAULT 03/0001) | No transaction, no counter increment. The account is still rewritten with cycle fields zeroed (unless it is the last one). |
| Several TCATBAL rows per account | Interest summed into one balance update; one transaction per non-zero-rate row. |
| Same acct id not contiguous | A new break each time: the account is re-read from the **updated** map, and rewritten again. |
| Empty TCATBALF | Only the START/END lines. ACCTFILE = input, TRANSACT empty, RC 0. |
| Accounts with several xref cards | The first in file order is used (D7). |

## Preserved defects (fix later, deliberately)

- **D1** The last account's interest and cycle reset are never written back.
- **D2** `ACCT-GROUP-ID` is blank in the data → every lookup uses DEFAULT (2 SYSOUT lines each).
- **D3** Fees are not computed.
- **D4** Abend leaves partial outputs, with no rollback.
- **D5** The DISCGRP open error says `ERROR OPENING DALY REJECTS FILE`.
- **D6** Overflow silently drops high-order digits (interest mod 10^9, balance mod 10^10).
- **D7** An account with several cards uses the first xref record.

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -f modernized/cbact04c test` green; pom has only JUnit 5 + Surefire | 1 |
| `GoldenEquivalenceTest.stock`: stock inputs → TRANSACT, ACCTFILE, SYSOUT (152), RC 0 byte-equal to `legacy/golden/CBACT04C/` | 2, 4 |
| `GoldenEquivalenceTest.afterCbtrn02c`: ACCTFILE+TCATBALF from `golden/CBTRN02C` → equal to `golden/CBACT04C-after-CBTRN02C/` (302 SYSOUT lines) | 3, 4 |
| `InterestTest.truncatesNotRounds` (100.40 × 15% / 12 = 1.255 → 1.25; rounding gives 1.26); negative case → toward zero | 5 |
| `CharacterizationTest.lastAccountNotRewritten`: acct 50 unchanged, TRANSACT charges `0000000187G` | 6 |
| `CharacterizationTest.defaultGroupFallback` + `missingDefaultAbends` (RC 16) | 7 |
| `CharacterizationTest.zeroRateNoTranButCycleZeroed` | 8 |
| `CharacterizationTest.missingAccount` / `missingXref`: lines + RC 16, SYSOUT+RC matched to the real COBOL via `legacy/tests/cbact04c-abend.sh` (harness exports no datasets on abend) | 9 |
| `legacy/harness/golden.sh --check` passes | 10 |
