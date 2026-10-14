# CR-1 Intent — INTCALC (`CBACT04C`) to Java 21

## Problem
The nightly interest calculation (`app/jcl/INTCALC.jcl`, step `STEP15`, program `CBACT04C`) runs only as COBOL on
the mainframe. That ties a core nightly batch to mainframe capacity and skills, and stops us running it in a
container alongside the rest of the modernised estate.

## Outcome
A plain Java 21 program in `modernized/cbact04c/` that, given the same inputs and run date, produces
**byte-identical** interest transactions (`TRANSACT`) and updated account master (`ACCTFILE`) to the COBOL.
Behaviour is unchanged, including known defects, so finance sees no difference in the numbers on cut-over day.

## What the legacy code does today

### Files (JCL `INTCALC.jcl`, SELECTs `CBACT04C.cbl:28-56`)
| DD | Org / access | LRECL | Key | Use | Copybook |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | 50 | acct-id 9(11) + type X(2) + cat 9(4) (cols 1-17) | input, driver | `CVTRA01Y` |
| `XREFFILE` | KSDS, random by **alternate key** acct-id (cols 26-36) | 50 | card-num X(16) | input | `CVACT03Y` |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct-id 9(11) | read + REWRITE | `CVACT01Y` |
| `DISCGRP` | KSDS, random | 50 | group X(10) + type X(2) + cat 9(4) | input, rate lookup | `CVTRA02Y` |
| `TRANSACT` | sequential, OUTPUT (new GDG) | 350 | — | output | `CVTRA05Y` |

`PARM='2022071800'` (10 chars) supplies the transaction-id prefix (`CBACT04C.cbl:176-178`).
`XREFFIL1` (AIX path) is not referenced by the program; the alternate key covers it.

### Processing steps
1. Open all five files; any non-`00` status → `DISPLAY` + abend U999 (`:234-323`, `:628-632`; harness RC 16).
2. Read `TCATBALF` sequentially in key order (`:325-348`). For every record: count it and `DISPLAY` the raw
   50-byte record to SYSOUT (`:192-193`).
3. **On account break** (`TRANCAT-ACCT-ID ≠ WS-LAST-ACCT-NUM`, `:194-206`):
   - if not the first account, rewrite the previous account (step 6);
   - reset `WS-TOTAL-INT` to 0; read `ACCTFILE` by acct-id (`:372-391`) and `XREFFILE` by alternate key acct-id
     (`:393-413`). Not found → `ACCOUNT NOT FOUND: …` then abend.
4. **Rate lookup** (`:210-213`, `:415-460`): key = `ACCT-GROUP-ID` + `TRANCAT-TYPE-CD` + `TRANCAT-CD`. Status `23`
   → `DISPLAY 'DISCLOSURE GROUP RECORD MISSING'` / `'TRY WITH DEFAULT GROUP CODE'`, retry with group `DEFAULT`;
   a missing DEFAULT row abends.
5. **If `DIS-INT-RATE ≠ 0`** (`:214-217`):
   - `WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200`, S9(9)V99, **truncated, not rounded** (no
     `ROUNDED`, `:464-465`); added to `WS-TOTAL-INT` (`:467`).
   - Write one transaction (`:473-515`): `TRAN-ID` = PARM-DATE + 6-digit running suffix across the run;
     type `01`, cat `0005`, source `System`, desc `Int. for a/c ` + ACCT-ID (rest of 100 bytes spaces),
     amount = monthly interest, merchant id 0, merchant name/city/zip spaces, card num = `XREF-CARD-NUM`,
     orig/proc TS = `YYYY-MM-DD-HH.MM.SS.hh0000` from `CURRENT-DATE` (`:613-626`), 20-byte filler spaces.
   - Perform `1400-COMPUTE-FEES`, which is empty (`:518-520`).
   - Rate 0 → no transaction, no interest.
6. **Account update** (`1050-UPDATE-ACCOUNT`, `:350-370`): `ACCT-CURR-BAL += WS-TOTAL-INT`;
   `ACCT-CURR-CYC-CREDIT = 0`; `ACCT-CURR-CYC-DEBIT = 0`; REWRITE (all other bytes unchanged).
7. At EOF close files, `DISPLAY 'END OF EXECUTION OF PROGRAM CBACT04C'`, RC 0 (`:224-232`).
   Accounts without any TCATBAL row are never touched.

### Observed behaviour (harness, run date 2022-07-18)
| Scenario | TCATBAL in | TRANSACT out | ACCTFILE records changed | SYSOUT / RC |
|---|---|---|---|---|
| `CBACT04C` (stock) | 50 (all `01/0001`, all balance 0) | 50, all amount `0000000000{` | 0 of 50 (identical to `acctdata.txt`) | 152 / 0 |
| `CBACT04C-after-CBTRN02C` | 100 (50 × `01/0001`, 50 × `03/0001`) | 50 (only `01/0001`: DEFAULT rate 15.00%; `03/0001` rate 0) | 49 of 50 | 302 / 0 |

My run of `legacy/harness/run.sh CBACT04C` is byte-identical to `legacy/golden/CBACT04C/` (`diff` clean).
Worked check, account 1: balance 1164.87 (`0000011648G`) × 15.00 / 1200 = 14.5608… → **14.56** (`0000000145F`);
new `ACCT-CURR-BAL` 1288.10 → 1302.66 (`00000012881{` → `00000013026F`).

## Scope
**In**
- Java 21 / Maven / JUnit 5 rewrite of `CBACT04C` in `modernized/cbact04c/`, no frameworks.
- Reads and writes the harness canonical text format (fixed width, LF, zoned decimal with EBCDIC overpunch) for
  `TCATBALF`, `XREFFILE`, `ACCTFILE`, `DISCGRP` → `TRANSACT`, `ACCTFILE`.
