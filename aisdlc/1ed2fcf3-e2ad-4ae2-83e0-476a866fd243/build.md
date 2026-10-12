# CR-2 Build

## Changed
- `app/cbl/CBACT04C.cbl`
  - New `WS-TOTAL-FEE`, `WS-OVERLIMIT-FEE` (25.00).
  - `1400-COMPUTE-FEES` is now performed once on the account break, after the xref read. The per-category call is removed.
  - New `1400-B-WRITE-FEE-TX` writes the fee record (01/0006, System, desc, card from xref).
  - `1050-UPDATE-ACCOUNT` adds `WS-TOTAL-FEE` to the balance.
  - `MOVE SPACES TO TRAN-DESC` before both STRINGs.
- `app/data/ASCII/trancatg.txt`: `010006Over-limit Fee` added after `010005`. CRLF kept, one line added.

## Evidence
- `cobc -m -fsign=EBCDIC -fassign-clause=ibm`: no output (no warnings).
- `golden.sh --check`: OK, all 3 scenarios byte-identical.
- Ad-hoc run with the spec §6 inputs (a/c 1, 2, 3 edited): RC 0, TRANSACT 51 records, SYSOUT 298 lines.
  - Fees are on a/c 1 and a/c 3 only, as `…0001` and `…0004`.
  - a/c 1 balance `00000013276F`, a/c 2 `00000017642A` (equal to limit, no fee), a/c 3 `00000018826E`.

## Not done (Test stage)
`legacy/scenarios/CBACT04C-overlimit/` (input, expected, check.sh, README) is not written yet.
