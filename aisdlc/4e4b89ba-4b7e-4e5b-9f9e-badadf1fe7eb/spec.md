# CR-2 Spec: over-limit fee in CBACT04C

Context, file table and defects: [intent.md](intent.md). Approver defaults taken: Q1 no fix, Q2 fee
regardless of rate, Q3 inactive accounts charged.

## Inputs / outputs (only the fields this change touches)

| DD | Copybook | Field | Cols (1-based) | PIC | Use |
|---|---|---|---|---|---|
| `ACCTFILE` (I-O) | `app/cpy/CVACT01Y.cpy` | `ACCT-ID` | 1-11 | 9(11) | description text |
| | | `ACCT-CURR-BAL` | 13-24 | S9(10)V99 | compare; += fee |
| | | `ACCT-CREDIT-LIMIT` | 25-36 | S9(10)V99 | compare |
| `XREFFILE` | `app/cpy/CVACT03Y.cpy` | `XREF-CARD-NUM` | 1-16 | X(16) | fee card number |
| `TRANSACT` (out) | `app/cpy/CVTRA05Y.cpy` | `TRAN-ID` | 1-16 | X(16) | PARM-DATE + suffix |
| | | `TRAN-TYPE-CD` / `TRAN-CAT-CD` | 17-18 / 19-22 | X(2) / 9(4) | `01` / `0006` |
| | | `TRAN-SOURCE` | 23-32 | X(10) | `System` |
| | | `TRAN-DESC` | 33-132 | X(100) | see R3 |
| | | `TRAN-AMT` | 133-143 | S9(9)V99 | `0000000250{` |
| | | merchant id / name / city / zip | 144-262 | 9(9) / X | 0 / spaces |
| | | `TRAN-CARD-NUM`, `TRAN-ORIG-TS`, `TRAN-PROC-TS` | 263-330 | X | as interest |

`app/data/ASCII/trancatg.txt` (layout `app/cpy/CVTRA04Y.cpy`): new line 18→19 of 19 =
`010006` + `Over-limit Fee` padded to 50 + `0000` + CRLF, inserted directly after `010005`.

## New working storage (`WS-MISC-VARS`)

- `WS-OVERLIMIT-FEE  PIC S9(09)V99 VALUE 25.00` — the constant.
- `WS-TOTAL-FEE      PIC S9(09)V99` — fees for the current account; reset at the account break.

## Processing rules (new/changed only)

1. **R1 – where the check runs.** At the account break (`CBACT04C.cbl:194-205`), after
   `MOVE 0 TO WS-TOTAL-INT`, also `MOVE 0 TO WS-TOTAL-FEE`; after `1110-GET-XREF-DATA`,
   `PERFORM 1400-COMPUTE-FEES`. The existing `PERFORM 1400-COMPUTE-FEES` inside `IF DIS-INT-RATE NOT = 0`
   (`:216`) is **removed**. One break per account ⇒ at most one fee per account per run (F1), independent
   of rates (F2/Q2). No extra flag needed.
2. **R2 – 1400-COMPUTE-FEES.** `IF ACCT-CURR-BAL > ACCT-CREDIT-LIMIT` (signed numeric compare, balance as
   just read, before this run's interest): `ADD WS-OVERLIMIT-FEE TO WS-TOTAL-FEE`, then
   `PERFORM 1400-B-WRITE-FEE-TX`. Otherwise nothing. `ACCT-ACTIVE-STATUS` is not tested (Q3).
3. **R3 – 1400-B-WRITE-FEE-TX** mirrors `1300-B-WRITE-TX` (`:473-515`): `ADD 1 TO WS-TRANID-SUFFIX`, same
   TRAN-ID STRING, type `01`, cat `0006`, source `System`, `MOVE SPACES TO TRAN-DESC` then
   `STRING 'Over-limit fee for a/c ' ACCT-ID` (34 chars, rest spaces), amount `WS-OVERLIMIT-FEE`, merchant
   fields 0/spaces, card from xref, both timestamps from `Z-GET-DB2-FORMAT-TIMESTAMP`, same WRITE and
   status/abend handling and message.
4. **R4 – F3 fix.** Add `MOVE SPACES TO TRAN-DESC` before the STRING in `1300-B-WRITE-TX`. No golden
   change (tail is already spaces today).
5. **R5 – balance.** `1050-UPDATE-ACCOUNT`: after `ADD WS-TOTAL-INT TO ACCT-CURR-BAL`, add
   `ADD WS-TOTAL-FEE TO ACCT-CURR-BAL`. Cycle fields unchanged behaviour.
6. **R6 – ordering.** The fee record is written *before* that account's interest records, so it takes the
   next suffix and the account's interest IDs shift by +1 (intent R1).
7. **R7 – no new DISPLAYs** — SYSOUT must stay byte-identical for golden.

## Edge cases

| Case | Handling |
|---|---|
| Balance == limit | not charged (strict `>`) |
| Several TCATBALF records for the account | one fee (break runs once) |
| All categories 0% rate | still charged (Q2) |
| Negative balance / zero limit | plain signed compare (0 limit, balance 0.01 ⇒ charged) |
| Inactive account | charged (Q3) |
| Over limit only after adding this run's interest | not charged (balance as read) |
| Account with no TCATBALF record | never processed, not charged (out of scope) |
| Over-limit **last** account | fee TRANSACT written, balance **not** updated — preserved defect F4 |
| TRANSACT write error | existing message + abend, same as interest |

**Preserved defect:** F4 — last TCATBALF account is never rewritten (`:219-220`); now also drops its fee.

## Acceptance tests (→ intent criteria)

| Test | Criteria |
|---|---|
| `legacy/harness/golden.sh --check` passes, all 3 scenarios byte-identical | 1, 6 |
| `legacy/tests/cr2_overlimit_fee.sh`: fixture accounts A (over, 2 non-zero-rate TCATBALF), B (over, one 0%-rate record), C (exactly at limit), D (within, last account) | 2, 3, 8 |
| … asserts exactly 1 cat-`0006` record for A and B, 0 for C and D | 2, 3 |
| … asserts each fee record byte layout, cols 1-330 except timestamps vs. literal expected | 4 |
| … asserts ACCTFILE balance A, B = old + interest + 25.00; C, D = old + interest | 5, 6 |
| … asserts TRAN-IDs contiguous `…000001`..`…00000N`, RC 0 | 4, 8 |
| `trancatg.txt` check in the same test: 19 lines, line 19 bytes == expected, 60 bytes + CRLF | 7 |
