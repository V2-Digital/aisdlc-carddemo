# CR-1 Intent: INTCALC (`CBACT04C`) to Java 21

## Problem / outcome

**Problem.** The nightly interest run (`INTCALC`) only runs on the mainframe as COBOL program `CBACT04C`. That blocks the move of batch workloads off the mainframe.

**Outcome.** A plain Java 21 program in `modernized/cbact04c/` that runs in a container. Given the same inputs, it writes byte-identical interest transactions (`TRANSACT`) and an updated account master (`ACCTFILE`). Proof is the two golden scenarios. Legacy behaviour, defects included, is kept unchanged and pinned by tests.

## What the legacy code does today

Verified by running the harness: `run.sh CBACT04C` and `INPUT_DIR=legacy/golden/CBTRN02C run.sh CBACT04C` both reproduce `legacy/golden/` byte-for-byte, with RC 0.

### Job and files

`app/jcl/INTCALC.jcl:22` has one step: `EXEC PGM=CBACT04C,PARM='2022071800'`. The PARM is a 10-byte date prefix (`CBACT04C.cbl:176-178`).

| DD | Org / access | LRECL | Key | Use | Layout |
|---|---|---|---|---|---|
| TCATBALF | KSDS, sequential, INPUT | 50 | acct(11)+type(2)+cat(4), off 0 | driver, read in key order | `CVTRA01Y`; `TRAN-CAT-BAL` S9(9)V99 @17 |
| XREFFILE | KSDS, random, INPUT | 50 | card(16) @0; **alt key** acct(11) @25 | card number for the account | `CVACT03Y` |
| ACCTFILE | KSDS, random, **I-O** | 300 | acct(11) @0 | read, then REWRITE | `CVACT01Y`; bal @12, cyc-credit @78, cyc-debit @90, group-id @112 |
| DISCGRP | KSDS, random, INPUT | 50 | group(10)+type(2)+cat(4) @0 | interest rate | `CVTRA02Y`; `DIS-INT-RATE` S9(4)V99 @16 |
| TRANSACT | sequential, OUTPUT (new GDG) | 350 | none | interest transactions | `CVTRA05Y` |

The JCL also has `XREFFIL1` (the AIX path), but the program never references it.

Sample input counts: tcatbal 50, acctdata 50, cardxref 50 (36-byte lines), discgrp 51.

### Processing (main loop `CBACT04C.cbl:188-222`)

