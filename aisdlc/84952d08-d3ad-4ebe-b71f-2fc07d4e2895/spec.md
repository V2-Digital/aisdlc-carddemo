# CR-1 Spec: `CBACT04C` in Java 21 (behaviour-preserving)

Source of truth for legacy behaviour: [intent.md](intent.md). Approver defaults Q1-Q3 are assumed accepted:
SYSOUT parity on stdout, canonical text files, injectable clock.

## Interface

`java -jar cbact04c.jar <PARM> [--TCATBALF=p] [--XREFFILE=p] [--DISCGRP=p] [--ACCTFILE=in] [--ACCTFILE-OUT=p] [--TRANSACT=p] [--clock=YYYYMMDDHHMMSSss]`

- `PARM`: first 10 chars = `PARM-DATE` (right-padded with spaces if shorter).
- Missing path args fall back to env `DD_<DD>` (same as the harness). `ACCTFILE-OUT` defaults to `ACCTFILE` (overwrite).
- Clock: `--clock`, else env `CARDDEMO_CURRENT_DATE` (16-digit, as the harness), else system clock, local zone.
- stdout = SYSOUT lines. On abend: `CEE3ABD: ABEND U0999` to stderr, exit **16**. Success: exit 0.

## Files (canonical text: one record per line, LF, fixed width, overpunch zoned)

Reading: strip a trailing `CR`, right-pad with spaces to LRECL (`cardxref.txt` is 36 bytes, `tcatbal.txt` is CRLF).
A line longer than LRECL, or a non-numeric zoned field that is read, is **bad input**: abend (RC 16).

| DD | LRECL | Copybook | Fields used (1-based cols, PIC → Java) |
|---|---|---|---|
| `TCATBALF` in | 50 | [`CVTRA01Y`](../../app/cpy/CVTRA01Y.cpy) | acct-id 1-11 `9(11)`→String; type 12-13 `X(2)`; cat 14-17 `9(4)`→String; bal 18-28 `S9(9)V99`→BigDecimal |
| `XREFFILE` in | 50 | [`CVACT03Y`](../../app/cpy/CVACT03Y.cpy) | card 1-16 `X(16)`; acct-id 26-36 `9(11)` (alternate key, duplicates allowed) |
| `DISCGRP` in | 50 | [`CVTRA02Y`](../../app/cpy/CVTRA02Y.cpy) | key 1-16 = group `X(10)`+type `X(2)`+cat `9(4)`; rate 17-22 `S9(4)V99`→BigDecimal |
| `ACCTFILE` in/out | 300 | [`CVACT01Y`](../../app/cpy/CVACT01Y.cpy) | acct-id 1-11; curr-bal 13-24 `S9(10)V99`; cyc-credit 77-88 `S9(10)V99`; cyc-debit 89-100 `S9(10)V99`; group-id 111-120 `X(10)` |
| `TRANSACT` out | 350 | [`CVTRA05Y`](../../app/cpy/CVTRA05Y.cpy) | all fields, see below |

Keyed files are loaded into `TreeMap`s by primary key (Java `String` order; keys are digits,
uppercase and spaces, so this matches BDB byte order). ACCTFILE is written back **all records in primary-key
order**, untouched records byte-for-byte, rewritten records with only the 3 balance fields re-encoded.

TRANSACT record (cols): id 1-16, type 17-18 `01`, cat 19-22 `0005`, source 23-32 `System` + spaces, desc 33-132
`Int. for a/c ` + acct-id + spaces, amt 133-143 `S9(9)V99` overpunch, merchant-id 144-152 `000000000`, name/city/
zip 153-262 spaces, card 263-278, orig-ts 279-304, proc-ts 305-330 (same value), filler 331-350 spaces.

