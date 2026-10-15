# CR-1 Intent — INTCALC / `CBACT04C` to Java 21

Run `74748ec5-2d41-48cc-8e26-0e5584ce7b11` · Stage: Plan

## Problem / outcome

**Problem.** The nightly interest calculation (job `INTCALC`, program `CBACT04C`) runs only as COBOL on the
mainframe. That blocks moving batch work off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that can run in a container. Given the same
inputs, it writes the same interest transactions and the same updated account master as the COBOL, byte for
byte, legacy defects included. Proof: the two golden scenarios.

## What the legacy code does today

Evidence: I ran `legacy/harness/run.sh CBACT04C`. It matches `legacy/golden/CBACT04C` exactly (TRANSACT 50,
ACCTFILE 50, SYSOUT 152 lines, RC 0).

### Files (`app/jcl/INTCALC.jcl`, `CBACT04C.cbl:28-92`)

| DD | Org / access | LRECL | Key | Use | Copybook |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential, INPUT | 50 | acct(11)+type(2)+cat(4), offset 0 | driver: category balances | `CVTRA01Y` |
| `XREFFILE` | KSDS, random, INPUT | 50 | card(16); **AIX** acct-id offset 25 len 11 | card number for the account | `CVACT03Y` |
| `ACCTFILE` | KSDS, random, **I-O** | 300 | acct-id(11) | read, then REWRITE | `CVACT01Y` |
| `DISCGRP` | KSDS, random, INPUT | 50 | group(10)+type(2)+cat(4) | interest rate `DIS-INT-RATE S9(4)V99` | `CVTRA02Y` |
| `TRANSACT` | sequential, OUTPUT (new GDG) | 350 | – | interest transactions | `CVTRA05Y` |

PARM: `2022071800` (10 bytes → `PARM-DATE`, `:176-178`).

### Processing steps

1. Open all five files. Any status other than `00` → DISPLAY, then abend U0999 (`:234-323`, `:628-632`).
2. Read TCATBALF in key order. Each record is DISPLAYed to SYSOUT (`:190-193`).
3. **Control break on account id** (`:194-206`). When the account changes: rewrite the previous account
   (1050), unless this is the first record. Then reset `WS-TOTAL-INT`, read ACCTFILE by acct id (`:372-391`),
   and read XREFFILE by the alternate key acct id (`:393-413`). Not found → DISPLAY `ACCOUNT NOT FOUND`, then
   abend.
4. **Rate lookup** (`:415-460`): the key is `ACCT-GROUP-ID` + type + category. On status `23`, DISPLAY
   `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE`, then retry with group `DEFAULT`. If
   `DEFAULT` is also missing → abend.
5. **If rate ≠ 0** (`:214-217`): compute `WS-MONTHLY-INT = (TRAN-CAT-BAL * DIS-INT-RATE) / 1200`
   (`:464-465`). There is no `ROUNDED`, so the result is **truncated** to 2 dp. Add it to `WS-TOTAL-INT`
   and write a transaction (1300-B). Then call `1400-COMPUTE-FEES`, which is empty (`:518-520`). Rate = 0 →
   no transaction is written.
6. **Transaction record** (`:473-515`):
   - `TRAN-ID` = PARM-DATE + 6-digit run counter
   - type `01`, category `0005`, source `System`
   - desc `Int. for a/c <ACCT-ID>`, then spaces
   - amount = monthly interest
   - merchant id 0, name/city/zip spaces
   - card = `XREF-CARD-NUM`
   - ORIG-TS and PROC-TS = `CURRENT-DATE` formatted as `YYYY-MM-DD-HH.MM.SS.hh0000` (`:613-626`)
   - FILLER = spaces (the record is never initialised)
7. **Account update** (1050, `:350-370`): `ACCT-CURR-BAL += WS-TOTAL-INT`, cycle credit and cycle debit = 0,
   REWRITE.
8. Close all files, DISPLAY `END OF EXECUTION`, GOBACK with RC 0.

### Business rules (verified against golden data)

- Monthly interest = balance × annual rate % / 1200, truncated toward zero to cents. I checked this against
  `golden/CBACT04C-after-CBTRN02C`: **50/50 amounts match truncation, while 28/50 would differ under
  rounding.** Example: 1164.87 × 15.00 / 1200 = 14.56 (`0000000145F`).
- The account is rewritten even when its total interest is 0. The cycle fields are zeroed regardless.
- One transaction per category balance with a non-zero rate. After CBTRN02C, each account has 01/0001 (DEFAULT
  rate 15.00%) and 03/0001 (rate 0) → 50 transactions out of 100 balances.

