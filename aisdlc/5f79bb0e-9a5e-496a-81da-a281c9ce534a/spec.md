# CR-3 Spec: CBTRN02C (POSTTRAN) in Java 21

Run `5f79bb0e-9a5e-496a-81da-a281c9ce534a` · Stage: Design · Source `app/cbl/CBTRN02C.cbl` · Reference `legacy/golden/CBTRN02C/`

**Contract:** given the same input datasets and clock value, Java writes byte-identical `TRANFILE`, `DALYREJS`,
`ACCTFILE`, `TCATBALF` and `SYSOUT`, and exits with the same code as the GnuCOBOL harness.
Legacy defects are **kept** (marked **⚠ Fn**).

Approver defaults are taken as accepted: SYSOUT is part of the contract (Q1), canonical text I/O (Q2), F5 kept (Q3).

---

## 1. Interface

```
java -cp target/classes carddemo.cbtrn02c.Main <IN_DIR> <OUT_DIR>
```

| Item | Rule |
|---|---|
| Inputs | `<IN_DIR>/DALYTRAN.txt`, `XREFFILE.txt`, `ACCTFILE.txt`, `TCATBALF.txt` (same names as harness `INPUT_DIR`) |
| Outputs | `<OUT_DIR>/TRANFILE.txt`, `DALYREJS.txt`, `ACCTFILE.txt`, `TCATBALF.txt`, `SYSOUT.txt` |
| SYSOUT | Each DISPLAY line goes to stdout **and** `SYSOUT.txt` |
| Exit code | 0 / 4 / 16 (see §5). The caller writes `RC.txt`, as the harness does. |
| Clock | `java.time.Clock` is injected. The CLI reads env `CARDDEMO_CURRENT_DATE` = `YYYYMMDDHHMMSSss` (same as the harness) and uses the system clock in UTC when it is unset. |

## 2. Canonical text format (all files)

- One record per line, LF. Every output line is terminated, including the last.
- On read: strip `\r`. Pad short lines with spaces to the LRECL. A line longer than the LRECL is a load error: message on stderr, exit 16 (the harness `cdfiles` also fails on this).
- Charset: ISO-8859-1 (byte = char). Sample data is pure ASCII.
- Keyed (KSDS) outputs are written in ascending primary-key order (byte compare, which is `String.compareTo` for ASCII). Sequential outputs are written in write order.
- A duplicate primary key in a KSDS input file is a load error: exit 16.

### Zoned decimal `S9(n)V99`

- Last byte: `{`=+0, `A`–`I`=+1..9, `}`=−0, `J`–`R`=−1..9. A plain digit `0`–`9` is read as **positive** (probe: an amount of `00000001005` posted as +10.05).
- Value = signed `long` cents.
- Encoding (arithmetic results only) uses a positive overpunch when the value ≥ 0. **Zero is always `{`**, including the result of `x + (−0)`.
- **Truncation:** results are stored as `sign × (|v| mod 10^n)`, where n is the total digit count. There is no error (⚠ F7).
- Fields that are only MOVEd are copied as **raw bytes** and never re-encoded. For example, a `−0` amount stays `}` in `TRANFILE` (probe), and an unsigned `5` stays `5`.

## 3. Record layouts (1-based columns)

### DALYTRAN in / TRANFILE out (LRECL 350, `CVTRA06Y` / `CVTRA05Y`, identical shape)

| Field | Cols | Len | PIC | Java |
|---|---|---|---|---|
| ID (TRANFILE key) | 1–16 | 16 | X(16) | String |
| TYPE-CD | 17–18 | 2 | X(2) | String |
| CAT-CD | 19–22 | 4 | 9(4) | String (raw) |
| SOURCE | 23–32 | 10 | X(10) | String |
| DESC | 33–132 | 100 | X(100) | String |
| AMT | 133–143 | 11 | S9(9)V99 | long cents + raw bytes |
| MERCHANT-ID | 144–152 | 9 | 9(9) | String (raw) |
| MERCHANT-NAME | 153–202 | 50 | X(50) | String |
| MERCHANT-CITY | 203–252 | 50 | X(50) | String |
| MERCHANT-ZIP | 253–262 | 10 | X(10) | String |
| CARD-NUM | 263–278 | 16 | X(16) | String |
| ORIG-TS | 279–304 | 26 | X(26) | String |
| PROC-TS | 305–330 | 26 | X(26) | String |
| FILLER | 331–350 | 20 | X(20) | String |

### XREFFILE (LRECL 50, key cols 1–16, `CVACT03Y`)

CARD-NUM 1–16 X(16) · CUST-ID 17–25 9(9) · **ACCT-ID 26–36 9(11)** · FILLER 37–50. Stock lines are 36 bytes and are padded.

### ACCTFILE (LRECL 300, key cols 1–11, `CVACT01Y`)

