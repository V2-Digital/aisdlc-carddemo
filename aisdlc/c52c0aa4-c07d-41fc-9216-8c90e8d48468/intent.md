# CR-1 Intent: INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** Interest on card balances is calculated every night by a COBOL batch job (`INTCALC`, program `CBACT04C`) that only runs on the mainframe. That blocks the move of batch workloads off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. Given the same inputs, it produces **byte-identical** interest transactions (`TRANSACT`) and an identical updated account master (`ACCTFILE`). Equivalence is proven on the golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. The behaviour is unchanged, including known defects, which are pinned by tests and raised for a separate decision.

## What the legacy code does today

**Job.** `app/jcl/INTCALC.jcl:22` runs `EXEC PGM=CBACT04C,PARM='2022071800'`. The PARM is the run date. It becomes the prefix of every transaction ID.

### Files (all records fixed width; zoned decimals with overpunch signs)

| DD | Org / access | LRECL | Key | Copybook | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | 50 | acct(11)+type(2)+cat(4), cols 1-17 | `CVTRA01Y` | input, driver |
| `XREFFILE` | KSDS, random by **AIX** acct-id cols 26-36 (NONUNIQUEKEY, `XREFFILE.jcl`) | 50 | card 1-16 | `CVACT03Y` | input |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct 1-11 | `CVACT01Y` | read + REWRITE |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4), cols 1-16 | `CVTRA02Y` | input (rates) |
| `TRANSACT` | sequential, new GDG | 350 | — | `CVTRA05Y` | output |

The SELECTs are at `CBACT04C.cbl:28-56`. Fields used:
- **TCATBAL:** `TRAN-CAT-BAL` S9(9)V99, cols 18-28.
- **DISCGRP:** `DIS-INT-RATE` S9(4)V99, cols 17-22 (annual %, e.g. `00150{` = 15.00).
- **ACCOUNT:**
  - `ACCT-CURR-BAL` S9(10)V99, cols 13-24
  - `ACCT-CURR-CYC-CREDIT` / `-DEBIT`, cols 79-90 / 91-102
  - `ACCT-GROUP-ID`, cols 113-122
- **XREF:** `XREF-CARD-NUM`, cols 1-16.

### Processing steps

1. Open all five files. Any status other than `00` displays an error and abends (U0999; the harness returns RC 16). See `:234-323`, `:628-632`.
2. Read TCATBAL sequentially in key order. Each record is echoed to SYSOUT (`:190-193`).
3. **Account control break** when `TRANCAT-ACCT-ID` changes (`:194-206`):
   - Rewrite the *previous* account (step 6). This is skipped the first time.
   - Reset the running interest total to 0.
   - Read the account by key (`:372-391`) and the xref by the AIX acct-id (`:393-413`). If either is not found, the program abends.
4. **Rate lookup** with key (`ACCT-GROUP-ID`, type, cat) (`:210-213`, `:415-440`).
   - On status `23`, it displays `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and retries with group `DEFAULT` (`:443-460`). If `DEFAULT` is also missing, it abends.
5. **If rate ≠ 0** (`:214-217`):
   - Monthly interest = `TRAN-CAT-BAL * DIS-INT-RATE / 1200`. There is no `ROUNDED`, so the result is **truncated** to 2 dp (`:464-465`). Example: 1164.87 × 15 / 1200 = 14.560875, written as `0000000145F` (14.56).
   - The amount is added to the account total (`:467`).
   - One transaction is written (`:473-515`):
     - ID = PARM date + 6-digit run-wide counter
     - type `01`, cat `0005`, source `System`
     - desc `Int. for a/c <ACCT-ID>`
     - merchant id 0, merchant name/city/zip spaces
     - card = xref card
     - orig/proc TS = current time as `YYYY-MM-DD-HH.MM.SS.hh0000` (`:613-626`)
   - `1400-COMPUTE-FEES` runs, but it is empty (`:518-520`).
6. **Account update** (`:350-370`): `ACCT-CURR-BAL += total`, both cycle fields set to 0, then REWRITE.
7. Close all files and GOBACK with RC 0.

### Observed run (harness, frozen clock `2022-07-18`)
- Both scenarios reproduce `legacy/golden/` byte-for-byte with RC `0`.
- **Stock data:**
  - 50 TCATBAL rows (all type `01`/cat `0001`, all balances zero).
  - Output: 50 zero-amount transactions. ACCTFILE is identical to `acctdata.txt`.
  - SYSOUT has 152 lines.
- **After CBTRN02C:**
  - 100 TCATBAL rows: 50 × `01/0001` and 50 × `03/0001`. The `03/0001` DEFAULT rate is 0, so those rows write nothing.
  - Output: 50 transactions with non-zero amounts, and 49 of 50 accounts changed.
  - SYSOUT has 302 lines.
- **Every group lookup misses and falls back to `DEFAULT`.** In the sample data `ACCT-GROUP-ID` is blank and `A000000000` sits in the ZIP field.

## Scope

**In**
- Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only).
- It reads the canonical text datasets for `TCATBALF`, `XREFFILE`, `ACCTFILE` and `DISCGRP`, and writes `TRANSACT` and the updated `ACCTFILE`.
- It takes the run-date PARM and an injectable clock.
- Overpunch zoned-decimal codec, the fixed-width record layouts above, and the same rounding (truncation) and abend semantics.
- Equivalence tests against both golden scenarios, plus characterization tests for each defect below.

**Out**
- Fixing any legacy behaviour (see Risks).
- Implementing fees (CR-2).
- CBTRN02C.
- Real VSAM/EBCDIC I/O, scheduler (CA7/Control-M) changes, container image and deployment.

## Acceptance criteria

1. **Build.** `mvn -q verify` in `modernized/cbact04c/` passes on Java 21 with no dependencies beyond JUnit 5 and Surefire.
2. **Stock-data scenario.** On stock inputs with PARM `2022071800` and clock `2022-07-18 00:00:00.00`:
   - `TRANSACT` is byte-identical to `legacy/golden/CBACT04C/TRANSACT.txt` (50 records).
   - `ACCTFILE` is byte-identical to `legacy/golden/CBACT04C/ACCTFILE.txt` (50 records).
3. **After-CBTRN02C scenario.** With `ACCTFILE`/`TCATBALF` from `legacy/golden/CBTRN02C/`, both outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/` (50 + 50 records).
4. **Exit code.** On success the exit code is 0. Any condition where COBOL would abend (open failure, account or xref not found, `DEFAULT` disclosure group missing) gives a non-zero exit code (16, matching the harness) and an error message.
5. **Interest arithmetic.** Interest is `bal × rate / 1200`, truncated toward zero to 2 dp. Accounts are rewritten with balance + total and cycle credit/debit = 0. A unit test covers a case where truncation and rounding differ, including a negative balance.
6. **Transaction IDs.** They are `PARM(10) + 000001…`, sequential across the whole run, and one is written per TCATBAL row with a non-zero rate, including zero-amount rows.
7. **Fallback to DEFAULT.** A disclosure-group miss falls back to `DEFAULT` for the same type/cat.
8. **Characterization tests** pin defects D1 to D3 below as current behaviour.
9. **(Default, see Q1.)** SYSOUT is byte-identical to the golden `SYSOUT.txt` (152 / 302 lines).

