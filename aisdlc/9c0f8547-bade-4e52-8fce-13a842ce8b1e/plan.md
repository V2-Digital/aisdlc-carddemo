# CR-2 Plan: over-limit fee in CBACT04C

Spec: [spec.md](spec.md). Context: [intent.md](intent.md).

## Files, in order

1. `app/cbl/CBACT04C.cbl` — WS fields `WS-FEE-AMT`, `WS-TOTAL-FEE`; account-break reset + `PERFORM 1400-COMPUTE-FEES`; remove the call at `:216`; implement `1400-COMPUTE-FEES` + new `1400-B-WRITE-FEE-TX`; `MOVE SPACES TO TRAN-DESC` in `1300-B-WRITE-TX`; `ADD WS-TOTAL-FEE` in `1050`.
2. `app/data/ASCII/trancatg.txt` — insert line 6 `010006Over-limit Fee` padded to 50 + `0000` + CRLF (written with `printf`, check 62 bytes).
3. `legacy/tests/cr2_overlimit/` — inputs for `INPUT_DIR`:
   - `ACCTFILE.txt`: sample `acctdata.txt` with 3 accounts edited — A1 balance > limit (2 TCATBAL records), A2 balance > limit, A3 balance = limit. Last account (50) left within limit (F1).
   - `TCATBALF.txt`: sample with non-zero balances for A1/A2/a within-limit account, plus a second category record for A1 (key-ordered), so interest is non-zero too.
4. `legacy/tests/cr2_overlimit.sh` — runs `run.sh CBACT04C` with `INPUT_DIR`, then asserts AC2–AC7 with `awk`/`cut` on fixed columns; exits non-zero on the first failure and prints PASS/FAIL per AC.

## Test strategy

- Equivalence: all 3 golden scenarios (`CBACT04C`, `CBTRN02C`, `CBACT04C-after-CBTRN02C`) must stay byte-identical — no golden account is over limit, and `TRAN-DESC` clearing does nothing on golden data.
- New: `cr2_overlimit.sh` checks fee count per account (=1 / =0), fee record fields, balances (input + interest + 25.00), contiguous TRAN-IDs, RC 0, trancatg shape.
- AC5 (unchanged for within/equal-limit accounts): the script compares their TRANSACT records (cols 17-262, i.e. minus TRAN-ID and timestamps) and ACCTFILE records against an expected file checked in next to the inputs, generated once from the `baseline` program.

Commands:
```
legacy/harness/golden.sh --check
legacy/tests/cr2_overlimit.sh
```

## Top risks

| Risk | Mitigation |
|---|---|
| Golden drift (desc clearing, call moved) | run `golden.sh --check` first, after every edit |
| Hand-built ACCTFILE has wrong overpunch/offsets | build with a small `printf`/`awk` helper and assert cols 13-36 round-trip in the script |
| TCATBAL out of key order → extra account breaks | sort inputs by cols 1-17 and assert in the script |
| Over-limit account placed last hides fee update (F1) | keep account 50 within limit; documented in spec |
| Timestamps differ per run | compare TRANSACT on cols 1-262 only |

## Effort

Build ~10 min, test scenario + script ~15 min, verify ~5 min. **≈30 min.**
