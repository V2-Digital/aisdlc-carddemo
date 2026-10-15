# CR-1 Spec: `CBACT04C` in Java 21

Run `74748ec5-2d41-48cc-8e26-0e5584ce7b11` · Stage: Design · Legacy behaviour: [intent.md](intent.md)

**Contract:** the program must behave exactly like the legacy one, known defects included. Equivalence is checked
on TRANSACT, ACCTFILE, SYSOUT and the exit code (intent Q1–Q3, defaults accepted).

## Invocation

`java -jar cbact04c.jar --parm 2022071800 --clock 2022071800000000 --out <dir> TCATBALF=<f> XREFFILE=<f> ACCTFILE=<f> DISCGRP=<f>`

- `--parm`: 10 characters, used as `PARM-DATE`. Shorter values are right-padded with spaces.
- `--clock`: `YYYYMMDDHHMMSSss`, the same form as the harness's `COB_CURRENT_DATE`. If omitted, the system UTC
  clock is used.
- The program writes `<out>/TRANSACT.txt` and `<out>/ACCTFILE.txt`. DISPLAY lines go to stdout. The process
  exit code is the RC.

## Files (canonical text format, see `legacy/README.md` "Outputs")

On read, a trailing `CR` is stripped and short lines are right-padded with spaces to LRECL (for example,
`cardxref.txt` has 36 bytes on disk and LRECL 50). On write, each record is exactly LRECL bytes followed by `LF`.

| DD | Copybook | Fields used (0-based offset, length, PIC → Java) |
|---|---|---|
| `TCATBALF` in | `app/cpy/CVTRA01Y.cpy` | key 0,17 (acct 0,11 · type 11,2 · cat 13,4) · `TRAN-CAT-BAL` 17,11 S9(9)V99 → `BigDecimal` |
| `XREFFILE` in | `app/cpy/CVACT03Y.cpy` | `XREF-CARD-NUM` 0,16 · `XREF-ACCT-ID` 25,11 (alternate key) |
| `DISCGRP` in | `app/cpy/CVTRA02Y.cpy` | key 0,16 (group 0,10 · type 10,2 · cat 12,4) · `DIS-INT-RATE` 16,6 S9(4)V99 |
| `ACCTFILE` I-O | `app/cpy/CVACT01Y.cpy` | `ACCT-ID` 0,11 · `CURR-BAL` 12,12 S9(10)V99 · `CYC-CREDIT` 78,12 · `CYC-DEBIT` 90,12 · `GROUP-ID` 112,10 |
| `TRANSACT` out | `app/cpy/CVTRA05Y.cpy` | the whole 350-byte record, built as described in rule 6 |

- Records are handled as `byte[]`/ASCII strings. Every byte that is not rewritten is copied unchanged.
- **Overpunch:** the last digit carries the sign. `{`/`A`–`I` mean +0..9 and `}`/`J`–`R` mean −0..9. Zero is
  always encoded as `{`, never `}`.
- **Keyed files:** the program loads them into a `TreeMap` keyed by the key bytes. TCATBALF is iterated in key
  order. ACCTFILE is written in full, in key order, including records that were never touched.
- **XREF alternate key:** the first record in primary-key (card) order wins (D7).

## Processing rules (in order)

1. DISPLAY `START OF EXECUTION OF PROGRAM CBACT04C`. Load all four inputs. An unreadable file → the open-error
   path (rule 9) with that file's legacy message, for example `ERROR OPENING DALY REJECTS FILE` for DISCGRP (D5).
2. For each TCATBALF record, in key order: DISPLAY the raw 50 bytes.
3. If the acct id ≠ last acct id: if this is not the first record, rewrite the held account (rule 7). Then set
   total = 0, look up ACCTFILE by acct id, then look up XREF by acct id. Either miss → `ACCOUNT NOT FOUND: <id>`,
   `ERROR READING ACCOUNT FILE` (or `… XREF FILE`), `FILE STATUS IS: NNNN0023`, then abend.
