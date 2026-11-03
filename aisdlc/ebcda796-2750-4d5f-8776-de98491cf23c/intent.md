# CR-1 Intent: INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest calculation runs only as COBOL on the mainframe (`app/jcl/INTCALC.jcl` → `app/cbl/CBACT04C.cbl`), and batch workloads are moving off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. Given the same inputs, it writes the same interest transactions and the same updated account master as the COBOL program, byte for byte, legacy defects included. Equivalence is proven on the golden scenarios.

## What the legacy code does today

Run with `legacy/harness/run.sh CBACT04C`: RC 0. It writes `TRANSACT` (50 records), `ACCTFILE` (50) and `SYSOUT` (152 lines), all **identical** to `legacy/golden/CBACT04C/`.

### Files (JCL `INTCALC.jcl` STEP15, `PARM='2022071800'`)

| DD | Org / access | LRECL | Key | Copybook | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | 50 | acct(11)+type(2)+cat(4), off 0 len 17 | `CVTRA01Y` | input, read in key order (`CBACT04C.cbl:28-32`) |
| `XREFFILE` | KSDS, random by **alt key** acct-id (off 25 len 11) | 50 | card-num(16) | `CVACT03Y` | input (`:34-39`, `:393-398`) |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct-id(11) | `CVACT01Y` | read + REWRITE (`:41-45`, `:291`) |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4) | `CVTRA02Y` | interest rates (`:47-51`) |
| `TRANSACT` | sequential, OUTPUT | 350 | none | `CVTRA05Y` | new interest transactions (`:53-56`, `:309`) |

`XREFFIL1` (the AIX path) is not needed, because the harness serves the alternate key directly.

### Processing steps

1. Open all five files. Any open status other than `00` abends (`:234-323`).
2. Read `TCATBALF` sequentially. Each record is DISPLAYed to SYSOUT (`:190-193`).
3. **Account break**: when `TRANCAT-ACCT-ID` changes (`:194`):
   - Rewrite the *previous* account (`1050`). The first break skips this (`:195-199`).
   - Reset `WS-TOTAL-INT` to 0, then read `ACCTFILE` by acct-id (`1100`, `:372`) and `XREFFILE` by alt key acct-id (`1110`, `:393`).
4. Look up the rate in `DISCGRP` by `ACCT-GROUP-ID` + type + cat (`:210-213`, `1200`, `:415`).
   - On status `23` (not found), DISPLAY 2 lines and retry with group `DEFAULT` (`:417-419`, `:436-439`, `1200-A`, `:443`).
   - A missing `DEFAULT` record, or any other status, abends.
5. If `DIS-INT-RATE ≠ 0` (`:214`):
   - `WS-MONTHLY-INT = TRAN-CAT-BAL * DIS-INT-RATE / 1200`, **truncated** to 2 dp (no `ROUNDED`, `:464-465`).
   - Add it to `WS-TOTAL-INT` (`:467`).
   - Write one transaction (`1300-B`, `:473-515`).
   - Perform `1400-COMPUTE-FEES`, which is empty (`:518-520`).
6. **Transaction record** (`:474-498`):
   - `TRAN-ID` = PARM date (10) + 6-digit running suffix, e.g. `2022071800000001`.
   - type `01`, cat `0005`, source `System`.
   - desc `Int. for a/c <ACCT-ID>`, space padded.
   - amount = monthly interest.
   - merchant id 0, merchant name/city/zip spaces.
   - card number from xref.
   - orig and proc timestamps = `FUNCTION CURRENT-DATE` as `YYYY-MM-DD-HH.MM.SS.hh0000`.
   - trailing 20-byte FILLER = spaces (never initialised; spaces under the default dialect).
7. **Account rewrite** (`1050`, `:350-370`):
   - `ACCT-CURR-BAL += WS-TOTAL-INT`.
   - `ACCT-CURR-CYC-CREDIT = 0`, `ACCT-CURR-CYC-DEBIT = 0`.
   - All other bytes are kept as read.
8. At EOF, close all files, DISPLAY end banner, RC 0 (`:224-232`).
9. **Errors**: DISPLAY the message plus `FILE STATUS IS: NNNNxxxx`, then `CEE3ABD` U999. The harness exits with 16 (`:628-648`).

### Rules confirmed on golden data (`CBACT04C-after-CBTRN02C`)

- **Truncation.** All 50 amounts match `bal × rate / 1200` truncated. ROUND_HALF_UP would change **28 of 50**.
- **Account update.** The balance increase equals the interest transaction for accounts 1-49. Example: account 1 goes from 1288.11 to 1302.66, with interest 14.56.
- **Rate zero means no transaction.** Each account has 2 TCATBAL rows: `01/0001` at 15.00% and `03/0001` at 0%. That gives 100 rows in and 50 transactions out.
- **DEFAULT fallback everywhere.** `ACCT-GROUP-ID` is blank, so every lookup falls back to `DEFAULT`. SYSOUT is 1 + 100 + 200 + 1 = 302 lines.
- **Stock data.** All balances are zero, so 50 zero-amount transactions are written and `ACCTFILE` is byte-identical to `app/data/ASCII/acctdata.txt`.

## Scope

