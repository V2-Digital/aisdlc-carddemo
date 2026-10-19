# CR-1 Spec: `CBACT04C` (INTCALC) in Java 21

Like-for-like rewrite. Legacy behaviour, file roles and defects D1-D4 are in [intent.md](intent.md).
Line refs are to `app/cbl/CBACT04C.cbl`. Approver defaults Q1-Q3 are adopted (SYSOUT+RC parity,
full ACCTFILE output file, 16-digit clock setting).

## Interface
`java -jar cbact04c.jar --parm 2022071800 --tcatbalf F --xreffile F --discgrp F --acctfile-in F --acctfile-out F --transact F [--sysout F]`
- Clock: env `COB_CURRENT_DATE` = `YYYYMMDDHHMMSSss` (16 digits; anything else → error, exit 16). Unset → system clock, UTC.
- SYSOUT lines go to stdout (and to `--sysout` if given). Exit code 0 on success, 16 on abend.
- `PARM` is taken as the first 10 chars, right-padded with spaces (PIC X(10), :178).

## Files (canonical text: one record per line, LF, overpunch signs, see `legacy/README.md`)
On read, a trailing CR is stripped and short lines are right-padded with spaces to the LRECL (`cardxref.txt` is 36 B).

| DD | Copybook | LRECL | Fields used (1-based offset, len, PIC → Java) |
|---|---|---|---|
| TCATBALF in | `app/cpy/CVTRA01Y.cpy` | 50 | key 1-17 (X→String); acct 1,11 9(11); type 12,2 X; cat 14,4 9(4); bal 18,11 S9(9)V99 → BigDecimal |
| XREFFILE in | `app/cpy/CVACT03Y.cpy` | 50 | card 1,16 X; acct 26,11 9(11) (alt key) |
| DISCGRP in | `app/cpy/CVTRA02Y.cpy` | 50 | key 1-16 = group 1,10 X + type 11,2 X + cat 13,4 9(4); rate 17,6 S9(4)V99 → BigDecimal |
| ACCTFILE in/out | `app/cpy/CVACT01Y.cpy` | 300 | id 1,11; curr bal 13,12 S9(10)V99; cyc credit 79,12 / cyc debit 91,12 S9(10)V99; group 113,10 X. All other bytes copied unchanged |
| TRANSACT out | `app/cpy/CVTRA05Y.cpy` | 350 | all fields, see rule 6 |

Overpunch codec: last byte of a signed field `{`/`A-I` = +0..9, `}`/`J-R` = -0..9. Encode: zero is `{`
(never `}`); negative non-zero uses `J-R`/`}`. Unsigned 9(n) fields are plain digits.

## Processing rules (in order)
1. Load TCATBALF, sort by 17-byte key (KSDS order). XREF indexed by acct id (first record in card-number
   order wins on duplicates); DISCGRP by 16-byte key; ACCTFILE by 11-byte id, kept in key order.
   Duplicate primary key in any input → abend (a KSDS could not hold it).
2. SYSOUT `START OF EXECUTION OF PROGRAM CBACT04C`. Txn counter = 0.
3. For each TCATBAL record: SYSOUT the raw 50-byte record. If acct id ≠ previous:
   - not the first account → **update** the previous account (rule 7);
   - total interest = 0; look up account, then xref (rule 8 on miss).
4. Rate key = `ACCT-GROUP-ID` (10 B, as is, blank in sample) + TCATBAL type + cat. Miss →
   SYSOUT `DISCLOSURE GROUP RECORD MISSING`, `TRY WITH DEFAULT GROUP CODE`; retry with `DEFAULT` padded to
   10 B. Miss again → abend `ERROR READING DEFAULT DISCLOSURE GROUP` / `FILE STATUS IS: NNNN0023`.
5. If rate ≠ 0: monthly = `bal × rate / 1200`, exact BigDecimal, **truncated toward zero** to 2 dp
   (`RoundingMode.DOWN`), then high-order truncated to S9(9)V99 (keep |value| mod 10^9). total += monthly,
   also mod 10^9 to S9(9)V99. Write a transaction (rule 6). Fees: no-op.
