# CR-1 Intent — INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** Nightly interest calculation runs as COBOL batch on the mainframe (`app/jcl/INTCALC.jcl` →
`CBACT04C`). That ties a core cards process to mainframe batch capacity, tooling and skills we are retiring.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` can run in a container. From the same inputs it
produces byte-identical interest transactions (`TRANSACT`) and an updated account master (`ACCTFILE`). The two
golden scenarios prove it. All legacy behaviour is kept as-is, defects included. Characterization tests pin
those defects so they can be decided on later.

## What the legacy code does today

Evidence: I ran `legacy/harness/run.sh CBACT04C /tmp/out1` once. It gave RC 0, 50 TRANSACT, 50 ACCTFILE and
152 SYSOUT lines, and `diff -r` against `legacy/golden/CBACT04C` was empty.

### Files (JCL `INTCALC.jcl` STEP15, `PARM='2022071800'`)

| DD | Org / access | LRECL | Key | Use | Copybook |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential | 50 | acct(11)+type(2)+cat(4), off 0 len 17 | input, read in key order | `CVTRA01Y` |
| `XREFFILE` | KSDS, random, **alt key** acct id (off 25 len 11) | 50 | card num(16) | input | `CVACT03Y` |
| `ACCTFILE` | KSDS, random, I-O | 300 | acct id(11) | read + REWRITE | `CVACT01Y` |
| `DISCGRP` | KSDS, random | 50 | group(10)+type(2)+cat(4) | input, rate lookup | `CVTRA02Y` |
| `TRANSACT` | sequential, OUTPUT | 350 | — | new interest trans | `CVTRA05Y` |

Select clauses: `CBACT04C.cbl:28-56`. `XREFFIL1` is not needed because the harness serves the AIX from `XREFFILE`.

### Processing steps

1. Open all five files. Any status other than `00` → message + abend (`:234-323`).
2. Loop: read `TCATBALF` sequentially. Status `10` = EOF (`:325-348`). DISPLAY each record (`:193`).
3. **Account break** when `TRANCAT-ACCT-ID ≠ WS-LAST-ACCT-NUM` (`:194-206`):
   - if this is not the first account, run `1050-UPDATE-ACCOUNT` for the *previous* account (`:196`);
   - reset `WS-TOTAL-INT` to 0, read `ACCTFILE` by acct id (`:372-391`), and read `XREFFILE` by the acct-id
     alternate key (`:393-413`). Not found → `ACCOUNT NOT FOUND` + abend.
4. **Rate lookup** (`1200`, `:415-440`): the key is `ACCT-GROUP-ID` + type + cat. On status `23` it prints
   `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and retries with group `DEFAULT`
   (`:443-460`). If `DEFAULT` is also missing → abend.
5. If `DIS-INT-RATE ≠ 0` (`:214`):
   - **Interest** = `TRAN-CAT-BAL × DIS-INT-RATE / 1200`, into `S9(09)V99`, **truncated** (no `ROUNDED`)
     (`:464-465`). Added to `WS-TOTAL-INT` (`:467`).
   - **Write tran** (`1300-B`, `:473-515`):
     - `TRAN-ID` = PARM date(10) + 6-digit running suffix;
     - type `01`, cat `0005`, source `System`;
     - desc `Int. for a/c <acct id>`, rest spaces;
     - amt = monthly interest;
     - merchant id 0, merchant name/city/zip spaces;
     - card = the xref card number;
     - orig/proc TS = current date as `YYYY-MM-DD-HH.MM.SS.hh0000` (`:613-626`);
     - FILLER uninitialised (spaces).
   - `1400-COMPUTE-FEES` is empty (`:518-520`).
   - A zero balance still produces a transaction, with amount 0, whenever the rate is non-zero.
6. **Account update** (`1050`, `:350-370`): `ACCT-CURR-BAL += WS-TOTAL-INT`, cycle credit and cycle debit set to
   0, REWRITE. There is no `ON SIZE ERROR`.
7. At EOF: close all files, `END OF EXECUTION OF PROGRAM CBACT04C`, GOBACK with RC 0 (`:224-232`).
   Abend → `ABENDING PROGRAM`, CEE3ABD U999 (harness exit 16) (`:628-632`).

### Observed data facts

- Stock `TCATBALF`: 50 records, all type `01` cat `0001`, all balances 0. That gives 50 zero-amount trans, and
  ACCTFILE comes out identical to `acctdata.txt` (I checked with `cmp`).
- After CBTRN02C: 100 records (50 × `01/0001`, 50 × `03/0001`). `DEFAULT 03 0001` has rate 0, so only the 50
  `01/0001` rows (rate 15.00) produce trans. SYSOUT has 302 lines = 2 + 100 × 3.
- `ACCT-GROUP-ID` is blank in every account, so every lookup falls back to `DEFAULT`.
- None of the 50 accounts has more than one xref card in the sample data.

## Scope

**In**
- Java 21 / Maven / JUnit 5 rewrite in `modernized/cbact04c/`, with the same processing and business rules.
- I/O in the harness canonical text format (fixed width, LF, overpunch signs, KSDS in key order). Inputs are
  `TCATBALF`, `XREFFILE`, `ACCTFILE`, `DISCGRP`. Outputs are `TRANSACT` and the rewritten `ACCTFILE`.
