# CR-1 Spec: `CBACT04C` → Java 21 (behaviour-preserving)

Run `e2137536-1d75-488a-9bcd-4aa967f2d9cf` · Stage: Design · Source of truth: [intent.md](intent.md), `app/cbl/CBACT04C.cbl`, `legacy/golden/`.
Approver defaults Q1–Q3 are assumed: text I/O, SYSOUT/RC parity, D1 kept.

## Interface

`java -jar target/cbact04c.jar --parm 2022071800 --out <DIR> [--dd <DD>=<path>]... [--in <DIR>]`
- Input `<DD>` path = `--dd` override, else `<in>/<DD>.txt`. Clock = env `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`),
  else system clock (default zone). Same contract as `legacy/harness/run.sh`.
- Writes `<out>/TRANSACT.txt`, `ACCTFILE.txt`, `SYSOUT.txt`, `RC.txt` (`0\n` or `16\n`); process exit code = RC.
- Text form: one record per line, LF-terminated incl. last line. On read: strip `\r`, right-pad with spaces to LRECL.
  Charset ISO-8859-1 (1 byte = 1 char).

## Records (only fields used; 0-based offset/len)

| DD (LRECL) | Copybook | Fields read / written | Java |
|---|---|---|---|
| `TCATBALF` (50) | `app/cpy/CVTRA01Y.cpy` | key 0/17 (acct 0/11, type 11/2, cat 13/4); `TRAN-CAT-BAL` 17/11 `S9(9)V99` | `String`, `BigDecimal` scale 2 |
| `XREFFILE` (50) | `app/cpy/CVACT03Y.cpy` | `XREF-CARD-NUM` 0/16; `XREF-ACCT-ID` 25/11 (alt key) | `String` |
| `ACCTFILE` (300) | `app/cpy/CVACT01Y.cpy` | key 0/11; `ACCT-CURR-BAL` 12/12 `S9(10)V99` (R/W); `CURR-CYC-CREDIT` 78/12, `CURR-CYC-DEBIT` 90/12 (W); `ACCT-GROUP-ID` 112/10 (R) | `String`, `BigDecimal` |
| `DISCGRP` (50) | `app/cpy/CVTRA02Y.cpy` | key 0/16 (group 0/10, type 10/2, cat 12/4); `DIS-INT-RATE` 16/6 `S9(4)V99` | `String`, `BigDecimal` |
| `TRANSACT` (350) | `app/cpy/CVTRA05Y.cpy` | all fields written, see R6 | `StringBuilder` 350 |

Signed zoned (overpunch, last byte): `{ A–I` = +0..9, `} J–R` = −0..9. Write: non-negative → `{A–I`, negative → `}J–R`;
zero is always written `{` (COBOL normalises −0). Unsigned `9(n)` fields are zero-padded digits.

## Processing rules (must reproduce exactly)

- **R1 Load.** Each KSDS → `TreeMap` by key (byte order). ACCTFILE is written back from this map in key order.
  XREF alt index: acct-id → first XREF record in file order with that acct-id (BDB duplicate order).
- **R2 Open.** Missing/unreadable input → SYSOUT `ERROR OPENING <file> ` text from `:234-323` (incl. D8 wording),
  `FILE STATUS IS: NNNN0035`, `ABENDING PROGRAM`, RC 16.
- **R3 SYSOUT.** `START OF EXECUTION OF PROGRAM CBACT04C`; then for each TCATBAL record (key order) the 50-byte record.
- **R4 Account break** (acct-id ≠ last): if not first record, rewrite previous account (R7); total := 0; read account
  (missing → `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`, abend);
  read xref by acct-id (missing → `ACCOUNT NOT FOUND: <id>`, `ERROR READING XREF FILE`, status 0023, abend).
- **R5 Rate.** Key `ACCT-GROUP-ID`+type+cat. Miss → SYSOUT `DISCLOSURE GROUP RECORD MISSING`, `TRY WITH DEFAULT GROUP CODE`,
  retry with `DEFAULT   ` (padded to 10). Miss again → `ERROR READING DEFAULT DISCLOSURE GROUP`, status 0023, abend.
