# CR-2 Plan — Over-limit fee in CBACT04C

Spec: [spec.md](spec.md). COBOL only; no `modernized/` work.

## Files, in order

1. `app/cbl/CBACT04C.cbl` — WS fields; account-break reset + `PERFORM 1400-COMPUTE-FEES`; drop the call at
   `:216`; implement `1400-COMPUTE-FEES` + `1400-B-WRITE-FEE-TX`; `ADD WS-TOTAL-FEES` in `1050`.
2. `app/data/ASCII/trancatg.txt` — insert `010006Over-limit Fee` row (CRLF preserved).
3. `legacy/tests/cr2_overlimit/input/` — `TCATBALF.txt` = copy of `golden/CBTRN02C/TCATBALF.txt`
   (2 categories/acct); `ACCTFILE.txt` = `golden/CBTRN02C/ACCTFILE.txt` with:
   - acct 1: limit → 1000.00 (bal 1288.10) — over, 2 categories
   - acct 2: limit → 1000.00 (bal 1734.97) — over, 2 categories
   - acct 3: limit → its balance exactly — equal, no fee
   - acct 4: limit → 1000.00, group → `ZEROAPR` — over, zero rate
   - acct 50 (last, defect F3) untouched.
4. `legacy/tests/cr2_overlimit/expected/` — `TRANSACT/ACCTFILE/SYSOUT/RC.txt` from the new program, committed
   only after the script's independent asserts pass.
5. `legacy/tests/cr2_overlimit.sh` — runs scenario twice, then asserts:
   - RC 0; both runs byte-identical; equal to `expected/`.
   - exactly 3 cat-`0006` records, for accts 1, 2, 4, each byte-exact per spec table.
   - TRANSACT with fee records removed, ignoring TRAN-ID, == golden scenario 3 TRANSACT minus acct 4 line
     (interest unaffected, since it depends on `TCATBALF` only).
   - TRAN-IDs contiguous `000001..000052`.
   - ACCTFILE accts 1, 2, 4 balance = golden scenario 3 balance + 25.00 (acct 4: input + 25.00); others
     byte-equal golden apart from the edited limit/group fields.
   - `trancatg.txt` 19 rows × 62 bytes, row 6 as specified.

## Test commands

```
legacy/harness/golden.sh --check          # AC1: 3 scenarios unchanged
legacy/tests/cr2_overlimit.sh             # AC2-7
```
Internally: `INPUT_DIR=legacy/tests/cr2_overlimit/input legacy/harness/run.sh CBACT04C <tmp>`.

## Risks

- Golden drift from a stray change (DISPLAY, desc residue) → no new DISPLAYs; `golden.sh --check` first.
- Desc residue after a fee (R3) → clear `TRAN-DESC` before and after fee write; assert interest rows match golden.
- Self-referential expected outputs → expected files only accepted after independent asserts pass.
- `ZEROAPR` lookup misses type `03` → falls back to `DEFAULT 030001` (rate 0): still all-zero; asserted by no acct-4 interest row.
- Overpunch editing of test input by hand → generate with a small script, verify bal/limit via asserts.
- Last-account defect hides balance effect → no over-limit test account is last (acct 50).

## Effort

Build ~8 min, test scenario + script ~12 min, total **~20 min**.
