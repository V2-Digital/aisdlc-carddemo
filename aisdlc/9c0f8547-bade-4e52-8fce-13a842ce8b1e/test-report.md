# CR-2 Test report

**Result: green. 0 fix iterations needed for the program.** The only failures were two mistakes in my own test script (wrong column offsets), fixed before the final run.

## Commands and results

| Command | Result |
|---|---|
| `legacy/harness/golden.sh --check` | OK. 3 scenarios byte-identical, including SYSOUT/RC (50 + 50 + 262/38/50/100 records compared). Two runs identical. |
| `legacy/tests/cr2_overlimit.sh` | exit 0, 18/18 PASS |
| Same script against the **baseline** `CBACT04C.cbl` (stashed change) | exit 1, 9 FAIL, so the test detects a missing fee |
| `mvn -q test` | not run: no Maven project touched (COBOL change only) |

## New scenario: `legacy/tests/cr2_overlimit/`
Inputs for `INPUT_DIR`, built from the sample data: `ACCTFILE.txt` (50), `TCATBALF.txt` (51, key-ordered).
- Over limit: acct 1 (2 TCATBAL records), acct 2, acct 4.
- Balance = limit: acct 3. Within limit: acct 5 and the others (acct 50, the last, is within limit, so F1 does not hide anything).
- `baseline_TRANSACT.txt` / `baseline_ACCTFILE.txt` (51 / 50 records): output of the `baseline` program on the same inputs, used for the "unchanged" comparisons.

Run: RC 0, 54 TRANSACT records = 51 interest + 3 fees.

## Acceptance criteria
| AC | Evidence |
|---|---|
| 1 | golden check OK |
| 2, 3 | accts 1, 2, 4: exactly 1 record each with type/cat `010006`, source `System`, desc `Over-limit fee for a/c <id>` (tail spaces, F4 verified), amount `0000000250{`, card from XREF. Acct 1 has 2 TCATBAL records and still gets one fee. 3 fee records in total. |
| 4 | ACCTFILE balance = baseline (input + interest) + 25.00 for accts 1, 2, 4; other fields unchanged; cycle credit/debit 0 |
| 5 | accts 3 (equal) and 5: no fee; all 51 interest records equal the baseline program's (cols 17-262); all other ACCTFILE records byte-identical to baseline |
| 6 | TRAN-IDs `2022071800` + 000001..000054 contiguous and unique; RC 0 |
| 7 | 19 records, all 61 bytes (60 + CRLF); line 6 is `010006Over-limit Fee`+pad+`0000`; `git diff baseline --numstat` shows 1 added, 0 removed |

## Notes
- Not tested (by design, per spec): an over-limit *last* account (F1, preserved); TRAN-ID shift after a fee (F3, preserved, visible in the contiguous-ID check, since interest IDs follow the fees).
- The script depends on `git` and the `baseline` tag for the AC7 numstat check.
- Nothing left failing.
