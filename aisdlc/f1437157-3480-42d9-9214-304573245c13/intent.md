# CR-1 Intent: INTCALC (`CBACT04C`) → Java 21

## Problem / outcome

**Problem.** The nightly interest calculation (`INTCALC`, COBOL program `CBACT04C`) runs only on the mainframe. That blocks the move of batch workloads to containers.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` replaces `CBACT04C`. Given the same inputs and run date, it produces byte-identical interest transactions (`TRANSACT`) and an identical updated account master (`ACCTFILE`). Known legacy defects are kept on purpose and pinned by tests, so that fixing them can be decided separately.

## What the legacy code does today

### Job and files

The job is `app/jcl/INTCALC.jcl:22`, a single step `EXEC PGM=CBACT04C,PARM='2022071800'`. The PARM becomes `PARM-DATE X(10)` (`CBACT04C.cbl:176-178`).

| DD | Copybook | LRECL | Org / access | Key | Use |
|---|---|---|---|---|---|
| TCATBALF | CVTRA01Y | 50 | KSDS, sequential (`cbl:28-32`) | acct(11)+type(2)+cat(4), cols 1-17 | input, driver |
| XREFFILE | CVACT03Y | 50 | KSDS, random (`cbl:34-39`) | card(16). Read by **alt key** acct-id, cols 26-36 (`cbl:394-395`) | input |
| ACCTFILE | CVACT01Y | 300 | KSDS, random, **I-O** (`cbl:41-45,291`) | acct-id, cols 1-11 | read + REWRITE |
| DISCGRP | CVTRA02Y | 50 | KSDS, random (`cbl:47-51`) | group(10)+type(2)+cat(4), cols 1-16 | input (rates) |
| TRANSACT | CVTRA05Y | 350 | sequential, OUTPUT (`cbl:53-56,309`) | – | output, new GDG generation |
| XREFFIL1 | – | – | AIX path (`INTCALC.jcl:31-32`) | – | not referenced by the program |

The harness stand-ins (`legacy/README.md`) are fixed-width text, one record per line, with overpunch signs. KSDS files are unloaded in key order.

### Processing steps

1. Read TCATBALF sequentially in key order (`cbl:188-190,326`). DISPLAY each 50-byte record to SYSOUT (`cbl:193`).
2. **Account break** (`cbl:194-206`). When the acct-id changes:
   - If this is not the first account, rewrite the previous account (step 6).
   - Reset the total interest to 0 (`cbl:200`).
   - READ ACCTFILE by acct-id (`cbl:373`) and READ XREFFILE by alternate key acct-id (`cbl:394`).
3. **Rate lookup** (`cbl:210-213, 415-440`). The key is `ACCT-GROUP-ID` + tran type + tran cat, read from DISCGRP.
   - Status 23 (not found): DISPLAY `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then re-read with group `DEFAULT` (`cbl:418-419,436-438,444`).
4. **If the rate ≠ 0** (`cbl:214-217`):
   - Monthly interest = `TRAN-CAT-BAL * DIS-INT-RATE / 1200` into `S9(09)V99`, **no ROUNDED**, so it truncates (`cbl:464-465`).
   - Add it to the account total (`cbl:467`).
   - Write one transaction (step 5).
   - `1400-COMPUTE-FEES` is a no-op (`cbl:518-520`).
   - If the rate = 0, no transaction is written.
5. **Transaction record** (`cbl:473-500`):

   | Field | Value |
   |---|---|
   | `TRAN-ID` | PARM-DATE(10) + 6-digit running counter starting at 000001 for the whole run |
   | type / cat | `01` / `0005` (`MOVE '05'` to a `9(04)` field) |
   | source | `System` |
   | desc | `Int. for a/c ` + ACCT-ID, padded with spaces |
   | amt | the monthly interest |
   | merchant | id 0, name/city/zip spaces |
   | card | XREF-CARD-NUM |
   | `TRAN-ORIG-TS` = `TRAN-PROC-TS` | `YYYY-MM-DD-HH.MM.SS.hh0000` from `CURRENT-DATE` (`cbl:613-624`) |
   | FILLER (cols 331-350) | spaces, because the record is never initialised |

6. **Account update** (`cbl:350-356`):
   - `ACCT-CURR-BAL += total interest`.
   - Cycle credit and cycle debit are set to 0.
   - REWRITE the record.
   - This happens for every account that has TCATBAL records, even when its interest is 0.
7. Close the files, DISPLAY the end banner, GOBACK with RC 0 (`cbl:224-232`).

### Errors

Any unexpected file status does the following (`cbl:628-648`):

- DISPLAY `ERROR …`, then `FILE STATUS IS: NNNN…`, then `ABENDING PROGRAM`.
- `CEE3ABD` U0999. The harness turns this into exit **16**.

This covers account or xref not found (status 23 after the `ACCOUNT NOT FOUND:` display) and a missing `DEFAULT` rate row.

### Observed behaviour (harness, `run.sh CBACT04C`)

The run gives RC 0. TRANSACT, ACCTFILE and SYSOUT are byte-identical to `legacy/golden/CBACT04C`.

