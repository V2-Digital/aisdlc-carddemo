# CR-2 Spec — Over-limit fee in `CBACT04C` (COBOL)

Context, file table and existing rules: [intent.md](intent.md). This spec covers **only new or changed behaviour**.
Approver defaults taken: Q1 fee independent of interest, Q2 pre-interest balance strictly `>` limit, Q3 R1 bug left unfixed.

## Inputs / outputs (changed fields only)

| File | Copybook | Field | Bytes (1-based) | PIC | Use |
|---|---|---|---|---|---|
| `ACCTFILE` (in) | `app/cpy/CVACT01Y.cpy` | `ACCT-CURR-BAL` | 13-24 | S9(10)V99 | compared; fee added on REWRITE |
| | | `ACCT-CREDIT-LIMIT` | 25-36 | S9(10)V99 | compared (read only) |
| `TRANSACT` (out) | `app/cpy/CVTRA05Y.cpy` | `TRAN-ID` | 1-16 | X(16) | `PARM-DATE` + `WS-TRANID-SUFFIX` (shared counter) |
| | | `TRAN-TYPE-CD` / `TRAN-CAT-CD` | 17-18 / 19-22 | X(2) / 9(4) | `01` / `0006` |
| | | `TRAN-SOURCE` | 23-32 | X(10) | `System` |
| | | `TRAN-DESC` | 33-132 | X(100) | `Over-limit fee for a/c <ACCT-ID>`, space-filled |
| | | `TRAN-AMT` | 133-143 | S9(9)V99 | `0000000250{` (+25.00) |
| | | `TRAN-CARD-NUM` | 263-278 | X(16) | `XREF-CARD-NUM` |
| `app/data/ASCII/trancatg.txt` | `app/cpy/CVTRA04Y.cpy` | new record | 60 + CRLF | — | `010006` + `Over-limit Fee` padded to 50 + `0000` |

Merchant fields, timestamps and FILLER: same values as the interest record (`app/cbl/CBACT04C.cbl:490-498`).

## New working storage

- `WS-FEE-AMT PIC S9(09)V99 VALUE 25.00` — fixed fee constant.
- `WS-TOTAL-FEES PIC S9(09)V99` — fees accumulated for the current account.

## Processing rules (in order)

1. **Account break** (`:194-205`): after `1100-GET-ACCT-DATA` and `1110-GET-XREF-DATA`, `MOVE 0 TO WS-TOTAL-FEES`,
   then `PERFORM 1400-COMPUTE-FEES`. This is the **only** call site of `1400`; the per-category call at `:216` is
   removed. Once-per-account is therefore structural (R2), and the fee does not depend on any rate (R3, Q1).
2. **`1400-COMPUTE-FEES`**: `IF ACCT-CURR-BAL > ACCT-CREDIT-LIMIT` (as read, before this run's interest; strict `>`)
   → `ADD WS-FEE-AMT TO WS-TOTAL-FEES` and `PERFORM 1400-B-WRITE-FEE-TX`. Otherwise nothing.
3. **`1400-B-WRITE-FEE-TX`**: same as `1300-B-WRITE-TX` (`:473-515`) except type `01`, cat `06`, desc via
   `MOVE SPACES TO TRAN-DESC` then `STRING 'Over-limit fee for a/c ' ACCT-ID`, amount `WS-FEE-AMT`. Same
   `ADD 1 TO WS-TRANID-SUFFIX`, same write-error path (`ERROR WRITING TRANSACTION RECORD`, abend).
4. **`1300-B-WRITE-TX`**: add `MOVE SPACES TO TRAN-DESC` before its STRING (R4). Output unchanged, since today's tail
   is already spaces.
5. **`1050-UPDATE-ACCOUNT`**: `ADD WS-TOTAL-INT WS-TOTAL-FEES TO ACCT-CURR-BAL`, otherwise unchanged.
6. Record order per account: fee record (if any) first, then that account's interest records in TCATBAL order.

## Edge cases

| Case | Handling |
|---|---|
| Balance = limit, or below it | No fee record, balance = old + interest (unchanged behaviour) |
| Over limit, ≥2 non-zero-rate categories | Exactly one fee (rule 1) |
| Over limit, all categories rate 0 | Fee still charged (Q1); no interest records |
| Over limit only after this run's interest | No fee (Q2) |
| Negative balance / negative limit | Plain signed compare, no special case |
| Over-limit **last** account (R1, kept) | Fee record written, balance **not** updated. Documented and kept out of the test's last position |
| Over-limit account with no TCATBAL record (R7) | Not seen and not charged (existing driver behaviour) |

**Preserved defects (deliberately not fixed):** R1 last account never rewritten · R7 accounts without TCATBAL are
skipped · R8 TCATBAL `01/0006` balance not updated · DISCGRP lookups always fall back to `DEFAULT` (blank group id).

## Acceptance tests → intent ACs

| AC | Test |
|---|---|
| 1 | `legacy/harness/golden.sh --check`: 3 scenarios byte-identical |
| 2, 3, 5, 7 | `legacy/tests/cr2_overlimit_fee.sh`: count `01`/`0006` records per account = 1 for over-limit accts, 0 otherwise; check desc/amt/card bytes; check tran ids unique and 1..N; RC 0 |
| 4 | same script: new `ACCT-CURR-BAL` = input balance + Σ interest for that acct + 25.00 |
| 6 | same script: TRANSACT with fee records removed equals a baseline run on the same input with the fee disabled, apart from cols 1-16 (stored as `expected/` interest records) |
| 8 | same script: `trancatg.txt` has 19 lines, line `010006…` is 60 bytes + CRLF |
