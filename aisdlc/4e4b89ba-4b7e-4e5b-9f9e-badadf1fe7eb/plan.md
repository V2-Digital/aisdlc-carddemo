# CR-2 Plan: over-limit fee in CBACT04C

Behaviour: [spec.md](spec.md). Background: [intent.md](intent.md).

## Files, in order

1. `app/cbl/CBACT04C.cbl`
   - `WS-MISC-VARS`: add `WS-OVERLIMIT-FEE`, `WS-TOTAL-FEE` (spec "New working storage").
   - Account break: reset `WS-TOTAL-FEE`; `PERFORM 1400-COMPUTE-FEES` after xref read; remove call at `:216` (R1).
   - `1050-UPDATE-ACCOUNT`: `ADD WS-TOTAL-FEE TO ACCT-CURR-BAL` (R5).
   - `1300-B-WRITE-TX`: `MOVE SPACES TO TRAN-DESC` before STRING (R4).
   - `1400-COMPUTE-FEES` + new `1400-B-WRITE-FEE-TX` (R2, R3). Keep 80-col fixed format, cols 8-72.
2. `app/data/ASCII/trancatg.txt`: insert `010006Over-limit Fee…0000\r\n` after `010005` (60 bytes + CRLF).
3. `legacy/tests/data/cr2_overlimit_fee/`: `TCATBALF.txt`, `ACCTFILE.txt` fixtures (overpunch signs).
   Accounts `00000000001`–`00000000004` (exist in sample `cardxref.txt`; XREFFILE/DISCGRP use sample data):
   - 01 over limit, records `01/0001` + `01/0002` (both non-zero DEFAULT rate) — catches a per-category double charge
   - 02 over limit, one record `02/0001` (0% rate) — proves fee is rate-independent (Q2)
   - 03 balance == limit, one record
   - 04 within limit, one record — last account (absorbs defect F4)
   - Balances picked so interest is a round, hand-computed value.
4. `legacy/tests/cr2_overlimit_fee.sh` (bash, `set -euo pipefail`): runs
   `INPUT_DIR=<fixtures> legacy/harness/run.sh CBACT04C $tmp`, then asserts with `cut`/`awk` against
   hard-coded expected values: RC 0; fee counts 1/1/0/0; fee record cols 1-278 exact; timestamps equal
   to the interest records'; IDs contiguous; ACCTFILE balances for 01-03 (04 noted as F4, not asserted);
   `trancatg.txt` 19 lines and exact new line. Prints PASS/FAIL per check, exits non-zero on any failure.

## Test strategy / commands

```
legacy/harness/golden.sh --check           # criteria 1, 6 — all 3 scenarios unchanged
bash legacy/tests/cr2_overlimit_fee.sh     # criteria 2-5, 7, 8
for t in legacy/tests/*.sh; do bash "$t" || exit 1; done   # as CI runs it
```
Negative check (local, not committed): move the fee call back inside the rate loop and confirm the test
fails on account 01 (two fees).

## Risks

| Risk | Mitigation |
|---|---|
| Golden SYSOUT/TRANSACT drift (ordering, new DISPLAY) | No DISPLAYs; fee only when over limit; `golden.sh --check` first and last |
| Overpunch errors in hand-made fixtures | Derive fixtures from sample lines with `sed` edits; verify via harness unload |
| COBOL column/format errors | Copy layout of `1300-B-WRITE-TX`; compile via `run.sh` early |
| Expected interest values wrong | Pick TCATBALF balances giving exact cents; confirm from first run before hard-coding |
| CI picks up fixture dir as a test | Fixtures are `.txt` under `tests/data/`; CI globs `tests/*.sh` only |
| Fee on last account silently lost (F4) | Documented; test keeps over-limit accounts off the last account |

## Effort (agent)

Build ~6 min, test + fixtures ~8 min ⇒ **~15 min**.
