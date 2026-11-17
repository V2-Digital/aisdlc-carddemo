# CR-1 Intent — INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation (`app/jcl/INTCALC.jcl` → `app/cbl/CBACT04C.cbl`) only runs on the
mainframe. That blocks the move of batch workloads off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that can run in a container. Given the same inputs,
it produces the same interest transactions (`TRANSACT`) and updated account master (`ACCTFILE`), byte for byte.
Legacy behaviour, including its defects, is preserved and pinned by tests. Fixes are decided separately.

## What the legacy code does today

Evidence from running it: `legacy/harness/run.sh CBACT04C` (stock data), plus the same run with
`INPUT_DIR=legacy/golden/CBTRN02C`. Both runs are byte-identical to `legacy/golden/CBACT04C` and
`legacy/golden/CBACT04C-after-CBTRN02C` (TRANSACT, ACCTFILE, SYSOUT and RC all match; RC = 0).

### Files (JCL `INTCALC.jcl` STEP15, `PARM='2022071800'`)

| DD | Org / access | LRECL | Key | Use | Layout |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential read | 50 | acct(11)+type(2)+cat(4) | driver, read in key order | `CVTRA01Y` (bal `S9(9)V99` @18) |
| `XREFFILE` | KSDS, random by **alt key** acct-id | 50 | card(16); AIX acct @26(11) | card number for the transaction | `CVACT03Y` |
| `ACCTFILE` | KSDS, I-O random | 300 | acct-id(11) | read, then REWRITE | `CVACT01Y` |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4) | interest rate `S9(4)V99` @17 | `CVTRA02Y` |
| `TRANSACT` | sequential, OUTPUT | 350 | — | new interest transactions | `CVTRA05Y` |
| `XREFFIL1` | AIX path | — | — | not needed; the harness serves the alt key | — |

The program's DISPLAY output goes to SYSOUT.

### Processing steps

1. Open all five files. Any open status other than `00` abends (`CBACT04C.cbl:234-323`).
2. Read `TCATBALF` sequentially until status `10` (`:325-348`). Every record is DISPLAYed to SYSOUT (`:193`).
3. **When the account changes** (`:194-206`), and this is not the first record, rewrite the previous account
   (`1050`). Then reset the running interest total to 0, read `ACCTFILE` by acct-id (`:372-391`), and read
   `XREFFILE` by alt key acct-id (`:393-413`). A missing account or xref record prints `ACCOUNT NOT FOUND: …`
   and then abends.
4. **Rate lookup** (`:210-213`, `:415-460`): read `DISCGRP` with the key ACCT-GROUP-ID + type + cat.
   - Status `23` prints `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retries with
     group `DEFAULT`.
   - The program abends if the `DEFAULT` record is missing, or on any other status.
5. **If rate ≠ 0** (`:214-217`):
   - Monthly interest = `bal × rate / 1200` (`:464-465`). There is no `ROUNDED`, so the result is
     **truncated** to 2 decimals (`S9(9)V99`). It is added to the account total (`:467`).
   - One transaction is written (`:473-515`):
     - `TRAN-ID` = PARM date(10) + 6-digit counter. The counter runs across the whole run, from 000001.
     - Type `01`, category `0005`, source `System`, desc `Int. for a/c <ACCT-ID>`.
     - Amount = monthly interest, merchant id 0, merchant name/city/zip spaces, card number from xref.
     - `ORIG-TS` = `PROC-TS` = `YYYY-MM-DD-HH.MM.SS.hh0000`, taken from `CURRENT-DATE` (`:613-626`).
     - The rest of `TRAN-DESC` and the 20-byte FILLER are never initialised, so they stay spaces.
   - Then `1400-COMPUTE-FEES` runs. It is empty (`:518-520`).
6. **Account update** (`1050`, `:350-370`): `ACCT-CURR-BAL += total interest`, `CURR-CYC-CREDIT = 0`,
   `CURR-CYC-DEBIT = 0`, then REWRITE. This happens even when the total is 0 or no rate applied.
7. Close all files (`:224-228`). Print `END OF EXECUTION OF PROGRAM CBACT04C`, RC 0.
8. **Abend** (`:628-632`): print `ABENDING PROGRAM`, then call `CEE3ABD` with 999. Under the harness this
   prints `CEE3ABD: ABEND U0999` and exits with RC 16. File status is printed as `FILE STATUS IS: NNNN00xx`
   (`:635-648`).

### What the data shows

- **Stock data:** 50 accounts with one TCATBAL record each (type 01 / cat 0001). All balances are zero. Every
  group lookup misses and falls back to `DEFAULT` (rate 15.00). The run writes 50 zero-amount transactions, and
  ACCTFILE is identical to `acctdata.txt`. SYSOUT has 152 lines: 1 + 50 + 50×2 + 1.
- **After CBTRN02C:** 100 TCATBAL records, types 01/0001 and 03/0001 per account. `DEFAULT 030001` has rate 0,
  so only the 01 records generate transactions: 50 of them, non-zero. Example, account 1:
  1164.87 × 15.00 / 1200 = 14.560875, which becomes 14.56 (`0000000145F`). The balance goes from 1288.10 to
  1302.66 and the cycle fields are zeroed. SYSOUT has 302 lines. 49 of 50 account records change (see D1).

## Scope

**In scope**
- A Java 21 / JUnit 5 Maven project in `modernized/cbact04c/`. It reads the input datasets in the canonical
  text format (the same as `app/data/ASCII` / harness unload), takes the PARM date and a run timestamp, and
  writes `TRANSACT.txt` and the updated `ACCTFILE.txt`.
- The same record layouts, zoned/overpunch numerics, truncation, ordering, fallback and abend semantics
  (RC 16 on the same conditions).
- Equivalence tests against both golden scenarios.
- Characterization tests that pin D1 to D3.

**Out of scope**
- Fixing any legacy defect.
- Implementing fees (`1400`, CR-2).
- CBTRN02C.
- Real VSAM/EBCDIC binary I/O.
- Scheduler (CA7/Control-M) and JCL changes.
- Container image and deploy plumbing (Deploy stage).

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbact04c/` passes on Java 21 with no dependencies beyond JUnit 5 and Surefire.
2. On stock inputs with PARM `2022071800` and clock `2022-07-18 00:00:00.00`, `TRANSACT.txt` and `ACCTFILE.txt`
   are byte-identical to `legacy/golden/CBACT04C/`. That is 50 + 50 records, LF line endings, fixed width.
