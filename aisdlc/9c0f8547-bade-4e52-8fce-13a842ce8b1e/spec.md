# CR-2 Spec: over-limit fee in CBACT04C

Context, file table and defects: [intent.md](intent.md). Program: `app/cbl/CBACT04C.cbl`. Approver defaults Q1–Q4 are adopted as stated in the intent.

## Inputs / outputs (only fields this change touches)

| File (DD) | Copybook | Field | Cols | PIC | Use |
|---|---|---|---|---|---|
| ACCTFILE | `app/cpy/CVACT01Y.cpy` | `ACCT-ID` | 1-11 | 9(11) | in description |
| | | `ACCT-CURR-BAL` | 13-24 | S9(10)V99 | compared; += fee in `1050` |
| | | `ACCT-CREDIT-LIMIT` | 25-36 | S9(10)V99 | compared |
| XREFFILE | `app/cpy/CVACT03Y.cpy` | `XREF-CARD-NUM` | 1-16 | X(16) | → `TRAN-CARD-NUM` |
| TRANSACT | `app/cpy/CVTRA05Y.cpy` | `TRAN-ID` | 1-16 | X(16) | PARM-DATE + shared suffix |
| | | `TRAN-TYPE-CD` / `TRAN-CAT-CD` | 17-18 / 19-22 | X(2) / 9(4) | `01` / `0006` |
| | | `TRAN-SOURCE` | 23-32 | X(10) | `System` |
| | | `TRAN-DESC` | 33-132 | X(100) | `Over-limit fee for a/c ` + ACCT-ID (34 bytes), rest spaces |
| | | `TRAN-AMT` | 133-143 | S9(9)V99 | `0000000250{` |
| trancatg.txt | `app/cpy/CVTRA04Y.cpy` (60 bytes) | — | — | — | new line `010006Over-limit Fee` |

No copybook, JCL or file-layout change.

## Processing rules (new / changed only)

New working storage: `WS-FEE-AMT PIC S9(09)V99 VALUE 25.00`, `WS-TOTAL-FEE PIC S9(09)V99`.

1. **Account break** (`:194-206`): after `MOVE 0 TO WS-TOTAL-INT` also `MOVE 0 TO WS-TOTAL-FEE`; after `1110-GET-XREF-DATA`, `PERFORM 1400-COMPUTE-FEES`. → runs exactly once per account, independent of the rate.
2. Remove `PERFORM 1400-COMPUTE-FEES` from the `IF DIS-INT-RATE NOT = 0` block (`:216`).
3. `1400-COMPUTE-FEES`: `IF ACCT-CURR-BAL > ACCT-CREDIT-LIMIT` (signed numeric compare, balance as read by `1100`, before interest) → `MOVE WS-FEE-AMT TO WS-TOTAL-FEE`, `PERFORM 1400-B-WRITE-FEE-TX`. Otherwise nothing.
4. `1400-B-WRITE-FEE-TX`: same as `1300-B-WRITE-TX` (`ADD 1 TO WS-TRANID-SUFFIX`, ID, merchant 0/spaces, card from XREF, both timestamps from `Z-GET-DB2-FORMAT-TIMESTAMP`, same WRITE status check and abend path), except: cat `0006`, `MOVE SPACES TO TRAN-DESC` before the `STRING`, description as above, `TRAN-AMT = WS-FEE-AMT`.
5. `1300-B-WRITE-TX`: add `MOVE SPACES TO TRAN-DESC` before its `STRING` (otherwise bytes 25-34 of the fee description leak into the next interest record — F4). Golden-neutral: `TRAN-DESC` is spaces today on every write.
6. `1050-UPDATE-ACCOUNT`: add `ADD WS-TOTAL-FEE TO ACCT-CURR-BAL` after the interest ADD. Rest unchanged.

Resulting order per account: fee record (if any) first, then that account's interest records; TRAN-ID suffix stays one run-wide sequence.

## Edge cases

| Case | Handling |
|---|---|
| Balance = limit, or below | no fee, no extra record, no ID consumed |
| Negative balance / limit | plain signed compare |
| Account with several TCATBAL records | fee once (break only) |
| All rates zero for account | fee still charged (Q2) |
| Account with no TCATBAL record | never visited, no fee (Q4) |
| Account/XREF not found | existing behaviour (unchanged) |
| Fee WRITE fails | `ERROR WRITING TRANSACTION RECORD`, abend U0999, RC 16 (same as interest) |

**Preserved defects (deliberately not fixed):**
- F1 — last account processed is never rewritten; an over-limit last account gets a fee record but no balance update.
- F3 — interest TRAN-IDs shift by one per fee charged earlier in the run.
- Cycle credit/debit still zeroed on rewrite (existing `1050` behaviour).

## Acceptance tests

| AC | Test |
|---|---|
| 1 | `legacy/harness/golden.sh --check` — 3 scenarios byte-identical (no golden account is over limit) |
| 2, 3 | `legacy/tests/cr2_overlimit.sh`: exactly 1 cat-`0006` record per over-limit account (one has 2 TCATBAL records), fields as table above, card = XREF |
| 4 | same script: ACCTFILE bal = input bal + Σ interest + 25.00, cycle cr/db = 0 (not last account) |
| 5 | same script: within-limit and equal-to-limit accounts have 0 fee records and bal = input + Σ interest |
| 6 | same script: suffixes 000001..N contiguous, unique; RC = 0 |
| 7 | same script: `trancatg.txt` 19 lines, each 60 bytes + CRLF, new line 6 (after `010005`) matches expected, `git diff` shows only that added line |
