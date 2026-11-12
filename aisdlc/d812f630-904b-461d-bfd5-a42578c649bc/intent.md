# CR-2 Intent — Over-limit fee in the interest batch (`CBACT04C`)

## Problem
Accounts that run past their credit limit get no penalty from the nightly interest run. The job has an empty fee step (`1400-COMPUTE-FEES`), so the over-limit fee Product wants is never charged.

## Outcome
Each interest run charges a **25.00 over-limit fee**, at most **once per account**, to every account whose current balance is above its credit limit. The fee appears in the same transaction output as interest and is added to the account balance at the same point. Accounts within their limit see exactly the same results as today. The change stays in COBOL; there is no migration.

## What the legacy code does today

**Job:** `app/jcl/INTCALC.jcl`, `EXEC PGM=CBACT04C,PARM='2022071800'` (the PARM date becomes the prefix of each transaction ID).

| DD | Dataset | Org / access | LRECL | Key | Copybook | Use |
|---|---|---|---|---|---|---|
| TCATBALF | TCATBALF.VSAM.KSDS | KSDS, sequential | 50 | acct(11)+type(2)+cat(4) | `CVTRA01Y` | driver, input |
| XREFFILE | CARDXREF.VSAM.KSDS (+AIX) | KSDS, random by alt key acct-id | 50 | card(16); AIX acct(11) | `CVACT03Y` | input |
| ACCTFILE | ACCTDATA.VSAM.KSDS | KSDS, random, **I-O** | 300 | acct-id(11) | `CVACT01Y` | read + REWRITE |
| DISCGRP | DISCGRP.VSAM.KSDS | KSDS, random | 50 | group(10)+type(2)+cat(4) | `CVTRA02Y` | interest rate |
| TRANSACT | SYSTRAN(+1) GDG | sequential, OUTPUT | 350 | — | `CVTRA05Y` | new transactions |

`CBACT04C.cbl` references below:

1. **Control-break loop over TCATBAL**, ordered by account (`:188-222`). When the account ID changes (`:194`), it rewrites the previous account (`:196`), resets `WS-TOTAL-INT` (`:200`), reads the account (`:203`, `1100-GET-ACCT-DATA :372`) and reads the card xref by account (`:205`, `:393`).
2. **Rate lookup** for each category balance (`:210-213`, `1200-GET-INTEREST-RATE :415`). If the group key isn't found (status 23), it retries with group `DEFAULT` (`:436-438`).
3. **If the rate is non-zero** (`:214`), it computes `TRAN-CAT-BAL * DIS-INT-RATE / 1200` (`:464-465`), adds that to `WS-TOTAL-INT` (`:467`), writes one transaction (`1300-B-WRITE-TX :473`), and **then performs `1400-COMPUTE-FEES` (`:216`)**.
4. **Interest transaction layout** (`:474-498`):
   - ID = PARM-DATE + 6-digit running suffix `WS-TRANID-SUFFIX` (`:474-479`)
   - type `01`, category `05`, source `System`
   - description `Int. for a/c <ACCT-ID>`, built with STRING (`:485-488`)
   - amount, card number from the xref, and the timestamp in both orig-ts and proc-ts
5. **`1400-COMPUTE-FEES` is empty** (`:518-520`, just `EXIT.`).
6. **Account update** (`1050-UPDATE-ACCOUNT :350`): `ACCT-CURR-BAL += WS-TOTAL-INT` (`:352`), both cycle credit and cycle debit set to 0 (`:353-354`), then REWRITE (`:356`).
7. Balance fields: `ACCT-CURR-BAL` and `ACCT-CREDIT-LIMIT` are both `S9(10)V99` (`CVACT01Y.cpy:7-8`). `TRAN-AMT` is `S9(09)V99` (`CVTRA05Y.cpy:10`).

**Harness run (baseline, today):** `legacy/harness/run.sh CBACT04C` gives RC 0, TRANSACT 50, ACCTFILE 50 and SYSOUT 152 lines, **byte-identical to `legacy/golden/CBACT04C`**. Each of the 50 accounts has exactly 1 TCATBAL record (category `01/0001`) in both stock and post-CBTRN02C data.

**Over-limit accounts in existing data: none.** I checked balance against limit in `acctdata.txt` (highest balance 843.00), `golden/CBTRN02C/ACCTFILE.txt` (4212.54) and `golden/CBACT04C-after-CBTRN02C/ACCTFILE.txt` (4263.56). All three have 0 accounts over limit, so the golden outputs are unaffected by the rule itself.

**`trancatg.txt`**: 18 records, 60 bytes + CRLF, layout type(2)+cat(4)+desc(50)+filler `0000`(4). The last interest category is `010005 Interest Amount`. CBACT04C and the harness do not read this file. It is loaded by `app/jcl/TRANCATG.jcl` and used by the report program `CBTRN03C`.

