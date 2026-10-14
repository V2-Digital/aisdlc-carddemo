# CR-1 Intent: INTCALC (`CBACT04C`) → Java 21

## Problem / outcome

**Problem.** The nightly interest calculation (`app/jcl/INTCALC.jcl`, step `STEP15`, program `CBACT04C`) runs only on the mainframe, and the batch estate is moving off it.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container and is a drop-in replacement. Given the same inputs, it produces byte-identical interest transactions (`TRANSACT`) and an updated account master (`ACCTFILE`). Proof is the golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Legacy behaviour is kept as-is, defects included.

## What the legacy code does today

Evidence: I re-ran `legacy/harness/run.sh CBACT04C`. It gave RC 0, 50 `TRANSACT` records and 152 SYSOUT lines, and all 4 files are byte-identical to `legacy/golden/CBACT04C/`.

### Files (DD names from `INTCALC.jcl`, layouts from `app/cpy`)

| DD | Org / access | LRECL | Key | Copybook | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | 50 | acct(11)+type(2)+cat(4), cols 1-17 | `CVTRA01Y` | driver, read in key order (`CBACT04C.cbl:28-31`) |
| `XREFFILE` | KSDS, random | 50 | card no. cols 1-16; **AIX** acct id cols 26-36 | `CVACT03Y` | card number for the account, read by alt key (`:34-38`, `:394-395`) |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct id cols 1-11 | `CVACT01Y` | read, then REWRITE (`:41-44`, `:291`) |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4), cols 1-16 | `CVTRA02Y` | interest rate `DIS-INT-RATE` S9(4)V99 at cols 17-22 (`:47-50`) |
| `TRANSACT` | sequential, OUTPUT | 350 | none | `CVTRA05Y` | new interest transactions (`:53-55`, `:309`) |
| PARM | `'2022071800'` | 10 | | | `PARM-DATE`, the prefix of the transaction ID (`:176-178`) |

Signed fields are zoned with overpunch signs. The account amounts are S9(10)V99 (12 bytes). The balance and the transaction amount are S9(9)V99 (11 bytes).

### Processing steps

