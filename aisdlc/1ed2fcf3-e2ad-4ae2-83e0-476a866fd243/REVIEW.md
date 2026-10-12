# CR-2 Review: over-limit fee in `CBACT04C`

**Verdict: ready to merge.** The change follows spec §2 line for line. Golden is byte-identical, and the new scenario's expected output is built independently of the program. No blockers.

## What I ran (reviewer session, working tree = HEAD `593d40d`)

| Check | Result |
|---|---|
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty (untouched) |
| `cobc -m -fsign=EBCDIC -fassign-clause=ibm -I app/cpy …CBACT04C.cbl` | exit 0, no warnings (AC9) |
| `legacy/harness/golden.sh --check` | OK, 3 scenarios byte-identical, two runs identical (AC1) |
| `legacy/scenarios/CBACT04C-overlimit/check.sh` | 21/21 PASS (AC2-7, RC 0) |
| `python3 -I make-fixtures.py` into a clean `git archive` copy, then `diff -r` vs committed `legacy/scenarios` | identical, so the fixtures are reproducible from golden |
| `trancatg.txt` | 19 lines, all 61 chars + `\r\n`, new row after `010005`, diff = +1 line (AC8) |

## Correctness against spec

- Fee is decided once per account on the break (`CBACT04C.cbl:209`), after `1100`/`1110`, using a strict `>` on the balance as read (`:525`). The per-category call at old `:216` is removed. ✔
- `WS-TOTAL-FEE` is reset after `1050` runs for the previous account (`:203`), so the previous account's fee is not lost. ✔
- `1050` adds the fee next to interest (`:356`). ✔
- `MOVE SPACES TO TRAN-DESC` is done before both STRINGs (`:489`, `:543`). The interest record after a fee (a/c 2) is byte-compared clean. ✔
- Money: `WS-OVERLIMIT-FEE S9(09)V99 VALUE 25.00` goes to `TRAN-AMT S9(9)V99` with no truncation. The signed compare is between two S9(10)V99 fields. Expected `0000000250{`, `00000013276F` (1288.10+14.56+25.00) and `00000018826E` (1857.65+25.00) are all checked by hand. ✔

## Test quality

- `expected/` comes from `make-fixtures.py`, built from `golden/CBACT04C-after-CBTRN02C` records (renumbered) and fee records assembled field by field from the spec layout. It is not the program's output. This is not a tautology.
- `check.sh` also asserts the ACs directly, not only through `cmp`. The test report says it fails on mutations (`>`→`>=`, removing `MOVE SPACES`).
- It covers: over limit with 2 categories, balance = limit, over limit with all rates 0, and 47 in-limit regression accounts.

## Findings

1. **should-fix (follow-up CR, not this PR)**: `CBACT04C.cbl:219-224`, defect D1. If the **last** TCATBAL account is over limit, its fee transaction is written but its balance is never charged. That is customer-visible: a fee shows on the ledger but not on the balance. It is preserved by design (fixing it changes golden). Suggestion: raise the D1 CR now, so this fee doesn't run in production for long with the gap.
2. **nit**: `CBACT04C.cbl:536-573`. `1400-B-WRITE-FEE-TX` duplicates about 30 lines of `1300-B-WRITE-TX` (ID build, merchant blanking, timestamps, WRITE/error). The plan asked for this, and it matches house style. A shared write paragraph would be cleaner, but it would mean touching the interest path. Fine to leave.
3. **nit**: `legacy/scenarios/CBACT04C-overlimit/check.sh:14`. The harness output goes to `/dev/null`. If compile or run fails, you only see a cascade of FAILs. Suggestion: write it to `$OUT/run.log` and `cat` it when it fails.
4. **nit**: `make-fixtures.py` is the only Python in the legacy tree, and nothing documents how to call it (it needs the repo root as `argv[1]`). Suggestion: add one usage line to `legacy/scenarios/README.md`.
5. **info**: no test for an inactive over-limit account (Q2) or for an over-limit last account (D1). Both are documented as intended behaviour.

## What a human should look at before merging

- **Confirm the assumed decisions** (plan, "Decisions assumed"): D1 is not fixed, inactive accounts are charged, the fee is not posted to TCATBAL, and the fee record comes before the account's interest records.
- **Downstream impact on fee nights**: `TRAN-ID` suffixes after each fee shift by +1. Check that reconciliation or reporting doesn't key on interest-record IDs.
- **Balance basis**: the fee uses the balance before tonight's interest. An account that goes over the limit only because of tonight's interest is charged on the next run. Product should agree with this.
- **CI**: `.github/` only runs `golden.sh --check`. The new `check.sh` is **not** in CI, so it only protects anything if someone runs it. Decide whether a follow-up should wire it in (that needs a `.github/` change, outside this CR).
