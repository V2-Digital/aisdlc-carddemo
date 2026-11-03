# CR-1 Intent: INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation runs only as COBOL on the mainframe (`app/jcl/INTCALC.jcl`, `app/cbl/CBACT04C.cbl`). That holds up the move of batch work off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. For the same inputs it produces **byte-identical** interest transactions (`TRANSACT`) and an identical updated account master (`ACCTFILE`). It keeps every legacy behaviour, including the defects. Equivalence is proven on golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`.

## What the legacy code does today

Evidence: `legacy/harness/run.sh CBACT04C` gives RC 0 and 50 TRANSACT / 50 ACCTFILE / 152 SYSOUT lines, identical to `legacy/golden/CBACT04C/`.

### Files (JCL `STEP15 EXEC PGM=CBACT04C,PARM='2022071800'`)

| DD | Org / access | LRECL | Key | Use | Layout |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | 50 | acct(11)+type(2)+cat(4) | input, driver | `CVTRA01Y` (bal `S9(9)V99` @18) |
| `XREFFILE` | KSDS, random by **alt key** acct id (@26, 11) | 50 | card num(16) | input | `CVACT03Y` |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4) | input | `CVTRA02Y` (rate `S9(4)V99` @17) |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct id(11) | read + REWRITE | `CVACT01Y` |
| `TRANSACT` | sequential, OUTPUT | 350 | – | new interest trans | `CVTRA05Y` |

`CBACT04C.cbl:28-56`. The `XREFFIL1` AIX path DD is not needed: the harness loads the AIX itself.

### Processing steps

1. Open all five files. Any status other than `00` → message, `9999-ABEND-PROGRAM` (CEE3ABD U0999 → harness RC 16) (`:182-186`, `:234-323`, `:628-632`).
2. Read TCATBALF in key order. For each record: count it, `DISPLAY` the 50-byte record (`:190-193`).
3. **Account break.** When the account id changes (`:194`):
   - rewrite the previous account, unless this is the first record (`:195-199`);
   - reset the total interest (`:200`);
   - read ACCTFILE by id (`:372-391`) and XREFFILE by alt key acct id (`:393-413`). A miss displays `ACCOUNT NOT FOUND:` and then abends, because status 23 is not `00`.
4. **Rate lookup** (`:210-213`, `:415-460`). Key = `ACCT-GROUP-ID` + type + cat. On status 23 it displays `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE`, then retries with group `DEFAULT`. If `DEFAULT` is also missing → abend.
5. **If rate ≠ 0** (`:214-217`):
   - `monthly = bal * rate / 1200`, **truncated** to 2 dp (no ROUNDED) (`:464-465`). Verified: truncation matches all 50 amounts in `golden/CBACT04C-after-CBTRN02C/TRANSACT.txt`. Rounding would differ on 28 of them.
   - add it to the account total (`:467`).
   - write one transaction (`:473-515`):
     - `TRAN-ID` = PARM date(10) + 6-digit run-wide counter, e.g. `2022071800000001`;
     - type `01`, cat `0005`, source `System`;
     - desc `Int. for a/c ` + ACCT-ID;
     - amount = monthly;
     - merchant id 0, merchant name/city/zip spaces;
     - card = XREF card num;
     - orig/proc TS = `YYYY-MM-DD-HH.MM.SS.hh0000` from `CURRENT-DATE` (`:613-626`);
     - FILLER (331-350) and the unset tail of desc = spaces (WORKING-STORAGE never initialised).
   - `1400-COMPUTE-FEES` does nothing (`:518-520`).
6. **Account update** `1050-UPDATE-ACCOUNT` (`:350-370`): `CURR-BAL += total interest`, `CYC-CREDIT = 0`, `CYC-DEBIT = 0`, REWRITE the full 300-byte record.
7. At EOF: close all files and display `END OF EXECUTION...`, RC 0 (`:224-232`).

### Observed data facts

- Every account has a blank `ACCT-GROUP-ID` (the group code sits in `ACCT-ADDR-ZIP`). So **every** lookup falls back to `DEFAULT`, which prints 2 SYSOUT lines per TCATBAL record.
- Stock `tcatbal.txt` has 50 records, all type/cat `01/0001` with balance 0. DEFAULT 01/0001 has rate 15.00. Result: 50 transactions with amount 0, and ACCTFILE identical to `acctdata.txt`.
- After CBTRN02C, TCATBALF has 100 records (50 × `01/0001`, 50 × `03/0001`). `03/0001` has rate 0, so no transaction is written. Result: still 50 transactions, now with non-zero amounts, and the `TRAN-ID` suffix runs 1..50.
- XREF acct ids are unique in the sample (0 duplicates), so the alt-key read is unambiguous.

## Scope

