# CR-2 Spec: over-limit fee in `CBACT04C`

This change adds a fee to the existing COBOL program. It is not a rewrite. Everything not listed under **Changes** stays exactly as it is, including the known defects (§5).

## 1. Inputs / outputs (unchanged files, unchanged layouts)

| DD | Org | LRECL | Key / order | Copybook | Change |
|---|---|---|---|---|---|
| `TCATBALF` | KSDS, seq read | 50 | acct 9(11) + type X(2) + cat 9(4), ascending | `CVTRA01Y` | none |
| `XREFFILE` | KSDS, random by AIX acct 9(11) @26 | 50 | card X(16) | `CVACT03Y` | none |
| `ACCTFILE` | KSDS, random, I-O | 300 | acct 9(11) | `CVACT01Y` | balance also gets the fee |
| `DISCGRP` | KSDS, random | 50 | group X(10) + type X(2) + cat 9(4) | `CVTRA02Y` | none |
| `TRANSACT` | seq, OUTPUT, write order | 350 | – | `CVTRA05Y` | fee records added |
| `trancatg.txt` | sample data (KSDS via `TRANCATG.jcl`) | 60 + CRLF | type+cat, ascending | `CVTRA04Y` | +1 row |

### Account fields used (`CVACT01Y`, 1-based cols)

| Field | Cols | PIC | Use |
|---|---|---|---|
| `ACCT-ID` | 1-11 | 9(11) | fee description |
| `ACCT-CURR-BAL` | 13-24 | S9(10)V99 | **compared**, then updated |
| `ACCT-CREDIT-LIMIT` | 25-36 | S9(10)V99 | **compared** (new read) |
| `ACCT-CURR-CYC-CREDIT` / `-DEBIT` | 79-90 / 91-102 | S9(10)V99 | set to 0 (unchanged) |
| `ACCT-GROUP-ID` | 113-122 | X(10) | DISCGRP key (unchanged) |

### Fee transaction record (`CVTRA05Y`, 350 bytes)

| Field | Cols | PIC | Value |
|---|---|---|---|
| `TRAN-ID` | 1-16 | X(16) | `PARM-DATE` + `WS-TRANID-SUFFIX` 9(6). Same run-wide counter as interest |
| `TRAN-TYPE-CD` | 17-18 | X(2) | `01` |
| `TRAN-CAT-CD` | 19-22 | 9(4) | `0006` |
| `TRAN-SOURCE` | 23-32 | X(10) | `System` + 4 spaces |
| `TRAN-DESC` | 33-132 | X(100) | `Over-limit fee for a/c ` + `ACCT-ID` (34 chars), then spaces |
| `TRAN-AMT` | 133-143 | S9(9)V99 | +25.00 = `0000000250{` |
| `TRAN-MERCHANT-ID` | 144-152 | 9(9) | `000000000` |
| `TRAN-MERCHANT-NAME/CITY/ZIP` | 153-262 | X(50/50/10) | spaces |
| `TRAN-CARD-NUM` | 263-278 | X(16) | `XREF-CARD-NUM` of the account |
| `TRAN-ORIG-TS` / `TRAN-PROC-TS` | 279-304 / 305-330 | X(26) | `Z-GET-DB2-FORMAT-TIMESTAMP` (frozen: `2022-07-18-00.00.00.000000`) |
| FILLER | 331-350 | X(20) | spaces (as today) |

### `trancatg.txt` row

`010006` + `Over-limit Fee` padded with spaces to 50 + `0000` + CRLF (62 bytes on disk, the same as the other rows). It goes **after `010005`** (line 6), because `TRANCATG.jcl` REPROs it into a KSDS and the rows must be in key order. The 18 existing rows are byte-identical. They only move down by one line.

## 2. Processing rules (in order)

Steps 1-3 and 5-6 are today's logic. **New** marks what this CR adds.

1. Open the files. Read TCATBAL in key order (`:188-222`).
2. **On an account break** (`TRANCAT-ACCT-ID ≠ WS-LAST-ACCT-NUM`):
   1. If this is not the first account, run `1050-UPDATE-ACCOUNT` for the previous account.
   2. `MOVE 0 TO WS-TOTAL-INT` and, **new**, `MOVE 0 TO WS-TOTAL-FEE`.
   3. Read the account (`1100`) and the xref (`1110`).
   4. **New:** run `1400-COMPUTE-FEES` once:
      - if `ACCT-CURR-BAL > ACCT-CREDIT-LIMIT` (signed numeric compare, **strictly** greater), then `MOVE WS-OVERLIMIT-FEE (25.00) TO WS-TOTAL-FEE` and write one fee record (`1400-B-WRITE-FEE-TX`);
      - otherwise do nothing.
3. For each category record: look up the rate (a miss on status 23 falls back to `DEFAULT`). If the rate ≠ 0, run `1300-COMPUTE-INTEREST`. **The per-category `PERFORM 1400-COMPUTE-FEES` at `:216` is removed.**
4. `1300-COMPUTE-INTEREST` is unchanged: `WS-MONTHLY-INT = TRAN-CAT-BAL × DIS-INT-RATE / 1200`, truncated to 2 dp with no ROUNDED. It is added to `WS-TOTAL-INT`, and one interest record is written.
5. `1050-UPDATE-ACCOUNT`: `ADD WS-TOTAL-INT TO ACCT-CURR-BAL`, then, **new**, `ADD WS-TOTAL-FEE TO ACCT-CURR-BAL`. Then cycle credit and debit are set to 0, and the record is REWRITten. The error path is unchanged (abend U0999 → RC 16).
6. At EOF, close the files. Nothing is rewritten for the last account (defect D1, preserved).

### Why this placement

