# CR-1 Spec: `CBACT04C` → Java 21 (behaviour-preserving)

Legacy behaviour, file:line evidence and the defect analysis are in [intent.md](intent.md). This spec covers only
what the Java code must do. Approver questions Q1–Q3 are taken at their defaults: SYSOUT is matched too, files
are bound by DD name, and there are no fixes.

## Invocation (container contract)

`java -jar cbact04c.jar <PARM>`. For example, `2022071800` (10 chars) is the run date, used as the TRAN-ID prefix.

- **Datasets:** one env var per dataset, `DD_<ddname>=<path>`, the same scheme the harness uses for GnuCOBOL.
  If a DD is missing or unreadable, that is treated as an open failure (see Abends).
- **Clock:** `CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss` (16 digits, as in the harness). If it is unset, the system
  clock in UTC is used.
- **SYSOUT:** stdout. **Exit code:** 0 on success, 16 on abend. On abend, stderr gets `CEE3ABD: ABEND U0999`.

## Files

| DD | Copybook | Java handling |
|---|---|---|
| `TCATBALF` | [CVTRA01Y](../../app/cpy/CVTRA01Y.cpy) | Load all records, **sort by bytes 1-17** (the KSDS key), then iterate |
| `XREFFILE` | [CVACT03Y](../../app/cpy/CVACT03Y.cpy) | Map acct id (26-36) → **first** record in file order |
| `ACCTFILE` | [CVACT01Y](../../app/cpy/CVACT01Y.cpy) | Map by acct id (1-11). At close, rewrite the **whole file to the same path** (temp file + atomic rename), in key order |
| `DISCGRP` | [CVTRA02Y](../../app/cpy/CVTRA02Y.cpy) | Map by bytes 1-16 (group + type + cat) |
| `TRANSACT` | [CVTRA05Y](../../app/cpy/CVTRA05Y.cpy) | New file, records in write order |

**Text format.** One record per line. A trailing `\r` is stripped. Short lines are right-padded with spaces to
the LRECL (`cardxref.txt` has 36-byte lines). A line longer than the LRECL is an open error. Output uses LF only,
is exactly LRECL wide, and keeps trailing spaces. Charset is ISO-8859-1, so 1 byte = 1 char.

**Fields read or written** (1-based columns; all numerics are zoned decimal with an overpunched sign, mapped to
`BigDecimal`):

| Record | Field | Cols | PIC |
|---|---|---|---|
| TCATBAL | acct id / type / cat | 1-11 / 12-13 / 14-17 | 9(11) / X(2) / 9(4) |
| TCATBAL | `TRAN-CAT-BAL` | 18-28 | S9(9)V99 |
| ACCT | `ACCT-ID` | 1-11 | 9(11) |
| ACCT | `CURR-BAL` / `CYC-CREDIT` / `CYC-DEBIT` | 13-24 / 83-94 / 95-106 | S9(10)V99 each |
| ACCT | `GROUP-ID` | 117-126 | X(10) |
| XREF | card / acct id | 1-16 / 26-36 | X(16) / 9(11) |
| DISCGRP | `DIS-INT-RATE` | 17-22 | S9(4)V99 |
| TRAN | id, type, cat, source | 1-16, 17-18, 19-22, 23-32 | X(16), X(2), 9(4), X(10) |
| TRAN | desc / amt / merchant id | 33-132 / 133-143 / 144-152 | X(100) / S9(9)V99 / 9(9) |
| TRAN | merch name, city, zip | 153-202, 203-252, 253-262 | spaces |
| TRAN | card / orig-ts / proc-ts / filler | 263-278 / 279-304 / 305-330 / 331-350 | X(16) / X(26) ×2 / spaces |

**Overpunch.** The last digit carries the sign: `{`,`A`-`I` mean +0..9 and `}`,`J`-`R` mean -0..9. A plain
digit in the last position is read as positive. Zero is always written with `{`.

## Processing rules (in order)

1. SYSOUT `START OF EXECUTION OF PROGRAM CBACT04C`. Open/load TCATBALF, XREFFILE, DISCGRP, ACCTFILE, TRANSACT.
2. For each TCATBAL record in key order: print the 50-byte record to SYSOUT.
3. When acct id ≠ last acct id (initial value is 0):
   - If this is not the first record, rewrite the previous account (rule 7).
   - Set total = 0.
   - Look up ACCT, then XREF (abend if either is missing).