6. Transaction record (350 B): id = PARM(10) + counter+1 as 6 digits (mod 10^6, wraps to `000000`);
   type `01`; cat `0005`; source `System` pad 10; desc `Int. for a/c ` + ACCT-ID (from ACCTFILE) pad 100;
   amount S9(9)V99; merchant id `000000000`; name/city/zip spaces; card = xref card; orig = proc =
   `YYYY-MM-DD-HH.MM.SS.hh0000` from the clock; filler 20 spaces.
7. Update: curr bal += total (mod 10^10 to S9(10)V99); cyc credit = cyc debit = 0; replace record.
8. Account miss → SYSOUT `ACCOUNT NOT FOUND: <acct>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`.
   Xref miss → `ACCOUNT NOT FOUND: <acct>`, `ERROR READING XREF FILE`, `FILE STATUS IS: NNNN0023`. Then abend.
9. EOF: **do not** update the last account (D1). SYSOUT `END OF EXECUTION OF PROGRAM CBACT04C`, exit 0.
10. Abend: SYSOUT `ABENDING PROGRAM`, stderr `CEE3ABD: ABEND U0999`, exit 16. TRANSACT keeps the records
    written so far; ACCTFILE output is written with the updates applied so far (as the KSDS would be).

## Edge cases
| Case | Handling |
|---|---|
| Empty TCATBALF | START/END lines only, 0 txns, ACCTFILE copied unchanged, exit 0 |
| Rate = 0 (e.g. type 03) | no txn, no counter increment; account still updated (cycle zeroed) |
| Negative balance or rate | truncate toward zero: -10.01 × 15 / 1200 = -0.1251 → -0.12 (`0000000001K`) |
| Interest ≥ 10^9 | silent high-order truncation (D4) |
| Non-numeric numeric field | abend with message naming DD + record no. (legacy undefined; not in data) |
| Abend paths | rules 4, 8, 10; open errors (missing file) use the legacy open message, incl. `ERROR OPENING DALY REJECTS FILE` for DISCGRP |

## Preserved defects (fix later, deliberately)
- **D1** last account in TCATBALF never rewritten: its interest is posted as a txn, not to the balance.
- **D2** rate lookup uses blank `ACCT-GROUP-ID`, so every lookup falls back to `DEFAULT`.
- **D3** interest truncated, not rounded.
- **D4** txn-id counter wraps after 999,999; interest/balance overflow silently truncated.
- Cosmetic: DISCGRP open-failure text `ERROR OPENING DALY REJECTS FILE`; `1400-COMPUTE-FEES` is a no-op.

## Acceptance tests (→ intent AC)
| Test | AC |
|---|---|
| `mvn -f modernized/cbact04c test` green, JUnit 5 only | 1 |
| `GoldenEquivalenceTest` stock: TRANSACT, ACCTFILE, SYSOUT == `legacy/golden/CBACT04C/`, RC 0 | 2, 4 |
| `GoldenEquivalenceTest` after-CBTRN02C (ACCTFILE/TCATBALF from `golden/CBTRN02C/`) == golden | 3, 4 |
| `DefectCharacterizationTest`: D1 (acct 50 unchanged, txn `2022071800000050` = `0000000187G`), D2 (100 MISSING lines), D3 (acct 2 → 29.24; negative case), D4 (counter wrap, overflow) | 5 |
| `OverpunchTest` all 20 sign chars, each PIC used; `InterestTest` BigDecimal only | 6 |
| `AbendTest` missing account / xref / DEFAULT → exit 16 + exact SYSOUT lines | 7 |
| `legacy/tests/cbact04c_edge.sh`: same edge inputs through COBOL and Java, outputs diff-equal | 5, 7 |
| `legacy/harness/golden.sh --check` passes | 8 |
