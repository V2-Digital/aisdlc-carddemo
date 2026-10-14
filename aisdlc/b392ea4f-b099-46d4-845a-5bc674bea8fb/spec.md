# CR-1 Spec — `CBACT04C` in Java 21

Behaviour-preserving rewrite. Legacy behaviour, file roles and defects: [intent.md](intent.md).
Q1–Q3 defaults are taken: SYSOUT + RC also match, canonical text I/O, D1 goes to its own CR.

## Invocation
`java -jar cbact04c.jar <PARM> <OUT_DIR> TCATBALF=<f> XREFFILE=<f> ACCTFILE=<f> DISCGRP=<f>`
- `PARM`: transaction-ID prefix, 10 characters (`2022071800`); padded with spaces or cut to 10.
- Clock: `CARDDEMO_CURRENT_DATE` (16 digits, `YYYYMMDDHHMMSSss`, as the harness uses), else system clock UTC.
  Code takes an injected `Supplier<String>` so tests can freeze it.
- Writes `OUT_DIR/TRANSACT.txt`, `ACCTFILE.txt`, `SYSOUT.txt`, `RC.txt` (same names and format as
  `legacy/harness/run.sh`). Process exit code = RC.

## Files (canonical text: one record per line, fixed width, LF, trailing spaces kept)
Offsets are 0-based. `Zn` = zoned decimal, n bytes, last byte overpunched (`{`/`A-I` = +, `}`/`J-R` = −).
Amounts are `BigDecimal`, scale 2.

| DD | Copybook | Fields used |
|---|---|---|
| `TCATBALF` in, 50 B | `app/cpy/CVTRA01Y.cpy` | acct-id 0/11, type 11/2, cat 13/4, `TRAN-CAT-BAL` 17/Z11 S9(9)V99 |
| `XREFFILE` in, 50 B | `app/cpy/CVACT03Y.cpy` | card-num 0/16, acct-id 25/11 (alternate key) |
| `ACCTFILE` I-O, 300 B | `app/cpy/CVACT01Y.cpy` | acct-id 0/11; `ACCT-CURR-BAL` 12/Z12 S9(10)V99; `CYC-CREDIT` 78/Z12; `CYC-DEBIT` 90/Z12; `ACCT-GROUP-ID` 112/10 |
| `DISCGRP` in, 50 B | `app/cpy/CVTRA02Y.cpy` | key 0/16 (group 10 + type 2 + cat 4), `DIS-INT-RATE` 16/Z6 S9(4)V99 |
| `TRANSACT` out, 350 B | `app/cpy/CVTRA05Y.cpy` | all fields written, see R5 |

Note: intent's "cols 102-111" for the group id is the 0-based offset of `ACCT-ADDR-ZIP`; group id is 112/10.
The sample `A000000000` sits in the zip field, so D2 stands.

## Processing rules (in order)
- **R1 Load.** Index `ACCTFILE` (key 0/11), `DISCGRP` (key 0/16) and `XREFFILE` by acct-id. If an acct-id has
  more than one card, keep the first card in card-number order. Sort `TCATBALF` by key 0/17 (KSDS
  sequential read order). Missing or unreadable file → abend path (R8).
- **R2** SYSOUT `START OF EXECUTION OF PROGRAM CBACT04C`. For each TCATBAL record, echo its raw 50 bytes.
- **R3 Account break** (acct-id ≠ previous): if this is not the first account, apply R6 to the previous
  account. Reset total interest to 0. Look up the account, then the xref.
- **R4 Rate.** Look up key = `ACCT-GROUP-ID` + type + cat. If missing, SYSOUT
  `DISCLOSURE GROUP RECORD MISSING`, `TRY WITH DEFAULT GROUP CODE`, then look up `DEFAULT   ` + type + cat.