- The fee is decided at the account break, from the balance **as read**, before this run's interest. TCATBAL is a KSDS keyed with acct as the leading key part, so all of an account's records are contiguous. One break per account therefore gives exactly one fee, however many categories the account has and whatever their rates.
- Each account's fee record comes **before** that account's interest records in `TRANSACT`.

### New working storage (`WS-MISC-VARS`)

| Name | PIC | Value |
|---|---|---|
| `WS-TOTAL-FEE` | S9(09)V99 | reset per account |
| `WS-OVERLIMIT-FEE` | S9(09)V99 | `VALUE 25.00` |

### Description hygiene

`1300-B-WRITE-TX` and `1400-B-WRITE-FEE-TX` both `MOVE SPACES TO TRAN-DESC` before the `STRING`. Without that, an interest record written after a fee would keep `ee for a/c` debris in cols 57-66. Today the field is always spaces, so golden output doesn't change.

## 3. Edge cases

| Case | Behaviour |
|---|---|
| Balance = limit | No fee (strict `>`) |
| Balance < limit, or negative (credit) balance | No fee |
| Over limit, every rate 0 (e.g. group `ZEROAPR`) | Fee is charged; there is no interest record |
| Over limit, several TCATBAL categories | Exactly one fee |
| Pushed over limit only by tonight's interest | No fee tonight. It would be charged on the next run |
| Inactive account (`ACCT-ACTIVE-STATUS ≠ 'Y'`) | Charged (Q2 default). No stock account is inactive |
| Account with no TCATBAL record | Not visited, so no fee (out of scope) |
| Negative credit limit (bad data) | Charged if balance > limit. No validation (same as today: no checks) |
| Balance overflow on ADD (S9(10)V99) | Same as interest today: no `ON SIZE ERROR`, so it truncates. Not reachable with realistic data |
| Account / xref not found | Unchanged: `1100`/`1110` abend before any fee is written |
| Over-limit **last** account | Fee record is written, but the balance is **not** updated (D1) |

## 4. Changes vs. today's output

- An in-limit account gets the same records with the same content. Only `TRAN-ID` suffixes after each fee record shift by +1 (shared counter).
- `SYSOUT`: no new DISPLAY lines.
- `RC` is unchanged (0).

## 5. Preserved defects (kept on purpose; fix deliberately later)

| # | Defect | Effect with this CR |
|---|---|---|
| D1 | Last account never rewritten: the `ELSE PERFORM 1050` at `:219-220` is unreachable | An over-limit last account gets a fee **transaction** without the balance change. It is the same as interest today. Fixing it changes `golden/CBACT04C-after-CBTRN02C/ACCTFILE.txt`, so it needs a separate CR |
| D2 | Interest is truncated, not rounded | Unchanged |
| D3 | `ACCT-GROUP-ID` is blank in the sample data, so every lookup falls back to `DEFAULT` with SYSOUT noise | Unchanged |
| D4 | Fee is not posted to TCATBAL (nor is interest) | As per Q3 default |

## 6. Acceptance tests → intent AC

Scenario `legacy/scenarios/CBACT04C-overlimit` (see plan). Its input is `golden/CBTRN02C` `ACCTFILE` + `TCATBALF`, with three account rows edited:

| Acct | Edit | Bal / limit | Categories / rates | Expected fee | Expected new balance (cols 13-24) |
|---|---|---|---|---|---|
| `00000000001` | limit → 1000.00 | 1288.10 > 1000.00 | 2 (`01/0001` 15%, `03/0001` 0%) | 1 | 1288.10 + 14.56 + 25.00 = 1327.66 `00000013276F` |
| `00000000002` | limit → 1734.97 | 1734.97 = 1734.97 | 2 | 0 | 1764.21 `00000017642A` (= golden) |
| `00000000003` | limit → 1500.00, group → `ZEROAPR` | 1857.65 > 1500.00 | 2, both 0% | 1 | 1857.65 + 25.00 = 1882.65 `00000018826E` |

Expected `TRANSACT` = **51** records:
- `…000001` fee a/c 1
- `…000002` interest a/c 1 (14.56)
- `…000003` interest a/c 2
- `…000004` fee a/c 3
- `…000005`-`…000051` interest a/c 4-50

`SYSOUT` = 298 lines. That is the golden 302 minus 4 `DEFAULT` fallback lines, because a/c 3 now hits `ZEROAPR`. RC = `0`.

| AC | Test |
|---|---|
| 1 | `legacy/harness/golden.sh --check` passes, all 3 scenarios byte-identical |
| 2 | `check.sh`: exactly 2 records with cat `0006`, one for a/c 1 and one for a/c 3. a/c 1 has 2 TCATBAL records |
| 3 | `check.sh`: each fee record has cols 17-22 `010006`, `System`, the exact desc padded to 100, `0000000250{`, card = xref card of the account, and IDs unique and sequential `…000001-…000051` |
| 4 | `check.sh`: ACCTFILE balances for a/c 1 and 3 as in the table. Cycle credit and debit are `00000000000{` |
| 5 | a/c 2 (equal) has no fee, and its balance equals golden |
| 6 | a/c 3 (all rates 0) has a fee and no interest record |
| 7 | `check.sh`: TRANSACT without fee records, compared on cols 17-350, equals `golden/CBACT04C-after-CBTRN02C/TRANSACT.txt` minus a/c 3's line. ACCTFILE for a/c 4-50 is byte-identical to that golden |
| 8 | `trancatg.txt`: 19 lines, all 62 bytes with CRLF. Line 6 is the new row, and `git diff` shows exactly one added line |
| 9 | `cobc -fsign=EBCDIC -fassign-clause=ibm` compiles with no warnings, and the scenario's RC is `0` |
