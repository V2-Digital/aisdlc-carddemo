# CR-1 Spec: `CBACT04C` in Java 21 (behaviour-preserving)

Legacy behaviour, file table and defects: [intent.md](intent.md). This spec pins what the Java must reproduce.
Approver defaults adopted: `--now` clock flag, SYSOUT + RC parity, all defects preserved.

## CLI

`java -jar cbact04c.jar --parm=2022071800 --in=<dir> --out=<dir> [--now=YYYYMMDDHHMMSShh]`

- `<in>/<DD>.txt` for `TCATBALF XREFFILE DISCGRP ACCTFILE`; writes `<out>/TRANSACT.txt`, `<out>/ACCTFILE.txt`.
  SYSOUT → stdout. Exit code = RC.
- Clock: `--now`, else env `CARDDEMO_CURRENT_DATE`, else `COB_CURRENT_DATE`, else system clock in UTC. Only the 16-digit form is accepted.
- Text format: fixed width, LF after every record, no decoding beyond overpunch zoned decimals.

## Fields read / written

| File (copybook) | Field | Offset (1-based) | Len | PIC | Java |
|---|---|---|---|---|---|
| TCATBALF ([CVTRA01Y](../../app/cpy/CVTRA01Y.cpy)) | acct id / type / cat | 1 / 12 / 14 | 11/2/4 | 9(11) X(2) 9(4) | `String` |
| | `TRAN-CAT-BAL` | 18 | 11 | S9(9)V99 | `BigDecimal` scale 2 |
| XREFFILE ([CVACT03Y](../../app/cpy/CVACT03Y.cpy)) | card num / acct id | 1 / 26 | 16/11 | X(16) 9(11) | `String` (map acct→card) |
| DISCGRP ([CVTRA02Y](../../app/cpy/CVTRA02Y.cpy)) | group / type / cat | 1 / 11 / 13 | 10/2/4 | X(10) X(2) 9(4) | `String` key |
| | `DIS-INT-RATE` | 17 | 6 | S9(4)V99 | `BigDecimal` scale 2 |
| ACCTFILE ([CVACT01Y](../../app/cpy/CVACT01Y.cpy)) | `ACCT-ID` | 1 | 11 | 9(11) | `String` key |
| | `ACCT-CURR-BAL` | 13 | 12 | S9(10)V99 | `BigDecimal` (R/W) |
| | `ACCT-CURR-CYC-CREDIT` / `-DEBIT` | 77 / 89 | 12/12 | S9(10)V99 | written as 0 → `00000000000{` |
| | `ACCT-GROUP-ID` | 111 | 10 | X(10) | `String` (all blank in sample) |
| TRANSACT ([CVTRA05Y](../../app/cpy/CVTRA05Y.cpy)) | full 350-byte record | – | 350 | see copybook | built from fields below |

All other ACCTFILE bytes are copied through untouched. ACCTFILE output = every input record, in key order.

## Processing rules (in order)

1. Load the 4 input files into memory (DISCGRP, ACCTFILE, XREF by acct id; TCATBALF in input order = key order). A missing input file → `ERROR OPENING …` text of the matching open paragraph + `FILE STATUS IS: NNNN00xx` + `ABENDING PROGRAM`, RC 16 (D6 text kept for DISCGRP).
2. Print `START OF EXECUTION OF PROGRAM CBACT04C`.
3. For each TCATBALF record: print the raw 50-byte line. On account break: if not first, **rewrite** the previous account (rule 6); total := 0; look up account, then XREF.
   - Miss → `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE` (or `… XREF FILE`), `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM`, stderr `CEE3ABD: ABEND U0999`, RC 16. Output file contents after an abend are not part of the contract (not covered by goldens).
4. Rate: key `ACCT-GROUP-ID+type+cat`. Miss → print `DISCLOSURE GROUP RECORD MISSING`, `TRY WITH DEFAULT GROUP CODE`, retry with `DEFAULT` padded to 10. Second miss → `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, abend as above.
5. If rate ≠ 0:
   - `monthly = (bal × rate) / 1200`, exact intermediate, then **truncate toward zero** to scale 2 (`RoundingMode.DOWN`), then keep the low 9 integer digits (S9(9)V99, D7).
   - `total += monthly`, again modulo S9(9)V99.
   - suffix += 1 (PIC 9(6), wraps at 1 000 000); write the transaction:
     `TRAN-ID`=parm(10)+suffix(6), type `01`, cat `0005`, source `System` padded to 10, desc `Int. for a/c `+acctId padded to 100,
     amount=monthly (overpunch), merchant id `000000000`, name/city/zip spaces, card=XREF card num,
     orig TS = proc TS = `YYYY-MM-DD-HH.MM.SS.hh0000` from the clock, FILLER 20 spaces.
   - Rate = 0 → nothing written, suffix not consumed.
6. Rewrite: `CURR-BAL = (CURR-BAL + total)` modulo S9(10)V99; cycle credit and debit := 0; rest unchanged.
7. EOF: **no rewrite of the last account (D1)**. Print `END OF EXECUTION OF PROGRAM CBACT04C`, RC 0.

Overpunch: last digit carries the sign — `{ A–I` = +0..9, `} J–R` = −0..9. Encode positive/zero with `{A–I` and negative with `}J–R`. Decode also accepts a plain trailing digit as positive.

## Edge cases

| Case | Handling |
|---|---|
| Negative balance, rate ≠ 0 | truncation toward zero (−1.239 → −1.23), negative overpunch |
| Balance 0, rate ≠ 0 | 0.00 transaction `00000000000{` written (D5) |
| Rate 0 | no transaction, no suffix |
| Blank `ACCT-GROUP-ID` | always falls back to DEFAULT (D4), 2 SYSOUT lines per record |
| Overflow of monthly / total / balance | silent high-order truncation, no exception (D7) |
| Last account in file | interest posted to TRANSACT, balance/cycle fields not rewritten (D1) |
| Empty TCATBALF | START + END lines, empty TRANSACT, ACCTFILE unchanged, RC 0 |

## Preserved defects (fix later, separately)

- D1 last account never rewritten. D2 truncation, not rounding. D3 fees no-op. D4 group id never matches.
- D5 zero-amount transactions. D6 DISCGRP open-error text says "DALY REJECTS". D7 no overflow guard.

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -f modernized/cbact04c test`, deps JUnit 5 + Surefire only | 1 |
| `GoldenTest.stock`: TRANSACT/ACCTFILE byte-equal `legacy/golden/CBACT04C/` | 2 |
| `GoldenTest.afterCbtrn02c`: inputs ACCTFILE+TCATBALF from `golden/CBTRN02C` | 3 |
| Both: stdout line-equal `SYSOUT.txt`, exit 0 = `RC.txt` | 4 |
| `InterestTest.truncatesNotRounds`: a golden case where HALF_UP differs | 5 |
| `DefectTest.lastAccountNotRewritten`: acct `00000000050` equals input; its 18.77 `0000000187G` txn exists | 6 |
| `InterestTest`: rate-0 category → no txn, next suffix unchanged; bal 0 → 0.00 txn | 7 |
| `AbendTest`: missing account / XREF / DEFAULT → legacy lines + exit 16 | 8 |
| `ClockTest`: `--now=2023010203040506` → `2023-01-02-03.04.05.060000` | 9 |
| `OverpunchTest`: round-trip of ±0 and ±1..9 | supports 2–3 |