## Processing rules (in order, reproduce exactly)

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`. Open/load TCATBALF, XREFFILE, DISCGRP, ACCTFILE, TRANSACT in
   that order; a missing/unreadable file → the COBOL's `ERROR OPENING ...` text (incl. F5 `DALY REJECTS FILE`
   for DISCGRP), `FILE STATUS IS: NNNN0035`, `ABENDING PROGRAM`, RC 16.
2. For each TCATBAL record in file order: print the raw 50-byte record.
3. Account break (acct-id ≠ last): if not first, **apply update** (rule 6) to the previous account. Total := 0.
   Look up ACCTFILE by acct-id; missing → print `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE`,
   `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM`, RC 16. Look up XREF by acct-id (first card in primary-key
   order); missing → same pattern with `ERROR READING XREF FILE`.
4. Rate: key = account group-id + type + cat. Not found → print `DISCLOSURE GROUP RECORD MISSING`,
   `TRY WITH DEFAULT GROUP CODE`, retry with `DEFAULT   `; not found → `ERROR READING DEFAULT DISCLOSURE GROUP`,
   `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM`, RC 16.
5. If rate ≠ 0: `int = trunc2(bal × rate / 1200)` (BigDecimal exact product, divide with `RoundingMode.DOWN`,
   scale 2), then keep the low 9 integer digits (high-order truncation, sign kept). `total = low9(total + int)`.
   Suffix := `(suffix + 1) mod 1,000,000`; write TRANSACT with id `PARM-DATE` + suffix `%06d`. The timestamp is
   read from the clock per write: `YYYY-MM-DD-HH.MM.SS.hh0000`.
6. Update: `curr-bal = low10(curr-bal + total)`, cyc-credit = 0, cyc-debit = 0.
7. At EOF: **no update for the last account** (F1). Print `END OF EXECUTION OF PROGRAM CBACT04C`, RC 0.

On abend, outputs keep what was done so far: TRANSACT holds records written, ACCTFILE holds rewrites applied
(as the harness unload shows). Oracle cases pin this.

Zoned encoding: last char overpunch, `{A-I` positive, `}J-R` negative; plain digit read as positive. Writes
always overpunch. Sign of a zero result: as produced by the oracle (R1), expected `{`.

## Preserved defects (fix deliberately later)

- F1 last account in TCATBAL order is never rewritten (interest charged, balance/cycle fields stale).
- F2 fees never computed (empty `1400-COMPUTE-FEES`).
- F3 interest truncated toward zero, not rounded.
- F4 lookup uses blank `ACCT-GROUP-ID` → always DEFAULT on sample data.
- F5 DISCGRP open-error text says `DALY REJECTS FILE`.
- F6 no size error: high-order digit loss on interest/total/balance; tran-id suffix wraps at 1,000,000.
- TRAN-DESC tail and FILLER are spaces (uninitialised WORKING-STORAGE, default dialect).

## Edge cases

| Case | Handling | Proven by |
|---|---|---|
| Rate 0 | no tran, account still updated at break | golden after-CBTRN02C (`03/0001`) |
| Negative balance, `-0.00` result | truncation toward zero, sign per oracle | oracle `negative` |
| Several cards per account | first card in primary-key order | oracle `multicard` |
| Group found (non-DEFAULT) | uses that rate, no MISSING lines | oracle `group-found` |
| Overflow (bal 999999999.99, rate 9999.99) | low-digit truncation | oracle `overflow` |
| Missing account / XREF / DEFAULT rate | messages + RC 16, partial outputs | oracles `abend-*` |
| Empty TCATBALF | banners only, nothing rewritten, 0 trans | oracle `empty` |
| Suffix wrap | `999999` → `000000` | unit test (too big for oracle) |

## Acceptance tests (→ intent AC)

| AC | Test |
|---|---|
| 1 | `mvn test` green, pom has only JUnit 5 + Surefire |
| 2, 3 | `GoldenEquivalenceTest`: TRANSACT, ACCTFILE, SYSOUT byte-equal for both scenarios, RC 0 |
| 4 | `OracleCasesTest` abend-* cases: RC 16 + SYSOUT + partial outputs; unit test for bad input |
| 5 | `InterestTest`: `bal×15/1200` truncation case where rounding differs (one of the 28 golden amounts, plus a synthetic `x.xx5` case) |
| 6 | `CharacterizationTest`: rate-0 category writes no tran, cycle fields zeroed |
| 7 | DEFAULT fallback (golden) + oracle `group-found` |
| 8 | `CharacterizationTest.lastAccountNotRewritten` (acct `00000000050` unchanged) |
| 9 | fixed clock → `2022-07-18-00.00.00.000000`; ids `PARM`+`000001…` |
| 10 | `git diff --exit-code baseline -- legacy/golden legacy/harness`; `golden.sh --check` |
