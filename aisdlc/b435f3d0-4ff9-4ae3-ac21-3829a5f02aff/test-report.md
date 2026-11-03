# CR-1 Test report

**Result: all green. 0 fix iterations needed for the Java code** (the only failures were my own first-draft test fixtures, corrected before any production change).

## Commands and results
| Command | Result |
|---|---|
| `mvn -q -f modernized/cbact04c test` | 16 tests, 0 failures/errors |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` |
| `git diff --stat baseline -- legacy/golden legacy/harness .github` | empty |

## Tests (16)
| Class | Tests | Proves |
|---|---|---|
| `GoldenStockTest` | 1 | TRANSACT (50×350), ACCTFILE (50×300), SYSOUT (152 lines), RC byte-identical to `golden/CBACT04C` (AC 2, 4) |
| `GoldenAfterTrn02Test` | 1 | Same for `golden/CBACT04C-after-CBTRN02C` (50 transactions, SYSOUT 302 lines) (AC 3, 4) |
| `InterestTest` | 4 | 1164.87→`0000000145F`; 1165.50 → 14.56 where HALF_UP gives 14.57; −0.60 → `0000000000{`; −1164.87 → `0000000145O` (AC 5) |
| `DefectR1Test` | 1 | **R1 pinned**: acct 50 has a transaction `0000000187G` but its ACCTFILE row equals `golden/CBTRN02C` (`00000019458G`); acct 1 is rewritten (`00000013026F`, cycle fields `…{`) (AC 6) |
| `AbendTest` | 3 | Missing account / XREF / DEFAULT group → RC 16, spec'd SYSOUT tail (AC 7) |
| `ZonedTest` | 5 | −0 → `{`, decode of `}`/plain digit, high-order truncation, balance overflow wrap mod 10^10, round-trip of 500 golden field values |

Golden comparisons use `Files.mismatch` on 4 files per scenario (8 files total): all identical.

## COBOL parity for abend (manual, harness)
Removed the `DEFAULT*` rows from `discgrp.txt`, ran `INPUT_DIR=… run.sh CBACT04C` and the Java jar with the same inputs. COBOL: RC 16. Java: RC 16. SYSOUT is **identical** (`diff` empty), ending `ERROR READING DEFAULT DISCLOSURE GROUP / FILE STATUS IS: NNNN0023 / ABENDING PROGRAM`.

## Gaps / notes
- No `legacy/tests/*.sh`: no COBOL change was made.
- Not covered by tests: tran-id suffix wrap at 999,999 (needs 1M rows; the code uses `% 1_000_000`) and the open-error paths for missing input files (status `0035` is an assumption from build-notes; COBOL's real message tail was not compared).
- Not covered by golden: negative interest and overflow (unit-tested only, as the intent states).
- Nothing left failing. R1–R3, R5, R8 and R9 remain preserved for a separate decision (R1 → Cards Finance CR).