- Run-date PARM and an injectable clock (same frozen `2022071800000000` as the harness) for determinism.
- Same SYSOUT lines and return codes (0 normal, 16 on abend path).
- Characterization tests pinning every defect listed below.
- Equivalence tests against both golden scenarios.

**Out**
- Fixing any legacy defect (separate decision, see Risks).
- `1400-COMPUTE-FEES` logic (CR-2).
- CBTRN02C, JCL/scheduler changes, VSAM/EBCDIC binary I/O, containerisation and deployment wiring.

## Acceptance criteria
1. `mvn test` in `modernized/cbact04c/` passes on Java 21 with JUnit 5 as the only test dependency.
2. Scenario `CBACT04C`: Java `TRANSACT.txt` and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C/`.
3. Scenario `CBACT04C-after-CBTRN02C` (inputs `ACCTFILE`/`TCATBALF` from `legacy/golden/CBTRN02C/`): Java
   `TRANSACT.txt` and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. For both scenarios `SYSOUT.txt` and `RC.txt` also match golden (see Q1).
5. Interest = `trunc((bal × rate) / 1200, 2 dp)`, toward zero for negative balances; pinned by a unit test
   including a negative balance.
6. Characterization test: last account in TCATBAL order is **not** rewritten (D1).
7. Characterization test: rate 0 writes no transaction; rate ≠ 0 with balance 0 writes a zero-amount transaction.
8. Characterization test: missing group row falls back to `DEFAULT` with the two SYSOUT lines; missing `DEFAULT`
   row, missing account or missing xref ends with RC 16.
9. `TRAN-ID` = PARM date (10) + 6-digit run-wide sequence; timestamps from the injected clock in
   `YYYY-MM-DD-HH.MM.SS.hh0000` form.
10. `legacy/harness/golden.sh --check` still passes (legacy untouched).

## Risks and findings
**Defects (preserve, pin, flag for a separate decision)**
- **D1 — last account never rewritten.** The `ELSE PERFORM 1050-UPDATE-ACCOUNT` at `CBACT04C.cbl:219-220` is
  unreachable: after the read that hits EOF sets `END-OF-FILE='Y'`, the `UNTIL` is tested before the body runs
  again. Evidence: `golden/CBACT04C-after-CBTRN02C/TRANSACT.txt` charges account `00000000050` 18.77
  (`0000000187G`), yet line 50 of its `ACCTFILE.txt` is byte-identical to `golden/CBTRN02C/ACCTFILE.txt`
  (balance and cycle credit/debit unchanged). 49 of 50 accounts change. **Financial impact:** last account
  each night is billed a transaction but its balance is not updated and cycle totals are not reset.
- **D2 — group lookup always misses on sample data.** `ACCT-GROUP-ID` (cols 102-111 of ACCTFILE) is blank; the
  `A000000000` code sits in `ACCT-ADDR-ZIP`. Every lookup falls back to `DEFAULT` (SYSOUT prints
  `DISCLOSURE GROUP RECORD MISSING` 50/100 times). Data defect rather than code, but it means non-DEFAULT rates
  are never exercised by the golden scenarios.
- **D3 — truncation, not rounding** (`:464-465`). Up to 0.0099 per category per month lost in the bank's or
  customer's favour depending on sign. Possibly intended; flag for Finance.
- **D4 — zero-amount transactions** are written when the rate is non-zero but the balance is 0 (all 50 stock
  transactions; account 37 in the after-POSTTRAN scenario).
- **D5 — negative category balances (credits) produce negative interest** if the rate is non-zero; no guard.
- **D6 — no overflow guard** on `ADD WS-TOTAL-INT TO ACCT-CURR-BAL` or the COMPUTE (no `ON SIZE ERROR`); COBOL
  silently truncates high-order digits. Not reachable with sample data; Java must reproduce or we must agree it
  cannot occur.
- **D7 — cosmetic:** DISCGRP open failure displays `ERROR OPENING DALY REJECTS FILE` (`:281`).
- **D8 — TRAN-ID only unique per run date.** Rerunning with the same PARM regenerates the same IDs
  (`…000001`…); harmless today because `TRANSACT` is a new GDG generation, risky if ever loaded into a keyed file.

**Migration risks**
- Zoned-decimal overpunch encode/decode must match exactly (e.g. `-0` is `}`; COBOL writes `{` for zero).
- `TRAN-RECORD` is never initialised: unset bytes are spaces under the harness dialect (see `legacy/README.md`).
  On real z/OS they may be `x'00'`; golden is the contract, so Java writes spaces.
- Timestamp uses hundredths from `CURRENT-DATE` padded with `0000`; Java must not emit micro/milliseconds.
- XREF by alternate key returns the first card for the account; sample data has one card per account (no
  duplicate acct-ids in `cardxref.txt`), so multi-card ordering is untested.
- Golden scenarios cover only DEFAULT rates 15.00% and 0.00% and one positive-balance path; unit tests must cover
  the rest (fallback, negatives, abends).

## Questions for the approver
1. **Should SYSOUT and RC also be byte-identical, or only TRANSACT and ACCTFILE?**
   *Default:* match them too. It costs little and gives operations the same job log.
2. **Which I/O format should the Java job use?**
   *Default:* the canonical fixed-width text files the harness unloads, one file per DD (`<DD>.txt`), with paths
   given as arguments. VSAM/EBCDIC conversion stays with the platform team.
3. **D1 (last account not updated) has real money impact. Should it get its own CR?**
   *Default:* yes, raise CR right after cut-over. This CR preserves the defect as instructed.