| Field | Cols | PIC | Used |
|---|---|---|---|
| ACCT-ID | 1–11 | 9(11) | key |
| ACTIVE-STATUS | 12 | X | no (⚠ F8) |
| CURR-BAL | 13–24 | S9(10)V99 | updated |
| CREDIT-LIMIT | 25–36 | S9(10)V99 | read |
| CASH-CREDIT-LIMIT | 37–48 | S9(10)V99 | no |
| OPEN-DATE | 49–58 | X(10) | no |
| EXPIRAION-DATE | 59–68 | X(10) | read |
| REISSUE-DATE | 69–78 | X(10) | no |
| CURR-CYC-CREDIT | 79–90 | S9(10)V99 | read + updated |
| CURR-CYC-DEBIT | 91–102 | S9(10)V99 | read + updated |
| ADDR-ZIP / GROUP-ID / FILLER | 103–300 | X | no |

On REWRITE, only cols 13–24, 79–90 and 91–102 change. All other bytes are kept as read.

### TCATBALF (LRECL 50, key cols 1–17, `CVTRA01Y`)

ACCT-ID 1–11 9(11) · TYPE-CD 12–13 X(2) · CAT-CD 14–17 9(4) · **BAL 18–28 S9(9)V99** · FILLER 29–50 X(22).

### DALYREJS out (LRECL 430)

Cols 1–350 hold the input DALYTRAN record **as read** (after padding). Cols 351–354 hold the reason `9(4)`, e.g. `0102`. Cols 355–430 hold the description X(76), space-padded.

## 4. Processing rules (in order)

**State carried across records:** `tranCount`, `rejectCount`, plus two COBOL work areas whose contents leak between records:
- `tcatWork`: the 50-byte `TRAN-CAT-BAL-RECORD`, initially 50 spaces (⚠ F1).
- `acctWork`: the account record from the latest successful READ.

1. **Start.** DISPLAY `START OF EXECUTION OF PROGRAM CBTRN02C`.
2. **Open, in COBOL order:** DALYTRAN, TRANFILE, XREFFILE, DALYREJS, ACCTFILE, TCATBALF. A missing input file is open status `35` → §5 abend with `ERROR OPENING <name>`. TRANFILE starts **empty**, whatever exists in IN_DIR/OUT_DIR (⚠ F5).
3. **For each DALYTRAN record, in file order:** `tranCount++`, then reason = 0 and desc = spaces.
4. **Validate:**
   1. Look up XREF by CARD-NUM. Missing → **100** `INVALID CARD NUMBER FOUND`. Stop validation.
   2. Look up ACCOUNT by XREF ACCT-ID. Missing → **101** `ACCOUNT RECORD NOT FOUND`. Stop.
   3. `tempBal = trunc11(CYC-CREDIT − CYC-DEBIT + AMT)`, using signed cents with S9(9)V99 truncation (⚠ F2, ⚠ F7). If `CREDIT-LIMIT < tempBal` → **102** `OVERLIMIT TRANSACTION`.
   4. If `EXPIRAION-DATE < ORIG-TS[1..10]` (string compare; equal passes) → **103** `TRANSACTION RECEIVED AFTER ACCT EXPIRATION`. This overwrites 102 (⚠ F3).
5. **If reason ≠ 0:** `rejectCount++`. Append input(350) + reason(4) + desc(76) to DALYREJS. Go to the next record.
6. **Post:**
   1. Build TRAN: cols 1–304 are copied raw from the input. PROC-TS = `YYYY-MM-DD-HH.MM.SS.hh0000` from the clock (hh = hundredths), read **once per posted record**. FILLER 331–350 = 20 spaces. The input PROC-TS and FILLER are dropped.
   2. **TCATBAL:** key = XREF ACCT-ID + input TYPE-CD + input CAT-CD (raw).
      - Found: `tcatWork` = the stored record. `BAL = trunc11(BAL + AMT)`. Rewrite.
      - Not found: DISPLAY `TCATBAL record not found for key : <17-char key>.. Creating.`. `tcatWork` keeps its previous content. INITIALIZE overwrites cols 1–28 (key + BAL = 0). Set the key, then `BAL = trunc11(0 + AMT)`. **Cols 29–50 keep whatever `tcatWork` held** (⚠ F1). Write the record. `tcatWork` now equals the new record.
   3. **Account** (`acctWork`): `CURR-BAL = trunc12(CURR-BAL + AMT)`. If `AMT ≥ 0` (−0 counts as ≥ 0), `CYC-CREDIT = trunc12(CYC-CREDIT + AMT)`, else `CYC-DEBIT = trunc12(CYC-DEBIT + AMT)`. Rewrite. If the rewrite fails ("not found"), set reason 109 and continue: no abend, no reject, and TRAN is still written (⚠ F4; unreachable with the in-memory store).
   4. **TRANFILE:** write by TRAN-ID. A duplicate key (status `22`) → §5 abend with `ERROR WRITING TO TRANSACTION FILE`.
7. **EOF.** Close all files (= write outputs, §2). Then DISPLAY:
   - `TRANSACTIONS PROCESSED :` + `tranCount` as 9 digits
   - `TRANSACTIONS REJECTED  :` + `rejectCount` as 9 digits (two spaces before `:`)
   - `END OF EXECUTION OF PROGRAM CBTRN02C`
