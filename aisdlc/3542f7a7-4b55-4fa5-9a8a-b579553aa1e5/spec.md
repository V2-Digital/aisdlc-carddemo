# Spec: CR-1, `CBACT04C` (INTCALC) in Java 21

This spec defines the behaviour of `modernized/cbact04c`. It is a like-for-like port of `app/cbl/CBACT04C.cbl`.
Wherever this text and the golden files disagree, `legacy/golden/` wins.

Facts marked **[probe]** came from runs of the real COBOL under `legacy/harness/run.sh` in this stage, using
crafted `INPUT_DIR` data. They become test expectations.

> **Erratum to intent.md.** `DIS-INT-RATE` is `S9(4)V99`, so `00150{` decodes to **15.00 %** per year, not
> 1.50 %. Check: account 1 has a balance of 1164.87, and 1164.87 × 15 / 1200 = 14.5608, which truncates to
> 14.56. That is golden `0000000145F`. The other evidence in the intent (50/50 truncation matches, total
> 1,279.16, 28 half-up mismatches) holds and was re-verified at 15 %.

## 1. Interface

```
java -jar cbact04c.jar --PARM=2022071800
     --TCATBALF=<in> --XREFFILE=<in> --DISCGRP=<in> --ACCTFILE=<in>
     --TRANSACT=<out> [--ACCTFILE-OUT=<out>] [--CURRENT-DATE=YYYYMMDDHHMMSSss]
```

| Item | Rule |
|---|---|
| `PARM` | 10 characters, which become `PARM-DATE X(10)`. Shorter values are right-padded with spaces and longer ones truncated. Golden only covers the 10-character case. |
| `ACCTFILE` | The I-O dataset. Updated records go to `--ACCTFILE-OUT`. If that is omitted, the input file is replaced in place via a temp file and an atomic rename. |
| `CURRENT-DATE` | Same format as `COB_CURRENT_DATE`. If it is absent, the system clock is used in UTC. In code it is an injectable `Clock`, so tests can freeze it. |
| stdout | SYSOUT: the DISPLAY lines, each ending in LF. |
| stderr | On abend: `CEE3ABD: ABEND U0999`, the same as the harness stub. |
| Exit code | `0` on success, `16` on any abend. If the abend happens before processing starts, no output files are written (see §6, D1). |

## 2. Files and record layouts

The canonical text form for all files: one record per line, LF line endings, fixed width.

On read, each line is processed in this order:
1. A trailing `\r` is stripped.
2. The line is right-padded with spaces to LRECL.
3. A line longer than LRECL is an input error (see §5).

On write, every record is exactly LRECL bytes followed by LF. Offsets below are 1-based columns.

**TCATBALF**: input, LRECL 50, `CVTRA01Y`. Key = cols 1-17. The program processes records in **ascending byte
order of the key**, which is KSDS order. The Java program sorts by key and does not rely on the order of lines
in the file.

| Field | Cols | PIC | Java |
|---|---|---|---|
| TRANCAT-ACCT-ID | 1-11 | 9(11) | String (11) |
| TRANCAT-TYPE-CD | 12-13 | X(2) | String |
| TRANCAT-CD | 14-17 | 9(4) | String (4) |
| TRAN-CAT-BAL | 18-28 | S9(9)V99 zoned | BigDecimal, scale 2 |
| FILLER | 29-50 | X(22) | ignored |

**XREFFILE**: input, LRECL 50, `CVACT03Y`. Primary key = cols 1-16. The lookup uses the **alternate key**
`XREF-ACCT-ID`, cols 26-36. The harness defines the alternate key without `DUPLICATES`, so each account id is
unique (a duplicate is an input error, see §5). `cardxref.txt` lines are 36 bytes and are padded.

| Field | Cols | PIC |
|---|---|---|
| XREF-CARD-NUM | 1-16 | X(16) |
| XREF-CUST-ID | 17-25 | 9(9) |
| XREF-ACCT-ID | 26-36 | 9(11) |
| FILLER | 37-50 | X(14) |