3. With ACCTFILE and TCATBALF taken from `legacy/golden/CBTRN02C/`, both outputs are byte-identical to
   `legacy/golden/CBACT04C-after-CBTRN02C/` (50 + 50 records).
4. Interest is `bal × rate / 1200`, truncated toward zero to 2 decimals. There is a test with a negative
   balance and one with a value that has a fractional remainder.
5. No transaction is written when the rate is 0. A transaction is written when the rate ≠ 0 and the
   amount is 0.
6. A missing group/type/cat key falls back to `DEFAULT`. A missing `DEFAULT` record, account or xref record
   ends with RC 16.
7. Transaction IDs are PARM date + a 6-digit sequence across the whole run. Timestamps use the
   `YYYY-MM-DD-HH.MM.SS.hh0000` format.
8. Characterization tests pin D1 (last account not rewritten) and D2 (cycle fields zeroed even when interest
   is 0).
9. RC is 0 on success, and the program prints the same SYSOUT lines as the golden `SYSOUT.txt` (see Q1).

## Risks and findings

| # | Finding | Evidence | Treatment |
|---|---|---|---|
| **D1** | **The last account is never rewritten.** When EOF is set inside the loop body, `PERFORM UNTIL` exits before the `ELSE PERFORM 1050` branch can run (`:188-222`). The last account's interest is posted as a transaction but never added to its balance, and its cycle fields are not zeroed. | Golden after-CBTRN02C: TRANSACT `2022071800000050` charges acct `00000000050` 18.77 (`0000000187G`), yet its ACCTFILE record is byte-identical to `golden/CBTRN02C/ACCTFILE.txt` (bal `00000019458G`). 49 of 50 records changed. | Preserve and pin. **Money defect: the ledger and the transactions disagree.** Needs a separate decision. |
| D2 | Cycle credit/debit are zeroed for every account that has a TCATBAL record, even with zero interest. Accounts without TCATBAL are untouched. | `:352-354` | Preserve. Probably intended cycle close, but confirm separately. |
| D3 | Interest is truncated, not rounded. | `:464` no `ROUNDED`; 14.560875 → 14.56 | Preserve. Negative balances are not exercised by the goldens, so a unit test pins it. |
| D4 | ADD/COMPUTE have no `ON SIZE ERROR`, so overflow silently drops high-order digits. | `:352`, `:464` | Preserve (modulo 10^9 / 10^10 truncation). Low risk. |
| D5 | Wrong or misleading messages: the DISCGRP open error says `DALY REJECTS FILE`, and an xref miss says `ACCOUNT NOT FOUND`. | `:281`, `:397` | Preserve the text. Cosmetic. |
| D6 | Sample data puts the group id in `ACCT-ADDR-ZIP`, so `ACCT-GROUP-ID` is blank and every lookup uses `DEFAULT`. Group-specific rates (`A000000000` rows) are never exercised by the goldens. | SYSOUT shows `DISCLOSURE GROUP RECORD MISSING` for every record | A unit test covers the direct-hit path. Data fix out of scope. |
| R1 | Fidelity of COBOL numerics: overpunch signs, `-0` (`}`), and the precision of the intermediate in `bal × rate / 1200` before truncation. | `legacy/README.md` "Signs" | Use exact decimal arithmetic. Golden + unit tests. |
| R2 | Timestamps come from the wall clock; the goldens are frozen at `2022071800000000`. | README "Determinism" | The Java program takes an injectable clock / override (default: system clock). |
| R3 | The xref alt key is non-unique in principle; the program takes the first match. | `:38`, `:394`; stock xref has no duplicate acct ids | Take the first by primary-key order. Note it in the design. |

## Questions for the approver

1. **SYSOUT parity.** The CR asks for byte-identical TRANSACT and ACCTFILE. Should the console log also match
   golden `SYSOUT.txt`? *Default: yes. It is cheap, and it keeps ops messages familiar.*
2. **I/O contract.** The container reads and writes the canonical fixed-width text datasets (the harness/ASCII
   format), not VSAM/EBCDIC. *Default: yes. Data conversion at the mainframe boundary is a separate concern.*
3. **D1 follow-up.** It is preserved in this change. Should a separate CR fix it, which would also require
   reconciling past runs? *Default: raise CR immediately after this migration; no fix here.*
