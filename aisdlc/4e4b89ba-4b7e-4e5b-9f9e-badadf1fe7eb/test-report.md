# CR-2 Test report

## Result: green. 24/24 checks pass, golden unchanged. Nothing failing.

## Commands
| Command | Result |
|---|---|
| `legacy/harness/golden.sh --check` | OK. 3 scenarios (`CBACT04C`, `CBTRN02C`, `CBACT04C-after-CBTRN02C`) byte-identical, including SYSOUT and RC. 50/50/50 TRANSACT, 50/50/50 ACCTFILE records compared in the interest scenarios. 0 diffs. |
| `bash legacy/tests/cr2_overlimit_fee.sh` | 24 PASS, 0 FAIL, exit 0 |
| `for t in legacy/tests/*.sh; do bash "$t"; done` (as CI) | pass |

No Maven project is touched (COBOL-only change).

## New test: `legacy/tests/cr2_overlimit_fee.sh`
Fixtures: `legacy/tests/data/cr2_overlimit_fee/{ACCTFILE,TCATBALF}.txt` (4 accounts, 5 TCATBALF records). Runs `run.sh CBACT04C` with `INPUT_DIR`.

| Acct | Case | Expect | Actual |
|---|---|---|---|
| 1 | over limit, 2 non-zero-rate records | 1 fee; 1500.00+10+10+25 = 1545.00 | pass |
| 2 | over limit, 0% rate (type 02) | 1 fee; 300.00+25 = 325.00 | pass |
| 3 | balance == limit | no fee; 500.00+20 | pass |
| 4 | within limit, last account | no fee (balance not rewritten, F4, not asserted) | pass |

Also asserted: RC 0; 6 TRANSACT records; exactly 2 cat-0006 records; fee bytes 1-262 and card number exact; fee timestamps equal the interest timestamps; IDs contiguous `…01`–`…06`; the interest description after a fee is clean (F3); `trancatg.txt` has 19 lines, the new line sits after `010005` and has the exact text and CRLF (62 bytes with the newline).

## Negative check (local, reverted)
Moving the fee call back into the non-zero-rate branch makes the test fail: account 2 gets no fee and the ID/layout checks break. Source restored; golden re-checked OK afterwards.

## Iterations
0 fix iterations for the COBOL. The test script itself needed one correction (my column offsets for the fee record and the trancatg line number). Production code unchanged in this stage.

## Notes for the reviewer
- F4 (last account never rewritten) is preserved as approved. The fixture keeps over-limit accounts off the last record. A fee on the last account would be written to TRANSACT but not added to the balance.
- The code diff in the working tree is still uncommitted.
