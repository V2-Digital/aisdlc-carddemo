# CR-2 Build notes

Implemented the approved plan as written. No deviations.

## Changes
- `app/cbl/CBACT04C.cbl`
  - New WS fields `WS-OVERLIMIT-FEE` (25.00) and `WS-TOTAL-FEE`.
  - `1400-COMPUTE-FEES` is performed once per account at the control break, after the xref read. The old call inside the non-zero-rate branch is removed.
  - New `1400-B-WRITE-FEE-TX` writes the 01/0006 fee transaction.
  - `MOVE SPACES TO TRAN-DESC` added in `1300-B-WRITE-TX` (R2).
  - `1050` now does `ADD WS-TOTAL-INT WS-TOTAL-FEE TO ACCT-CURR-BAL`.
- `app/data/ASCII/trancatg.txt`: `010006Over-limit Fee` added (19 records, sorted, 60 bytes + CRLF).
- `legacy/tests/cr2_overlimit/{input,expected}` and `legacy/tests/cr2_overlimit.sh`.

## Evidence
- `legacy/harness/golden.sh --check`: OK, all 3 scenarios byte-identical.
- `legacy/tests/cr2_overlimit.sh`: all checks pass (AC2–AC9; the script covers expected-file diff, one fee per over-limit account, amount `0000000250{`, desc, card, timestamps, sequential IDs, balances, at-limit and below-limit accounts, clean interest desc, baseline comparison, trancatg format).
- New scenario: 52 TRANSACT records (50 interest + 2 fees).
  - Acct 1: balance 2500.00 > limit 2020.00, 2 categories. Fee 25.00 plus interest 125.00 and 104.16, giving balance 2754.16.
  - Acct 2: over limit, `ZEROAPR` group (rate 0). Fee only, balance 7025.00.
  - Acct 3 is at its limit and acct 4 is below it. Neither is charged.
- Baseline comparison (AC7) runs the `baseline` tag's CBACT04C on the same input. Non-fee records are identical apart from TRAN-ID.

## For a human
- Preserved defect R1 (last account never rewritten) is untouched. The scenario keeps acct 50 within its limit.
- The production `TRANCATG` KSDS needs a reload (`TRANCATG.jcl`) for reports to show the new category (R6).
