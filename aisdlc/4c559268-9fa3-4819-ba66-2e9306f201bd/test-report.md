# CR-2 Test report

**Result: green, 0 fix iterations needed for the program.** The first script drafts had wrong column offsets in the test itself, fixed in 2 edits. COBOL was unchanged.

## Commands
| Command | Result |
|---|---|
| `legacy/harness/golden.sh --check` | OK: 3 scenarios byte-identical to `legacy/golden/` (TRANSACT 50, ACCTFILE 50, SYSOUT 152/302, RC); two runs identical (AC1) |
| `bash legacy/tests/cr2_overlimit.sh` | 24/24 PASS, exit 0 |
| `mvn -q test` | not run: no `modernized/` project touched |

## New scenario `legacy/tests/cr2_overlimit/`
Input: `TCATBALF` = golden CBTRN02C (2 categories/acct); `ACCTFILE` = golden CBTRN02C with limits edited:
accts 1, 2 → 1000.00 (over, 2 cats each); acct 3 → limit = balance (equal); acct 4 → 1000.00 + group `ZEROAPR` (over, zero rate).

Output: RC 0, 52 TRANSACT records (50 interest-run rows minus acct 4 interest, plus 3 fees = 52 with IDs `000001`–`000052` contiguous), ACCTFILE 50, SYSOUT 298 lines (the 4 missing lines vs golden are the group-fallback messages for acct 4, which now has a ZEROAPR rate for its types).

Asserted independently of `expected/` (which was committed only after these passed):
- exactly 3 `010006` records, accts 1, 2, 4 once each; none for acct 3 (equal) or others (AC2, AC3)
- fee record 350 bytes; source, 100-byte desc `Over-limit fee for a/c 00000000001`, amt `0000000250{`, merchant fields, TRAN-ID prefix (AC4)
- interest rows equal golden scenario 3 minus acct 4, so there is no description residue after a fee (R3)
- acct 4 has no interest tx (Q2); accts 1, 2, 4 balance = base + 25.00 (acct 1, 2: golden after-interest; acct 4: input), cycle fields zero (AC5)
- accts 3, 5–49 equal golden; acct 50 equals input (F3 defect preserved) 
- two runs byte-identical; equals committed `expected/` (AC6)
- `trancatg.txt`: 19 rows, 60 bytes + CRLF, row 6 = `010006Over-limit Fee…0000` (AC7)

## Left / for humans
- F3 (last account not rewritten) is preserved; a fee on the last account would be written but its balance is not updated. Test avoids it. Needs a separate CR.
- `legacy/tests/cr2_overlimit.sh` is new; CI runs it with bash from the repo root.