**DISCGRP**: input, LRECL 50, `CVTRA02Y`. Key = cols 1-16 (group 10 + type 2 + category 4).

| Field | Cols | PIC |
|---|---|---|
| DIS-ACCT-GROUP-ID | 1-10 | X(10) |
| DIS-TRAN-TYPE-CD | 11-12 | X(2) |
| DIS-TRAN-CAT-CD | 13-16 | 9(4) |
| DIS-INT-RATE | 17-22 | S9(4)V99 zoned → BigDecimal, scale 2 |
| FILLER | 23-50 | X(28) |

**ACCTFILE**: I-O, LRECL 300, `CVACT01Y`. Key = cols 1-11. Output is written in ascending key order, as the
KSDS unload is. Only the three fields marked ✎ are ever changed. All other bytes are copied through unchanged.

| Field | Cols | PIC |
|---|---|---|
| ACCT-ID | 1-11 | 9(11) |
| ACCT-ACTIVE-STATUS | 12 | X |
| ACCT-CURR-BAL ✎ | 13-24 | S9(10)V99 |
| ACCT-CREDIT-LIMIT / CASH-CREDIT-LIMIT | 25-36 / 37-48 | S9(10)V99 |
| OPEN / EXPIRATION / REISSUE date | 49-58 / 59-68 / 69-78 | X(10) |
| ACCT-CURR-CYC-CREDIT ✎ | 79-90 | S9(10)V99 |
| ACCT-CURR-CYC-DEBIT ✎ | 91-102 | S9(10)V99 |
| ACCT-ADDR-ZIP | 103-112 | X(10) (holds `A000000000` in the sample data) |
| ACCT-GROUP-ID | 113-122 | X(10) (blank in the sample data) |
| FILLER | 123-300 | X(178) |

**TRANSACT**: output, sequential, LRECL 350, `CVTRA05Y`. Records are written in write order.

| Field | Cols | Value written |
|---|---|---|
| TRAN-ID | 1-16 | PARM-DATE (10) + suffix `9(6)` |
| TRAN-TYPE-CD | 17-18 | `01` |
| TRAN-CAT-CD | 19-22 | `0005` |
| TRAN-SOURCE | 23-32 | `System` + 4 spaces |
| TRAN-DESC | 33-132 | `Int. for a/c ` + ACCT-ID (from the account record), padded with spaces to 100 |
| TRAN-AMT | 133-143 | monthly interest, S9(9)V99 zoned |
| TRAN-MERCHANT-ID | 144-152 | `000000000` |
| MERCHANT-NAME / CITY / ZIP | 153-202 / 203-252 / 253-262 | spaces |
| TRAN-CARD-NUM | 263-278 | XREF-CARD-NUM |
| TRAN-ORIG-TS / TRAN-PROC-TS | 279-304 / 305-330 | `YYYY-MM-DD-HH.MM.SS.hh0000` (hh = hundredths), taken from the clock **per transaction** |
| FILLER | 331-350 | spaces |

**Zoned decimal (overpunch).** All digits except the last are `0`-`9`. The last character carries both the
final digit and the sign: `{`/`A`-`I` = +0..9 and `}`/`J`-`R` = -0..9. Values are decoded exactly. On encode:
- Zero is always written as `…{`. Negative zero is never written **[probe]**: a result of -0.002 truncates
  to 0 and is written as `0000000000{`.
- The value is first reduced to the field's digit count, as in §3, rule R0.

## 3. Processing rules, in order

