# CR-1 Spec: CBACT04C → Java 21 (behaviour-preserving)

The legacy behaviour, with file:line refs, is in [intent.md](intent.md). This spec fixes the contract for the Java code.

## Invocation

`java -jar cbact04c.jar <PARM-DATE> <IN_DIR> <OUT_DIR>`
- `PARM-DATE`: up to 10 chars, right-padded with spaces to `X(10)`.
- Clock: env `CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss`. If unset, the system clock (UTC) is used. Tests inject a `java.time.Clock`.
- Input files are `<IN_DIR>/<DD>.txt`. The CLI accepts the stock names (`tcatbal.txt` …) through a per-DD path override, so it can take the same inputs as `run.sh`.
- Exit code: 0 on success, 16 on abend.

## Files (canonical text: one record per line, LF, fixed width)

| DD | Copybook | LRECL | Direction |
|---|---|---|---|
| TCATBALF | `app/cpy/CVTRA01Y.cpy` | 50 | in. Strip CR, read in file (key) order |
| XREFFILE | `app/cpy/CVACT03Y.cpy` | 50 | in. Right-pad short lines (36 bytes) to 50 |
| DISCGRP | `app/cpy/CVTRA02Y.cpy` | 50 | in, as a map keyed on cols 1-16 |
| ACCTFILE | `app/cpy/CVACT01Y.cpy` | 300 | in, and written back in full to `OUT_DIR/ACCTFILE.txt` in the input order |
| TRANSACT | `app/cpy/CVTRA05Y.cpy` | 350 | out, `OUT_DIR/TRANSACT.txt`, in write order |

Fields used (1-based cols). Each zoned field is a `long` of scaled cents (or a scaled `BigDecimal`), never `double`.

| Field | Cols | PIC | Java |
|---|---|---|---|
| TRANCAT-ACCT-ID / TYPE-CD / CD | 1-11 / 12-13 / 14-17 | 9(11) / X(2) / 9(4) | String (raw bytes) |
| TRAN-CAT-BAL | 18-28 | S9(09)V99 | overpunch → long cents |
| XREF-CARD-NUM / XREF-ACCT-ID | 1-16 / 26-36 | X(16) / 9(11) | String |
| ACCT-ID | 1-11 | 9(11) | String |
| ACCT-CURR-BAL | 13-24 | S9(10)V99 | long cents, re-encoded in place |
| ACCT-CURR-CYC-CREDIT / DEBIT | 87-98 / 99-110 | S9(10)V99 | set to `00000000000{` |
| ACCT-GROUP-ID | 121-130 | X(10) | String |
| DIS-INT-RATE | 17-22 | S9(04)V99 | long (hundredths) |

ACCTFILE rewrite: only cols 13-24 and 87-110 change. All other bytes are copied as they are, including FILLER 131-300.

Overpunch: the last byte of a zoned field carries the sign. `{`/`A`-`I` mean +0..9 and `}`/`J`-`R` mean -0..9. The encoder writes `{`…`I` for values ≥ 0 and `}`…`R` for values < 0. A result of zero is written as `{`.

## Processing rules (in order)

1. For each TCATBAL record in order, append its 50 bytes to SYSOUT.
2. When the acct-id differs from the previous record's:
   1. If this is not the first account, rewrite the previous account (rule 6).
   2. Set total = 0.
   3. Look up the account by ACCT-ID. If it is missing: `ACCOUNT NOT FOUND: <id>`, then abend.
   4. Look up the xref: the first XREF record in file order whose cols 26-36 equal the acct-id. If none: `ACCOUNT NOT FOUND: <id>`, then abend.
3. Rate lookup. The key is ACCT-GROUP-ID + TYPE-CD + CD.
   - On a miss: print `DISCLOSURE GROUP RECORD MISSING`, then `TRY WITH DEFAULT GROUP CODE`, and retry with the key `DEFAULT␣␣␣` + TYPE-CD + CD.
   - If the default is also missing: print the error, then abend.
