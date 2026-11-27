# CR-1 Intent: INTCALC / CBACT04C to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation (`INTCALC`, COBOL program `CBACT04C`) only runs on the mainframe. That blocks the move of batch workloads off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. Given the same inputs and run date, it produces byte-identical interest transactions (`TRANSACT`) and an updated account master (`ACCTFILE`). It keeps every legacy behaviour, including the defects. Each defect is pinned by a test and listed for a separate decision.

## What the legacy code does today

Evidence comes from running the program in the harness. `legacy/harness/run.sh CBACT04C` and `INPUT_DIR=legacy/golden/CBTRN02C run.sh CBACT04C` both return RC 0. The outputs are byte-identical to `legacy/golden/CBACT04C` and `legacy/golden/CBACT04C-after-CBTRN02C`. Each run takes about 0.4 s.

### Job and files

`app/jcl/INTCALC.jcl:22` runs a single step: `STEP15 EXEC PGM=CBACT04C,PARM='2022071800'`. The PARM is a 10-character run date (`CBACT04C.cbl:176-178`).

| DD | Org / access | LRECL | Key | Copybook | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential (`:28`) | 50 | acct(11)+type(2)+cat(4), offset 0 | `CVTRA01Y` | input: category balances, read in key order |
| `XREFFILE` | KSDS, random (`:34`) | 50 | card(16), offset 0. Alt key acct-id(11) at offset 25 | `CVACT03Y` | input: card number for the account, read by alt key |
| `ACCTFILE` | KSDS, random, **I-O** (`:41`, `:291`) | 300 | acct-id(11), offset 0 | `CVACT01Y` | read, then REWRITE |
| `DISCGRP` | KSDS, random (`:47`) | 50 | group(10)+type(2)+cat(4), offset 0 | `CVTRA02Y` | input: interest rate `DIS-INT-RATE S9(4)V99` at offset 16 |
| `TRANSACT` | sequential, OUTPUT (`:53`, `:309`) | 350 | none | `CVTRA05Y` | output: new GDG `SYSTRAN(+1)` (`INTCALC.jcl:37-41`) |

The JCL also defines `XREFFIL1` (the AIX path), but no SELECT in the program refers to it.

Account amount fields are `S9(10)V99` zoned with a trailing overpunch sign. `ACCT-CURR-BAL` is at offset 12, `ACCT-CURR-CYC-CREDIT` at 78, `ACCT-CURR-CYC-DEBIT` at 90, and `ACCT-GROUP-ID` at 112.

### Processing steps

1. Open all five files. Any status other than `00` causes an abend (`:234-323`).
2. Read `TCATBALF` sequentially. For each record, add 1 to the count and DISPLAY the 50-byte record (`:190-193`).
3. **Account break** (`:194-206`): when `TRANCAT-ACCT-ID` differs from the previous one:
   - If this is not the first account, run `1050-UPDATE-ACCOUNT` for the previous account.
   - Reset the total interest to 0.
   - Read `ACCTFILE` by acct-id (`:372`).
   - Read `XREFFILE` by the alternate key acct-id (`:393-398`).
   - If either record is not found, the program DISPLAYs `ACCOUNT NOT FOUND: …` and abends.