**In**
- Java 21 rewrite of `CBACT04C` in `modernized/cbact04c/`: Maven, JUnit 5, no frameworks.
- Reads and writes the harness's canonical fixed-width text format: one record per line, LF, overpunch signs. KSDS files are rewritten in key order.
- PARM date as a CLI argument.
- Injectable clock, so timestamps are deterministic.
- Same exit codes: 0 for success, 16 for abend.
- Characterization tests that pin each legacy defect below.
- Equivalence tests against both golden scenarios.

**Out**
- Fixing any legacy defect.
- Implementing fees (`1400`, which is CR-2).
- CBTRN02C.
- Real VSAM/EBCDIC binary I/O, JCL or scheduler changes.
- Container image and deploy plumbing (that is the Deploy stage).

## Acceptance criteria

1. **Build.** `mvn -q verify` in `modernized/cbact04c/` passes on Java 21, with JUnit 5 as the only dependency.
2. **Stock scenario.** With PARM `2022071800`, clock `2022-07-18 00:00:00.00`, inputs from `app/data/ASCII/`:
   - `TRANSACT` is byte-identical to `legacy/golden/CBACT04C/TRANSACT.txt` (50 records).
   - `ACCTFILE` is byte-identical to `legacy/golden/CBACT04C/ACCTFILE.txt` (50 records).
3. **Nightly-order scenario.** Same as AC2, but with `ACCTFILE` and `TCATBALF` taken from `legacy/golden/CBTRN02C/`. Both outputs match `legacy/golden/CBACT04C-after-CBTRN02C/` byte for byte.
4. **Interest arithmetic.** Interest is `bal × rate / 1200` truncated toward zero to 2 dp, with exact decimal arithmetic (no floating point). A unit test pins a case where rounding would differ.
5. **Last-account defect pinned.** A characterization test shows that the last account's interest is written to `TRANSACT` but its `ACCTFILE` record is left unchanged.
6. **DEFAULT fallback.** A missing group falls back to `DEFAULT`. If `DEFAULT` is also missing, the program exits with 16.
7. **Missing data abends.** A missing account or a missing xref exits with 16.
8. **Rate zero.** A zero rate writes no transaction and adds no interest.
9. **SYSOUT and RC.** Exit code 0 on success. SYSOUT matches golden `SYSOUT.txt` (152 / 302 lines). See Q1.
10. **Golden untouched.** `legacy/harness/golden.sh --check` still passes, and nothing under `legacy/` or `.github/` changes.

## Risks and findings

| # | Finding | Evidence | Treatment |
|---|---|---|---|
| D1 | **Last account never rewritten.** At EOF the loop exits before `ELSE PERFORM 1050` (`:188-221`). | The account 50 transaction `2022071800000050` charges 18.77 (`0000000187G`), but account 50 in the output `ACCTFILE` is byte-identical to the input: balance 1945.87, cycle fields not zeroed. 49/50 records change. | Preserve and pin. Flag for a fix CR. |
| D2 | **Interest truncated, not rounded** (`:464`). | 28/50 golden amounts would differ under half-up rounding. | Preserve. Business should confirm the intended policy. |
| D3 | **`ACCT-GROUP-ID` blank in data**, so every rate comes from `DEFAULT`. The group code sits in `ACCT-ADDR-ZIP`. | `acctdata.txt` cols 103-112 = `A000000000`, group blank. SYSOUT shows "DISCLOSURE GROUP RECORD MISSING" ×50/×100. | Data issue. Preserve the behaviour. |
| D4 | `1400-COMPUTE-FEES` is empty. | `:518-520` | Out of scope (CR-2). |
| D5 | Transaction timestamps use the wall clock, not the PARM date, so reruns differ. | `:614`, `legacy/README.md` "Determinism" | Inject the clock and freeze it in tests. |
| D6 | Zero-amount transactions are written when the balance is 0 and the rate is non-zero. | Stock golden: 50 × `0000000000{` | Preserve. |
| D7 | No guard on negative balances: a credit balance would produce negative "interest". The 6-digit tran-ID suffix wraps after 999,999. | `:464`, `:173` | Not exercised by golden data. Mirror COBOL. Flag. |
| D8 | The alt-key xref read takes the first card for an account. | `:394`. Sample data has 1 card per account. | Mirror: first card in primary-key order. |
| D9 | Misleading error text: the DISCGRP open error says "DALY REJECTS FILE". | `:281` | Preserve the text if SYSOUT parity is required. |
| R1 | Overpunch sign and implied-decimal handling must be exact. `-0` (`}`) is not expected in output. | `legacy/README.md` "Known quirks" | Dedicated codec plus unit tests. |
| R2 | Input files have CRLF line endings (`tcatbal.txt`) and short lines (`cardxref.txt` is 36 bytes). | `cat -A`, `legacy/README.md` | Strip CR and pad lines to LRECL when loading. |

## Questions for the approver

1. **SYSOUT parity.** The CR requires byte-identity only for `TRANSACT` and `ACCTFILE`. Should SYSOUT/DISPLAY output match too? **Default: yes.** It costs little, ops runbooks may grep for it, and it is tested against golden `SYSOUT.txt`.
2. **I/O format.** The Java job reads and writes the canonical fixed-width text files (the harness unload format), not VSAM/EBCDIC. **Default: yes.** Conversion to and from the mainframe datasets belongs to the migration's data-transfer layer.
3. **Defect follow-ups.** D1 (last-account write-back) and D2 (truncation) affect customer balances. **Default:** preserve them in CR-1, and raise D1 as a separate fix CR right after cut-over. Business must confirm D2.