4. If rate ≠ 0:
   - `monthly = trunc0(bal × rate / 1200)` to 2 decimals, truncating toward zero. Then reduce mod 10^9 (keep the low 11 digits, sign kept), as for `S9(09)V99` with no SIZE ERROR.
   - `total = (total + monthly)` mod 10^9, since `WS-TOTAL-INT` is also `S9(09)V99` (`cbl:169`).
   - Write the transaction (rule 5).
   - Fees: a no-op hook.
5. TRAN record, 350 bytes, space-filled first:
   - `TRAN-ID` = PARM-DATE(10) + a 6-digit counter. The counter is global, starts at 000001, and wraps 999999→000000 because it is `9(06)`.
   - Type `01`, cat `0005`, source `System` padded to 10.
   - Desc: `Int. for a/c ` + ACCT-ID, padded to 100.
   - Amt: `monthly` as S9(09)V99.
   - Merchant id: `000000000`. Name, city and zip: spaces.
   - Card: XREF-CARD-NUM.
   - ORIG-TS = PROC-TS = `YYYY-MM-DD-HH.MM.SS.hh0000`, taken from the clock once per record.
   - FILLER: spaces.
6. Rewrite:
   - `ACCT-CURR-BAL = (bal + total)` mod 10^10, keeping the low 12 digits.
   - Both cycle fields are set to 0.
   - This applies even when total = 0.
7. At EOF: close and write the outputs. **The last account is not rewritten (D1).** Print the end banner and exit 0.

Abend means: print `ERROR …`, then `FILE STATUS IS: NNNN0023` (or the matching status), then `ABENDING PROGRAM`, then exit 16. No partial-success message is printed. Output files may be partial, as on the mainframe.

SYSOUT is written to stdout using the golden wording. It is informational (approver Q1 default) and compared in a test, but it does not gate.

## Edge cases

| Case | Handling |
|---|---|
| Account with no TCATBAL rows | copied byte for byte |
| Rate = 0 (e.g. DEFAULT 03/0001) | no TRAN record, the account is still rewritten |
| Negative balance | truncated toward zero, e.g. -0.019 → -0.01 |
| Result `-0` | encoded `{` (COBOL normalises -0 to +0) |
| Empty TCATBALF | no TRAN records, ACCTFILE unchanged, RC 0 |
| Direct group hit (`A000000000`) | uses that rate, no MISSING lines |

### Preserved defects (do not fix in this change)

- **D1** The last account's interest is never written back to ACCTFILE, and its cycle fields are not zeroed.
- **D2** Interest is truncated, not rounded (under-charges up to 0.01 per category).
- **D3** Silent high-order truncation of monthly/total (9 integer digits) and of the balance (10).
- **D4** Lookups use ACCT-GROUP-ID, which is blank in the sample data, so every lookup falls back to DEFAULT.
- **D5** Fees are not computed.
- **D6** The TRAN-ID counter wraps after 999,999 records (duplicate IDs).

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -f modernized/cbact04c test` green, JUnit 5 only | 1 |
| `GoldenTest.stock`: byte-compare to `legacy/golden/CBACT04C/{TRANSACT,ACCTFILE}.txt`, 50/50 | 2, 4 |
| `GoldenTest.afterCbtrn02c`: ACCTFILE and TCATBALF from `golden/CBTRN02C`, compared to `golden/CBACT04C-after-CBTRN02C`, 50/50 | 3, 4 |
| `InterestTest.truncatesNotRounds` (+ a count of 28 golden amounts that rounding would change) | 5 |
| `DefectTest.lastAccountNotRewritten`: a/c 50 stays 1945.87 / 1501.75 / -47.88 | 6 |
| `RateTest.defaultFallback`, `zeroRateNoTranButRewrite`, `directGroupHit` | 7 |
| `TranTest.globalCounterTypeCatClock` (`…000001`–`…000050`, `01`/`0005`, injected clock) | 8 |
| `PassThroughTest.untouchedAccountsAndFiller` | 9 |
| `AbendTest.missingAccount / missingXref / missingDefaultRate` → exit 16 | 10 |
| `OverflowTest` (D3), `NegativeTest`, `OverpunchTest` round-trip (incl. `}`) | risks |