4. **Rate lookup** (`1200`, `:415-440`): read `DISCGRP` with key `ACCT-GROUP-ID` + type + cat.
   - On status `23` (not found), DISPLAY `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and retry with group `DEFAULT` (`:443-460`).
   - If the `DEFAULT` record is also missing, the program abends.
5. If the rate is not 0 (`:214`):
   - **Interest**: `WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200`, with no `ROUNDED`, so the result is **truncated** to 2 decimals (`:464-465`). It is added to the account total (`:467`).
   - **Write the transaction** (`1300-B-WRITE-TX`, `:473-515`):
     - `TRAN-ID` = PARM-DATE(10) + a 6-digit run-wide counter starting at 000001.
     - type `01`, category `0005`, source `System`.
     - desc `Int. for a/c ` + ACCT-ID(11). The rest of the field is spaces.
     - amount = monthly interest, merchant id 0, merchant name/city/zip spaces.
     - card number from XREF.
     - orig and proc timestamps are `CURRENT-DATE` as `YYYY-MM-DD-HH.MM.SS.hh0000` (`:613-626`).
     - The 20-byte FILLER stays spaces because `TRAN-RECORD` is never initialised.
   - `1400-COMPUTE-FEES` is called, but it is empty (`:518-520`).
6. **Account update** (`1050`, `:350-370`):
   - `ACCT-CURR-BAL += total interest`.
   - `ACCT-CURR-CYC-CREDIT = 0`, `ACCT-CURR-CYC-DEBIT = 0`.
   - REWRITE the record. Any status other than `00` causes an abend.
7. At EOF, close all files, DISPLAY the end banner, and finish with RC 0 (`:224-232`).

Abends go through `9999-ABEND-PROGRAM`: DISPLAY `ABENDING PROGRAM`, then `CEE3ABD` with code 999. The harness turns that into exit status 16. Before an abend, `9910` displays the file status as `FILE STATUS IS: NNNN00xx` (`:628-648`).

### Observed data facts

- **Stock data**: 50 TCATBAL records, all type/cat `01/0001` with zero balances. The run writes 50 zero-amount transactions, and `ACCTFILE` comes out byte-identical to `acctdata.txt`. SYSOUT has 152 lines.
- **After CBTRN02C**: 100 TCATBAL records (50 × `01/0001`, 50 × `03/0001`). The `DEFAULT 03 0001` rate is 0, so only the 50 type-01 records produce transactions. Example: account 1 balance 1164.87 × 15.00% / 1200 = 14.5608, written as 14.56 (`0000000145F`). SYSOUT has 302 lines.
- Every DISCGRP lookup misses and falls back to `DEFAULT`, because `ACCT-GROUP-ID` is blank in the sample data: 100 of 100 lookups in the after-CBTRN02C run. The `A000000000` group code sits in `ACCT-ADDR-ZIP`.
- Input quirks: `cardxref.txt` rows are 36 bytes, not 50. `tcatbal.txt` has CRLF line endings, and its last line has no line terminator.

## Scope

**In**
- A Java 21 Maven project at `modernized/cbact04c/` (JUnit 5 only).
- It reads the four inputs and writes `TRANSACT` and the updated `ACCTFILE` in the harness's canonical text format: fixed-width lines, LF endings, overpunch signs, KSDS output in key order.
- It takes the run date (the PARM) and the clock as inputs, so runs are deterministic.
- It reproduces SYSOUT messages and the return code, including abend paths.
- Equivalence tests against both golden scenarios.
- Characterization tests for each defect and quirk listed below.

**Out**
- Fixing any legacy defect, including the last-account bug, the group-id/zip data issue, and the empty fees paragraph (fees are CR-2).
- CBTRN02C and other programs.
- Real VSAM/EBCDIC binary I/O, scheduler (CA7/Control-M) changes, and container image/deploy plumbing beyond a runnable jar.

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbact04c/` passes on Java 21, with no dependencies other than JUnit 5 and Surefire.
2. On stock inputs (`app/data/ASCII`) with run date `2022071800` and clock `2022-07-18 00:00:00.00`, the Java `TRANSACT.txt` (50 records) and `ACCTFILE.txt` (50 records) are byte-identical to `legacy/golden/CBACT04C/`.
3. On the inputs ACCTFILE/TCATBALF = `legacy/golden/CBTRN02C/*`, with the rest of the inputs taken from the stock data, the outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/` (50 + 50 records).
4. On both scenarios, SYSOUT is byte-identical to golden `SYSOUT.txt` (152 / 302 lines), and the exit code equals `RC.txt` (0).
5. Interest is computed with exact decimal arithmetic and truncated toward zero to 2 decimals. Unit tests cover positive, negative and zero balances. Wherever a GnuCOBOL run can be done for a case, the result is cross-checked against it.
6. A characterization test pins the **last-account defect**: the final account in TCATBAL key order gets its transaction(s) but is not rewritten.
7. Characterization tests pin:
   - the fallback to the `DEFAULT` group on a miss;
   - "no transaction when the rate is 0";
   - the transaction-id counter running across the whole run;
   - the space-filled tail of `TRAN-DESC` and FILLER.
8. A missing account, XREF or DEFAULT disclosure record leads to the legacy SYSOUT messages and a non-zero exit (16). No output is committed beyond what COBOL would have written.
9. `legacy/harness/golden.sh --check` still passes, and nothing under `legacy/golden/`, `legacy/harness/` or `.github/` is modified.

## Risks and findings

| # | Finding | Evidence | Handling |
|---|---|---|---|
| D1 | **Defect: the last account's interest is never written back.** After EOF the `PERFORM UNTIL` exits before the `ELSE PERFORM 1050-UPDATE-ACCOUNT` branch can run (`CBACT04C.cbl:188-222`). | After CBTRN02C, account `00000000050` is charged 18.77 (TRANSACT record `2022071800000050`), but its ACCTFILE record is byte-identical to the input (`golden/CBTRN02C/ACCTFILE.txt` line 50). The balance stays 1945.87 and the cycle credit/debit are not zeroed. | Preserve. Pin with a test (AC 6). Flag for a separate decision. |
| D2 | **Data defect: the group code is in the wrong field.** `ACCT-GROUP-ID` is blank and `A000000000` is in `ACCT-ADDR-ZIP`, so every account is priced at the `DEFAULT` rate. | 100 of 100 lookups show `DISCLOSURE GROUP RECORD MISSING` in SYSOUT. | Preserve. Flag to the data owner. |
| D3 | `1400-COMPUTE-FEES` is empty. | `:518-520` | Preserve as a no-op (CR-2 target). |
| R1 | **Truncation, not rounding.** `COMPUTE` has no `ROUNDED`. A naive `BigDecimal`/`double` port would drift by a cent. | `:464-465`. 14.5608 is written as 14.56. | Use `BigDecimal` with `RoundingMode.DOWN` (AC 5). |
| R2 | **Overflow is silent.** With no `ON SIZE ERROR`, `WS-MONTHLY-INT` (S9(9)V99), `ACCT-CURR-BAL` (S9(10)V99) and the 6-digit tran-id suffix truncate high-order digits. Not reachable with the sample data. | `:168`, `:173`, `:352`, `:474` | Replicate the modulo truncation and add a unit test. |
| R3 | **Overpunch signs.** `{`/`}` and `A-R` must be decoded and encoded exactly. A wrong sign convention changes bytes. | `legacy/README.md` "Signs" | Shared codec with round-trip tests. |
| R4 | **Uninitialised fields are spaces.** The golden files assume the default dialect. | README "Uninitialised WORKING-STORAGE" | Fill new records with spaces. |
| R5 | **Input quirks.** `cardxref.txt` rows are 36 bytes, `tcatbal.txt` has CRLF endings and no final newline. | Line-length check | The reader pads to LRECL and strips CR, as the harness loader does. |
| R6 | **ACCTFILE update model.** COBOL rewrites in place. Java writes a new file, so a crash midway would behave differently from a VSAM partial update. | n/a | Write to a temp file, then rename. Record the decision in the design. |
| R7 | **Golden coverage is thin.** Both scenarios use the DEFAULT group, and all balances are non-negative. | TCATBAL data | Unit and characterization tests fill the gaps (AC 5, 7). |

## Questions for the approver

1. **Must SYSOUT and the return code also match byte for byte, or only `TRANSACT`/`ACCTFILE`?** *Default: match them too (AC 4, 8).* It costs little, and operations scripts may parse it.
2. **What exit code should the Java program use on an abend?** *Default: 16, matching the harness's `CEE3ABD` stub.* The scheduler then treats it like the mainframe abend.
3. **How should the run date and clock be supplied?** *Default: the run date as the first CLI argument (the PARM). The clock is frozen through an optional override, mirroring `COB_CURRENT_DATE`, and otherwise uses the system clock in UTC.*