**R0, arithmetic.** All arithmetic uses `BigDecimal` or `long` cents. `double` is never used.
- Storing a result into an `S9(n)V99` field **truncates toward zero** to 2 decimal places.
- It also **drops high-order digits** (`cents mod 10^(n+2)`, keeping the sign). There is no SIZE ERROR.
- Each COMPUTE, ADD or MOVE stores its result separately.

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`.
2. Open the files in this order: TCATBALF, XREFFILE, DISCGRP, ACCTFILE, TRANSACT. If an input file is
   missing, that is status `35`. Print the paragraph's message, then the IO status line (§4), then abend.
   The messages are:
   - `ERROR OPENING TRANSACTION CATEGORY BALANCE`
   - `ERROR OPENING CROSS REF FILE` + the 2-character status, on the same line
   - `ERROR OPENING DALY REJECTS FILE` (sic, used for DISCGRP)
   - `ERROR OPENING ACCOUNT MASTER FILE`
   - `ERROR OPENING TRANSACTION FILE`
3. Set the state: `lastAcct` = 11 spaces, `firstTime` = true, `suffix` = 0 (`9(6)`), `totalInt` = 0 (`S9(9)V99`).
4. For each TCATBAL record in key order:
   1. Print the 50-byte record as it was read (after padding).
   2. **Account break**, when `acctId ≠ lastAcct`:
      - If this is not the first record, run R6 for the *previous* account. Otherwise clear `firstTime`.
      - Set `totalInt = 0` and `lastAcct = acctId`.
      - Look up ACCTFILE by `acctId`. If it is missing, print `ACCOUNT NOT FOUND: <acctId>`, then
        `ERROR READING ACCOUNT FILE`, then `FILE STATUS IS: NNNN0023`, then abend.
      - Look up XREF by the alternate key `acctId`. If it is missing, print `ACCOUNT NOT FOUND: <acctId>`,
        then `ERROR READING XREF FILE`, then `FILE STATUS IS: NNNN0023`, then abend.
   3. **Rate lookup** with key = `ACCT-GROUP-ID` (10) + type + category:
      - If the key is found, that row's rate is used.
      - If it is missing, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then
        retry with the group `DEFAULT` + 3 spaces. If that is also missing, print
        `ERROR READING DEFAULT DISCLOSURE GROUP`, then `FILE STATUS IS: NNNN0023`, then abend.
   4. If `rate ≠ 0` (numeric compare, so `-0` counts as 0):
      - `monthly = store_S9(9)V99( bal × rate / 1200 )`, using exact intermediates (R0).
      - `totalInt = store_S9(9)V99( totalInt + monthly )`.
      - `suffix = (suffix + 1) mod 1 000 000`.
      - Write a TRANSACT record (§2) using the xref card currently held.
      - Fees: nothing (F4).
5. At EOF, close the files and print `END OF EXECUTION OF PROGRAM CBACT04C`. Exit 0.
   **The last account is not updated (F1).**
6. **R6, update the account:**
   - `ACCT-CURR-BAL = store_S9(10)V99( bal + totalInt )`.
   - Set `ACCT-CURR-CYC-CREDIT` and `ACCT-CURR-CYC-DEBIT` to `+0`.
   - Rewrite the record.
   - This runs on every account break, even if every rate for that account was 0. In that case the cycle
     fields are still zeroed.
7. **Abend:** print `ABENDING PROGRAM`, write `CEE3ABD: ABEND U0999` to stderr, exit 16.

## 4. IO status line

`FILE STATUS IS: NNNN` + 4 characters. For a numeric status not starting with `9`, the 4 characters are `00`
+ the status (for example `NNNN0023`). The binary form for `9x` statuses is not reachable from Java and is not
emulated.

## 5. Edge cases

| Case | Behaviour |
|---|---|
| Empty TCATBALF | Only the START and END lines are printed. TRANSACT is empty, ACCTFILE is unchanged, RC 0. |
| Only one account in TCATBALF | No account is rewritten (F1). |
| `bal × rate / 1200` is negative | Truncated toward zero **[probe]**: -123.49 × 15 / 1200 = -1.543625 → `0000000015M` (-1.54). |
| Monthly interest above 9 integer digits | High-order digits dropped **[probe]**: 999,999,999.99 × 9999.99 / 1200 = 8,333,324,999.91… → `3333249999A` (333,324,999.91). |
| Account total above 9 integer digits | Each ADD drops high-order digits **[probe]**: 4 × 333,324,999.91 accumulates to 333,299,999.64, and the account ends at 333,300,193.64 (`03333001936D`). |
| Suffix past 999,999 | Wraps to `000000`. This can produce duplicate TRAN-IDs. |
| CRLF lines, or short lines (xref is 36 bytes) | CR stripped, line padded with spaces to LRECL. |
| TCATBALF not sorted | Sorted by key before processing, as the KSDS would be. |
| Duplicate primary key in any keyed input, or a duplicate xref account id | Input error. The harness load would fail, so there is no COBOL behaviour to preserve. Java prints `INPUT ERROR: <DD> duplicate key <k>` and exits 16. |
| Line longer than LRECL, or non-numeric zoned data | Input error, handled the same way: message and exit 16. Legacy behaviour is undefined here. |
| `ACCT-GROUP-ID` blank | Falls back to `DEFAULT` with the warning lines (F3). This happens on every sample row. |

## 6. Preserved defects. Do not fix in CR-1.

| ID | Defect | Pinned by |
|---|---|---|
| F1 | The last account's interest is never written back, and its cycle fields are not zeroed. | Golden after-scenario: account `00000000050` ACCTFILE record equals `golden/CBTRN02C`, while TRAN `2022071800000050` charges 18.77. Also a dedicated test. |
| F2 | Interest is truncated, not rounded. | 50/50 golden amounts. Test: 100.40 × 15 / 1200 = 1.255 → **1.25** (half-up would give 1.26); -1.543625 → -1.54. |
| F3 | The group id is blank, so every row falls back to `DEFAULT` with 2 warning lines. | SYSOUT golden: 152 and 302 lines. |
| F4 | `1400-COMPUTE-FEES` is empty. | Test: no records other than type 01 / category 0005. |
| F5 | Silent overflow: high-order digits are lost in the monthly amount, the total and the balance, and the suffix wraps. | Probe-derived tests (§5). |
| F6 | TCATBAL balances are not cleared, so a re-run charges interest again. | Test: running twice on the same inputs charges the same amounts twice. |
| F7 | The DISCGRP open error says `DALY REJECTS`. | Test on a missing DISCGRP file. |

**D1, needs a decision. Abend atomicity.** VSAM keeps REWRITEs and TRANSACT records written before an abend.
The harness discards all outputs on abend, so golden does not cover this case.

*Proposed:* on abend, Java writes **no** output files, and the in-place ACCTFILE is left untouched. This
differs from z/OS behaviour after a mid-run abend, but makes a rerun safe.

## 7. Acceptance tests, mapped to intent AC

| AC | Test |
|---|---|
| 1 | `mvn -B verify` on Java 21. The POM declares only JUnit 5 and Surefire (jar plugin config only for the manifest). |
| 2, 4 | `GoldenEquivalenceTest.stock`: inputs from `app/data/ASCII`, PARM `2022071800`, clock `2022071800000000`. TRANSACT (50), ACCTFILE (50) and SYSOUT (152) are byte-equal to golden, and RC = `RC.txt` (0). |
| 3, 4 | `GoldenEquivalenceTest.afterPosting`: same as above, with TCATBALF and ACCTFILE taken from `golden/CBTRN02C`. TRANSACT (50), ACCTFILE (50) and SYSOUT (302) are byte-equal, RC 0. |
| 5 | `CharacterizationTest` covers F1-F4, plus F5-F7 and the probe values in §5. |
| 6 | `AbendTest` covers a missing account, a missing xref and a missing DEFAULT row. Each test checks the exact SYSOUT tail shown in §3, exit 16, and that no output files were written. |
| 7 | `InputFormatTest`: 36-byte xref lines and CRLF tcatbal both handled; the stock run itself covers both. |
| 8 | Code review: the code uses no `double` or `float`. A test greps the sources for them. |
| 9 | `legacy/harness/golden.sh --check` passes, and `git diff --stat legacy/ .github/` is empty. |
