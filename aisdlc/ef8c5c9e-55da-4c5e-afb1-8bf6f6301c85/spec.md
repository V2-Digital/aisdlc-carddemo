# CR-1 Spec — `CBACT04C` → Java 21 (`modernized/cbact04c/`)

Legacy behaviour, file table and defects: [intent.md](intent.md). This spec covers only what the Java code must
reproduce exactly. Approver defaults Q1–Q3 are assumed (SYSOUT+RC parity, text datasets, injected PARM/clock).

## Interface

`java -jar cbact04c.jar <PARM-DATE(10)>`. Environment:
- `DD_<name>`: path for each dataset (same as the harness). Alternatively `DATA_DIR`/`OUT_DIR` with `<DD>.txt`.
- `CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss`: fixed clock. Default is the system clock in UTC.

The program writes SYSOUT to stdout. Exit codes: 0 on success, 16 on abend.

## Inputs / outputs (canonical text: fixed width, LF, overpunch zoned decimal)

| DD | Copybook | Fields used (1-based col, len, PIC → Java) |
|---|---|---|
| `TCATBALF` in | `app/cpy/CVTRA01Y.cpy` | acct-id 1,11 `9(11)`→String key; type 12,2; cat 14,4; `TRAN-CAT-BAL` 18,11 `S9(9)V99`→long cents |
| `XREFFILE` in | `app/cpy/CVACT03Y.cpy` | card-num 1,16 X; acct-id 26,11 (alt key, **first match in file order**) |
| `DISCGRP` in | `app/cpy/CVTRA02Y.cpy` | key 1,16 (group 10 + type 2 + cat 4); `DIS-INT-RATE` 17,6 `S9(4)V99`→long hundredths |
| `ACCTFILE` in/out | `app/cpy/CVACT01Y.cpy` | id 1,11; `ACCT-CURR-BAL` 13,12 `S9(10)V99`; `CURR-CYC-CREDIT` 246,12 and `-DEBIT` 258,12 `S9(10)V99`; `ACCT-GROUP-ID` 280,10 X. All other bytes are copied through unchanged. |
| `TRANSACT` out | `app/cpy/CVTRA05Y.cpy` | 350-byte record, built from spaces; see R7 |

Zoned codec: the last digit carries the sign. `{ A–I` = +0..9 and `} J–R` = −0..9. **Encode** non-negative
values with `{A–I` and negative values with `}J–R`. Zero is always written as `{`. Unsigned `9(n)` fields are
plain digits.

## Processing rules (in order)

- R1 **Open:** all five files open first. On failure, print the program's exact open-error text (including the
  defect D4 `ERROR OPENING DALY REJECTS FILE` for DISCGRP), then `FILE STATUS IS: NNNN00xx`, then abend.
- R2 Print `START OF EXECUTION OF PROGRAM CBACT04C`. For each TCATBALF record, in file order, print the raw
  50-byte record.
- R3 **Account break** (acct-id ≠ last; last starts blank):
  - If this is not the first record, apply R6 to the previous account.
  - Set total = 0.
  - Look up the account. If missing, print `ACCOUNT NOT FOUND: <id>` and abend.
  - Look up the xref by acct-id. If missing, print `ACCOUNT NOT FOUND: <id>` and abend.
- R4 **Rate:** key = `ACCT-GROUP-ID`+type+cat.
  - If missing, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with
    `DEFAULT   ` (padded to 10).
  - If `DEFAULT` is also missing, print `ERROR READING DEFAULT DISCLOSURE GROUP` + status, then abend.
- R5 **If rate ≠ 0:**
  - `monthly = trunc0(bal × rate / 1200)` in cents. Use `long` math: `bal_cents × rate_hundredths / 120000`,
    with Java `/` (truncates toward zero). Then **truncate mod 10^11** (S9(9)V99, D6).
  - `total = (total + monthly) mod 10^11`, keeping the sign (`WS-TOTAL-INT` is S9(9)V99).
  - Increment the counter mod 10^6 (`9(06)`) and write the transaction (R7).
  - If the rate is 0: skip all of R5.
- R6 **Update account:**
  - `CURR-BAL = (CURR-BAL + total) mod 10^12`, keeping the sign (S9(10)V99, D6).
  - Set cycle credit and debit to 0.
  - Store the record.
- R7 **TRANSACT record:**
  - `TRAN-ID` = PARM + 6-digit counter.
  - `01` + `0005` + `System    `.
  - Desc = `Int. for a/c ` + acct-id, padded to 100.
  - Amount = zoned `S9(9)V99` (11 bytes).
  - Merchant id = `000000000`. Name/city/zip = spaces (110 bytes).
  - Card number = 16 bytes from the xref.
  - Orig-ts and proc-ts are both `YYYY-MM-DD-HH.MM.SS.hh0000`, computed **per record** from the clock.
  - FILLER(20) = spaces.
- R8 **EOF:**
  - **Do not** apply R6 to the last account (D1).
  - Write ACCTFILE: every input record in primary-key order, with the updated records substituted.
  - Print `END OF EXECUTION OF PROGRAM CBACT04C` and exit 0.
- R9 **Abend:** print `ABENDING PROGRAM`, print `CEE3ABD: ABEND U0999` to stderr, and exit 16. Outputs:
  - TRANSACT: the records already written stay in the file (sequential, as COBOL).
  - ACCTFILE: the outputs reflect the rewrites done so far (KSDS REWRITE is immediate). Write ACCTFILE on abend
    as well.

## Edge cases

| Case | Handling |
|---|---|
| Negative balance | Truncation toward zero. −1164.87 @15.00 → −14.56 `0000000145O` (harness-verified) |
| Result in (−0.01, 0) | Becomes 0 and is encoded `0000000000{` (harness-verified: −0.01 @15.00) |
| Balance 0, rate ≠ 0 | Writes a zero-amount transaction (D7) |
| Rate 0 (e.g. type 03) | No transaction and no change to total. The account is still rewritten, with cycle fields zeroed |
| Group-specific rate exists | Use it with no SYSOUT noise (never hit by golden data, D3) |
| Duplicate acct in XREF | First record in file order (R3 of intent) |
| Overflow | Silent high-order truncation (D6) |

## Preserved defects (fix later, deliberately)

- D1: the last TCATBALF account is never rewritten.
- D2: fees are a no-op.
- D3: blank group id means every lookup falls back to `DEFAULT`.
- D4: DISCGRP open error says `DALY REJECTS`.
- D5: interest is truncated, not rounded.
- D6: no size error on overflow.
- D7: zero-amount transactions are written, and cycle fields are zeroed even with no interest.

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -q test` green, deps = JUnit 5 + Surefire | 1 |
| `GoldenTest.stock`: TRANSACT, ACCTFILE, SYSOUT, RC byte-equal to `legacy/golden/CBACT04C/` | 2, 9 |
| `GoldenTest.afterCbtrn02c`: inputs ACCTFILE+TCATBALF from `golden/CBTRN02C`, equal to `golden/CBACT04C-after-CBTRN02C/` | 3, 9 |
| `InterestTest`: 1164.87@15→14.56; −1164.87→`0000000145O`; −0.01→`0000000000{`; overflow wrap | 4 |
| `ZeroRateTest`: type 03 is skipped, 50 tx in after-scenario | 5 |
| `RateFallbackTest`: specific group used; DEFAULT fallback; DEFAULT missing → 16; acct/xref missing → 16 | 6 |
| `LastAccountDefectTest`: acct `00000000050` row equals input, tx amount 18.77 present | 7 |
| `TransactLayoutTest`: desc tail cols 57-132 and 331-350 are blank; timestamp from the injected clock | 8 |
