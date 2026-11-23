# CR-1 Spec — `CBACT04C` in Java 21 (behaviour-preserving)

Legacy behaviour, line refs and defects: [intent.md](intent.md). This spec only adds what the Java must do exactly.
Approver defaults are taken as given: Q1 SYSOUT/RC parity = **yes**, Q2 text files with one path per DD = **yes**.

## Interface

`java -jar cbact04c.jar --parm 2022071800 --tcatbalf F --xreffile F --acctfile F --discgrp F --transact OUT --acctfile-out OUT`
- Clock: env `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`, the same as the harness). If it is not set, the system clock in UTC.
  Tests inject a `Clock`.
- Exit code: 0 on success, 16 on abend. On abend the outputs are left incomplete, so the caller must discard them.

## Files (canonical text: fixed width, LF, overpunch, KSDS in key order)

| DD | Copybook | LRECL | Fields used (offset, len, PIC → Java) |
|---|---|---|---|
| `TCATBALF` in | `app/cpy/CVTRA01Y.cpy` | 50 | acct 0,11 `9(11)`→String · type 11,2 `X` · cat 13,4 `9(4)` · bal 17,11 `S9(9)V99`→BigDecimal |
| `XREFFILE` in | `app/cpy/CVACT03Y.cpy` | 50 | card 0,16 `X` · acct 25,11 `9(11)` (alt key) |
| `ACCTFILE` in/out | `app/cpy/CVACT01Y.cpy` | 300 | id 0,11 · curr-bal 12,12 `S9(10)V99` · cyc-credit 78,12 · cyc-debit 90,12 · group-id 112,10 `X`; other bytes copied verbatim |
| `DISCGRP` in | `app/cpy/CVTRA02Y.cpy` | 50 | key group 0,10 + type 10,2 + cat 12,4 · rate 16,6 `S9(4)V99` |
| `TRANSACT` out | `app/cpy/CVTRA05Y.cpy` | 350 | all fields, see rule 5 |

Input lines may be shorter than the LRECL (for example `cardxref.txt` = 36 bytes) or end in CRLF. Strip the CR and right-pad with spaces.
Zoned decimal: the last byte is an overpunch (`{A-I` = +, `}J-R` = −). On write, ≥ 0 uses `{A-I` and < 0 uses `}J-R`. Negative zero results are written as `{` (+0); the build confirms this with a COBOL test.

## Processing rules (in order)

1. Load XREF, ACCT and DISCGRP into maps. XREF is a multimap by acct id that keeps file order. Display `START OF EXECUTION OF PROGRAM CBACT04C`.
2. For each TCATBAL line in file order, print the 50-byte record (trailing spaces kept), then:
3. If acct ≠ last acct: if this is not the first record, update the previous account (rule 7). Set total = 0. Look up the account; if it is missing, print
   `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`, then abend. Look up xref the same way
   (`ERROR READING XREF FILE`). With more than one card per acct, the alt-key read may return status `02`, which the code treats as an
   error and abends on. Intent D7 assumes the first card is used instead. **Build must settle this** with `legacy/tests/xref_dup.sh` and then mirror the result.
4. Rate: key (group-id, type, cat). On a miss, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with
   `DEFAULT   ` (padded to 10). If that also misses, print `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, then abend.
5. If rate ≠ 0:
   - `monthly = bal × rate / 1200`. The intermediate is exact, then `setScale(2, DOWN)` (truncate toward zero), then keep it mod 10^9 (D6).
   - `total = (total + monthly)`, mod 10^9 on the integer part (S9(9)V99).
   - suffix += 1 (`9(6)`, wraps at 999999 → 000000).
   - Write TRANSACT: id = parm(10) + suffix(6) · `01` · `0005` · `System    ` · desc `Int. for a/c ` + acct(11), padded to 100 ·
     amt `S9(9)V99` · merchant id `000000000` · name 50sp, city 50sp, zip 10sp · card(16) · orig TS = proc TS =
     `YYYY-MM-DD-HH.MM.SS.hh0000` (taken from the clock once per tran) · FILLER 20 spaces.
6. Update `last acct = acct`, then read the next record.
7. Account update: `curr-bal = (curr-bal + total)` mod 10^10 (S9(10)V99, D6). Set cyc-credit = cyc-debit = 0 and store the result in the map.
8. At EOF, do **not** update the last account (D1). Write ACCTFILE: every record, in key order, updated or not.
   Print `END OF EXECUTION OF PROGRAM CBACT04C` and exit with 0.
9. Abend: print `ABENDING PROGRAM`, exit with 16. Before abending, a file-open failure prints the legacy text (D5: DISCGRP →
   `ERROR OPENING DALY REJECTS FILE`).

## Edge cases

| Case | Handling |
|---|---|
| rate = 0 | No tran and no suffix increment. The total is unchanged, but the account is still updated (cycle fields zeroed). |
| bal = 0, rate ≠ 0 | Amount-0 tran is written, with amt `0000000000{`. |
| negative bal | Truncate toward zero: −18.909 → −18.90. |
| TCATBAL not grouped by acct | Each break re-reads the account from the current map, so the legacy re-read semantics are kept. |
| empty TCATBAL | No trans. ACCTFILE is unchanged. Exit 0. |
| invalid overpunch / non-numeric | Abend (exit 16). Not reachable with golden data. |

## Preserved defects (fix later, deliberately)

- **D1** Last account in TCATBAL is never rewritten (interest is lost and its cycle fields are not zeroed).
- **D2** Interest is truncated, not rounded.
- **D3** The lookup uses blank `ACCT-GROUP-ID`, so it always falls back to `DEFAULT` (a data issue).
- **D4** Fees are never computed (CR-2).
- **D5** Wrong DISCGRP open-error text.
- **D6** Silent high-order truncation on overflow.
- **D7** Duplicate xref cards: behaviour is whatever `legacy/tests/xref_dup.sh` shows (first card, or abend on status 02).

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -f modernized/cbact04c verify`, JUnit 5 only | 1 |
| `GoldenCbact04cTest`: TRANSACT, ACCTFILE, stdout = `legacy/golden/CBACT04C/*` byte-for-byte | 2, 8, 9 |
| `GoldenAfterCbtrn02cTest`: same, inputs from `legacy/golden/CBTRN02C/` | 3, 8, 9 |
| `InterestTest`: acct 48 → 18.90. Negative and overflow cases. | 4 (D2, D6) |
| `LastAccountDefectTest`: acct 50 is unchanged, and tran `2022071800000050` = `0000000187G` | 5 (D1) |
| `ZeroRateZeroBalTest` | 6 |
| `AbendTest`: missing DEFAULT / account / xref → exit 16; duplicate xref per legacy test, `ABENDING PROGRAM` | 7 (D7) |
| `ZonedDecimalTest`: overpunch round-trip and sign of zero results | all |