## Risks and findings

**Defects, preserved and pinned (each needs a separate decision):**

- **D1: the last account is never rewritten.**
  - Where: the `ELSE PERFORM 1050-UPDATE-ACCOUNT` at `:219-220` can never run. After EOF is set inside the body, `PERFORM UNTIL` exits before the `ELSE` is reached.
  - Effect: the last account in TCATBAL order keeps its old balance and cycle fields, but its interest transaction *is* written.
  - Evidence: in `golden/CBACT04C-after-CBTRN02C`, transaction `2022071800000050` charges account `00000000050` 18.77 (`0000000187G`). Yet that account's ACCTFILE record is identical to `golden/CBTRN02C/ACCTFILE.txt`: balance still 1945.87 and cycle fields not zeroed.
  - Impact: the ledger and the account master disagree every night for one account.
- **D2: disclosure group taken from a blank field.**
  - The rate lookup uses `ACCT-GROUP-ID`. In this data it is blank, so every account gets `DEFAULT` rates and the `A000000000` group rates are never used.
  - This could be a data defect (group code loaded into ZIP) rather than a code defect.
- **D3: interest is truncated, not rounded** (`:464`, no `ROUNDED`). The customer is under-charged by up to 0.01 per category per month.
- **D4: minor issues.**
  - DISCGRP open error text says `DALY REJECTS FILE` (`:281`).
  - Zero-amount interest transactions are written whenever the rate is non-zero (50 on stock data).
  - The 6-digit counter has no overflow guard.
  - `1400-COMPUTE-FEES` is empty.

**Migration risks:**
- **Uninitialised `TRAN-RECORD`.** The tail of `TRAN-DESC` and the 20-byte FILLER are spaces under the default dialect but `x'00'` under `-std=ibm` / real z/OS. Golden uses spaces, so Java will write spaces.
- **Decimal handling.** Java must use `BigDecimal` with truncation (`RoundingMode.DOWN`) and exact overpunch encoding, including `{`/`}` for zero digits.
- **Xref AIX is non-unique.** COBOL returns the first card for an account, in primary-key (card number) order. The sample has no duplicate accounts, so goldens don't exercise this case. Java will pick the lowest card number.
- **I/O model change.** There is no VSAM in the container, so Java works on the canonical text form (as in the goldens). The real mainframe EBCDIC/VSAM unload/reload is out of scope.
- **Raw input quirks.** `cardxref.txt` lines are 36 bytes (pad to 50). `tcatbal.txt` has CRLF line endings. The harness normalises both on load, so the Java reader must accept short lines and CRLF.
- **Timestamps** need an injectable clock to be deterministic. The format keeps the COBOL quirk of hundredths followed by `0000`.

## Questions for the approver

1. **Should SYSOUT be part of the equivalence contract?** The CR names only `TRANSACT` and `ACCTFILE`. *Default: yes, match SYSOUT byte-for-byte as well.* It is cheap and keeps operator logs and run books unchanged.
2. **How should `ACCTFILE` be written?** Options are updating the file in place or writing a new file. *Default: read the input path and write the full updated master to an output path (same text format).* The deploy step then swaps files, which also makes reruns safe.
3. **Who owns D1 to D3?** *Default: open a separate CR (e.g. CR-3) for D1 (ledger/master mismatch, highest impact) and raise D2/D3 with Cards business owners. This CR fixes none of them.*
