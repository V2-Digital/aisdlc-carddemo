# CR-1 Spec — `CBACT04C` (INTCALC) in Java 21

This is a **behaviour-preserving** rewrite: legacy defects are kept on purpose (see §5).
Line refs are to `app/cbl/CBACT04C.cbl`. Columns are 1-based.
"Golden" means `legacy/golden/`, the reference outputs of the original COBOL.

## 1. Invocation

```
java -cp target/classes cbact04c.Main --parm 2022071800 \
  --tcatbalf F --xreffile F --discgrp F --acctfile F --transact F \
  [--acctfile-out F] [--clock YYYYMMDDHHMMSSss]
```

| Input | Meaning | Default |
|---|---|---|
| `--parm` | `PARM-DATE`, 10 chars, used only as the tran-id prefix. Right-padded with spaces or truncated to 10, like the COBOL `PIC X(10)`. | required |
| `--tcatbalf`, `--xreffile`, `--discgrp` | input datasets, canonical text | required |
| `--acctfile` | account master input | required |
| `--acctfile-out` | updated account master | same path as `--acctfile`, written to a temp file then renamed (mirrors I-O REWRITE) |
| `--transact` | new interest transactions, OUTPUT (truncated) | required |
| `--clock` | frozen `FUNCTION CURRENT-DATE`, 16 digits (same as the harness's `COB_CURRENT_DATE`) | the `COB_CURRENT_DATE` env var if set, else the system clock in the default zone |

- **SYSOUT:** stdout, one `DISPLAY` per line, LF line endings.
- **Abend:** stderr gets `CEE3ABD: ABEND U0999 TIMING=0` and the exit code is **16**.
- **Normal end:** exit code **0**.
- **CLI usage error:** exit 2. Java only; not legacy behaviour.

## 2. Files

### Canonical text form (all files)

- One record per line, LF terminated.
- On read: strip a trailing `\r`. Right-pad short lines with spaces to the LRECL. `cardxref.txt` lines are 36 bytes; `tcatbal.txt` is CRLF. A line longer than the LRECL is a load error.
- On write: exactly LRECL bytes per line, then `\n`. Trailing spaces are kept.
- Charset: ISO-8859-1, one byte per char. Bytes pass through unchanged.

Every input KSDS is loaded into an in-memory `TreeMap` on its key. The harness loader (`cdfiles`) rejects duplicate primary or alternate keys. Java does the same: it prints `LOAD ERROR: duplicate key <k> in <DD>` to stderr and exits **3**, the harness load-failure code.

### Zoned decimal `S9(n)V99` (overpunch, `-fsign=EBCDIC`)

- The last byte holds the last digit and the sign:
  - `{`=+0, `A`–`I`=+1..9
  - `}`=−0, `J`–`R`=−1..9
  - a plain digit `0`–`9` reads as positive.
- Decoded with `BigDecimal` and scale 2. **No `double` anywhere.**
- On write, a value of zero always encodes as `{` (verified on GnuCOBOL: −0.00 is written `0000000000{`).
- A non-digit in the leading positions is a data error → abend (exit 16). See §4 E9.

### TCATBALF — input KSDS, LRECL 50, read sequentially in key order

| Field | Cols | Len | PIC | Java |
|---|---|---|---|---|
| key: `TRANCAT-ACCT-ID` | 1-11 | 11 | 9(11) | String |
| key: `TRANCAT-TYPE-CD` | 12-13 | 2 | X(2) | String |
| key: `TRANCAT-CD` | 14-17 | 4 | 9(4) | String |
| `TRAN-CAT-BAL` | 18-28 | 11 | S9(9)V99 | BigDecimal |
| filler | 29-50 | 22 | X | — |

The key is cols 1-17, compared as a byte string. Records are processed in ascending key order, whatever order the file is in.

### XREFFILE — input KSDS, LRECL 50, random read by alternate key

| Field | Cols | Len | PIC |
|---|---|---|---|
| `XREF-CARD-NUM` (primary key) | 1-16 | 16 | X(16) |
| `XREF-CUST-ID` | 17-25 | 9 | 9(9) |
| `XREF-ACCT-ID` (**AIX, used**) | 26-36 | 11 | 9(11) |
| filler | 37-50 | 14 | X |

### ACCTFILE — KSDS, LRECL 300, random READ + REWRITE (I-O)

| Field | Cols | Len | PIC | Use |
|---|---|---|---|---|
| `ACCT-ID` (key) | 1-11 | 11 | 9(11) | key; also in the tran desc |
| `ACTIVE-STATUS` | 12 | 1 | X | pass-through |
| `ACCT-CURR-BAL` | 13-24 | 12 | S9(10)V99 | `+= total interest` |
| credit limit, cash limit | 25-48 | 24 | S9(10)V99 ×2 | pass-through |
| open / expiry / reissue dates | 49-78 | 30 | X(10) ×3 | pass-through |
| `ACCT-CURR-CYC-CREDIT` | 79-90 | 12 | S9(10)V99 | set to 0 |
| `ACCT-CURR-CYC-DEBIT` | 91-102 | 12 | S9(10)V99 | set to 0 |
| `ACCT-ADDR-ZIP` | 103-112 | 10 | X | pass-through |
| `ACCT-GROUP-ID` | 113-122 | 10 | X | DISCGRP key part |
| filler | 123-300 | 178 | X | pass-through |

- **Output:** every input record, in key order.
- Only bytes 13-24 and 79-102 of rewritten records change; all other bytes are copied through.
- Accounts not rewritten are byte-identical to the input.

### DISCGRP — input KSDS, LRECL 50, random read

| Field | Cols | Len | PIC |
|---|---|---|---|
| key: `DIS-ACCT-GROUP-ID` | 1-10 | 10 | X(10) |
| key: `DIS-TRAN-TYPE-CD` | 11-12 | 2 | X(2) |
| key: `DIS-TRAN-CAT-CD` | 13-16 | 4 | 9(4) |
| `DIS-INT-RATE` | 17-22 | 6 | S9(4)V99 (annual %) |
| filler | 23-50 | 28 | X |

### TRANSACT — output, sequential, LRECL 350, in write order

| Field | Cols | Len | PIC | Value |
|---|---|---|---|---|
| `TRAN-ID` | 1-16 | 16 | X(16) | `PARM-DATE` + suffix 9(6), zero-padded |
| `TRAN-TYPE-CD` | 17-18 | 2 | X(2) | `01` |
| `TRAN-CAT-CD` | 19-22 | 4 | 9(4) | `0005` |
| `TRAN-SOURCE` | 23-32 | 10 | X(10) | `System` + 4 spaces |
| `TRAN-DESC` | 33-132 | 100 | X(100) | `Int. for a/c ` + `ACCT-ID` (24 chars) + 76 spaces |
| `TRAN-AMT` | 133-143 | 11 | S9(9)V99 | monthly interest |
| `TRAN-MERCHANT-ID` | 144-152 | 9 | 9(9) | `000000000` |
| merchant name / city / zip | 153-262 | 110 | X | spaces |
| `TRAN-CARD-NUM` | 263-278 | 16 | X(16) | `XREF-CARD-NUM` |
| `TRAN-ORIG-TS`, `TRAN-PROC-TS` | 279-330 | 26 ×2 | X(26) | `YYYY-MM-DD-HH.MM.SS.hh0000` |
| filler | 331-350 | 20 | X | spaces (F3) |

Example from golden `CBACT04C-after-CBTRN02C` record 1: `2022071800000001010005System    Int. for a/c 00000000001 …0000000145F000000000…`.

## 3. Processing rules (in order)

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`.
2. Open TCATBALF, XREFFILE, DISCGRP, ACCTFILE and TRANSACT, in that order. A failure prints the open message (§4) and abends.
3. State:
   - `lastAcct` = 11 spaces
   - `firstTime` = true
   - `totalInt` = 0
   - `suffix` = 0
   - `recordCount` = 0 (counted, never printed)
4. For each TCATBAL record in key order:
   1. `recordCount++`. Print the 50-byte record exactly as read, after CR strip and padding.
   2. If `acct != lastAcct`:
      - If not `firstTime`, run **UPDATE-ACCOUNT** (step 6) for the account held in memory. Otherwise set `firstTime` = false.
      - `totalInt = 0` and `lastAcct = acct`.
      - Read ACCTFILE by `acct`. If it is missing, print `ACCOUNT NOT FOUND: <acct>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`, then abend.
      - Read XREFFILE by AIX `acct`. If it is missing, print `ACCOUNT NOT FOUND: <acct>`, `ERROR READING XREF FILE`, `FILE STATUS IS: NNNN0023`, then abend.
   3. Rate lookup, key = `ACCT-GROUP-ID` (10 bytes as stored, blanks included) + type + cat:
      - **hit:** use its rate;
      - **miss:** print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then look up `DEFAULT   ` + type + cat;
      - **DEFAULT miss:** print `ERROR READING DEFAULT DISCLOSURE GROUP` and `FILE STATUS IS: NNNN0023`, then abend.
   4. If `rate != 0`:
      - `monthly = trunc2(bal × rate / 1200)` (exact product, then divide, then truncate toward zero to 2 dp; `RoundingMode.DOWN`). Then drop high-order digits beyond 9 integer digits (D4).
      - `totalInt = wrap9(totalInt + monthly)`.
      - `suffix = (suffix + 1) mod 1,000,000` (D4).
      - Write a TRANSACT record (§2) with the current account's id and card number and a timestamp from the clock.
      - Fees: no-op (`1400-COMPUTE-FEES` is empty, CR-2).
   5. If `rate == 0`: no transaction and no suffix increment.
5. At EOF, **do nothing more** for the last account (D1). Close the files in the order TCATBALF, XREFFILE, DISCGRP, ACCTFILE, TRANSACT. Print `END OF EXECUTION OF PROGRAM CBACT04C`. Exit 0.
6. **UPDATE-ACCOUNT** (for the account in memory):
   - `CURR-BAL = wrap10(CURR-BAL + totalInt)`
   - `CYC-CREDIT = 0` and `CYC-DEBIT = 0`
   - REWRITE.

### Arithmetic

COBOL semantics without `ROUNDED` or `ON SIZE ERROR`, verified on GnuCOBOL 3.2:

- Truncation is toward zero: −10.01 @ 15% → **−0.12**; 2339.97 @ 15% → **29.24**.
- Overflow drops high-order digits and keeps the sign:
  - 999,999,999.99 × 9999.99 / 1200 → **333,324,999.91**
  - S9(9)V99 999,999,999.99 + 1 → **0.99**
  - 9(6) 999,999 + 1 → **000000**

### Timestamp

- Taken from the clock **per transaction written** (`Z-GET-DB2-FORMAT-TIMESTAMP`).
- `YYYY-MM-DD-HH.MM.SS.` + hundredths (2 digits) + `0000`.
- Clock `2022071800000000` → `2022-07-18-00.00.00.000000`.

## 4. Errors and edge cases

| # | Case | Behaviour (SYSOUT lines, then exit) |
|---|---|---|
| E1 | Input file missing or unreadable at open | `ERROR OPENING TRANSACTION CATEGORY BALANCE` / `ERROR OPENING CROSS REF FILE35` / `ERROR OPENING DALY REJECTS FILE` (DISCGRP, F1) / `ERROR OPENING ACCOUNT MASTER FILE`, then `FILE STATUS IS: NNNN0035`, `ABENDING PROGRAM` → 16 |
| E2 | TRANSACT cannot be created | `ERROR OPENING TRANSACTION FILE`, `FILE STATUS IS: NNNN0030`, `ABENDING PROGRAM` → 16 |
| E3 | Account not in ACCTFILE | see 4.2 → 16 |
| E4 | Account not in XREF AIX | see 4.2 → 16 |
| E5 | Group and DEFAULT both missing | see 4.3 → 16 |
| E6 | Write or rewrite I/O failure | `ERROR WRITING TRANSACTION RECORD` / `ERROR RE-WRITING ACCOUNT FILE`, `FILE STATUS IS: NNNN0030`, `ABENDING PROGRAM` → 16 |
| E7 | Empty TCATBALF | START/END only; 0 transactions; ACCTFILE unchanged → 0 |
| E8 | Only one account | Transactions written, account **not** updated (D1) → 0 |
| E9 | Non-numeric zoned field | Java-only guard: `ERROR READING TRANSACTION CATEGORY FILE` (or the matching file message), `FILE STATUS IS: NNNN0034`, `ABENDING PROGRAM` → 16. GnuCOBOL would compute on garbage, so this is a **deliberate deviation** for invalid data only. |
| E10 | Negative balance | Negative interest, truncated toward zero; overpunch `J`–`R`/`}` |
| E11 | Group hit (non-blank `ACCT-GROUP-ID` that matches) | Uses that rate, no miss lines |
| E12 | Duplicate key in an input KSDS | Load error, exit 3 (harness parity) |

- Status codes: `23` is **verified** with the harness (E3). `35`/`30`/`34` are the expected GnuCOBOL codes but are **not yet verified**; Build confirms them with a small `cobc` probe, then pins them. These paths cannot be reached through `run.sh`, which always loads every file.
- On abend, outputs keep the state at the point of failure. Transactions already written stay. Accounts already rewritten stay rewritten, as with VSAM. The golden data does not compare these.
- The CEE3ABD line and any Java diagnostics go to stderr only.

## 5. Preserved defects (fix later, deliberately)

| ID | Defect | Preserved as | Pinned by |
|---|---|---|---|
| D1 | The last account (in key order) is never rewritten; its interest is posted as a transaction but not added to its balance, and its cycle fields are not zeroed | Step 5 does nothing | Golden after-CBTRN02C acct 50 is unchanged (`00000019458G`); unit test E8 |
| D2 | Interest is truncated, not rounded | `RoundingMode.DOWN` | acct 2: 2339.97 → 29.24; −10.01 → −0.12 |
| D3 | Sample `ACCT-GROUP-ID` is blank (code is in ZIP), so every lookup uses DEFAULT | Lookup uses cols 113-122 verbatim | 50 / 100 miss pairs in golden SYSOUT |
| D4 | Silent overflow of the suffix (9(6)) and interest (S9(9)V99 / S9(10)V99) | mod 10^n wrap, sign kept | Unit tests with the values in §3 |
| F1 | DISCGRP open error says `DALY REJECTS` | Text kept | Unit test E1 |
| F1b | XREF open message concatenates the status (`…CROSS REF FILE35`) | Text kept | Unit test E1 |
| F2 | Timestamps come from the clock, not PARM | Injectable clock, defaults to system | Golden (frozen clock) |
| F3 | Filler and desc tail are spaces (default dialect) | Spaces | Golden byte-compare |

## 6. Acceptance tests (→ intent criteria)

| AC | Test | Evidence |
|---|---|---|
| 1 | `mvn -f modernized/cbact04c test` green; POM has only JUnit 5 and Surefire | build log |
| 2, 4 | `GoldenEquivalenceTest.stock`: `app/data/ASCII` inputs, PARM `2022071800`, clock `2022071800000000` | TRANSACT (50), ACCTFILE (50), SYSOUT (152) byte-equal to `golden/CBACT04C`; exit 0 = `RC.txt` |
| 3, 4 | `GoldenEquivalenceTest.afterCbtrn02c`: ACCTFILE and TCATBALF from `golden/CBTRN02C` | byte-equal to `golden/CBACT04C-after-CBTRN02C` (50/50/302); exit 0 |
| 5 | `InterestTest`: 2339.97@15 → 29.24, negative, overflow; source has no `double`/`float` (grep check) | unit |
| 6 | `DefectCharacterizationTest`: D1 (single account and golden acct 50), D2, D3 (blank group → DEFAULT + miss lines), D4 (suffix wrap, amount wrap) | unit |
| 7 | `ErrorPathTest`: E1–E6 messages and exit 16 | SYSOUT lines equal to the COBOL lines (E3 recorded from the harness: `ACCOUNT NOT FOUND: 99999999999` / `ERROR READING ACCOUNT FILE` / `FILE STATUS IS: NNNN0023` / `ABENDING PROGRAM`, RC 16) |
| 8 | `ZonedTest`: round-trip of every overpunch char, ±0, max values | unit |
| 9 | `legacy/harness/golden.sh --check` passes; `git diff --stat baseline -- legacy/golden legacy/harness .github` is empty | CI |
| R2 | `DifferentialEdgeTest`: a crafted edge dataset (negative balance, group hit, zero rate, two categories per account) run through **both** the COBOL harness and Java. The expected outputs are captured from `run.sh` into test resources. | byte-equal |
