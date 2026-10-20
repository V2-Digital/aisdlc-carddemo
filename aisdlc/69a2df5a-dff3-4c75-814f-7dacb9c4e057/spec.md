# CR-2 Spec: over-limit fee in `CBACT04C`

Context, file table and current flow: [intent.md](intent.md). Approver defaults accepted: Q1 the fee is checked
for every visited account, whatever its rates. Q2 the R1 defect is left as is. Q3 the test uses the balance as read.

## Inputs / outputs (only fields this change touches)

| File | Copybook | Field | Offset (1-based) / len | PIC | Use |
|---|---|---|---|---|---|
| `ACCTFILE` (I-O) | `app/cpy/CVACT01Y.cpy` | `ACCT-ID` | 1 / 11 | `9(11)` | fee desc |
| | | `ACCT-CURR-BAL` | 13 / 12 | `S9(10)V99` | test; fee added on REWRITE |
| | | `ACCT-CREDIT-LIMIT` | 25 / 12 | `S9(10)V99` | test |
| `XREFFILE` | `app/cpy/CVACT03Y.cpy` | `XREF-CARD-NUM` | 1 / 16 | `X(16)` | fee tran card num |
| `TRANSACT` (out) | `app/cpy/CVTRA05Y.cpy` | `TRAN-ID` | 1 / 16 | `X(16)` | `PARM-DATE` + `WS-TRANID-SUFFIX` |
| | | `TRAN-TYPE-CD` / `TRAN-CAT-CD` | 17 / 2, 19 / 4 | `X(2)`, `9(4)` | `01`, `0006` |
| | | `TRAN-SOURCE` | 23 / 10 | `X(10)` | `System` |
| | | `TRAN-DESC` | 33 / 100 | `X(100)` | `Over-limit fee for a/c ` + acct id, rest spaces |
| | | `TRAN-AMT` | 133 / 11 | `S9(09)V99` | `0000000250{` |
| `trancatg.txt` (data only) | `app/cpy/CVTRA04Y.cpy` | type+cat / desc | 1 / 6, 7 / 50 | | `010006` / `Over-limit Fee` |

## Processing rules (new or changed)

1. **New working storage:** `WS-OVERLIMIT-FEE PIC S9(09)V99 VALUE 25.00`.
2. **Where the hook runs.** `PERFORM 1400-COMPUTE-FEES` moves out of `IF DIS-INT-RATE NOT = 0` (`CBACT04C.cbl:214-216`)
   to the account-break block, right after `1110-GET-XREF-DATA` (`:206`). So it runs **exactly once per visited
   account**, after the account and xref are read and after `MOVE 0 TO WS-TOTAL-INT`. No per-account flag is needed.
3. **`1400-COMPUTE-FEES`:** `IF ACCT-CURR-BAL > ACCT-CREDIT-LIMIT` (signed numeric compare, balance as read):
   - `ADD WS-OVERLIMIT-FEE TO WS-TOTAL-INT`. The existing `1050-UPDATE-ACCOUNT` (`:352`) then adds it to the
     balance with the interest. `1050` does not change.
   - `PERFORM 1400-B-WRITE-FEE-TX`.
   Otherwise it does nothing.
4. **`1400-B-WRITE-FEE-TX`** is a copy of `1300-B-WRITE-TX` (`:473-515`) with these changes: `TRAN-CAT-CD` = `06`,
   desc `'Over-limit fee for a/c '` + `ACCT-ID`, `TRAN-AMT` = `WS-OVERLIMIT-FEE`. The rest is the same: shared
   `WS-TRANID-SUFFIX`, merchant id 0, merchant name/city/zip spaces, xref card num, both timestamps from
   `Z-GET-DB2-FORMAT-TIMESTAMP`, and the same WRITE status check and abend path.
5. **Clear the desc.** `MOVE SPACES TO TRAN-DESC` before the `STRING` in **both** write paragraphs. Without it, the
   24-byte interest desc written after a 34-byte fee desc would keep the old bytes 25-34
   (`STRING` does not pad). This is a no-op for the golden runs, because their desc is always the same length.
6. **Tran order** within an account: the fee tran first, then that account's interest trans, in TCATBAL order.
   Every later tran id moves up by 1 per fee (R4).

## Edge cases

| Case | Behaviour |
|---|---|
| Balance = limit, or below it | no fee, no tran, output unchanged |
| Several non-zero-rate TCATBAL records | one fee (rule 2) |
| All rates 0 (e.g. only `030001`) | fee still charged (Q1) |
| Negative balance | never over limit |
| Account with no TCATBAL record | never visited, never charged (R6, unchanged) |
| Last account in TCATBAL is over limit | fee tran written, **balance not rewritten** (R1, preserved) |
| Fee WRITE fails | `ERROR WRITING TRANSACTION RECORD` + abend, as for interest |

**Preserved defects (fix later, deliberately):**
- R1: the last TCATBAL account is never rewritten, so neither its interest nor its fee is posted.
- R6: over-limit accounts with no TCATBAL record are skipped.
- The over-limit test uses the pre-interest balance, so an account that goes over limit only because of this run's interest is not charged until the next run.

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `legacy/harness/golden.sh --check` passes, all 3 scenarios byte-identical, RC 0 | 1 |
| Scenario `cr2-overlimit`: one `0006` tran per over-limit account, including the account with 2 non-zero-rate records and the all-zero-rate account | 2 |
| The fee tran's fields (bytes 17-132, amount, card num, ts, id sequence) match rule 4 | 3 |
| Over-limit balance = input balance + sum of its interest + 25.00, cycle credit/debit 0 | 4 |
| Account at limit plus all other accounts: same ACCTFILE line and interest tran (except tran id) as the pre-change run | 5 |
| `trancatg.txt`: 19 lines, CRLF, `010006Over-limit Fee` padded to 60 bytes after `010005`. The other 18 lines unchanged (`git diff` shows +1 line) | 6 |
| The scenario is run twice, and the outputs of both runs are byte-identical to the committed expected outputs | 7 |
