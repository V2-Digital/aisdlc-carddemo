# CR-2 Spec — Over-limit fee in CBACT04C

Background, file inventory and defects: [intent.md](intent.md). This spec only covers what changes.
Approver defaults are taken as given: **Q1 yes** (charge even at rate 0), **Q2 no** (keep F3),
**Q3 before** (compare the balance as read).

## Inputs / outputs touched

| File (DD) | Copybook | Fields this change uses |
|---|---|---|
| ACCTFILE (I-O) | `app/cpy/CVACT01Y.cpy` | `ACCT-ID` 1-11 `9(11)`; `ACCT-CURR-BAL` 13-24 `S9(10)V99` (read, then rewritten); `ACCT-CREDIT-LIMIT` 25-36 `S9(10)V99` (read only) |
| XREFFILE (in) | `app/cpy/CVACT03Y.cpy` | `XREF-CARD-NUM` → `TRAN-CARD-NUM` (same as interest) |
| TRANSACT (out) | `app/cpy/CVTRA05Y.cpy` | Writes a full 350-byte record. See the fee record below |
| `app/data/ASCII/trancatg.txt` | `app/cpy/CVTRA04Y.cpy` | New data line only. CBACT04C does not read it |

No copybook, JCL or file layout changes.

## New working storage (`CBACT04C.cbl` WS-VARIABLES, next to `WS-TOTAL-INT` :169)

- `WS-OVERLIMIT-FEE PIC S9(09)V99 VALUE 25.00`: the fee amount, a constant.
- `WS-TOTAL-FEE PIC S9(09)V99`: the fee charged to the current account (0 or 25.00).

## Processing rules (in order)

1. **Account break** (`:194-205`): this rewrites the previous account (unchanged), then `MOVE 0 TO WS-TOTAL-INT`
   **and `MOVE 0 TO WS-TOTAL-FEE`**, then reads the account (1100) and the xref (1110). Then (new):
   `IF ACCT-CURR-BAL > ACCT-CREDIT-LIMIT PERFORM 1400-COMPUTE-FEES`.
   - The check sits at the break, so it runs **once per account** whatever the number of TCATBAL records
     (F1) and whatever the rate (F2/Q1). No flag is needed: TCATBAL is a KSDS read in key order, so each
     account's records are contiguous.
   - The comparison is a signed numeric `>` on the values as read (Q3). Equal → no fee.
2. The `PERFORM 1400-COMPUTE-FEES` inside `IF DIS-INT-RATE NOT = 0` (`:216`) is **removed**, because it moved
   to rule 1.
3. **`1400-COMPUTE-FEES`** (this was the empty hook): `MOVE WS-OVERLIMIT-FEE TO WS-TOTAL-FEE`, then it
   writes the fee record (rule 4).
4. **Fee record**: built field by field like `1300-B-WRITE-TX` (`:473-515`):
   - `ADD 1 TO WS-TRANID-SUFFIX`; `TRAN-ID` = `PARM-DATE` + suffix. Same counter, so IDs stay unique and
     sequential (AC6).
   - `TRAN-TYPE-CD '01'`, `TRAN-CAT-CD 6` (stored `0006`), `TRAN-SOURCE 'System'`.
   - `MOVE SPACES TO TRAN-DESC`, then `STRING 'Over-limit fee for a/c ' ACCT-ID` (34 bytes).
   - `TRAN-AMT` = `WS-OVERLIMIT-FEE` → `0000000250{`.
   - Merchant id 0, merchant name/city/zip spaces, card = `XREF-CARD-NUM`, orig/proc TS = `DB2-FORMAT-TS`
     from `Z-GET-DB2-FORMAT-TIMESTAMP`.
   - Error path: same as interest. A status other than `00` → `ERROR WRITING TRANSACTION RECORD`,
     9910, 9999 abend (RC 16).
5. **`1300-B-WRITE-TX`**: add `MOVE SPACES TO TRAN-DESC` before its STRING (F7). In golden this is a no-op,
   because the tail is already spaces. It stops fee text leaking into bytes 25-34 of a later interest record.
6. **`1050-UPDATE-ACCOUNT`** (`:350-370`): after `ADD WS-TOTAL-INT TO ACCT-CURR-BAL`, it now also runs
   `ADD WS-TOTAL-FEE TO ACCT-CURR-BAL`. Everything else stays the same (cycle credit/debit = 0, REWRITE).
7. **Record order**: for an over-limit account, the fee record comes **before** that account's interest
   records (it is written at the break). No new SYSOUT lines.

## Edge cases

| Case | Handling |
|---|---|
| Balance = limit, or below it | No fee, no record. Output is byte-identical to today's (AC5) |
| Over limit, several TCATBAL records | One fee, at the break (AC3) |
| Over limit, all categories rate 0 | Fee charged; no interest records (Q1) |
| Negative balance, or limit 0 with positive balance | Plain signed compare: negative is never over; limit 0 + bal > 0 is over |
| Pushed over the limit only by tonight's interest | No fee this run (Q3) |
| Over-limit account with no TCATBAL record | Never visited, no fee (F4, unchanged) |
| Over-limit account is the **last** in TCATBAL | Fee record is written; balance is **not** rewritten (F3, preserved) |

**Preserved defects** (fix later, deliberately):
- F3: the last account is never rewritten. Its interest and fee are lost from ACCTFILE.
- F4: accounts without TCATBAL records are skipped.
- Cycle credit/debit are zeroed on every rewritten account (`:353-354`).

## Acceptance tests

| AC | Test |
|---|---|
| 1 | `legacy/harness/golden.sh --check` → `OK - outputs match legacy/golden` |
| 2, 6 | Scenario: each over-limit account has exactly 1 record with cat `0006`, the exact description, `0000000250{`, card and TS like interest. TRAN-ID suffixes are 1..N with no gaps |
| 3 | Scenario: acct `00000000001` has 2 TCATBAL records and gets 1 fee |
| 4 | Scenario: rewritten `ACCT-CURR-BAL` = input + interest + 25.00; cycle fields 0 |
| 5 | Scenario: acct at limit and acct below limit match the **unmodified** program's output (ACCTFILE byte-equal; interest records equal except TRAN-ID) |
| 7 | `wc -l` = 19; line 6 = `010006Over-limit Fee` + 36 spaces + `0000` + CRLF |
| 8 | Compiler stderr shows no new warnings compared with baseline; RC.txt = 0 in every run |