- PARM date as an argument, plus an injectable clock for the timestamps.
- Golden equivalence tests for both scenarios, characterization tests for the defects below, and unit tests for
  decimal and overpunch handling.

**Out**
- Fixing any defect listed below, and `1400-COMPUTE-FEES` (CR-2).
- CBTRN02C, CICS screens, JCL/scheduler changes, VSAM/EBCDIC binary I/O, and container image/deploy plumbing
  beyond a runnable jar.

## Acceptance criteria

1. `mvn -f modernized/cbact04c verify` passes on Java 21. The only dependencies are JUnit 5 and Surefire.
2. Scenario `CBACT04C`: stock `app/data/ASCII` inputs, PARM `2022071800`, clock `2022-07-18 00:00:00.00`.
   `TRANSACT` is byte-identical to `legacy/golden/CBACT04C/TRANSACT.txt` (50 × 350). `ACCTFILE` is
   byte-identical to `.../ACCTFILE.txt` (50 × 300).
3. Scenario `CBACT04C-after-CBTRN02C`: `TCATBALF` and `ACCTFILE` come from `legacy/golden/CBTRN02C/`, and the
   other inputs are stock. `TRANSACT` and `ACCTFILE` are byte-identical to
   `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. Interest is truncated toward zero to 2 decimals. A test pins account `00000000048`: raw 18.909625 → 18.90.
5. The last-account defect is preserved and pinned. In scenario 3, account `00000000050` is unchanged in
   `ACCTFILE`, but its interest transaction (`0000000187G` = 18.77) is still written.
6. Records with a zero rate write no transaction. Records with a non-zero rate and a zero balance write an
   amount-0 transaction.
7. Disclosure-group fallback to `DEFAULT` on miss. If `DEFAULT` is also missing, or the account or xref is
   missing, the program fails with a non-zero exit, and no partial success is reported.
8. Process exit code is 0 on success.
9. *(If Q1 = yes)* stdout is byte-identical to the golden `SYSOUT.txt` for both scenarios.

## Risks and findings

| # | Finding | Evidence | Handling |
|---|---|---|---|
| D1 | **Last account never rewritten.** After EOF the loop condition is tested before the `ELSE PERFORM 1050` branch, so that branch is dead code (`:188-221`). The last account's interest is never added, and its cycle fields are not zeroed. | Scenario 3: TRANSACT rec `2022071800000050` charges acct 50 18.77. In ACCTFILE, acct 50 is identical to the CBTRN02C input (bal `00000019458G`, cycle `15017E`/`478Q`). The other 49 accounts change. | Preserve + pin (AC5). Flag for a decision. |
| D2 | **Interest truncated, not rounded.** | 28 of 50 amounts in scenario 3 would differ by 0.01 if rounded. | Preserve (AC4). |
| D3 | **Group code in the wrong field.** Sample data has `A000000000` in `ACCT-ADDR-ZIP` and a blank `ACCT-GROUP-ID`, so the specific group rates (`A000000000` rows in DISCGRP) are never used. | Every lookup prints `DISCLOSURE GROUP RECORD MISSING` (golden SYSOUT). | Data issue; out of scope. Flag. |
| D4 | `1400-COMPUTE-FEES` is empty. | `:518-520` | CR-2. |
| D5 | DISCGRP open-failure message says `ERROR OPENING DALY REJECTS FILE`. | `:281` | Preserve the text if SYSOUT parity is chosen. |
| D6 | No `ON SIZE ERROR` on the interest/balance arithmetic. High-order digits are silently truncated on overflow (S9(9)V99 / S9(10)V99). | `:464`, `:352` | Preserve modulo semantics; pin with a unit test. Not reachable with sample data. |
| D7 | With more than one card per account, xref read by alt key returns the first duplicate. That card goes on every interest tran. | `:393-398`. Sample has 0 duplicates. | Use the first card in file order. Low risk. |
| R1 | Timestamp hundredths come from the clock and the micro tail is `0000`. Outputs are only deterministic with a fixed clock. | `:613-626`, README *Determinism* | Inject the clock. |
| R2 | Uninitialised WORKING-STORAGE (tail of `TRAN-DESC`, 20-byte FILLER) is spaces under the default dialect. It is `x'00'` under `-std=ibm`. | README *Known quirks* | Match golden (spaces). |
| R3 | Rewrite works on text unloads, not VSAM. Production I/O wiring (conversion to and from VSAM/EBCDIC, or a new store) is outside this CR. | — | Q2. |

## Questions for the approver

1. **SYSOUT and RC parity.** Should stdout reproduce the COBOL DISPLAY output byte-for-byte (`SYSOUT.txt`, 152
   and 302 lines), and should abends use exit 16? *Default: yes to both.* It is cheap, the golden files exist,
   and ops runbooks may grep these messages.
2. **File interface in the container.** *Default:* the canonical text format, with one path argument per DD.
   `ACCTFILE` is read in full and written back in key order, to a separate output path or in place.
   VSAM/EBCDIC conversion belongs to the deploy/data-migration work.
3. **Defect D1 (last account not updated).** It is preserved in this CR, as instructed. *Default:* raise a
   separate CR to fix it, because it understates balances for one account per run whenever that account
   accrues interest.
