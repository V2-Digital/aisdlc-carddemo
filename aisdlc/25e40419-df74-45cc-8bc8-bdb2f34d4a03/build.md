# CR-2 Build notes

Changes (per approved plan, steps 1-2):
- `app/cbl/CBACT04C.cbl`: `WS-FEE-AMT` (25.00) and `WS-TOTAL-FEES`; at the account break, reset fees and
  `PERFORM 1400-COMPUTE-FEES` (only call site; per-category call removed); `1400` charges when
  `ACCT-CURR-BAL > ACCT-CREDIT-LIMIT`; new `1400-B-WRITE-FEE-TX` (type 01, cat 06, `Over-limit fee for a/c <id>`);
  `MOVE SPACES TO TRAN-DESC` added in `1300-B-WRITE-TX`; `1050` adds interest + fees.
- `app/data/ASCII/trancatg.txt`: `010006Over-limit Fee` (19 records, all 61 bytes incl. CRLF).

Verified: `legacy/harness/golden.sh --check` -> OK, all 3 scenarios byte-identical.
Not yet done (Test stage): over-limit scenario in `legacy/tests/cr2_overlimit_fee*` and its script, so the fee path itself is compiled but not yet exercised.
R1 (last-account write-back) left unfixed, as approved.