**In**
- Java 21 Maven project `modernized/cbact04c/` (JUnit 5 + Surefire only), runnable as a CLI in a container.
- Read and write the harness's canonical text datasets: fixed-width lines, LF, overpunch signs. Reads TCATBALF, XREFFILE, DISCGRP, ACCTFILE; writes TRANSACT and the updated ACCTFILE, in key order like the harness unload.
- PARM date argument and an injectable/frozen clock.
- Same SYSOUT lines and same return codes (0, abend 16), so operators see the same thing.
- Golden equivalence tests for both CBACT04C scenarios, plus characterization tests that pin the defects below.

**Out**
- Fixing any legacy defect, including the fee calculation (CR-2).
- CBTRN02C, other programs, CICS screens.
- Real VSAM/EBCDIC binary I/O, JCL/scheduler (CA7/Control-M) changes, and the deployment pipeline.

## Acceptance criteria

1. `mvn -f modernized/cbact04c test` passes on Java 21, with only JUnit 5 + Surefire as dependencies.
2. Stock inputs (`app/data/ASCII`), PARM `2022071800`, clock `2022-07-18 00:00:00.00`: `TRANSACT.txt` (50 records) and `ACCTFILE.txt` (50) are byte-identical to `legacy/golden/CBACT04C/`.
3. Inputs from `legacy/golden/CBTRN02C` (ACCTFILE, TCATBALF) plus stock for the rest: outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. SYSOUT is line-identical to the golden `SYSOUT.txt`, and the exit code is 0 in both scenarios.
5. Interest is truncated, not rounded. A test pins at least one golden case where rounding would differ.
6. A characterization test pins the last-account defect: account `00000000050` is unchanged in the after-CBTRN02C output while its transaction (18.77, `0000000187G`) is still written.
7. Zero-rate categories write no transaction and do not consume a TRAN-ID suffix. Non-zero rate with zero balance writes a 0.00 transaction.
8. Missing account, missing XREF, or missing `DEFAULT` disclosure group → the legacy messages and exit code 16.
9. `TRAN-ORIG-TS` and `TRAN-PROC-TS` come from the injected clock in `YYYY-MM-DD-HH.MM.SS.hh0000` format.

## Risks and findings

**Legacy defects (preserve, flag for a separate decision):**

- **D1: the last account is never rewritten.** The `ELSE PERFORM 1050-UPDATE-ACCOUNT` (`:219-220`) is unreachable: once EOF is set inside the body, `UNTIL END-OF-FILE='Y'` ends the loop before the ELSE branch can run. Evidence: `golden/CBACT04C-after-CBTRN02C/ACCTFILE.txt` changes 49 of 50 records. Account 50 keeps bal `00000019458G` (1945.87) and cycle credit/debit 1501.75 / -47.88, even though 18.77 of interest was posted to TRANSACT. **Money impact:** the interest is billed as a transaction but never added to the balance.
- **D2: interest truncates instead of rounding** (`:464`, no `ROUNDED`). It under-charges by up to 0.01 per category per month (28 of 50 cases in the sample).
- **D3: fees are not implemented** (`1400-COMPUTE-FEES`, `:518-520`). This is CR-2.
- **D4: data/layout mismatch.** `ACCT-GROUP-ID` is blank for every account, so the account-specific rates (`A000000000…`) are never used and everyone gets `DEFAULT`.
- **D5: zero-amount transactions are written** whenever the rate is non-zero and the balance is 0. That produces 50 empty transactions on stock data.
- **D6: wrong error text.** A DISCGRP open failure says `ERROR OPENING DALY REJECTS FILE` (`:281`). This is cosmetic.
- **D7: no overflow guard.** `ADD`/`COMPUTE` have no `ON SIZE ERROR`, so very large values would lose high-order digits silently. The sample data never reaches this. The Java must reproduce the PIC-width truncation, not throw.

**Migration risks:**

- **Decimal semantics:** the Java must use `BigDecimal` with truncation to the PIC scale and modulo the PIC precision, and must encode and decode overpunch exactly (`{`/`}` for ±0).
- **Clock:** COBOL reads `CURRENT-DATE` on every write. The goldens rely on a frozen clock, so the Java needs the same hook.
- **Uninitialised storage:** spaces under the default dialect, the same as the goldens. The Java must emit spaces in the desc tail and FILLER.
- **ACCTFILE output order and content:** the Java must write all 50 records (rewritten and untouched) in key order.

## Questions for the approver

1. **Clock injection.** How should the Java freeze the timestamp? *Default:* an optional `--now=YYYYMMDDHHMMSShh` argument, falling back to the env var `CARDDEMO_CURRENT_DATE`/`COB_CURRENT_DATE` used by the harness, else the system clock (UTC).
2. **SYSOUT/RC parity.** The CR only requires TRANSACT and ACCTFILE. *Default:* also match SYSOUT and exit codes (low cost, helps operators). Treat SYSOUT as a test, not a contract.
3. **Defects D1, D2, D4, D5.** *Default:* preserve all of them in CR-1 and raise a separate CR to decide on them. D1 has a direct impact on customer balances and should be prioritised.
