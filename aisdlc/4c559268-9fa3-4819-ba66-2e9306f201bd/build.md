# CR-2 Build notes
- `app/cbl/CBACT04C.cbl`: added `WS-TOTAL-FEES`, `WS-OVERLIMIT-FEE` (25.00); reset fees and `PERFORM 1400-COMPUTE-FEES` at the account break; removed the per-category call; implemented `1400-COMPUTE-FEES` + `1400-B-WRITE-FEE-TX` (clears `TRAN-DESC` before and after); `ADD WS-TOTAL-FEES` in `1050-UPDATE-ACCOUNT`. No new DISPLAYs.
- `app/data/ASCII/trancatg.txt`: row `010006Over-limit Fee` after `010005` (19 rows, CRLF).
- Verified: `legacy/harness/golden.sh --check` → OK (3 scenarios byte-identical, two runs identical).
- Not done (Test stage): `legacy/tests/cr2_overlimit*` scenario and script; the over-limit path is not yet exercised.
- Open: last-account rewrite defect (F3) preserved by design.
