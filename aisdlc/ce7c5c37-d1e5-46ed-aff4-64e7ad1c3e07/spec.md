# CR-1 Spec — `CBACT04C` in Java 21 (behaviour-preserving)

Legacy behaviour, file table and defects D1-D9: [intent.md](intent.md). Source: `app/cbl/CBACT04C.cbl`.
This spec only adds what the Java code must do to the byte.

## Interface

`java -jar cbact04c.jar` with env/args:
- `--in <dir>`: reads `TCATBALF.txt`, `XREFFILE.txt`, `DISCGRP.txt`, `ACCTFILE.txt`.
- `--out <dir>`: writes `TRANSACT.txt`, `ACCTFILE.txt`, `SYSOUT.txt`, `RC.txt` (same names as the harness).
- `--parm <10 chars>` (PARM-DATE, default `2022071800`); `--clock YYYYMMDDHHMMSSss` (default: system clock, UTC).
- Process exit code = RC (0, or 16 on abend). Abend also prints `CEE3ABD: ABEND U0999` to stderr.

Input lines: strip a trailing `\r`, right-pad with spaces to LRECL (cardxref is 36 bytes, tcatbal has CRLF).
Output lines: exactly LRECL bytes + `\n`. Charset ISO-8859-1 (1 byte = 1 char).

## Fields used (offset 0-based, length)

| File / copybook | Field | Off | Len | PIC | Java |
|---|---|---|---|---|---|
| TCATBALF `app/cpy/CVTRA01Y.cpy` | key (acct+type+cat) | 0 | 17 | 9(11) X(2) 9(4) | `String` |
| | `TRAN-CAT-BAL` | 17 | 11 | S9(09)V99 | `BigDecimal` scale 2 |
| XREFFILE `app/cpy/CVACT03Y.cpy` | `XREF-CARD-NUM` | 0 | 16 | X(16) | `String` |
| | `XREF-ACCT-ID` (alt key) | 25 | 11 | 9(11) | `String` |
| DISCGRP `app/cpy/CVTRA02Y.cpy` | key group+type+cat | 0 | 16 | X(10) X(2) 9(4) | `String` |
| | `DIS-INT-RATE` | 16 | 6 | S9(04)V99 | `BigDecimal` scale 2 |
| ACCTFILE `app/cpy/CVACT01Y.cpy` | `ACCT-ID` (key) | 0 | 11 | 9(11) | `String` |
| | `ACCT-CURR-BAL` | 12 | 12 | S9(10)V99 | `BigDecimal` |
| | `ACCT-CURR-CYC-CREDIT` | 78 | 12 | S9(10)V99 | set 0 |
| | `ACCT-CURR-CYC-DEBIT` | 90 | 12 | S9(10)V99 | set 0 |
| | `ACCT-GROUP-ID` | 112 | 10 | X(10) | `String` |
| TRANSACT `app/cpy/CVTRA05Y.cpy` | whole record | 0 | 350 | see copybook | built per rule 6 |

All other bytes of ACCTFILE records are copied through unchanged. Keys are compared as raw strings.

## Processing rules (in order)