8. **Exit** 4 if `rejectCount > 0`, else 0.

Updates are cumulative: later records see balances already changed by earlier records in the same run.

## 5. Fatal errors (abend)

SYSOUT sequence: `<ERROR message>`, then `FILE STATUS IS: NNNN` + 4-char status, then `ABENDING PROGRAM`. Stderr gets `CEE3ABD: ABEND U0999 TIMING=0`. Exit **16**.

- Status format (para 9910): numeric status `ab` with `a`≠`9` → `00ab` (e.g. `0022`, `0035`). Otherwise → `a` + 3-digit binary value of `b`.
- Messages follow COBOL text exactly (`CBTRN02C.cbl:247-687`). Example: `ERROR CLOSING DAILY REJECTS FILE`, which reports the **XREFFILE** status (⚠ F6).
- Harness evidence: a duplicate TRAN-ID (stock record 2 twice) gives 5 SYSOUT lines (`START…`, `TCATBAL … 00000000020030001.. Creating.`, `ERROR WRITING TO TRANSACTION FILE`, `FILE STATUS IS: NNNN0022`, `ABENDING PROGRAM`) and RC 16. The harness exports **no** datasets on abend.
- Datasets on abend: Java writes them in their state at the abend point, because KSDS updates are not rolled back on z/OS. The harness cannot check this, so only SYSOUT and RC are compared.

## 6. Preserved defects (flag for a later CR)

| # | Defect | Java behaviour | Evidence |
|---|---|---|---|
| F1 | A created TCATBAL record takes FILLER (cols 29–50) from the last record in the work area | `tcatWork` carried between records | golden: 50 created records have `0…0`; probe run2 line 21: 22 spaces (first record is a create) |
| F2 | Overlimit uses `credit − debit + amt`. Debits are negative, so debits add headroom. CURR-BAL is ignored | same formula | golden: 38 rejects (the "fixed" formula would give 23) |
| F3 | 103 overwrites 102. Only one reason is kept | same order | probe `000000000068358X` → `0103` |
| F4 | Reason 109 on a failed account REWRITE is ignored. TRAN is still posted | same, via a store seam | code `:554-559`. Unit test with a failing store |
| F5 | TRANFILE is opened OUTPUT, so the master is replaced with the day's postings | always fresh | harness starts it empty. Duplicate id → RC 16 |
| F6 | DALYREJS close error prints the XREFFILE status | same | code `:649`. Unit test on the message |
| F7 | `WS-TEMP-BAL`/`TRAN-CAT-BAL` S9(9)V99 truncate silently | `mod 10^11` | probe run6: CYC-CREDIT 1,000,000,000.00 + 504.77 → tempBal 504.77 → **accepted**. TCATBAL 999,999,999.99 + 504.77 → `0000005047F` (504.76) |
| F8 | ACTIVE-STATUS, type/category validity and input PROC-TS are never checked | not checked | code |

## 7. Edge cases

| Case | Handling |
|---|---|
| Empty DALYTRAN | 0/0 counts, RC 0. TRANFILE and DALYREJS empty. ACCTFILE and TCATBALF unchanged (byte-identical to input, CR stripped) |
| 36-byte XREF lines, CRLF in `tcatbal.txt` | padded / stripped (§2) |
| AMT = `−0` (`}`) | TRAN keeps `}`. Treated as ≥ 0, so credit += 0, which stays `{` (probe run4) |
| Unsigned last digit | read as positive (probe run5) |
| Expiry equal to ORIG-TS date | accepted |
| Same account / category posted several times | cumulative (§4) |
| Non-digit in a numeric field | not defined by the legacy program. Java aborts with a load error (exit 16). Stock data has none |
| Overflow to exactly ±10^n | stored as `{` zero (not verified against GnuCOBOL; unreachable with stock data) |

## 8. Acceptance tests ↔ intent ACs

| AC | Test | Proof |
|---|---|---|
| 1 | `mvn -q verify` | build is green, dependencies = JUnit 5 + Surefire |
| 2 | `GoldenTest` | `app/data/ASCII` inputs → 4 files byte-equal to `legacy/golden/CBTRN02C` (262/38/50/100 lines) |
| 3 | `GoldenTest` RC 4 · `ReturnCodeTest`: accept-only input → 0, duplicate id → 16 | harness: dup id RC 16 |
| 4 | `GoldenTest` | SYSOUT equal to golden (54 lines) |
| 5 | `TimestampTest` | `2022-07-18T00:00:00.00Z` → `2022-07-18-00.00.00.000000`. `…12:34:56.78` → `2022-07-18-12.34.56.780000` |
| 6 | `HarnessProbeTest` cases `reasons` (100/101/103, 103-over-102) | expected outputs captured from `legacy/harness/run.sh` |
| 7 | `DefectTest` F1–F7 (+ F8 by reason count) | harness probes `f1-first-create`, `f7-truncation`, `dup-id`, plus unit seams for F4/F6 |
| 8 | `GoldenTest` uses the raw stock files (36-byte xref, CRLF tcatbal) | byte-equal |
