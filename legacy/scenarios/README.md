# Scenarios (CR-2)

Extra COBOL scenarios outside `legacy/golden/` (which is the judge) and `legacy/harness/`.

`CBACT04C-overlimit`: INTCALC with over-limit accounts. Input = `golden/CBTRN02C` ACCTFILE + TCATBALF with three edited accounts.
Run: `legacy/scenarios/CBACT04C-overlimit/check.sh` (runs the harness with `INPUT_DIR=…/input`, diffs `expected/`, asserts AC 2-7).
`expected/` is built by `make-fixtures.py` from golden + hand arithmetic, not from the program's output.

| Acct | Edit | Expect |
|---|---|---|
| 1 | limit 1000.00 (bal 1288.10) | 1 fee, balance 1327.66 |
| 2 | limit = balance 1734.97 | no fee |
| 3 | limit 1500.00, group ZEROAPR | 1 fee, no interest, balance 1882.65 |
