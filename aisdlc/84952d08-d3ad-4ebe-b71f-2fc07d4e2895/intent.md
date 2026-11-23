# CR-1 Intent: INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** Nightly interest calculation (`app/jcl/INTCALC.jcl`, step `STEP15`, program `CBACT04C`) only runs on
the mainframe. That blocks the move of batch workloads to containers.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` produces the same interest transactions
(`TRANSACT`) and the same updated account master (`ACCTFILE`) as the COBOL, byte for byte, for the same inputs.
It runs in a container with no mainframe dependencies. Legacy defects stay as they are, each pinned by a test and
listed for a separate decision.

## What the legacy code does today

Evidence: I ran `legacy/harness/run.sh CBACT04C` on stock data, and again with `INPUT_DIR=legacy/golden/CBTRN02C`.
Both runs match `legacy/golden/CBACT04C` and `legacy/golden/CBACT04C-after-CBTRN02C` byte for byte (TRANSACT,
ACCTFILE, SYSOUT, RC=0).

### Files (`app/cbl/CBACT04C.cbl:28-56`, `app/jcl/INTCALC.jcl`)

| DD | Org / access | Rec len | Key | Layout | Use |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | 50 | acct(11)+type(2)+cat(4), cols 1-17 | `CVTRA01Y`: balance `S9(9)V99` at cols 18-28 | input, the driver |
| `XREFFILE` | KSDS, random by **alternate** key | 50 | card(16); AIX acct-id at cols 26-36 | `CVACT03Y` | input: card number for the account |
| `ACCTFILE` | KSDS, random, I-O | 300 | acct-id(11) | `CVACT01Y`: curr-bal 13-24, cyc-credit 77-88, cyc-debit 89-100, group-id 111-120 | read + REWRITE |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4) | `CVTRA02Y`: rate `S9(4)V99` at cols 17-22 | input: annual rate % |
| `TRANSACT` | sequential, new | 350 | none | `CVTRA05Y` | output |
| `XREFFIL1` | AIX path | | | | not needed by the harness (BDB holds the AIX) |

PARM: `'2022071800'`, a 10-char `PARM-DATE` (`:176-178`). All numerics are zoned with EBCDIC overpunch signs.

### Processing steps

1. Open all five files. Any status other than `00` ends in DISPLAY + abend (`:234-323`).
2. Read TCATBALF sequentially in key order. Each record is DISPLAYed to SYSOUT (`:190-193`, `:325-348`).
3. **Account break** (`:194-206`): when the acct-id changes:
   - if this is not the first record, REWRITE the previous account (step 6);
   - reset total interest to 0;
   - read ACCTFILE by acct-id (`:372-391`);
   - read XREFFILE by its alternate acct-id key (`:393-413`).
4. **Rate lookup** (`:210-213`, `:415-460`): key = `ACCT-GROUP-ID` + tran type + tran cat. On status `23` (not
   found), DISPLAY `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE`, then retry with group
   `DEFAULT`. The program abends if the DEFAULT lookup also fails.
5. **If rate ≠ 0** (`:214-217`):
   - monthly interest = `bal × rate / 1200` (`:464-465`), stored in `S9(9)V99` with **no ROUNDED**, so it is
     truncated toward zero;
   - add it to the account total (`:467`);
   - write one transaction (`:473-515`);
   - PERFORM `1400-COMPUTE-FEES`, which is empty (`:518-520`).
   - If the rate is 0, no transaction is written.
6. **Account update** (`:350-370`): `ACCT-CURR-BAL += total interest`, `CURR-CYC-CREDIT = 0`,
   `CURR-CYC-DEBIT = 0`, then REWRITE. Accounts with no TCATBAL record are not touched.
7. Close files, DISPLAY end banner, RC 0 (`:224-232`).

### Transaction record built (`:473-498`)

| Field | Value |
|---|---|
| `TRAN-ID` | `PARM-DATE` + 6-digit running suffix starting at 000001, e.g. `2022071800000001` |
| type / cat | `01` / `0005` |
| `TRAN-SOURCE` | `System` |
| `TRAN-DESC` | `Int. for a/c ` + 11-digit acct-id, rest spaces |
| `TRAN-AMT` | monthly interest |
| merchant id | 0 |
| merchant name / city / zip | spaces |
| `TRAN-CARD-NUM` | from XREF |
| `TRAN-ORIG-TS` / `TRAN-PROC-TS` | `FUNCTION CURRENT-DATE` as `YYYY-MM-DD-HH.MM.SS.hh0000` (`:613-626`). This is the clock, not PARM. |
| FILLER (20) | spaces (never initialised, README "Known quirks") |

### Errors

Any unexpected file status, missing account, or missing XREF leads to `DISPLAY 'ABENDING PROGRAM'`, then
`CEE3ABD` U999. Under the harness that is RC **16** (`:628-632`). File status is printed as `NNNN` (`:635-648`).

### Observed data facts

- **Stock data.** All 50 TCATBAL records are type/cat `01/0001` and all balances are 0. The DEFAULT rate for
  `01/0001` is 15.00%, so the run writes 50 zero-amount transactions. ACCTFILE comes out identical to
  `acctdata.txt`.
- **After CBTRN02C.**
  - Inputs: 100 TCATBAL records, 2 per account (`01/0001` and `03/0001`). The `03/0001` rate is 0, so only one
    transaction per account is written: 50 transactions.
  - Rounding: all 50 amounts equal the truncated value of `bal×15/1200`; 28 of them would differ if rounded.
  - ACCTFILE: 49 of 50 records change (see Finding F1).
- `ACCT-GROUP-ID` is blank in every account, so every lookup falls back to DEFAULT (50 and 100 `MISSING` lines
  in SYSOUT). The `A000000000` group rows in `discgrp.txt` (lines 1-17) are never used.

## Scope

**In**
- Java 21 Maven project `modernized/cbact04c/`, JUnit 5 only.
- Reads and writes the harness's canonical fixed-width text form (one file per DD).
- PARM date and clock are injectable.
- Same TRANSACT and ACCTFILE bytes, same RC (0 / 16).
- Equivalence tests on both golden scenarios.
- Characterization tests that pin each legacy defect and quirk.

**Out**
- Fixing any defect (F1-F6).
- Implementing fees.
- Real VSAM/EBCDIC binary I/O.
- JCL or scheduler changes.
- CBTRN02C.
- Container image / deploy plumbing. That is a later stage.

## Acceptance criteria

1. `mvn test` in `modernized/cbact04c/` passes with Java 21. The only dependency is JUnit 5 (+ Surefire).
2. On stock inputs (`app/data/ASCII`, PARM `2022071800`, clock `2022-07-18 00:00:00.00`), the Java `TRANSACT.txt`
   and `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C/`.
3. On inputs from `legacy/golden/CBTRN02C` (ACCTFILE, TCATBALF) plus stock XREF/DISCGRP, the outputs are
   byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. Exit code is 0 on success. On the legacy abend conditions (missing account, missing XREF, missing DEFAULT
   rate, bad input) it is non-zero, 16 to match the harness.
5. Interest is `bal × rate / 1200`, truncated toward zero to 2 decimals. A test pins a case where rounding would
   differ.
6. A rate of 0 writes no transaction and still resets the account's cycle credit and debit.
7. The rate lookup falls back to `DEFAULT` when the group, type and category key is missing. A test also covers a
   **found** non-DEFAULT group: a synthetic input, with its expected output produced by running the COBOL via
   `INPUT_DIR`.
8. F1 is pinned by a test: the last account in TCATBAL order is not rewritten.
9. The timestamp comes from an injectable clock, formatted `YYYY-MM-DD-HH.MM.SS.hh0000`. Transaction ids are
   `PARM-DATE` + a 6-digit suffix.
10. `legacy/golden/` and `legacy/harness/` are unchanged, and `legacy/harness/golden.sh --check` still passes.

## Risks and findings

| # | Finding | Evidence | Treatment |
|---|---|---|---|
| **F1** | **Last account never updated.** The `ELSE PERFORM 1050-UPDATE-ACCOUNT` branch (`:219-220`) is unreachable. EOF is set inside the body (`:190`), and `PERFORM UNTIL` (`:188`) exits before running the body again. The last account's interest is charged as a transaction but never added to its balance, and its cycle fields are not reset. | `golden/CBACT04C-after-CBTRN02C`: tran `2022071800000050` charges acct `00000000050` 18.77 (`0000000187G`). That account's ACCTFILE row is byte-identical to `golden/CBTRN02C/ACCTFILE.txt` (balance 1945.87). The other 49 rows change. | Preserve, pin, flag. This is a customer-impacting ledger mismatch. |
| F2 | Fees are never computed: `1400-COMPUTE-FEES` is empty (`:518-520`). | source | Preserve (CR-2 target). |
| F3 | Interest is truncated, not rounded (`:464`, no `ROUNDED`). | 28/50 amounts would differ | Preserve. Flag for business confirmation. |
| F4 | The rate lookup uses `ACCT-GROUP-ID`, which is blank in all sample accounts. The group code sits in `ACCT-ADDR-ZIP`. Every account gets DEFAULT rates. | SYSOUT: 50 / 100 `DISCLOSURE GROUP RECORD MISSING` | Data issue. Preserve the logic. |
| F5 | The DISCGRP open-error message says `DALY REJECTS FILE` (`:281`). | source | Preserve the text if SYSOUT parity is chosen (Q1). |
| F6 | No `ON SIZE ERROR` on the interest or balance arithmetic (`:352`, `:464`). Overflow would silently drop high-order digits. `WS-TRANID-SUFFIX PIC 9(6)` wraps after 999,999 transactions, giving duplicate tran ids. | source | Preserve the COBOL truncation semantics. Untested by golden data. |
| R1 | **Negative balances and negative zero.** No golden case has a negative balance. The COBOL may sign a truncated `-0.00` as `}` or `{`. | none yet | In Build, generate oracle cases via the harness with `INPUT_DIR`. Do not guess. |
| R2 | The golden scenarios have only one card per account, so the AIX lookup's choice of card when an account has several is unproven. | `cardxref.txt`: 0 duplicate acct-ids | Take the first card in primary-key order (BDB AIX behaviour) and verify with a harness case. |
| R3 | Byte-level formatting: overpunch encoding, the fixed 300/350 widths with trailing spaces, and the uninitialised tail of `TRAN-DESC`. | README quirks | Covered by the golden diff tests. |

## Questions for the approver

1. **SYSOUT parity.** Should the Java program also reproduce `SYSOUT.txt` byte for byte (152 / 302 lines)? The
   CR only requires TRANSACT and ACCTFILE. *Default: yes, as stdout. It costs little, and operators keep their
   log format.*
2. **File interface.** Should the program read and write the canonical fixed-width text files (the golden
   format), with paths taken from DD-named arguments or environment variables? *Default: yes. ACCTFILE is
   updated by writing a new file in key order. Native VSAM/EBCDIC conversion stays at the platform boundary.*
3. **Timestamp source.** Should it keep the "current clock" behaviour, overridable for tests? *Default: yes,
   like-for-like.*
