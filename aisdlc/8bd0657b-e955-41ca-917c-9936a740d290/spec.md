# CR-1 Spec — `CBACT04C` (INTCALC) in Java 21

Behaviour-preserving rewrite of the program described in [intent.md](intent.md). Here, "intent §N" means processing step N in that file. The Q1–Q3 defaults are assumed: SYSOUT and RC must match too, F1 is preserved, and I/O is fixed-width text.

## Interface
`java -jar cbact04c.jar <PARM> --in <dir> --out <dir>`
- Reads `<dir>/<DD>.txt` for `TCATBALF XREFFILE ACCTFILE DISCGRP`. Writes `<out>/TRANSACT.txt` and `<out>/ACCTFILE.txt`. Display lines go to stdout. Exit code = RC.
- PARM: the first 10 chars, right-padded with spaces (F6).
- Clock: `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`, same as the harness), otherwise the system clock in UTC. Tests inject a `Clock`.
- Input lines: strip `\r` and right-pad with spaces to LRECL. Output lines: exactly LRECL + `\n`, with no `\r`.

## Records (only the fields used; 0-based offset/length)
| File | Copybook | Field | Off/Len | PIC → Java |
|---|---|---|---|---|
| TCATBALF (50) | `app/cpy/CVTRA01Y.cpy` | key acct+type+cat | 0/17 | `String` (sort key) |
| | | TRAN-CAT-BAL | 17/11 | S9(9)V99 → `BigDecimal` scale 2 |
| XREFFILE (50) | `app/cpy/CVACT03Y.cpy` | XREF-CARD-NUM | 0/16 | `String` |
| | | XREF-ACCT-ID (alt key) | 25/11 | `String` |
| ACCTFILE (300) | `app/cpy/CVACT01Y.cpy` | ACCT-ID (key) | 0/11 | `String` |
| | | ACCT-CURR-BAL | 12/12 | S9(10)V99 → `BigDecimal` |
| | | ACCT-CURR-CYC-CREDIT / -DEBIT | 78/12, 90/12 | S9(10)V99 |
| | | ACCT-GROUP-ID | 112/10 | `String` |
| DISCGRP (50) | `app/cpy/CVTRA02Y.cpy` | key group+type+cat | 0/16 | `String` |
| | | DIS-INT-RATE | 16/6 | S9(4)V99 → `BigDecimal` |
| TRANSACT (350) | `app/cpy/CVTRA05Y.cpy` | whole record | 0/350 | see R6 |

Signed numbers use zoned overpunch in the last byte: `{A-I` are +0..9 and `}J-R` are -0..9. Zero encodes as `{`.

