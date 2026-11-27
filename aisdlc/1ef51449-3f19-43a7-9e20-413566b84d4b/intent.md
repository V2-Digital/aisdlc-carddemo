# CR-1 Intent — INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation (`app/jcl/INTCALC.jcl`, step `STEP15`, program `CBACT04C`) only runs
on the mainframe, which blocks the plan to move batch work off it.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that can run in a container. Given the same inputs
and run date, it writes **byte-identical** interest transactions (`TRANSACT`) and an identical updated account master
(`ACCTFILE`). It keeps every legacy behaviour, including the defects listed below.

## What the legacy code does today

Evidence comes from `legacy/harness/run.sh CBACT04C`, which reproduces all four golden files in `legacy/golden/CBACT04C/` byte-for-byte (RC 0).

### Files (JCL `INTCALC.jcl:22-42`, FDs `CBACT04C.cbl:28-92`)

| DD | Org / access | LRECL | Key | Layout | Mode |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | 50 | acct(11)+type(2)+cat(4), pos 1-17 | `CVTRA01Y`: bal `S9(9)V99` at 18-28 | INPUT |
| `XREFFILE` | KSDS, random by **alt key** acct-id (pos 26-36) | 50 | card num (1-16) | `CVACT03Y` | INPUT |
| `ACCTFILE` | KSDS, random | 300 | acct-id (1-11) | `CVACT01Y` | I-O (REWRITE) |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4) | `CVTRA02Y`: rate `S9(4)V99` at 17-22 | INPUT |
| `TRANSACT` | sequential, new | 350 | — | `CVTRA05Y` | OUTPUT |

The run date comes from PARM `'2022071800'` (`INTCALC.jcl:22`), read as `PARM-DATE X(10)` (`CBACT04C.cbl:178`). `SYSOUT` holds the DISPLAY output.

### Processing steps

1. Open all five files. Any status other than `00` makes the program abend (U999, RC 16) (`:234-323`).
2. Read `TCATBALF` in key order and DISPLAY each 50-byte record (`:190-193`).
3. **On an account break** (`:194-206`): if this is not the first record, REWRITE the previous account (step 6). Then reset the total interest to 0, read `ACCTFILE` by acct-id (`:372`) and read `XREFFILE` by alt key acct-id (`:393`). A missing account or xref abends.
4. **Rate lookup** (`:210-213`, `:415-460`): the key is `ACCT-GROUP-ID` + type + cat. Status `23` DISPLAYs
   `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE`, then retries with group `DEFAULT`. If that also misses, the program abends.
5. **If rate ≠ 0** (`:214-217`):
   - Monthly interest = `bal × rate / 1200` into `S9(9)V99`. The result is **truncated, not rounded** (no `ROUNDED`, `:464`). It is then added to the account total (`:467`).
   - One transaction is written per category balance (`:473-515`):
     - `TRAN-ID` = PARM-DATE + 6-digit running counter. The counter is global, not per account.
     - type `01`, cat `0005`, source `System`.
     - desc `Int. for a/c ` + ACCT-ID, padded with spaces.
     - amount = monthly interest; merchant id 0; merchant name/city/zip spaces.
     - card = `XREF-CARD-NUM`.
     - orig/proc timestamps = `CURRENT-DATE` as `YYYY-MM-DD-HH.MM.SS.hh0000`.
     - FILLER cols 331-350 = spaces (never initialised).
   - `1400-COMPUTE-FEES` is then performed. It is empty (`:518-520`).
6. **Account update** (`:350-370`): `ACCT-CURR-BAL += total`, cycle credit and cycle debit set to 0, then REWRITE. Other accounts are not touched.
7. Close the files and DISPLAY the end banner. RC 0.

Accounts are updated even when the total interest is 0, so their cycle fields are zeroed.

## Scope

**In scope**
- Java 21 rewrite of `CBACT04C` covering steps 1-7, including the abend paths (exit code 16).
- Reads and writes the harness's canonical text form (fixed width, LF, overpunch signs).
- Golden-equivalence tests on `CBACT04C` and `CBACT04C-after-CBTRN02C`.
- Characterization tests that pin the defects listed below.

**Out of scope**
- Fixing any defect.
- Implementing fees (CR-2).
- `CBTRN02C`.
- Real VSAM/EBCDIC binary I/O.
- Scheduler (CA7/Control-M), JCL and container image changes. Deploy owns these.

## Acceptance criteria

