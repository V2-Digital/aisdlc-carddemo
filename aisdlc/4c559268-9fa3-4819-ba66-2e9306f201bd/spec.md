# CR-2 Spec — Over-limit fee in CBACT04C

Context, file layouts and legacy behaviour: [intent.md](intent.md). Approver defaults Q1–Q4 are adopted.

## Inputs / outputs (unchanged files, new usage only)

| DD | Copybook | Fields this change touches |
|---|---|---|
| `ACCTFILE` (I-O) | `app/cpy/CVACT01Y.cpy` | reads `ACCT-CURR-BAL` (off 12, len 12, `S9(10)V99`), `ACCT-CREDIT-LIMIT` (off 24, len 12, `S9(10)V99`); writes `ACCT-CURR-BAL` |
| `XREFFILE` | `app/cpy/CVACT03Y.cpy` | reads `XREF-CARD-NUM` (as interest does) |
| `TRANSACT` (OUTPUT) | `app/cpy/CVTRA05Y.cpy` | writes a new fee record, fields below |
| `trancatg.txt` | `app/cpy/CVTRA04Y.cpy` | data only: new row, not read by CBACT04C |

Fee `TRAN-RECORD` (350 bytes, 0-based offsets):

| Field | Off | Len | Value |
|---|---|---|---|
| `TRAN-ID` | 0 | 16 | `PARM-DATE` + next `WS-TRANID-SUFFIX` (shared with interest) |
| `TRAN-TYPE-CD` | 16 | 2 | `01` |
| `TRAN-CAT-CD` | 18 | 4 | `0006` |
| `TRAN-SOURCE` | 22 | 10 | `System` + spaces |
| `TRAN-DESC` | 32 | 100 | `Over-limit fee for a/c ` + `ACCT-ID` (11), rest spaces |
| `TRAN-AMT` | 132 | 11 | `0000000250{` (+25.00) |
| merchant id / name / city / zip | 143.. | 9/50/50/10 | `0`s / spaces |
| `TRAN-CARD-NUM` | 262 | 16 | `XREF-CARD-NUM` |
| `TRAN-ORIG-TS`, `TRAN-PROC-TS` | 278, 304 | 26 each | `Z-GET-DB2-FORMAT-TIMESTAMP`, as interest |

## New working storage

- `WS-OVERLIMIT-FEE` `S9(09)V99 VALUE 25.00` — constant.
- `WS-TOTAL-FEES` `S9(09)V99` — fees charged to the current account.

## Processing rules (new / changed only)

1. **Account break** (`CBACT04C.cbl:194-206`): after `MOVE 0 TO WS-TOTAL-INT`, also `MOVE 0 TO WS-TOTAL-FEES`.
   After `1110-GET-XREF-DATA`, `PERFORM 1400-COMPUTE-FEES`.
2. **Remove** the `PERFORM 1400-COMPUTE-FEES` inside `IF DIS-INT-RATE NOT = 0` (`:216`). The fee is thus
   evaluated exactly once per account, independent of rates (Q2), and written before that account's first
   interest transaction (Q4). Structure, not a flag, guarantees once-per-account (F1).
3. **`1400-COMPUTE-FEES`**: `IF ACCT-CURR-BAL > ACCT-CREDIT-LIMIT` (signed numeric compare, balance as read
   at the break, before interest — Q3): `ADD WS-OVERLIMIT-FEE TO WS-TOTAL-FEES`, `PERFORM 1400-B-WRITE-FEE-TX`.
   Equal or lower: nothing.
4. **`1400-B-WRITE-FEE-TX`**: mirrors `1300-B-WRITE-TX` (`:473-515`) with the values above:
   `ADD 1 TO WS-TRANID-SUFFIX`; `MOVE SPACES TO TRAN-DESC` before the STRING; same WRITE/status/abend path.
   After the WRITE, `MOVE SPACES TO TRAN-DESC` again so the next (shorter) interest description has no
   residue (R3). The interest paragraph itself is not changed.
5. **`1050-UPDATE-ACCOUNT`** (`:350-370`): after `ADD WS-TOTAL-INT TO ACCT-CURR-BAL`, add
   `ADD WS-TOTAL-FEES TO ACCT-CURR-BAL`. Adding zero leaves within-limit accounts byte-identical.
6. No new `DISPLAY` lines; SYSOUT is unchanged for every run.
7. `app/data/ASCII/trancatg.txt`: insert after the `010005` row
   `010006` + `Over-limit Fee` padded to 50 + `0000` + CRLF (60 bytes + CRLF), giving 19 rows.

## Edge cases

| Case | Handling |
|---|---|
| Balance = limit | No fee (strict `>`) |
| Negative balance / negative limit | Signed compare as-is; no special case |
| Account with several `TCATBALF` records | One fee (rule 2) |
| Over-limit, all rates zero (e.g. group `ZEROAPR`) | Fee charged, no interest tx; balance += 25.00 |
| Interest pushes balance over limit | No fee this run (Q3) |
| Account with no `TCATBALF` record | Never read, no fee (as today) |
| Fee WRITE fails | Same as interest: display status, `9999-ABEND-PROGRAM` |

**Preserved defect (Q1):** the last account in `TCATBALF` is never rewritten (`:219-220` unreachable), so
its fee transaction is written but its balance is not increased. Raise as a separate CR.

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `legacy/harness/golden.sh --check` passes, 3 scenarios byte-identical | 1 |
| New scenario: accts 1 & 2 (2 categories each, over limit) have exactly one `0006` record each | 2, 6 |
| New scenario: acct 3 balance = limit, all others within limit → no `0006` record | 3 |
| Fee records match the field table byte-for-byte; TRAN-IDs `…000001`–`…NNNNNN` contiguous, unique | 4 |
| `ACCTFILE` accts 1, 2, 4: balance = input + interest + 25.00, cycle fields 0; others = golden | 5 |
| Zero-rate over-limit acct 4 (`ZEROAPR`) gets fee, no interest | 2, Q2 |
| Scenario RC 0; two runs byte-identical; matches committed expected outputs | 6 |
| `trancatg.txt`: 19 rows, row 6 = new row, every row 60 bytes + CRLF | 7 |
