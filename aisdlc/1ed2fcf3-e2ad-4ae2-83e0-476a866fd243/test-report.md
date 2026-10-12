# CR-2 Test report

**Result: all green. 0 fix iterations to the program.** (Two fixture/script mistakes of mine were fixed; see below.)

## Commands and results
| Command | Result |
|---|---|
| `cobc -m -fsign=EBCDIC -fassign-clause=ibm -I app/cpy app/cbl/CBACT04C.cbl` | no output (no warnings) - AC9 |
| `legacy/harness/golden.sh --check` | OK. 3 scenarios byte-identical, incl. SYSOUT and RC (50 TRANSACT, 50 ACCTFILE, 302 SYSOUT in the largest) - AC1 |
| `legacy/scenarios/CBACT04C-overlimit/check.sh` | 21 checks, ALL PASS |
| `mvn test` | n/a: no Java in this CR |

## New scenario `legacy/scenarios/CBACT04C-overlimit`
Input: `golden/CBTRN02C` ACCTFILE + TCATBALF (100 records) with a/c 1-3 edited. `expected/` is built by `make-fixtures.py` from golden files and hand arithmetic, not from the program output.
- TRANSACT 51 records, byte-identical to expected. ACCTFILE 50 records, SYSOUT 298 lines, RC 0, all identical.
- AC2: exactly 2 records with cat 0006, for a/c 1 (2 TCATBAL records) and a/c 3.
- AC3: `System`, amount `0000000250{`, card = xref card, IDs `…000001`-`…000051` unique.
- AC4: a/c 1 `00000013276F`, a/c 3 `00000018826E`, cycle credit/debit zero.
- AC5: a/c 2 (balance = limit) has no fee, balance equals golden.
- AC6: a/c 3 (ZEROAPR, all rates 0) has a fee and no interest record.
- AC7: TRANSACT minus fees (cols 17-350) equals golden minus a/c 3's line. ACCTFILE a/c 4-50 byte-identical to golden. The interest record after a fee (a/c 2) is clean, which covers the TRAN-DESC fix.
- AC8: `trancatg.txt` has 19 lines, all the same length, CRLF kept. The diff against the previous commit is exactly one added line, after `010005`.

## Sensitivity (the check can fail)
- Changing `>` to `>=`: check.sh fails (9 FAIL lines).
- Removing the `MOVE SPACES TO TRAN-DESC` lines: check.sh fails (3 FAIL lines).
- The source was restored afterwards, `git status` shows only the new `legacy/scenarios/`.

## Iterations / issues
- My first expected SYSOUT dropped a/c 3's echo lines. The program was right and the spec's 298 lines is correct. I fixed the generator.
- check.sh had a wrong column for the account id and an over-complex AC5 check. I fixed the script. No change to the COBOL.

## Left failing / for a human
- Nothing failing.
- D1 (last account not rewritten) is preserved as decided. It is untested for an over-limit last account by design (a/c 50 is within its limit).
- Q2 (inactive accounts are charged) is not covered by a test, as no stock account is inactive.
