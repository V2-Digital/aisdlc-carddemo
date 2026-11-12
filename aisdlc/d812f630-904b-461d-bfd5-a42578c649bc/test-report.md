# CR-2 Test report

## Commands and results
| Command | Result |
|---|---|
| `legacy/harness/golden.sh --check` | **OK**. 3 scenarios (CBACT04C, CBTRN02C, CBACT04C-after-CBTRN02C) are byte-identical to `legacy/golden/`. Two runs identical. CBACT04C scenarios compare 50 TRANSACT, 50 ACCTFILE, 152 and 302 SYSOUT lines (as printed by the harness), RC 0 |
| `bash legacy/tests/cr2_overlimit.sh` | **exit 0**, 27 checks `ok`, 0 failed |
| `mvn -q test` | not run: no Java project touched (COBOL-only change) |

## New scenario `legacy/tests/cr2_overlimit/`
- Compared against `expected/`: TRANSACT 52 records (50 interest + 2 fees), ACCTFILE 50, SYSOUT 153 lines, RC 0. All identical.
- Over-limit accounts 1 (2 TCATBAL records) and 2 (rate 0) each get exactly one `01`/`0006` fee of `0000000250{`, with the right description, xref card and timestamps (AC2–AC4).
- Fee IDs are unique and sequential (AC4).
- Rewritten balances equal original + interest + 25.00 (AC5).
- Account 3 (balance = limit) and account 4 (below limit) get no fee (AC6).
- Non-fee records match the `baseline` program's output on the same input, with TRAN-ID masked (AC7).
- Interest description written after a fee has no leftover text (AC8).
- `trancatg.txt`: 19 records, sorted, 62 bytes per line including CRLF, new line correct (AC9).

## Iterations
0 fix iterations. All checks were green on the first run.

## Failing / notes
- Nothing failing.
- The last-account write-back defect (R1) is preserved. The scenario keeps account 50 within its limit.
- Transaction-ID suffixes after a fee shift by +1 per fee (R4).
