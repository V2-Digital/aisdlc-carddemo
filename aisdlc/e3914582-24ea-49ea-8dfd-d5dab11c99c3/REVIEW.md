# CR-2 Review — Over-limit fee in CBACT04C

**Verdict: ready to merge.** The change follows spec rules 1-7, golden is byte-identical, and the new scenario proves one fee per over-limit account, using values I checked by hand. No blockers.

## What I ran

| Command | Result |
|---|---|
| `legacy/harness/golden.sh --check` | `two runs byte-identical`, `OK - outputs match legacy/golden` (AC1) |
| `bash legacy/tests/cr2_overlimit.sh` | 16/16 `ok`, exit 0 |
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty: the protected paths are untouched |

## Hand verification of `expected/` (not just regenerated)

- **Acct 01:** TCATBAL 1200.00 @15% = 15.00 and 600.00 @25% = 12.50. 1000.00 + 27.50 + 25.00 = 1052.50 = `00000010525{` ✓ (AC3, AC4)
- **Acct 02** (700 = limit 700) → 712.50. **Acct 03** (300 < 700) → 312.50. Neither has a fee ✓ (AC5)
- **Acct 04:** 900 > 500, type 02 only (rate 0). The fee is the only record, and the balance is 925.00 ✓ (Q1)
- **Acct 05** is last. It gets a fee and an interest record, and ACCTFILE is unchanged ✓ (F3, as approved)
- **Fee records** (TRANSACT ids 1, 6, 7): cols 17-22 = `010006`, amount `0000000250{`, merchant id 0, merchant name blank, card = the account's xref card (same as its interest record), TS = the run TS, length 350 ✓ (AC2)
- **TRAN-ID suffixes** run 000001-000008 with no gaps ✓ (AC6)
- **`trancatg.txt` line 6:** `010006Over-limit Fee` + pad + `0000\r\n`, 19 lines ✓ (AC7)
- **Money:** `WS-OVERLIMIT-FEE`/`WS-TOTAL-FEE` are `S9(09)V99`, the same as `TRAN-AMT` and `WS-TOTAL-INT`. Moving and adding them into `ACCT-CURR-BAL S9(10)V99` cannot truncate. The compare is signed numeric.

## Findings

1. **should-fix — the AC5 baseline comparison is not automated.** See `legacy/tests/cr2_overlimit.sh:9-11`. Accts 02/03 are compared only with `expected/`, and `expected/` came from the *new* program. The comparison with the baseline program (test-report.md, "AC5 / AC8 evidence") was a one-off manual run. *Suggestion:* add two hard-coded asserts. ACCTFILE row 2 cols 13-24 = `00000007125{` and row 3 cols 13-24 = `00000003125{`. Also assert acct 04's balance `00000009250{`, so that Q1's balance effect is checked explicitly and not only by the `expected/` diff.
2. **nit — the amount assert is not pinned to its column.** At `legacy/tests/cr2_overlimit.sh:16`, `grep -c '0000000250{'` matches anywhere in the line. *Suggestion:* use `cut -c133-143`, the same way the line 13 check pins cols 17-22.
3. **nit — the harness output is discarded.** At `legacy/tests/cr2_overlimit.sh:8`, `>/dev/null 2>&1` hides compile errors. When the build breaks, the runner reports only "expected/... identical FAIL", with no cause. *Suggestion:* send the output to `$out/run.log` and `cat` it on failure.
4. **nit — the fee and interest write paths are duplicated.** `1400-COMPUTE-FEES` (`app/cbl/CBACT04C.cbl:525-569`) copies about 40 lines of `1300-B-WRITE-TX`, including the write/error block. This is acceptable because the spec asks for the code to be "built field by field like 1300-B", and it matches the file's style. A shared write paragraph can wait until a third transaction type is added.
5. **nit — `WS-OVERLIMIT-FEE` is a constant in the middle of the work fields.** It is declared at `app/cbl/CBACT04C.cbl:171`. A short comment ("fee amount, CR-2") would help future readers. This is optional.

## What a human should look at before merging

- **F3 now costs money.** If the last account in TCATBAL order is over its limit, it gets a fee *transaction* but its balance is never charged (acct 05 in the scenario). Production will have the same ledger/balance mismatch for that one account per run. This was approved as Q2 = no. Please make sure the follow-up CR to fix the last-account write-back is actually raised. test-report.md says "nothing for a human to decide", which undersells this.
- **Record order changed.** For over-limit accounts the fee is written *before* the interest records (spec rule 7). Check that no downstream consumer of the TRANSACT GDG (e.g. posting, the CBTRN03C report) relies on interest coming first.
- **Q3 timing.** An account pushed over its limit only by tonight's interest is charged on the next run, not tonight. Confirm that Product accepts this.
- **Fees are not added to TCATBAL** (out of scope, the same as interest). Category balances will not show over-limit fees until a later CR adds this.