4. Rate lookup with key `ACCT-GROUP-ID + type + cat`.
   - On a miss, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then look up
     `DEFAULT` (padded to 10) + type + cat. If that also misses, abend.
5. If rate ≠ 0:
   - `monthly = bal × rate / 1200`, computed exactly, then **truncated toward zero to 2 dp** (`RoundingMode.DOWN`).
   - Then truncate high-order digits to S9(9)V99 (mod 10⁹, no size error).
   - `total += monthly`, truncated to S9(9)V99.
   - Write a TRAN (rule 6).
   - If rate = 0, write nothing.
6. TRAN record:
   - Suffix += 1, as PIC 9(6), so 999999 wraps to 000000.
   - id = PARM + suffix, type `01`, cat `0005`, source `System` (space-padded).
   - desc = `Int. for a/c ` + ACCT-ID, space-padded to 100.
   - amt = monthly, merchant id `000000000`.
   - card from XREF, both timestamps `YYYY-MM-DD-HH.MM.SS.hh0000`, all other bytes spaces.
7. Account rewrite:
   - `CURR-BAL += total`, truncated to S9(10)V99.
   - `CYC-CREDIT = 0`, `CYC-DEBIT = 0`.
   - All other bytes are kept verbatim.
8. At EOF: **no rewrite of the last account (D1)**. Close everything, print `END OF EXECUTION OF PROGRAM
   CBACT04C`, exit 0.

## Abends (exit 16; SYSOUT lines exactly as COBOL)

- Account missing: `ACCOUNT NOT FOUND: <acct11>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`,
  `ABENDING PROGRAM`.
- XREF missing: the same lines, but with `ERROR READING XREF FILE`.
- DEFAULT rate missing: `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM`.
- Open failure: the COBOL message from `CBACT04C.cbl:245-320`, including F5 `ERROR OPENING DALY REJECTS FILE`
  for DISCGRP. Then `FILE STATUS IS: NNNN0035` and `ABENDING PROGRAM`.
- **Partial outputs persist**, like VSAM and QSAM: on abend, the TRANs written so far and the accounts already
  rewritten are flushed to disk.

## Preserved defects / quirks

- **D1:** the last account in key order is never rewritten. Its interest is in TRANSACT but not in the balance.
- **D2:** interest is truncated, not rounded. Negative interest truncates toward zero.
- **D3:** fees are never computed. The `1400-COMPUTE-FEES` no-op is kept as an empty method.
- **F1:** the real group lookup happens first, even though sample data always falls back to `DEFAULT`.
- **F4:** the TRAN-ID suffix silently wraps after 999999. An account with several cards uses the first XREF
  record.
- **F5:** the DISCGRP open-error message has the wrong text (`DALY REJECTS`).

## Acceptance tests (→ intent AC#)

| Test | AC |
|---|---|
| `mvn -q verify` on Java 21; the pom has only JUnit 5 + Surefire | 1 |
| `GoldenTest.stock`: TRANSACT, ACCTFILE, SYSOUT, RC byte-equal to `legacy/golden/CBACT04C/` | 2, 4 |
| `GoldenTest.afterPosting`: inputs from `golden/CBTRN02C`, outputs byte-equal to `golden/CBACT04C-after-CBTRN02C/` | 3, 4 |
| `truncatesNotRounds`: a golden case where truncating and rounding differ, checked in the TRANSACT amount | 5 |
| `fallsBackToDefault`, `zeroRateWritesNothingButUpdatesAccount` | 6 |
| `lastAccountNotRewritten_D1`: acct `00000000050` equals its input; tran `…000050` has amt `0000000187G` | 7 |
| `missingAccount/Xref/DefaultRate → rc 16`, exact SYSOUT, no further processing | 8 |
| `crlfInputAccepted`: stock `tcatbal.txt` (CRLF) gives the stock golden output; output has no `\r` | 9 |
| `negativeBalance`, `suffixWraps`, `firstXrefWins`, `nonDefaultGroup` (F4/F1, unit level) | — |