1. `mvn -f modernized/cbact04c test` passes on Java 21. The only dependencies are JUnit 5 and Surefire.
2. Scenario `CBACT04C` (stock inputs from `app/data/ASCII`, PARM `2022071800`, clock `2022-07-18 00:00:00.00`): `TRANSACT.txt` (50 records × 350) and `ACCTFILE.txt` (50 × 300) are byte-identical to `legacy/golden/CBACT04C/`.
3. Scenario `CBACT04C-after-CBTRN02C`: the same comparison, with `ACCTFILE`/`TCATBALF` taken from `legacy/golden/CBTRN02C/` (100 balance records give 50 non-zero transactions, 49 accounts change).
4. Exit code matches `RC.txt` (0) in both scenarios.
5. Interest is truncated toward zero to 2 decimals. Test: bal 1164.87, rate 15.00 gives 14.56 (exact 14.56087…). In golden scenario 3, 48 of 50 amounts are truncated.
6. Characterization test: the last account in `TCATBALF` is **not** rewritten (account `00000000050` stays at 1945.87, with cycle credit/debit unchanged, while its 18.77 interest transaction is written).
7. A missing disclosure group falls back to `DEFAULT`. A missing `DEFAULT` record, account or xref ends with exit 16.
8. The run date and clock can be injected (PARM argument and a clock override), so runs are deterministic.

## Risks and findings

- **D1 — last account never updated (defect, keep it).** The `ELSE PERFORM 1050-UPDATE-ACCOUNT` at `:219-220` is unreachable: `PERFORM UNTIL` re-tests `END-OF-FILE` before the body runs again. Evidence: in `golden/CBACT04C-after-CBTRN02C/ACCTFILE.txt` only 49 of 50 records differ from `golden/CBTRN02C/ACCTFILE.txt`. Account 50 is unchanged, yet `TRANSACT` id `2022071800000050` charges it 18.77 (`0000000187G`). The customer is billed interest that never reaches the balance.
- **D2 — truncation instead of rounding** (`:464`). The bank may have intended rounding to the nearest cent. Keep truncation.
- **D3 — fees not implemented** (`:518-520`). This is a no-op, and CR-2 covers it.
- **D4 — no overflow protection.** `WS-MONTHLY-INT`/`WS-TOTAL-INT` (`S9(9)V99`, `:168-169`) and `ACCT-CURR-BAL` have no `ON SIZE ERROR`, so overflow silently loses high-order digits. The sample data never reaches this, so Java must reproduce the modulo behaviour deliberately.
- **D5 — sample data puts the group code in the wrong field.** `A000000000` sits in `ACCT-ADDR-ZIP`, and `ACCT-GROUP-ID` is blank. Every lookup falls back to `DEFAULT`, so the per-group rates are never exercised by golden data. The DISCGRP open-error text says `DALY REJECTS FILE` (`:281`, cosmetic).
- **R1 — hidden initial values.** The unset tail of `TRAN-DESC` and FILLER 331-350 are spaces under the harness dialect, and `x'00'` under `-std=ibm` (legacy/README). Java should emit spaces to match golden.
- **R2 — xref alternate key is non-unique in principle.** With several cards per account, COBOL returns the first one in alt-key order. Sample data is 1:1, so that case is untested.
- **R3 — input quirks.** `cardxref.txt` has 36-byte lines (padded to 50 on load), and `tcatbal.txt` uses CRLF. The Java reader must pad and strip the same way the harness does.
- **R4 — negative balances.** Truncation is toward zero. Signed overpunch handling (`}`, `J`-`R`) must round-trip. Golden data has negative balances only in categories with a 0 rate, so a unit test must cover this.
- The SYSOUT trace is 152 lines on stock data and 302 after CBTRN02C. It is not a stated deliverable (see Q1).

## Questions for the approver

1. **Should SYSOUT be byte-identical too?** It is cheap, and operations may grep for these messages. **Default: yes, match `SYSOUT.txt` exactly, but do not gate on it beyond the two golden scenarios.**
2. **What is the I/O format?** **Default:** the Java program reads and writes the canonical text files (`<DD>.txt` in an input and output directory). It rewrites `ACCTFILE` fully, in key order, rather than updating it in place. VSAM/DB targets are a later decision.
3. **Where does the clock come from?** **Default:** read `COB_CURRENT_DATE`/`CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`) if set, otherwise use the system clock in UTC. The run date stays a CLI argument, like PARM.
4. **D1 and D2 are preserved here as instructed.** Should they be raised as a separate CR? **Default: yes. Log D1 (customer-impacting) as high priority.**