1. Open all five files and abend if any status is not `00` (`:234-323`).
2. Read TCATBALF sequentially (`:325-348`). Status `10` means EOF. For each record, DISPLAY it to SYSOUT (`:193`).
3. **On an account break** (`:194-206`): REWRITE the previous account, except on the first record. Reset `WS-TOTAL-INT` to 0. READ ACCTFILE by acct id (`:372`). READ XREFFILE by its alternate key, acct id (`:393`). If either record is missing, print `ACCOUNT NOT FOUND:` and abend.
4. **Rate lookup** (`:210-213`, `:415-460`): the key is `ACCT-GROUP-ID` + type + cat. On status `23`, print `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and retry with group `DEFAULT`. Abend if that read fails too.
5. **If the rate is not 0** (`:214-217`):
   - **Interest** (`:464-467`): `WS-MONTHLY-INT = TRAN-CAT-BAL * DIS-INT-RATE / 1200`. The result is S9(9)V99 with no `ROUNDED`, so it is **truncated** toward zero. Example: 2339.97 × 15 / 1200 = 29.2496, stored as 29.24 (`0000000292D`). The result is added to `WS-TOTAL-INT`.
   - **Write one TRANSACT record** (`:473-515`):
     - `TRAN-ID` = PARM-DATE + 6-digit running suffix (`2022071800000001`, ...).
     - type `01`, cat `0005`, source `System`.
     - desc `Int. for a/c <ACCT-ID>`.
     - amount, merchant id 0, merchant name/city/zip spaces.
     - card number from XREF.
     - orig and proc timestamps = current date as `YYYY-MM-DD-HH.MM.SS.hh0000` (`:613-626`).
   - `1400-COMPUTE-FEES` is called but is empty (`:518-520`).
6. **Account update** (`1050`, `:350-370`): `ACCT-CURR-BAL += WS-TOTAL-INT`, cycle credit and cycle debit set to 0, then REWRITE. This happens even when the total is 0.
7. Close everything, DISPLAY start/end banners, `GOBACK` RC 0. Any I/O error leads to `9999-ABEND-PROGRAM`, which calls `CEE3ABD` U999. In the harness that gives exit 16 (`:628-632`).

### Observed results

| Scenario | TCATBAL | TRANSACT | ACCTFILE changed | SYSOUT |
|---|---|---|---|---|
| `CBACT04C` (stock) | 50 (all type 01/cat 0001, balance 0) | 50, all amount `0000000000{` | 0 of 50 (identical to `acctdata.txt`) | 152 lines |
| `CBACT04C-after-CBTRN02C` | 100 (50 × type 01, 50 × type 03, the 03s are negative) | 50 (type 01 only; DEFAULT 03/0001 rate = 0) | **49 of 50** | 302 lines |

The rates come from the DEFAULT rows, `discgrp.txt:18-34`. 01/0001 is 15.00%. 03/xxxx is 0.

## Scope

**In**
- A Java 21 Maven project `modernized/cbact04c/` (JUnit 5 only).
- It reads and writes the harness's canonical text form: fixed-width lines, LF, overpunch signs. That form covers TCATBALF, XREFFILE, ACCTFILE, DISCGRP and TRANSACT.
- PARM date and clock can be injected, so tests are deterministic.
- Same SYSOUT lines and same exit codes as the COBOL, including the abend paths.
- Golden equivalence tests, plus characterization tests for each finding below.

**Out**
- Fixing any legacy defect.
- Implementing fees (CR-2).
- VSAM/EBCDIC binary I/O, JCL/scheduler changes, the container image and deploy, and CBTRN02C.

## Acceptance criteria

1. `mvn -f modernized/cbact04c test` passes on Java 21, with no dependencies other than JUnit 5 and Surefire.
2. On the stock inputs with PARM `2022071800` and clock `2022-07-18 00:00:00.00`, TRANSACT and ACCTFILE are byte-identical to `legacy/golden/CBACT04C/` (50 and 50 records).
3. On the inputs from `legacy/golden/CBTRN02C` (ACCTFILE, TCATBALF) plus stock XREF/DISCGRP, outputs are byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C/`.
4. SYSOUT is line-identical to the golden `SYSOUT.txt` (152 / 302 lines) and the exit code is 0 in both scenarios.
5. Interest truncates rather than rounds. The test pins 2339.97 @ 15.00% → 29.24.
6. A characterization test pins the last-account defect (F1): account `00000000050` is left unchanged in ACCTFILE, but its interest transaction (`0000000187G`, 18.77) is still written.
7. Each of these causes a non-zero exit (16) with the COBOL's error message, and is tested:
   - missing account
   - missing XREF
   - missing DEFAULT rate
8. If a group-specific DISCGRP row exists, it is used in place of DEFAULT. A synthetic test covers this, because the sample data never exercises it.
9. Overpunch encoding and decoding round-trips every sign character (`{A-I}J-R`).

## Risks and findings

| # | Finding | Evidence | Treatment |
|---|---|---|---|
| F1 | **Defect: the last account is never rewritten.** After EOF, `PERFORM UNTIL` stops before the `ELSE PERFORM 1050` (`:219-220`) can run. The last account's interest is posted as a transaction but never added to its balance, and its cycle totals are not cleared. | `golden/CBACT04C-after-CBTRN02C`: TRANSACT `2022071800000050` charges 18.77. ACCTFILE row 50 is still identical to `golden/CBTRN02C/ACCTFILE.txt`, with balance `00000019458G`. | Preserve, pin (AC6), flag |
| F2 | Interest is **truncated**, not rounded, which can under-charge by up to 0.01 per category. | `:464-465` has no `ROUNDED`; the 29.2496 → 29.24 example above | Preserve, pin (AC5) |
| F3 | The rate lookup uses `ACCT-GROUP-ID`, which is blank in the sample data (the group code `A000000000` sits in `ACCT-ADDR-ZIP`). Every lookup falls back to DEFAULT and SYSOUT prints the warning 100 times. | `golden/*/SYSOUT.txt`; `legacy/README.md` | Data issue, preserve |
| F4 | Every account in TCATBAL gets its cycle credit/debit zeroed, even when there is no interest. Combined with F1, the last account keeps its cycle totals. | `:350-356` | Preserve |
| F5 | `1400-COMPUTE-FEES` is empty. | `:518-520` | Preserve (CR-2) |
| F6 | Silent truncation in edge cases: `WS-MONTHLY-INT`/`TRAN-AMT` S9(9)V99 and `ACCT-CURR-BAL` S9(10)V99 have no `ON SIZE ERROR`. The 6-digit TRAN-ID suffix wraps after 999,999. | `:168`, `:173`, `:352` | Preserve with COBOL modulo semantics, unit-test it |
| F7 | `TRAN-RECORD` is never initialised. The tail of `TRAN-DESC` and FILLER cols 331-350 are spaces only under the default GnuCOBOL dialect (`-std=ibm` would give `x'00'`). | `legacy/README.md` "Known quirks" | Emit spaces to match the golden output |
| F8 | Timestamps come from the wall clock for each transaction. Hundredths are padded with `0000`. | `:613-626` | Injectable clock |
| F9 | Negative balances would produce negative interest (a credit). This is not exercised: all type-03 balances are negative but their rate is 0. | `golden/CBTRN02C/TCATBALF.txt` | Preserve, synthetic test |
| F10 | Sample `cardxref.txt` lines are 36 bytes, not 50. | the explorer's survey | The reader must pad short lines with spaces |

Risk: byte identity depends on exact fixed-point arithmetic and overpunch encoding. Use `BigDecimal` with `RoundingMode.DOWN` at scale 2, plus a high-order modulo. Never use `double`.

## Questions for the approver

1. **F1 last-account defect.** It is preserved here, as the CR requires. Should a separate CR to fix it be raised? It affects the balance of one account per nightly run. *Default: yes, raise CR-3 after this one merges.*
2. **SYSOUT parity.** The CR only requires TRANSACT and ACCTFILE. *Default: also match SYSOUT line for line (AC4), since ops runbooks may grep these messages. It costs little.*
3. **Abend exit code.** *Default: exit 16 with the COBOL message text, the same as the harness's CEE3ABD stub.*