1. DISPLAY the start banner, then open all 5 files. Any open failure abends (`:181-186`, `:234-323`).
2. Read `TCATBALF` sequentially until EOF (`:188-222`, `:325-348`). For each record:
   - Add 1 to the record count and DISPLAY the raw 50-byte record (`:192-193`).
   - **On an account break** (`TRANCAT-ACCT-ID ≠ WS-LAST-ACCT-NUM`): if this is not the first record, PERFORM `1050-UPDATE-ACCOUNT` for the previous account. Then reset `WS-TOTAL-INT=0`, read `ACCTFILE` by acct id and read `XREFFILE` by the acct-id alternate key (`:194-205`, `:372-413`).
   - **Rate lookup** with key (`ACCT-GROUP-ID`, type, cat). On INVALID KEY (status 23) it DISPLAYs `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and re-reads with group `DEFAULT` (`:210-213`, `:415-460`).
   - If the rate ≠ 0: `WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200`, add it to `WS-TOTAL-INT`, write a transaction, then PERFORM `1400-COMPUTE-FEES`, which is empty (`:214-216`, `:462-470`, `:518-520`).
3. **Transaction record** (`1300-B-WRITE-TX`, `:473-515`):
   - `TRAN-ID` = PARM-DATE + a 6-digit running suffix: `2022071800000001`, ….
   - Type `01`, cat `0005`, source `System`.
   - Desc `Int. for a/c <ACCT-ID>`, then spaces.
   - Amount = monthly interest; merchant id 0; merchant name, city and zip blank.
   - Card number from XREF.
   - ORIG-TS and PROC-TS are both `FUNCTION CURRENT-DATE` formatted as `YYYY-MM-DD-HH.MM.SS.hh0000` (`:613-626`). The PARM date is not used for these.
   - FILLER cols 331-350 are never set, so they are spaces (`TRAN-RECORD` is uninitialised WORKING-STORAGE).
4. **Account update** (`1050-UPDATE-ACCOUNT`, `:350-370`): `ACCT-CURR-BAL += WS-TOTAL-INT`, cycle credit = 0, cycle debit = 0, REWRITE. Every other byte is unchanged.
5. Close all files, DISPLAY the end banner, GOBACK with RC 0 (`:224-232`).
6. **Errors:** any unexpected file status DISPLAYs `ERROR …` plus the status, then `ABENDING PROGRAM`, then calls `CEE3ABD`. Under the harness that is exit code 16 (`:628-648`).

### Business rules, verified against golden data

- **Truncation, not rounding.** The COMPUTE has no `ROUNDED`. In `CBACT04C-after-CBTRN02C`, all 50 amounts equal the truncated result. 28 of the 50 would differ under half-up rounding. Example: acct 2, 2339.97 × 15 / 1200 = 29.249625 is booked as **29.24**.
- **The rate always falls back to `DEFAULT`** on the sample data, because `ACCT-GROUP-ID` (cols 113-122) is blank in all 50 accounts. `DEFAULT 01/0001` = 15.00%. `DEFAULT 03/0001` = 0.00%, so no transaction is written for it.
- The XREF and ACCT reads happen once per account. The rate lookup happens once per TCATBAL record.
- **SYSOUT** = 1 start line + 3 lines per TCATBAL record (raw record + 2 DISCGRP messages) + 1 end line. That is 152 lines on stock data and 302 after CBTRN02C (100 TCATBAL records).

## Scope

**In**
- Java 21 / Maven / JUnit 5 rewrite of `CBACT04C` in `modernized/cbact04c/`.
- It reads and writes the harness's canonical text datasets: fixed width, LF, overpunch, KSDS unloaded in key order.
- Inputs it reads: `TCATBALF`, `XREFFILE`, `ACCTFILE`, `DISCGRP` and the PARM. Outputs: `TRANSACT`, the rewritten `ACCTFILE`, SYSOUT and the exit code.
- An injectable clock, equivalent to `COB_CURRENT_DATE`.
- An equivalence test that diffs both golden scenarios.
- Characterization tests that pin the defects listed below.

**Out**
- Fixing any legacy defect, including the last-account bug and the empty fees paragraph (CR-2).
- `CBTRN02C`.
- Real VSAM/EBCDIC I/O, JCL or scheduler changes (CA7/Control-M), and container/deploy plumbing beyond a runnable jar.

## Acceptance criteria

1. `mvn -f modernized/cbact04c verify` passes on Java 21, with JUnit 5 as the only dependency.
2. Scenario `CBACT04C` (stock inputs from `app/data/ASCII`, PARM `2022071800`, clock `2022-07-18 00:00:00.00`): `TRANSACT.txt` (50 × 350 bytes) and `ACCTFILE.txt` (50 × 300 bytes) are byte-identical to `legacy/golden/CBACT04C/`.
3. Scenario `CBACT04C-after-CBTRN02C` (`ACCTFILE` and `TCATBALF` from `legacy/golden/CBTRN02C/`, the rest stock): `TRANSACT.txt` and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. In both scenarios, SYSOUT and the exit code also match the golden `SYSOUT.txt` (152 / 302 lines) and `RC.txt` (`0`). *(Default; see Q1.)*
5. Interest is truncated toward zero to 2 decimals. A test pins acct 2: 29.249625 → `0000000292D`.
6. A characterization test pins the last-account defect. In scenario 3, account `00000000050` gets a transaction for 18.77 (`0000000187G`), but its `ACCTFILE` record is byte-identical to the input.
7. A characterization test pins the DEFAULT fallback. When a group key is missing, both messages are printed and the `DEFAULT` rate is used. A missing `DEFAULT` record, a missing account or a missing XREF entry ends the run with a non-zero exit code (16), as the abend does.
8. A zero rate writes no transaction and adds no interest. The account is still rewritten, with its cycle fields zeroed.
9. `legacy/harness/golden.sh --check` still passes. Nothing under `legacy/golden/`, `legacy/harness/` or `.github/` is modified.

## Risks and findings

| # | Finding | Evidence | Handling |
|---|---|---|---|
| D1 | **Last account never rewritten.** The `ELSE PERFORM 1050-UPDATE-ACCOUNT` branch (`:219-220`) cannot run: at EOF the `PERFORM UNTIL` test ends the loop before the body runs again. The last account's interest is booked in `TRANSACT`, but it is not added to its balance, and its cycle credit/debit are not zeroed. | Scenario 3: 49 of 50 accounts follow the rule. Acct 50 is unchanged (balance 1945.87, cycle 1501.75 / -47.88), despite its 18.77 transaction. | Preserve and pin (AC6). **Flag for a separate CR.** |
| D2 | `1400-COMPUTE-FEES` is empty: no fees are ever charged. | `:518-520` | Preserve (CR-2). |
| D3 | Interest is truncated, not rounded. Customers are under-charged by up to 0.01 per category per month. | 28 of 50 amounts in scenario 3 | Preserve (AC5). Flag for a business decision. |
| D4 | The account's group id is never populated in the sample data: the group code sits in `ACCT-ADDR-ZIP`. So group-specific rates are never used. | `acctdata.txt` cols 103-112 = `A000000000`, cols 113-122 blank | Data issue. Preserve the lookup logic exactly. |
| D5 | Silent truncation on overflow. `WS-MONTHLY-INT`/`TRAN-AMT` S9(9)V99 and `ACCT-CURR-BAL` S9(10)V99 lose high-order digits without `ON SIZE ERROR`. The transaction ID suffix 9(6) wraps after 999,999 transactions. | `:168-173`, `:464-465`, `:352`, `:474` | Mirror the COBOL MOVE/ADD truncation. Not exercised by the golden data. |
| D6 | The transaction timestamps use the wall clock, not the business date in PARM. A rerun on another day stamps a different date. | `:496-498`, `:613-626` | Preserve. The clock is injectable for tests. |
| D7 | The XREF alternate key allows duplicates. With more than one card per account, the card is the first by alternate key (BDB insertion order). | `:38`; no duplicates in `cardxref.txt` | Use the first matching card in file order. This is a risk only for data outside the goldens. |
| R1 | Byte-identity depends on details that are easy to get wrong: overpunch encoding, the uninitialised spaces in `TRAN-DESC` and the FILLER, the hundredths in the timestamp (`…00.00.00.000000`), and keeping the KSDS output in key order. | `legacy/README.md` "Known quirks" | Covered by the golden diff tests. |
| R2 | `PARM-LENGTH` is ignored and 10 bytes are always used. A shorter PARM would pull adjacent storage into the transaction ID. | `:176-178`, `:476` | Java will right-pad a short PARM with spaces, which is a documented deviation. Not exercised. |

## Questions for the approver

1. **Should SYSOUT and the exit code also be byte-identical, or only `TRANSACT`/`ACCTFILE`?** *Default:* match both. The goldens exist and the cost is low. Operations scripts may also depend on the messages.
2. **Should D1 (last-account write-back) be raised now as its own CR?** It mis-states one account's balance every night. *Default:* yes, raise it now. CR-1 preserves the defect.
3. **Data format for the Java program:** use the canonical text datasets (as the harness and goldens do), with no VSAM/EBCDIC binary support. *Default:* yes. Real dataset conversion belongs to the platform migration, not this CR.
