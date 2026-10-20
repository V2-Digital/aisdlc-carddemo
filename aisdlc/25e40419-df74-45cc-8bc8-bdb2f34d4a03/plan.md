# CR-2 Plan — Over-limit fee in `CBACT04C`

Behaviour: [spec.md](spec.md). Context: [intent.md](intent.md).

## Files, in order

1. `app/cbl/CBACT04C.cbl`: add `WS-FEE-AMT` and `WS-TOTAL-FEES` (near `:169`). At the account break, reset the
   fees and `PERFORM 1400` (`:200-205`). Remove the per-category `PERFORM 1400` (`:216`). Implement `1400` and
   `1400-B-WRITE-FEE-TX`. Add `MOVE SPACES TO TRAN-DESC` in `1300-B-WRITE-TX`. Add fees in `1050`.
2. `app/data/ASCII/trancatg.txt`: insert `010006Over-limit Fee…0000` + CRLF after `010005`.
3. `legacy/tests/cr2_overlimit_fee/input/ACCTFILE.txt`, `TCATBALF.txt`: copied from `legacy/golden/CBTRN02C/`
   (non-zero balances), then edited by hand:
   - acct `…01`: balance > limit, plus an extra TCATBAL `01/0002` record (rate 25%), so it has ≥2 non-zero-rate categories (AC3)
   - acct `…02`: balance > limit, 1 non-zero-rate category
   - acct `…03`: balance **=** limit (AC5)
   - acct `…04`: balance > limit, its `01/0001` record removed so only the rate-0 `03/0001` remains (Q1)
   - the last account `…50` stays within its limit (R1)
4. `legacy/tests/cr2_overlimit_fee/expected/`: `TRANSACT.txt`, `ACCTFILE.txt`, `RC.txt` from the new build,
   checked by hand against the spec before commit.
5. `legacy/tests/cr2_overlimit_fee.sh`: runs `INPUT_DIR=…/input legacy/harness/run.sh CBACT04C $tmp`, diffs the
   result with `expected/`, and runs the explicit AC checks from spec.md (fee count per account, bytes of
   desc/amt/card, balance arithmetic, ids 1..N unique, RC 0, trancatg 19 lines / 60 bytes + CRLF).
   AC6: build the baseline program in a `git worktree` at tag `baseline`, run it on the same input, and compare
   its interest records with the new interest records, ignoring cols 1-16. If that is too slow for CI, the
   baseline interest records are saved under `expected/` instead.

## Test strategy / commands

```
legacy/harness/golden.sh --check          # AC1: all 3 golden scenarios byte-identical
bash legacy/tests/cr2_overlimit_fee.sh    # AC2-8, exits non-zero on any failure
```
CI already runs both (`.github/workflows/ci.yml`, step "Legacy tests" globs `legacy/tests/*.sh`).

## Risks

- **Golden drift from the extra `MOVE SPACES`/call move.** Mitigation: run `golden.sh --check` right after step 1.
- **Hand-crafted overpunch values wrong** (e.g. `{` vs `}`). Mitigation: assert input balance/limit decoding in the script; diff SYSOUT.
- **Harness `INPUT_DIR` not overriding all DDs.** Mitigation: only override `ACCTFILE`/`TCATBALF`; others stay stock data, which keeps XREF/DISCGRP valid.
- **TCATBAL key order broken by the inserted `01/0002` record.** Mitigation: insert it in key order (the KSDS load sorts it or fails loudly).
- **R1: the fee is lost for the last account in production.** Mitigation: documented in the spec; follow-up CR to fix it and regenerate golden.

## Effort (agent)

Build ≈ 10 min, test data + script ≈ 15 min, verification ≈ 5 min. **Total ≈ 30 min.**