- **R6 Interest** only if rate ≠ 0 (negative rates count):
  - `monthly = (bal × rate) / 1200`, exact product, divide with `RoundingMode.DOWN` to scale 2, then keep low 9 integer
    digits (`S9(9)V99` high-order truncation, D7). `total += monthly` (same truncation).
  - `suffix += 1` (mod 1 000 000, D6). Write TRANSACT: id = PARM(10)+suffix `%06d`; type `01`; cat `0005`;
    source `System` (pad 10); desc `Int. for a/c ` + acct-id (pad 100); amt = monthly; merchant id `000000000`;
    name/city/zip spaces; card = `XREF-CARD-NUM`; orig = proc = `YYYY-MM-DD-HH.MM.SS.hh0000` from the clock,
    taken per record; filler 20 spaces.
  - Fees: nothing (D2).
- **R7 Rewrite.** `CURR-BAL = CURR-BAL + total` (low 10 integer digits kept, D7); cycle credit/debit := `+0`
  (`00000000000{`); every other byte unchanged.
- **R8 EOF.** No rewrite of the last account (D1). Write outputs, SYSOUT `END OF EXECUTION OF PROGRAM CBACT04C`, RC 0.
- **Abend.** SYSOUT ends with `ABENDING PROGRAM`, RC 16. TRANSACT/ACCTFILE written as of the abend point
  (TRANSACT records so far; ACCTFILE with rewrites so far), matching the harness unload.

## Edge cases

| Case | Handling |
|---|---|
| Empty TCATBALF | Start/end lines only, 0 TRANSACT, ACCTFILE unchanged, RC 0 |
| Negative balance or rate | Truncate toward zero (−0.005 → `{` zero); overpunch `J–R` |
| Group id present in DISCGRP | Used directly, no fallback messages |
| Account with no TCATBAL rows | Not touched (D9) |
| CRLF / short lines (`tcatbal.txt`, `cardxref.txt`) | Strip CR, pad to LRECL |
| Unsorted input text | Sorted by key on load (R1) |

## Preserved defects (pinned, not fixed)

- **D1** Last account never rewritten (R8).
- **D2** `1400-COMPUTE-FEES` empty; no fees.
- **D3** Interest truncated, not rounded (R6).
- **D4** `ACCT-GROUP-ID` blank in data → always `DEFAULT` rates.
- **D5** Missing account/xref aborts the whole batch (R4).
- **D6** Tran-id suffix wraps after 999 999 (R6).
- **D7** Silent high-order truncation on overflow (R6, R7).
- **D8** DISCGRP open error says `DALY REJECTS FILE` (R2).
- **D9** Accounts rewritten (cycle fields zeroed) even when all rates are 0 (R7).

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -q verify`, JUnit 5 only | 1 |
| `GoldenEquivalenceTest.stock`: all 4 files byte-equal `legacy/golden/CBACT04C/` | 2, 4 |
| `GoldenEquivalenceTest.afterPosting`: inputs from `legacy/golden/CBTRN02C/`, equal `golden/CBACT04C-after-CBTRN02C/` | 3, 4 |
| `InterestTest.truncatesNotRounds` (e.g. bal 10.04 × 15 → 0.12, half-up 0.13); negative balance | 5, 9 |
| `DefectTest.lastAccountNotRewritten` (D1) | 6 |
| `RulesTest`: DEFAULT fallback; zero rate → no tx; ids run across accounts; rewrite diff = bytes 12–23, 78–101 only | 7 |
| `AbendTest`: missing account / xref / DEFAULT row / input file → RC 16 + SYSOUT text | 8 |
| `OverpunchTest`: round-trip all 20 sign chars, −0 → `{` | 9 |
| `legacy/tests/cbact04c-diff.sh`: COBOL vs Java on edge fixtures, all 4 files equal | 5–9 |