## Scope

**In**
- Java 21 Maven project `modernized/cbact04c/`, JUnit 5 only.
- Reads and writes the harness canonical text format: fixed width, LF, overpunch signs.
- Same TRANSACT and ACCTFILE output, plus the same SYSOUT lines and RC for the success path.
- Clock and PARM date are injectable, for deterministic runs.
- Equivalence tests on both golden scenarios.
- Characterization tests that pin the defects below.

**Out**
- Fixing any legacy defect.
- Fee computation (CR-2).
- CBTRN02C.
- JCL/scheduler changes, container image build, deploy.
- Real VSAM/EBCDIC binary I/O.
- Performance tuning.

## Acceptance criteria

1. `mvn -f modernized/cbact04c verify` passes on Java 21, with JUnit 5 as the only dependency.
2. Scenario `CBACT04C` (stock inputs, PARM `2022071800`, clock `2022-07-18 00:00:00.00`): `TRANSACT.txt` and
   `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C/` (50 + 50 records).
3. Scenario `CBACT04C-after-CBTRN02C` (ACCTFILE and TCATBALF from `golden/CBTRN02C`): `TRANSACT.txt` and
   `ACCTFILE.txt` are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. On both scenarios, SYSOUT lines and exit code match the golden `SYSOUT.txt` / `RC.txt` (152 / 302 lines,
   RC 0).
5. A characterization test pins the last-account defect: account `00000000050` is unchanged in the output
   ACCTFILE, even though transaction `2022071800000050` charged it 18.77.
6. A characterization test pins truncation: for example, a balance of 1164.87 at 15.00% gives 14.56.
7. Unit tests cover the DEFAULT-group fallback, the rate = 0 skip (no transaction), and the overpunch
   encode/decode for all 20 sign characters.
8. Fatal conditions (account/xref/DEFAULT-rate not found, I/O error) stop the run with a non-zero exit code
   and the legacy message text.
9. The legacy harness is untouched, and `legacy/harness/golden.sh --check` still passes.

## Risks and findings

| # | Finding | Evidence | Handling |
|---|---|---|---|
| D1 | **The last account is never rewritten.** After EOF, `PERFORM UNTIL` exits before the `ELSE PERFORM 1050` branch can run. | `CBACT04C.cbl:188-222`. Golden after-CBTRN02C: acct 50 is identical to `CBTRN02C/ACCTFILE.txt` (bal `00000019458G` = 1945.87, cycle 1501.75 / -47.88), even though tran `…000050` = 18.77. 49/50 accounts change. | Preserve and pin (AC5). Flag for a separate fix. |
| D2 | **Interest is truncated, not rounded.** | `:464` has no `ROUNDED`; 28/50 golden amounts differ from rounding. | Preserve (AC6). Business decision. |
| D3 | **The group lookup always misses.** `ACCT-GROUP-ID` is blank in all sample accounts, so every lookup falls back to `DEFAULT` (100 `MISSING` messages). | README quirk; `SYSOUT.txt`. | Data issue; preserve the fallback. |
| D4 | `1400-COMPUTE-FEES` is empty. | `:518-520` | CR-2. |
| D5 | The DISCGRP open error says `ERROR OPENING DALY REJECTS FILE`. | `:281` | Keep the text. Cosmetic. |
| D6 | No `ON SIZE ERROR`. The S9(9)V99 interest and the S9(10)V99 balance silently lose high-order digits on overflow. The 6-digit tran counter wraps after 999,999. | `:168, :173, :352, :464` | Mirror COBOL truncation semantics. Not reachable with the sample data. |
| D7 | The XREF alternate key allows duplicates. With several cards on one account, the first card in AIX order is used. | `:38, :393` | No duplicates in the sample (0). Mirror "first by insertion/key order". |
| R1 | Byte identity depends on uninitialised = spaces (default dialect), the overpunch signs, and the clock format with hundredths + `0000`. | README "Known quirks" | Encode these explicitly and test them. |
| R2 | The Java program gets text files, not VSAM. TCATBALF must be processed in key order, and the ACCTFILE output must be in key order. | README: KSDS unloads are in key order | Sort or validate on load. |

## Questions for the approver

1. **SYSOUT and exit code:** should they be part of the equivalence contract, or only TRANSACT/ACCTFILE?
   *Default: include them (AC4). It costs little and makes operations parity visible.*
2. **Abend exit code:** COBOL abends with U0999, which the harness maps to exit 16. *Default: Java exits with 16
   and prints the same message lines.*
3. **D1 and D2:** confirm they are preserved in this CR and tracked as separate change requests. *Default: yes,
   as the CR states.*