4. Rate lookup, key = `ACCT-GROUP-ID` + type + cat. On a miss: DISPLAY `DISCLOSURE GROUP RECORD MISSING` and
   `TRY WITH DEFAULT GROUP CODE`, then retry with group `DEFAULT` (padded to 10). A second miss →
   `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, then abend.
5. If rate ≠ 0: `monthly = trunc2(bal × rate / 1200)`, truncated toward zero (`RoundingMode.DOWN`, scale 2). It is
   then stored modulo 10^9 in the integer part (S9(9)V99, D6). `total = (total + monthly)`, also mod 10^9.
   Write the TRANSACT record (rule 6). The fees step is a no-op (D4). If rate = 0: no transaction is written.
6. The TRANSACT record is initialised to 350 spaces:
   - `TRAN-ID` = PARM + counter `%06d` (counter +1 per transaction, wraps at 10^6)
   - type `01`, cat `0005`, source `System` (padded to 10)
   - desc `Int. for a/c ` + acct id (padded to 100)
   - amount = monthly (S9(9)V99 overpunch), merchant id `000000000`
   - card = `XREF-CARD-NUM`
   - ORIG-TS = PROC-TS = `YYYY-MM-DD-HH.MM.SS.hh0000` from the clock
   - name/city/zip/FILLER stay as spaces.
7. Rewrite: `CURR-BAL = (CURR-BAL + total)` mod 10^10; `CYC-CREDIT = CYC-DEBIT = 0`. The account is rewritten
   even when total = 0.
8. At EOF: **do not rewrite the held (last) account (D1).** DISPLAY `END OF EXECUTION OF PROGRAM CBACT04C`, then
   exit 0.
9. Abend: DISPLAY `ABENDING PROGRAM`, write `CEE3ABD: ABEND U0999` to stderr, and exit **16**. Outputs are not
   part of the contract after an abend.

## Edge cases

| Case | Handling |
|---|---|
| Empty TCATBALF | No rewrites, empty TRANSACT, ACCTFILE copied unchanged, RC 0 |
| Negative balance | Truncated toward zero (−14.569 → −14.56). Negative amount encoded with `J`–`R`/`}` |
| Negative zero result | Encoded as `{` |
| Same account in non-adjacent records | Not possible: TCATBALF is processed in key order |
| Last account has non-zero interest | Its TRANSACT record is written, and ACCTFILE stays unchanged (D1) |
| Overflow of amount/balance/counter | Silent high-order truncation (D6) |

## Preserved defects (fix later, deliberately)

- **D1:** the last account is never rewritten. Its interest and the cycle-field reset are lost.
- **D2:** interest is truncated, not rounded.
- **D3:** the blank `ACCT-GROUP-ID` always falls back to `DEFAULT` (2 extra SYSOUT lines per record).
- **D4:** the fees step is empty.
- **D5:** the DISCGRP open error says `DALY REJECTS FILE`.
- **D6:** no size-error check. Values wrap silently.
- **D7:** with duplicate cards per account, the first one by card order wins.

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -f modernized/cbact04c verify`, JUnit 5 only | 1 |
| `GoldenEquivalenceTest.stock`: stock `app/data/ASCII` inputs vs `legacy/golden/CBACT04C` (TRANSACT, ACCTFILE, SYSOUT 152, RC 0) | 2, 4 |
| `GoldenEquivalenceTest.afterCbtrn02c`: ACCTFILE+TCATBALF from `golden/CBTRN02C` vs `golden/CBACT04C-after-CBTRN02C` (SYSOUT 302) | 3, 4 |
| `DefectCharacterizationTest.lastAccountNotRewritten`: acct `00000000050` output == input, tran `…000050` = 18.77 | 5 |
| `InterestTest.truncates`: 1164.87 @ 15.00 → 14.56; negative case | 6 |
| `InterestBatchTest`: DEFAULT fallback, rate 0 → no transaction · `OverpunchTest`: all 20 sign characters, both directions | 7 |
| `AbendTest`: missing acct / xref / DEFAULT rate → exit 16 and the legacy message lines | 8 |
| `legacy/harness/golden.sh --check` passes; `git diff baseline -- legacy .github` is empty | 9 |