- **R5 Interest** only if rate ≠ 0:
  `monthly = (bal × rate) / 1200`, exact quotient **truncated toward zero** to 2 dp (`RoundingMode.DOWN`), then
  high-order digits cut to S9(9)V99 (keep |v| mod 10⁹). Total += monthly, also kept to S9(9)V99.
  Write one TRANSACT record: id = PARM(10) + 6-digit run-wide counter (starts at `000001`); type `01`;
  cat `0005`; source `System` padded to 10; desc `Int. for a/c ` + acct-id padded to 100; amount Z11;
  merchant-id `000000000`; name/city/zip spaces; card-num from xref; orig-TS = proc-TS =
  `YYYY-MM-DD-HH.MM.SS.hh0000` from the clock, read again for every record; filler 20 spaces.
- **R6 Account update:** curr-bal = (curr-bal + total) kept to S9(10)V99; cyc-credit = 0; cyc-debit = 0
  (written `00000000000{`); all other bytes unchanged.
- **R7 EOF:** no R6 for the last account (D1). SYSOUT `END OF EXECUTION OF PROGRAM CBACT04C`, RC 0.
  ACCTFILE is written in key order, with every record (changed or not).
- **R8 Abend** (stops at the first error), SYSOUT lines then `ABENDING PROGRAM`, RC 16, plus stderr
  `CEE3ABD: ABEND U0999`. Only `SYSOUT.txt` and `RC.txt` are written (the harness exports nothing else on abend).
  - account missing: `ACCOUNT NOT FOUND: <acct>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`
  - xref missing: `ACCOUNT NOT FOUND: <acct>`, `ERROR READING XREF FILE`, `FILE STATUS IS: NNNN0023`
  - DEFAULT missing: R4's two lines, `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`
  - open failure: `ERROR OPENING <file>` with the legacy text (DISCGRP says `DALY REJECTS`, D7)

## Zoned decimal
Decode: digits + sign from the last byte; `}` = −0 decodes to 0. Encode: zero-pad to width; a zero result is
always `{` (never `}`). A value too wide for the field keeps its low-order digits (COBOL MOVE truncation).

## Preserved defects (one line each, pinned by tests)
- D1 last account in TCATBAL is never rewritten (transaction is still written).
- D2 non-`DEFAULT` group never matches on the sample data (fallback fires every time).
- D3 interest is truncated, not rounded.
- D4 rate ≠ 0 with balance 0 writes a zero-amount transaction.
- D5 negative balance gives negative interest.
- D6 high-order digits are dropped silently on overflow of monthly/total (S9(9)V99) and curr-bal (S9(10)V99).
- D7 DISCGRP open-error text says `DALY REJECTS FILE`.
- D8 TRAN-ID repeats when the same PARM is rerun.

## Acceptance tests (→ intent AC)
| Test | AC |
|---|---|
| `mvn test` green, JUnit 5 only | 1 |
| `GoldenEquivalenceTest` stock: inputs `app/data/ASCII/{tcatbal,cardxref,acctdata,discgrp}.txt` → 4 files == `legacy/golden/CBACT04C/` | 2, 4 |
| same, ACCTFILE/TCATBALF from `legacy/golden/CBTRN02C/` → == `legacy/golden/CBACT04C-after-CBTRN02C/` | 3, 4 |
| `InterestTest`: 1164.87×15.00 → 14.56; −1164.87 → −14.56; 0.01×0.01 → 0.00; overflow wraps (D3, D5, D6) | 5 |
| `DefectCharacterizationTest`: last account unchanged (D1); rate 0 → no txn; balance 0 → zero txn (D4) | 6, 7 |
| `DefectCharacterizationTest`: fallback emits 2 lines and uses DEFAULT; real group hit uses its own rate; missing DEFAULT/account/xref → RC 16 + exact SYSOUT | 8 |
| `TranRecordTest`: id `2022071800000001`…, TS `2022-07-18-00.00.00.000000`, length 350 | 9 |
| `ZonedDecimalTest`: round trip of all overpunch chars, `}` → 0, zero → `{` | 5, 9 |
| `legacy/tests/cbact04c_edge.sh`: COBOL and Java on the same edge inputs give identical outputs | 5, 8 |
| `legacy/harness/golden.sh --check` | 10 |
