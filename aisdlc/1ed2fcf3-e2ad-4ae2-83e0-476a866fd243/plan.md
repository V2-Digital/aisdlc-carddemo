# CR-2 Plan: over-limit fee in `CBACT04C`

## Files, in order

| # | Path | Change |
|---|---|---|
| 1 | `app/cbl/CBACT04C.cbl` | Add `WS-TOTAL-FEE` and `WS-OVERLIMIT-FEE VALUE 25.00` to `WS-MISC-VARS`.<br>On the account break: reset `WS-TOTAL-FEE` next to `WS-TOTAL-INT`, then `PERFORM 1400-COMPUTE-FEES` after `1110`.<br>Remove the `PERFORM 1400-COMPUTE-FEES` at `:216`.<br>Implement `1400-COMPUTE-FEES` and a new `1400-B-WRITE-FEE-TX`, modelled on `1300-B-WRITE-TX`, with the same write/error handling.<br>`MOVE SPACES TO TRAN-DESC` before both STRINGs.<br>Add `ADD WS-TOTAL-FEE TO ACCT-CURR-BAL` in `1050`.<br>Keep the existing style: fixed format, cols 8-72, uppercase, `*----*` paragraph separators |
| 2 | `app/data/ASCII/trancatg.txt` | Insert `010006Over-limit Fee…0000\r\n` after the `010005` line |
| 3 | `legacy/scenarios/CBACT04C-overlimit/input/ACCTFILE.txt` | Copy of `golden/CBTRN02C/ACCTFILE.txt`, LF, 300 bytes per line. Edits:<br>a/c 1: limit `00000010000{`<br>a/c 2: limit `00000017349G`<br>a/c 3: limit `00000015000{`, group `ZEROAPR   ` |
| 4 | `legacy/scenarios/CBACT04C-overlimit/input/TCATBALF.txt` | Copy of `golden/CBTRN02C/TCATBALF.txt`, unchanged (100 records) |
| 5 | `legacy/scenarios/CBACT04C-overlimit/expected/{TRANSACT,ACCTFILE,SYSOUT,RC}.txt` | Expected output. Build it with a script from the golden files plus the spec §6 values. It must **not** be copied from the new program's output |
| 6 | `legacy/scenarios/CBACT04C-overlimit/check.sh` | Runs the scenario and diffs against `expected/`. It also asserts AC 2-7 directly with awk/grep: fee count, fields, unique IDs, balances, cycle zeroes, and the in-limit comparison against golden |
| 7 | `legacy/scenarios/README.md` | 10 lines: what the scenario is, how to run it, and the account table |

`legacy/scenarios/` is new and is outside `golden/` and `harness/`. No change to `.github/` (CI keeps running `golden.sh --check`).

## Test strategy

**Equivalence (AC 1).** Three golden scenarios, unchanged:
- `CBACT04C`: stock data, no TCATBAL balances.
- `CBTRN02C`: proves the shared copybooks and data are untouched.
- `CBACT04C-after-CBTRN02C`: non-zero interest, 50 accounts × 2 categories.

None of them has an over-limit account, so each must stay byte-identical, including SYSOUT and RC.

**New scenario (AC 2-7).** It covers four cases in one run:
- a/c 1: over limit, has interest, 2 categories.
- a/c 2: balance equal to limit.
- a/c 3: over limit, every rate 0.
- a/c 4-50: within limit, used as the regression check.

The last account (50) is left within its limit, so D1 doesn't affect the result.

**Commands**
```sh
cobc -m -fsign=EBCDIC -fassign-clause=ibm -I app/cpy -o /tmp/x.so app/cbl/CBACT04C.cbl   # AC9: expect no output
legacy/harness/golden.sh --check                                                          # AC1
legacy/scenarios/CBACT04C-overlimit/check.sh                                              # AC2-7, AC9 RC
# check.sh runs: INPUT_DIR=legacy/scenarios/CBACT04C-overlimit/input legacy/harness/run.sh CBACT04C <tmp>
awk '{print length($0)}' app/data/ASCII/trancatg.txt | sort -u; git diff --stat app/data   # AC8: 61 (+CR), +1 line
```

Baseline checked: the current source compiles under those flags with no warnings.

## Risks and mitigations

| Risk | Mitigation |
|---|---|
| Double fee from the per-category hook | The hook call at `:216` is removed and the fee is moved to the account break. a/c 1 (2 categories) proves it |
| Fee missed when rates are 0 | The fee does not depend on the rate. a/c 3 (`ZEROAPR`) proves it |
| `TRAN-DESC` debris after a fee | `MOVE SPACES` before the STRING. `check.sh` byte-compares a/c 2's interest record (the first one after a fee) |
| Signed compare on overpunch fields | `-fsign=EBCDIC` is already in place. The `>` is numeric (both fields S9(10)V99) |
| Golden drift from moving/clearing code | `golden.sh --check` is run after each edit |
| Expected output that just restates the program ("self-fulfilling") | Expected values are derived from golden plus hand arithmetic (spec §6), not from the new binary |
| Downstream ID renumbering on fee nights | Documented (spec §4). Not mitigated in code, by design |
| D1 hides a last-account fee in the balance | Documented as a preserved defect. Recommend a follow-up CR, which must also regenerate the golden output |
| `trancatg.txt` CRLF lost by the editor | Write the line with `printf '%s\r\n'`, then verify with `od -c`/`cat -A` |

## Effort estimate (agent)

| Task | Minutes |
|---|---|
| COBOL change | 8 |
| `trancatg.txt` | 1 |
| Scenario data, expected files, `check.sh`, README | 12 |
| Run golden and the scenario, fix issues | 6 |
| **Total build + test** | **~27** |

## Decisions assumed (from intent defaults; flag if wrong)

1. D1 is **not** fixed in this CR.
2. Inactive accounts **are** charged.
3. No TCATBAL posting for the fee.
4. **New:** the fee record is written before the account's interest records, and its amount is a constant `VALUE 25.00` in working storage, not a PARM.