1. SYSOUT `START OF EXECUTION OF PROGRAM CBACT04C`. Load ACCTFILE (TreeMap by id), XREFFILE (first record per acct id, in primary-key order — D9), DISCGRP (map by 16-byte key). Missing/unreadable input → open-error message per `:234-323` + `FILE STATUS IS: NNNN00xx` + `ABENDING PROGRAM`, RC 16.
2. For each TCATBALF record in key order: SYSOUT the raw 50-byte record.
3. If acct id ≠ last acct id: if not first record, rewrite previous account (rule 7). Total-int := 0. Look up account, then xref. Account missing → `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM`. Xref missing → same with `ERROR READING XREF FILE`.
4. Rate: key = `ACCT-GROUP-ID`(10) + type + cat. Missing → SYSOUT `DISCLOSURE GROUP RECORD MISSING`, `TRY WITH DEFAULT GROUP CODE`, retry with `DEFAULT` padded to 10. Retry missing → `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM`.
5. If rate ≠ 0: `monthly = bal × rate / 1200`, exact, then **truncate toward zero to 2 dp** (`RoundingMode.DOWN`, D3), then keep the low 9 integer digits (COBOL high-order truncation, no SIZE ERROR). `total = trunc9(total + monthly)`. Write a transaction (rule 6). Fees: no-op (D4).
6. Transaction: `suffix = (suffix + 1) mod 10^6` (D7); TRAN-ID = parm + `%06d`; type `01`; cat `0005`; source `System` padded 10; desc `Int. for a/c ` + ACCT-ID padded to 100 with spaces (D8); amount = monthly as zoned S9(09)V99; merchant id `000000000`; name/city/zip spaces; card = xref card num; orig = proc = `YYYY-MM-DD-HH.MM.SS.hh0000` from clock; FILLER 20 spaces.
7. Rewrite: `bal = trunc10(bal + total)`, cycle credit = cycle debit = +0, store back in the map (D6: even when total is 0).
8. EOF: **do not rewrite the last account** (D1). SYSOUT `END OF EXECUTION OF PROGRAM CBACT04C`. Write ACCTFILE.txt (all records, key order), RC 0.

On abend, outputs reflect the state at the abend point (transactions written so far, accounts rewritten so far), as the harness unload does. Not covered by golden.

## Zoned decimal codec

Unsigned digits, last digit overpunched: `{`,`A`-`I` = +0..9; `}`,`J`-`R` = −0..9. Encode positive/zero with `{A-I`, negative with `}J-R`; a zero result is encoded positive (`{`). Decoding also accepts plain digits as positive. Invalid char → `IllegalArgumentException` (not seen in data).

## Edge cases

| Case | Handling |
|---|---|
| Empty TCATBALF | No rewrites, no transactions, RC 0 |
| Negative balance | Interest truncated toward zero (−0.005 → 0.00, encoded `{`) |
| Rate 0 (e.g. type 03) | No transaction, no total change, account still rewritten (D6) |
| Several cards per account | First xref in primary-key order (D9) |
| > 999,999 transactions | Suffix wraps to `000000` (D7) |
| Group id blank (all data) | Always falls back to DEFAULT (D2) |

## Preserved defects (one line each, fix later deliberately)

D1 last account never rewritten · D2 group id read from blank field · D3 truncation not rounding · D4 fees empty · D5 DISCGRP open error says `DALY REJECTS FILE` · D6 rewrite at zero interest · D7 suffix wraps · D8 desc/FILLER = spaces · D9 first duplicate xref.

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -q verify` green, only JUnit 5 + Surefire | 1 |
| `GoldenEquivalenceTest`: stock inputs (`app/data/ASCII`) vs `legacy/golden/CBACT04C` — TRANSACT, ACCTFILE, SYSOUT, RC byte-equal | 2, 4 |
| same with ACCTFILE+TCATBALF from `legacy/golden/CBTRN02C` vs `legacy/golden/CBACT04C-after-CBTRN02C` | 3, 4 |
| `InterestTest`: 2339.97 @15 → 29.24; −2339.97 @15 → −29.24 | 5 |
| `ZeroRateTest`: type 03 only → no tran, account cycle fields zeroed, bal unchanged | 6 |
| `CharacterizationTest.lastAccountNotRewritten` (D1) | 7 |
| `…fallbackToDefault`, `…missingDefaultAbends`, `…missingAccountAbends`, `…missingXrefAbends` (SYSOUT + RC 16) | 8 |
| `…tranIdCounterSpansAccounts`, `…suffixWraps` (D7) | 9 |
| `ZonedTest`: round-trip all 20 sign chars, zero, max/min | R1 |
| `git diff --stat baseline -- legacy/golden legacy/harness` empty; `golden.sh --check` passes | 10 |
