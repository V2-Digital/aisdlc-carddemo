# CR-2 Build notes

## Changed
- `app/cbl/CBACT04C.cbl`
  - New `WS-TOTAL-FEE`, `WS-OVERLIMIT-FEE` (25.00).
  - Account break: reset `WS-TOTAL-FEE`, `PERFORM 1400-COMPUTE-FEES` after the xref read. Removed the call inside the non-zero-rate branch.
  - `1400-COMPUTE-FEES` charges when `ACCT-CURR-BAL > ACCT-CREDIT-LIMIT`; new `1400-B-WRITE-FEE-TX` writes the 01/0006 record.
  - `1050-UPDATE-ACCOUNT` adds `WS-TOTAL-FEE` to the balance.
  - `1300-B-WRITE-TX`: `MOVE SPACES TO TRAN-DESC` before the STRING (F3).
- `app/data/ASCII/trancatg.txt`: `010006Over-limit Fee` after `010005` (19 lines, 60 bytes + CRLF).

## Verified
- `legacy/harness/golden.sh --check`: OK, all scenarios byte-identical.
- Smoke run (temp fixture, not committed): accounts 1 and 2 over limit with 3 TCATBALF records. Exactly one fee each (IDs ...01, ...03, amount `0000000250{`). Balances rose by 25.00 (1940→2190, 1580→1830). Account 3 had no fee.

## Not done (Test stage)
- `legacy/tests/cr2_overlimit_fee.sh` and its fixtures.
- Last-account write-back defect (F4) is preserved, as approved.