## Scope
**In**
- Implement `1400-COMPUTE-FEES` (or an equivalent once-per-account step) in `app/cbl/CBACT04C.cbl`:
  - condition `ACCT-CURR-BAL > ACCT-CREDIT-LIMIT`, fee 25.00
  - transaction: type `01`, category `0006`, source `System`, description `Over-limit fee for a/c <acct id>`
  - same ID scheme, card number and timestamps as interest
  - the fee is added to the balance in `1050-UPDATE-ACCOUNT`
- Add `010006Over-limit Fee` (padded to 60 bytes, CRLF, `0000` filler) to `app/data/ASCII/trancatg.txt`, right after `010005`.
- A new harness test scenario (input files passed through `INPUT_DIR`) plus expected outputs, kept outside `legacy/golden/`.

**Out**
- Java migration of CBACT04C.
- Fixing the last-account write-back bug (see R1).
- Fee amount configuration (e.g. a DISCGRP or parameter-driven fee), fee waivers, and CBTRN02C / online (CICS) programs.
- EBCDIC data files and `TRANCATG` VSAM reload JCL. Only the ASCII sample file changes.

## Acceptance criteria
1. `legacy/harness/golden.sh --check` passes: all 3 scenarios are byte-identical to `legacy/golden/`, RC 0.
2. A new scenario with **≥2 over-limit accounts** is added. At least one of them has **≥2 TCATBAL category records**.
3. In that scenario, each over-limit account gets **exactly one** TRANSACT record with type `01`, cat `0006`, source `System`, amount `+25.00` (`0000000250{`) and desc `Over-limit fee for a/c <11-digit id>`, with the rest of the description blank.
4. The fee's card number is the account's xref card, and its timestamps match the interest records. Its ID follows the same `PARM-DATE + suffix` sequence with no duplicate IDs.
5. Each over-limit account's rewritten `ACCT-CURR-BAL` = original balance + sum of its interest + 25.00.
6. Accounts **at** their limit (balance = limit) and below it are not charged. The scenario includes a balance = limit case.
7. Within-limit accounts in the new scenario produce the same records they would without the change. Only transaction-ID suffixes after a fee shift, because each fee uses one sequence number.
8. Interest descriptions written after a fee have no leftover text from the longer fee description (see R2).
9. `trancatg.txt` has 19 records in key order, and the new line matches the existing format byte for byte (60 bytes + CRLF).

## Risks and findings
- **R1 — Existing defect: the last account is never rewritten.** After EOF, the loop condition is tested before the body, so the `ELSE PERFORM 1050-UPDATE-ACCOUNT` at `:218-220` never runs. Evidence (also in `legacy/README.md`):
  - `golden/CBACT04C-after-CBTRN02C/TRANSACT.txt` charges account `00000000050` interest of 18.77.
  - Its ACCTFILE record is still byte-identical to the CBTRN02C input (balance `00000019458G` = 1945.87).
  - Impact on CR-2: if the **last** account is over limit, the fee transaction is written but the balance never gets it. Fixing the bug would change the golden ACCTFILE for account 50 and break AC1, so it stays out of scope. The test scenario should keep its over-limit accounts away from the last account, or explicitly document the effect on it.
- **R2 — Leftover description text.** `TRAN-RECORD` is never initialised, and STRING only overwrites as many bytes as it writes (`:485-488`). The fee description is 34 bytes, the interest one 24. Without a `MOVE SPACES TO TRAN-DESC` first, an interest transaction written after a fee would read `Int. for a/c 00000000002` followed by stale bytes 25-34. Clearing first doesn't change golden output, which is already spaces there.
- **R3 — The hook sits in the wrong place for "once per account".** `1400-COMPUTE-FEES` runs once **per category record** and **only if the rate is ≠ 0** (`:214-216`). Called as-is, it would charge an account with several categories several times, and charge nothing when every rate is 0 (e.g. the `ZEROAPR` group, or categories 02/03/07 at rate 0). The design needs a once-per-account guard, or should evaluate the fee at the account break.
- **R4 — Transaction ID sequence.** Each fee uses one `WS-TRANID-SUFFIX` value, so the IDs of everything written after it shift. This is harmless for golden (no fees there), but it is a visible change for downstream ID consumers.
- **R5 — Coverage.** Only accounts that have at least one TCATBAL record are visited. An over-limit account with no category balance gets no fee.
- **R6 — Reference data.** The new category isn't used by CBACT04C at runtime. The production `TRANCATG` KSDS needs a reload (`TRANCATG.jcl`) for reports (`CBTRN03C`) to show the description.

## Questions for the approver
1. **Which balance is tested: before or after this run's interest?** *Default:* the balance **as read at the start of the run** (before interest). This matches "current balance" and is deterministic.
2. **Charge accounts whose interest rate is 0?** *Default:* **yes**. The rule is about balance and limit, not rate, so evaluate it once per account regardless of rate (R3).
3. **Last-account bug (R1).** *Default:* **don't fix in CR-2** (it would break AC1). Raise it as a separate CR, and keep the new scenario's over-limit accounts off the last record.
4. **Fee order within an account.** *Default:* write the fee **once, when the account is first read at the control break, before that account's interest transactions**. One code path covers both the rate-0 and rate-non-zero cases.