## Processing rules (in order)
- **R1** Load the KSDS files into maps. TCATBALF is processed in ascending 17-byte key order, because the harness loads it into a KSDS. ACCTFILE is kept in key order for output. XREF alt key: the **first** record in file order per acct (F7).
- **R2** Display `START OF EXECUTION OF PROGRAM CBACT04C`.
- **R3** For each TCATBAL record: display the raw 50-byte record.
- **R4** If the acct differs from the last acct: if this is not the first record, apply R7 to the previous account. Then set total = 0, look up ACCTFILE, then XREF (error paths E1/E2).
- **R5** Rate key = `ACCT-GROUP-ID` + type + cat. On a miss, display `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with group `DEFAULT` padded to 10 (E3).
- **R6** If rate ≠ 0:
  - monthly = `bal × rate / 1200` with exact intermediate, **truncated toward zero** to 2 dp (F2), then **modulo 10^9** keeping the sign (F5).
  - total = (total + monthly) mod 10^9.
  - suffix = suffix + 1 mod 10^6.
  - Write the TRANSACT record: `PARM(10)+suffix(6 digits)`, `01`, `0005`, `System` padded to 10, desc `Int. for a/c `+ACCT-ID padded to 100, amt 11, merchant id `000000000`, then 50+50+10 spaces, card 16, ts, ts, 20 spaces.
  - ts = `YYYY-MM-DD-HH.MM.SS.hh0000`, read from the clock **for each record**.
  - Then call the fees hook, which is a no-op (F4).
- **R7** Account update: bal = (bal + total) mod 10^10. Cycle credit and debit = 0. Replace the record in the map. All other bytes stay unchanged.
- **R8** At EOF: **do not** update the last account (F1). Display `END OF EXECUTION OF PROGRAM CBACT04C`. Write ACCTFILE (all records, key order) and TRANSACT. RC 0.

## Error paths (abend = RC 16)
Abend sequence: display the lines below, then `ABENDING PROGRAM`, then stderr `CEE3ABD: ABEND U0999`, exit 16. **No output files are written**, matching the harness, which does not unload after an abend.
- **E1** Account missing: `ACCOUNT NOT FOUND: <acct11>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`.
- **E2** XREF missing: `ACCOUNT NOT FOUND: <acct11>`, `ERROR READING XREF FILE`, `FILE STATUS IS: NNNN0023`.
- **E3** DEFAULT missing: the two R5 lines, then `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`.
- **E4** Unreadable or missing input file: `ERROR OPENING …` with the message for that DD (`CBACT04C.cbl:245-318`), then status `NNNN0035`.

## Edge cases
| Case | Handling |
|---|---|
| Empty TCATBALF | Start and end lines only. 0 tran. ACCTFILE unchanged |
| Rate = 0 | No tran, suffix unchanged. Account still updated by R7 unless it is the last one |
| Negative bal or rate | Truncate toward zero. Encode with `}`/`J-R`. Sign of a zero result: **settled by legacy test** (assumed `{`) |
| Overflow (bal 999999999.99 × rate 9999.99) | Modulo 10^9 / 10^10. **Confirmed against COBOL by legacy test** before coding |
| Short/CRLF input lines | Pad / strip (`cardxref.txt` lines are 36 bytes) |
| Duplicate XREF acct | First in file order (F7) |
| Non-numeric overpunch byte | Treat as an input error and abend. Not reachable on golden data |

## Preserved defects (fix later, deliberately)
- **F1** The last account's interest is never posted, and its cycle fields are not zeroed.
- **F2** Interest is truncated, not rounded.
- **F3** The rate is keyed on the blank `ACCT-GROUP-ID`, so it always falls back to DEFAULT.
- **F4** Fees are a no-op.
- **F5** Silent high-order truncation on overflow.
- **F6** PARM-LENGTH is ignored. The 6-digit TRAN-ID suffix wraps.
- **F7** Duplicate XREF: the first one wins.
- **R2** Uninitialised bytes are spaces (z/OS may use `x'00'`).

## Acceptance tests (→ intent AC)
| Test | AC |
|---|---|
| `mvn -q verify`, JUnit 5 only | 1 |
| `GoldenTest.stock`: `app/data/ASCII` inputs → byte-compare TRANSACT, ACCTFILE, SYSOUT, RC with `legacy/golden/CBACT04C` | 2, 4 |
| `GoldenTest.afterCbtrn02c`: ACCTFILE+TCATBALF from `legacy/golden/CBTRN02C` → `legacy/golden/CBACT04C-after-CBTRN02C` | 3, 4 |
| `InterestTest`: acct 2 → `0000000292D` (29.24). Negatives truncate toward zero | 5 |
| `DefectF1Test`: acct 50 is unchanged, tran `2022071800000050` exists | 6 |
| `RateLookupTest`: DEFAULT fallback lines. Missing DEFAULT → RC 16 + `ABENDING PROGRAM` | 7 |
| `RateZeroTest`: no tran, cycle fields zeroed on a non-last account | 8 |
| `OverpunchTest`: all 20 sign chars round-trip. Negative amount written with `}`/`J-R` | 9 |
| `AbendTest`: missing acct / missing XREF → RC 16, SYSOUT matches E1/E2 | 10 |
| `EdgeFixtureTest`: Java output == COBOL output for `legacy/tests` fixtures (overflow, -0, abends) | 5, 7, 9, 10 (F5) |
