# CR-1 Intent — INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation (job `INTCALC`, program `CBACT04C`) only runs on the mainframe, which blocks moving batch workloads into containers.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` does the same job. Given the same input files and run date, it writes **byte-identical** interest transactions (`TRANSACT`) and an identical updated account master (`ACCTFILE`). The proof is the two golden scenarios. Legacy behaviour, defects included, stays as it is.

## What the legacy code does today

Evidence: `legacy/harness/run.sh CBACT04C` reproduces `legacy/golden/CBACT04C` byte-for-byte (TRANSACT 50, ACCTFILE 50, SYSOUT 152 lines, RC 0).

### Files (`app/jcl/INTCALC.jcl:22-43`, `CBACT04C.cbl:28-92`)

| DD | Org / access | LRECL | Key | Use |
|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential, input | 50 | acct(11)+type(2)+cat(4) | driver file, read in key order |
| `XREFFILE` | KSDS, random, input | 50 | card(16); **AIX** acct(11) @26 | card number lookup by account |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct id(11) | read, then REWRITE |
| `DISCGRP` | KSDS, random, input | 50 | group(10)+type(2)+cat(4) | interest rate |
| `TRANSACT` | sequential, **output** | 350 | n/a | new interest transactions |
| PARM | `'2022071800'` | 10 | n/a | prefix of the transaction ID |

Layouts: `CVTRA01Y` (TCATBAL, `TRAN-CAT-BAL` S9(9)V99), `CVACT03Y` (XREF), `CVTRA02Y` (DISCGRP, `DIS-INT-RATE` S9(4)V99), `CVACT01Y` (account, balances S9(10)V99, `ACCT-GROUP-ID` at cols 113-122), `CVTRA05Y` (TRAN record, 350 bytes).

### Processing steps

1. Opens all five files. If an open status is not `00`, it abends (`:234-323`).
2. Reads TCATBAL sequentially and DISPLAYs every record (`:188-193`, `:325-348`).
3. **Account control break** when the account ID changes (`:194-206`):
   - Rewrites the previous account (step 6). This is skipped for the first record.
   - Resets the total interest to 0.
   - Reads `ACCTFILE` by ID (`:372-391`).
   - Reads `XREFFILE` by the alternate key (acct ID) to get the card number (`:393-413`).