| Scenario | TCATBAL in | TRANSACT out | ACCTFILE changed | SYSOUT lines |
|---|---|---|---|---|
| `CBACT04C` (stock) | 50 (all 01/0001, bal 0) | 50, all amt 0 | 0 of 50 (same as `acctdata.txt`) | 152 |
| `CBACT04C-after-CBTRN02C` | 100 (50 × 01/0001, 50 × 03/0001) | 50 (03/0001 has DEFAULT rate 0) | 49 of 50 | 302 |

Every rate lookup misses and falls back to `DEFAULT`. The sample data has `ACCT-GROUP-ID` blank (README "Sample data").

## Scope

**In scope**
- Java 21 rewrite of CBACT04C in `modernized/cbact04c/`. Plain Maven, JUnit 5.
- Read and write the harness's canonical text format, with the same DD names.
- Take PARM-DATE and the run clock as inputs.
- Reproduce TRANSACT and ACCTFILE byte for byte on both golden scenarios.
- Reproduce RC as well.
- Characterization tests for the defects and quirks listed below.

**Out of scope**
- Fixing any legacy defect.
- Implementing fees (CR-2).
- CBTRN02C.
- JCL or scheduler changes, container image, deployment wiring.
- Real VSAM/EBCDIC binary I/O.
- GDG cataloguing.

## Acceptance criteria

1. `mvn -f modernized/cbact04c test` passes on Java 21, with JUnit 5 as the only test dependency.
2. On the stock inputs with PARM `2022071800` and clock `2022-07-18 00:00:00.00`, the Java `TRANSACT.txt` and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C/` (50 and 50 records).
3. With ACCTFILE and TCATBALF taken from `legacy/golden/CBTRN02C/` and the other inputs from stock, the outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/` (50 and 50 records).
4. Exit code 0 on both scenarios, matching `RC.txt`.
5. Interest is truncated toward zero, not rounded. A test pins a case where the two differ (28 of the 50 golden amounts would change if rounded).
6. A characterization test pins the last-account defect: account `00000000050` is not rewritten, so balance 1945.87 and cycle credit/debit 1501.75 / -47.88 stay unchanged.
7. Rate fallback works: a missing group key uses `DEFAULT`. A rate of 0 writes no transaction but still rewrites the account (cycle fields zeroed).
8. The transaction ID counter is global across accounts (`…000001`–`…000050`). The type/cat is `01`/`0005`. The timestamps follow the injected clock.
9. Accounts with no TCATBAL record pass through unchanged. Unrelated bytes, including ACCTFILE FILLER, are preserved.
10. Missing account, missing xref, or missing `DEFAULT` rate ends with a non-zero exit (16, like the harness abend) and does not report success.

## Risks and findings

- **D1, last account never rewritten (defect, preserve).**
  - Cause: the `ELSE PERFORM 1050-UPDATE-ACCOUNT` at `cbl:219-220` is unreachable. `PERFORM UNTIL` tests EOF before the body runs.
  - Evidence: golden after-CBTRN02C TRANSACT `2022071800000050` charges a/c 50 interest of 18.77 (`0000000187G`). Its ACCTFILE row is identical to `golden/CBTRN02C/ACCTFILE.txt`. Only 49 of 50 records change.
  - Impact: the interest transaction is posted, but the balance is understated by one account's interest every night.
- **D2, truncation instead of rounding** (`cbl:464`, no `ROUNDED`). The bank systematically under-charges up to 0.01 per category. Verified on all 50 golden amounts. Preserve.
- **D3, high-order truncation.** The result goes into `S9(09)V99`, and `ADD` to `ACCT-CURR-BAL S9(10)V99` has no `ON SIZE ERROR`. An overflow would silently drop leading digits. The Java code must use the same field-width semantics; it must not throw or widen. This is not exercised by the golden data and will be pinned by a unit test.
- **D4, `ACCT-GROUP-ID` never populated in the sample data.** Every lookup falls back to `DEFAULT`, so group-specific rates (`A000000000`, `ZEROAPR` in `discgrp.txt`) are never exercised. This is a data issue, not a code issue. Tests will add a direct-hit case.
- **D5, `1400-COMPUTE-FEES` is empty.** No fees are charged. Kept as a no-op hook for CR-2.
- **Risks to equivalence.**
  - Overpunch sign encoding, including `-0` handling.
  - The negative-balance truncation direction: COBOL truncates toward zero. No negatives in the golden data, so a unit test is needed.
  - Uninitialised TRAN fields must be spaces. With `-std=ibm` they would be `x'00'`; the golden data uses spaces.
  - The timestamp hundredths come from the clock and must be injectable.
  - `cardxref.txt` lines are 36 bytes and must be padded to 50.
  - CRLF in `tcatbal.txt` must be stripped.
- **SYSOUT** wording is not part of the byte-identical contract, but the golden copy is available as a further check.

## Questions for the approver

1. **SYSOUT parity.** Must the Java program's console output match `SYSOUT.txt` line for line, or only TRANSACT and ACCTFILE?
   *Default:* emit the same lines (it is cheap) and test them, but treat them as informational, not a gate.
2. **Failure exit code.** Legacy abends U0999, which the harness maps to RC 16.
   *Default:* the Java program exits **16** on any I/O or lookup failure and prints the same `ERROR`/`ABENDING PROGRAM` messages.
3. **Run clock.** Legacy timestamps use the wall clock.
   *Default:* use the system clock, with an optional override (env/arg, mirroring `COB_CURRENT_DATE`) for reproducible runs and tests.
