# CR-2 Spec — Over-limit fee in `CBACT04C`

Context, file table and line refs: [intent.md](intent.md). Approver defaults Q1–Q4 are adopted as written.

## Files and fields touched

| DD / file | Copybook | Fields this change uses |
|---|---|---|
| ACCTFILE (read, REWRITE) | [`CVACT01Y`](../../app/cpy/CVACT01Y.cpy) | `ACCT-ID` off 0 len 11 `9(11)`; `ACCT-CURR-BAL` off 12 len 12 `S9(10)V99`; `ACCT-CREDIT-LIMIT` off 24 len 12 `S9(10)V99` |
| TRANSACT (write) | [`CVTRA05Y`](../../app/cpy/CVTRA05Y.cpy) | `TRAN-ID` 0/16 `X`; `TRAN-TYPE-CD` 16/2; `TRAN-CAT-CD` 18/4 `9(04)`; `TRAN-SOURCE` 22/10; `TRAN-DESC` 32/100; `TRAN-AMT` 132/11 `S9(09)V99`; `TRAN-CARD-NUM` 262/16; `TRAN-ORIG-TS`/`TRAN-PROC-TS` 278/26, 304/26 |
| `app/data/ASCII/trancatg.txt` | — (60-byte text, CRLF) | new record only |

TCATBALF, XREFFILE and DISCGRP are unchanged. No new DISPLAY lines, so SYSOUT is unchanged.

## New working storage
- `WS-OVERLIMIT-FEE PIC S9(09)V99 VALUE 25.00` (constant)
- `WS-TOTAL-FEE PIC S9(09)V99` (fee charged to the current account, 0 or 25.00)

## Processing rules (in order)
1. **Control break** (`:194-206`): after `1110-GET-XREF-DATA`, `MOVE 0 TO WS-TOTAL-FEE` and `PERFORM 1400-COMPUTE-FEES`. This runs once per account, before any of its category records are rated. It runs whatever the rate is (Q2, Q4, R3).
2. **Remove** the `PERFORM 1400-COMPUTE-FEES` call inside `IF DIS-INT-RATE NOT = 0` (`:216`). Today it does nothing, so removing it changes no output.
3. **`1400-COMPUTE-FEES`**: `IF ACCT-CURR-BAL > ACCT-CREDIT-LIMIT`, then `MOVE WS-OVERLIMIT-FEE TO WS-TOTAL-FEE` and `PERFORM 1400-B-WRITE-FEE-TX`. The comparison is signed and strict, and uses the balance as read (Q1). `1050` has not run for this account yet, so the balance is unmodified.
4. **`1400-B-WRITE-FEE-TX`** writes the fee transaction the same way as `1300-B-WRITE-TX`:
   - `ADD 1 TO WS-TRANID-SUFFIX`; `TRAN-ID` = `PARM-DATE` + suffix (same STRING)
   - `TRAN-TYPE-CD '01'`, `TRAN-CAT-CD 0006`, `TRAN-SOURCE 'System'`
   - `MOVE SPACES TO TRAN-DESC`, then `STRING 'Over-limit fee for a/c ' ACCT-ID` (23 + 11 = 34 bytes, rest blank)
   - `TRAN-AMT` = `WS-TOTAL-FEE` → `0000000250{`
   - merchant id 0, merchant name/city/zip spaces, `TRAN-CARD-NUM` = `XREF-CARD-NUM`
   - `Z-GET-DB2-FORMAT-TIMESTAMP` → orig-ts and proc-ts
   - same WRITE status check and abend path (`9910`/`9999`)
5. **`1300-B-WRITE-TX`**: add `MOVE SPACES TO TRAN-DESC` before the STRING (R2). Golden bytes 25-100 are already spaces, so output doesn't change.
6. **`1050-UPDATE-ACCOUNT`**: `ADD WS-TOTAL-INT WS-TOTAL-FEE TO ACCT-CURR-BAL`. Everything else stays as it is.
7. **`trancatg.txt`**: insert `010006Over-limit Fee` + 37 spaces + `0000` + CRLF after `010005` (19 records).

## Edge cases
| Case | Handling |
|---|---|
| Balance = limit | Not charged (strict `>`) |
| Negative balance / credit balance | Not charged (signed compare) |
| Account with ≥2 TCATBAL records | One fee, at the break (AC2/AC3) |
| All rates 0 (e.g. group `ZEROAPR`) | Fee still charged, no interest tx |
| Over limit only after this run's interest | Not charged this run (Q1) |
| Account with no TCATBAL record | Never visited, no fee (R5, accepted) |
| Last account in TCATBAL over limit | Fee tx written, balance **not** rewritten (R1, preserved defect) |
| Balance near `S9(10)V99` max | ADD truncates high-order digits as today (no ON SIZE ERROR, same as interest) |

**Preserved defects (deliberately not fixed):**
- R1: the last account is never rewritten, so its fee/interest is lost from the balance.
- The cycle credit/debit zeroing in `1050` is unchanged.
- Interest uses `COMPUTE` without `ROUNDED`, so it is truncated. Unchanged.

**Visible change:** transaction-ID suffixes after a fee shift by +1 per fee (R4).

## Acceptance tests
| AC | Test |
|---|---|
| 1 | `legacy/harness/golden.sh --check` → OK, all 3 scenarios identical |
| 2 | Scenario `legacy/tests/cr2_overlimit/`: accts 1 and 2 over limit, acct 1 has 2 TCATBAL records |
| 3, 4 | Script asserts exactly 1 `01`/`0006` record per over-limit acct, with amount `0000000250{`, exact 100-byte desc, xref card, TS `2022-07-18-00.00.00.000000`. All 16-byte IDs are unique and sequential |
| 5 | Script checks the ACCTFILE balance for accts 1 and 2 = input + interest + 25.00 |
| 6 | Acct 3 balance = limit, acct 4 below: no `0006` record |
| 7 | The diff of non-fee records vs a run of the baseline program on the same input differs only in TRAN-ID |
| 8 | Interest desc for acct 1 (written after its fee) is exactly `Int. for a/c 00000000001` + 76 spaces |
| 9 | `trancatg.txt` has 19 lines, is sorted, and every line is 62 bytes incl. CRLF |