4. **Rate lookup** (`:210-213`, `:415-460`). The key is `ACCT-GROUP-ID` + type + category. On status `23` it DISPLAYs `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and retries with group `DEFAULT`. Any other non-`00` status abends.
5. **Interest is calculated only when the rate is not 0** (`:214-217`):
   - `monthly = bal * rate / 1200`. It is **truncated** to 2 dp, with no `ROUNDED` (`:464-465`).
   - The monthly amount is added to the account total (`:467`).
   - One transaction is written per category (`:473-515`):
     - `TRAN-ID` = PARM date (10) + 6-digit run counter (`:474-480`).
     - Type `01`, category `0005`, source `System`.
     - Description `Int. for a/c <acct11>`.
     - Amount = monthly interest; merchant ID 0, name/city/zip spaces; card number from the xref.
     - `ORIG-TS` and `PROC-TS` = `CURRENT-DATE` in `YYYY-MM-DD-HH.MM.SS.hh0000` form (`:613-626`).
   - Calls `1400-COMPUTE-FEES`, which is empty (`:518-520`).
6. **Account update** (`:350-370`):
   - `CURR-BAL += total`.
   - `CURR-CYC-CREDIT = 0` and `CURR-CYC-DEBIT = 0`.
   - REWRITE. The other 300-byte fields are left unchanged.
7. At EOF it closes all files and DISPLAYs the end banner. RC is 0 (`:224-232`). On any error it calls `CEE3ABD` with U999 (`:628-632`); the harness turns that into RC 16.

### Observed on the golden data

- **Stock scenario:**
  - All 50 TCATBAL balances are 0, and every account resolves to `DEFAULT 01/0001` at 1.50%.
  - It writes 50 zero-amount transactions.
  - ACCTFILE comes out identical to `acctdata.txt`.
- **After-CBTRN02C scenario:**
  - The input is 100 TCATBAL records: 50 of type `01/0001` (positive) and 50 of type `03/0001` (negative).
  - `DEFAULT 03/0001` has rate 0, so only the 50 type-01 records produce transactions.
  - All 100 lookups miss, so SYSOUT has 100 `MISSING` lines.
- **Truncation verified:** all 50 amounts match `bal*15/1200` truncated. Round-half-up would match only 22 of them.

## Scope

**In**
- A Java 21 Maven project in `modernized/cbact04c/`, with JUnit 5 only.
- Reads and writes the harness's canonical text datasets (fixed-width, LF, overpunch signs), selected by DD name.
- PARM and the run timestamp are inputs.
- Equivalence tests against both golden scenarios.
- Characterization tests that pin the defects listed below.

**Out**
- Fixing any defect.
- Fee calculation (CR-2).
- CBTRN02C.
- Real VSAM/EBCDIC binary I/O.
- JCL or scheduler changes, container image and deployment (Deploy stage).

## Acceptance criteria

1. `mvn -f modernized/cbact04c test` passes on Java 21, with JUnit 5 and Surefire as the only dependencies.
2. On stock inputs with PARM `2022071800` and clock `2022-07-18 00:00:00.00`, `TRANSACT.txt` and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C/`.
3. Same as AC2, but with `ACCTFILE`/`TCATBALF` from `legacy/golden/CBTRN02C/`, compared against `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. Exit code 0 in both scenarios. SYSOUT lines match the golden `SYSOUT.txt` (152 / 302 lines). This is the proposed default, see Q1.
5. Interest is `bal*rate/1200` truncated toward zero to 2 dp, using decimal arithmetic (no floating point). Tested with positive and negative balances.
6. A disclosure-group miss falls back to `DEFAULT` and prints the two SYSOUT lines. A rate of 0 writes no transaction and adds nothing.
7. Transaction layout is pinned:
   - ID = PARM + 6-digit counter running across the whole run.
   - Description is padded with spaces to 100 bytes.
   - The 20-byte FILLER is spaces.
   - Both timestamps are `YYYY-MM-DD-HH.MM.SS.hh0000`.
8. A characterization test pins defect D1: the last account in TCATBAL is not rewritten.
9. Fatal conditions end with a non-zero exit, as in the legacy abend (RC 16 in the harness):
   - an open fails;
   - an account or xref is missing;
   - `DEFAULT` is missing.

## Risks and findings

| # | Finding | Evidence | Treatment |
|---|---|---|---|
| D1 | **The last account's interest is never written back.** `ELSE PERFORM 1050-UPDATE-ACCOUNT` is unreachable, because `PERFORM UNTIL` re-tests the flag before the body runs. | `CBACT04C.cbl:188-221`. In after-CBTRN02C, 49 of 50 accounts change. `00000000050` is byte-identical to its input even though `TRANSACT` charges it 18.77 (`0000000187G`). | Preserve and pin. Raise a separate CR. |
| D2 | Interest is truncated, not rounded. | `:464-465`. 28 of 50 golden amounts would differ under rounding. | Preserve. |
| D3 | The rate key uses `ACCT-GROUP-ID`, which is blank in all sample accounts (the group code sits in `ACCT-ADDR-ZIP`), so every lookup falls back to `DEFAULT`. | SYSOUT: 50 and 100 `MISSING` lines. | Data or design issue. Preserve. The Java must still support real group hits. |
| D4 | `1400-COMPUTE-FEES` is a no-op. | `:518-520` | Preserve (CR-2). |
| D5 | Transactions are written even when the amount is 0 (rate ≠ 0, balance 0). | Stock golden: 50 transactions of `0000000000{`. | Preserve. |
| D6 | `TRAN-ORIG-TS` is the run time, not the original transaction time. The timestamp keeps only hundredths of a second (`hh0000`). | `:613-626` | Preserve. |
| D7 | The 6-digit counter wraps after 999,999 transactions. PARM length is not checked. | `:173`, `:176-178` | Preserve. Document it. |
| D8 | Cycle credit and debit are zeroed even when the interest is 0. | `:353-354` | Preserve. |
| R1 | **Numeric fidelity:** values are zoned overpunch, intermediates must not lose precision, and there is `-0` (`}`) handling. | Copybooks | Use `BigDecimal`. Golden diffs catch errors. |
| R2 | **Keyed semantics:** TCATBAL must be processed in key order. The xref lookup goes by the alternate key; with duplicates, COBOL returns the first. | Sample: 50 xref rows, 50 distinct accounts | Sort by key on load. First match wins. |
| R3 | **Clock:** golden output depends on the frozen `CURRENT-DATE`. | `legacy/README.md` Determinism | Inject the clock (defaults to the system clock). |
| R4 | Input quirks: `cardxref.txt` is 36 bytes and some inputs have CRLF line endings. | `legacy/README.md` | Pad short records and strip CR on read, as the harness does. |

## Questions for the approver

1. **Is SYSOUT part of the equivalence contract?** The CR names only TRANSACT and ACCTFILE. *Default: yes, also match `SYSOUT.txt` and `RC.txt`. It is cheap and it gives operations the same job log.*
2. **How should the run date and time be supplied in the container?** *Default: PARM as the first program argument. Clock from the optional env var `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`, matching the harness), else the system clock in UTC.*
3. **D1 follow-up.** *Default: open a separate CR to fix the lost last-account update after cut-over. Until then, the Java keeps the defect.*
