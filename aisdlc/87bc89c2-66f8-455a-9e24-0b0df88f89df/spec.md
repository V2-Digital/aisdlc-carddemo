# CR-1 Spec: `CBACT04C` (INTCALC) in Java 21

Legacy behaviour, line refs and defects D1–D7 / R1–R4: see [intent.md](intent.md). This spec only pins what the
Java code must do byte for byte. Approver defaults are assumed: **SYSOUT and RC are also matched**, and defects go to
separate CRs.

## Interface

- `java -jar cbact04c.jar <PARM>`. PARM = 10-char date prefix (`2022071800`). Shorter PARM is right-padded with
  spaces, longer is cut to 10. Legacy never checks the length, so this is the only defined reading.
- Files are bound by env var `DD_<ddname>` = path (the GnuCOBOL convention the harness uses).
- Clock: env `CARDDEMO_CURRENT_DATE` = `YYYYMMDDHHMMSSss` (same as the harness). If unset, use the system clock in UTC.
  Library entry point `Cbact04c.run(Config) → int rc` takes the PARM, paths, `Clock` and a SYSOUT `PrintStream`.
- SYSOUT goes to stdout. Exit status = rc (0, or 16 on abend).

## Files (canonical text: one record per line, LF, fixed width)

On read: strip a trailing CR and right-pad short lines with spaces to LRECL (cardxref is 36 bytes, tcatbal is CRLF).
This matches `cdfiles LOAD`. KSDS inputs are sorted by primary key in memory. Write: exactly LRECL bytes + LF.

| DD | Copybook | Use | Fields used (0-based offset, len, PIC → Java) |
|---|---|---|---|
| `TCATBALF` | `app/cpy/CVTRA01Y.cpy` | read in key order | acct-id 0,11 `9(11)`→String; type 11,2; cat 13,4; bal 17,11 `S9(09)V99`→BigDecimal |
| `XREFFILE` | `app/cpy/CVACT03Y.cpy` | lookup by acct-id 25,11 | card-num 0,16 → String. On duplicates, first in card-num order (R4) |
| `DISCGRP` | `app/cpy/CVTRA02Y.cpy` | lookup by key 0,16 | group 0,10; type 10,2; cat 12,4; rate 16,6 `S9(04)V99`→BigDecimal |
| `ACCTFILE` | `app/cpy/CVACT01Y.cpy` | I-O, rewritten in place | curr-bal 12,12; cyc-credit 78,12; cyc-debit 90,12 (all `S9(10)V99`); group-id 112,10 |
| `TRANSACT` | `app/cpy/CVTRA05Y.cpy` | output, write order | all 350 bytes, see rule 6 |

Records are kept as `byte[]`/`String` images. Only the fields above are decoded, and a rewrite patches exactly those
bytes, so all other bytes come through unchanged. ACCTFILE is written back as all records in primary-key order.

**Zoned decimal codec.** Last char carries the sign: `{ A–I` = +0..9, `} J–R` = −0..9. Encode positive/zero with `{A–I`
and negative with `}J–R`. A zero result is always `{` (no −0). Use `BigDecimal` with a fixed scale, never `double`.

## Processing rules (in order)

1. DISPLAY `START OF EXECUTION OF PROGRAM CBACT04C`. Open/load TCATBALF, XREFFILE, DISCGRP, ACCTFILE, TRANSACT in
   that order. A missing or unreadable file prints the legacy open-error text for that file (DISCGRP keeps
   `ERROR OPENING DALY REJECTS FILE`, D6), then a `FILE STATUS IS: NNNN00xx` line, then abend (not golden-tested).
2. For each TCATBAL record: DISPLAY the raw 50-byte record. **Account break** when acct-id ≠ last acct-id:
   - unless this is the first record, rewrite the previous account (rule 7);
   - set total = 0, read ACCTFILE by acct-id, then read XREF by acct-id.
   - Not found → `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE` (or `… XREF FILE`),
     `FILE STATUS IS: NNNN0023`, abend.
3. Rate: look up (ACCT-GROUP-ID, type, cat). On a miss, DISPLAY `DISCLOSURE GROUP RECORD MISSING` and
   `TRY WITH DEFAULT GROUP CODE`, then look up (`DEFAULT   `, type, cat). If that also misses:
   `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, abend.
4. If rate ≠ 0: `monthly = trunc((bal × rate) / 1200)` to scale 2, **toward zero** (`RoundingMode.DOWN`). Then
   keep it modulo 10^9 in the integer part (no SIZE ERROR, D3). `total = (total + monthly)`, also mod 10^9. Write the
   transaction (rule 6). The fees hook is a no-op (D5). If rate = 0, write nothing.
5. EOF: **do not rewrite the last account (D1)**. Write TRANSACT + ACCTFILE, DISPLAY
   `END OF EXECUTION OF PROGRAM CBACT04C`, rc 0.
6. TRANSACT record (350 bytes, all of it spaces unless set): id 0,16 = PARM(10) + 6-digit counter (run-wide, starts at
   1, wraps 999999→000000, D3); type `01` @16; cat `0005` @18; source `System` @22 (X(10)); desc @32 =
   `Int. for a/c ` + 11-digit acct-id, rest spaces; amt @132 zoned `S9(09)V99`; merchant-id `000000000` @143; name,
   city, zip spaces; card @262 = xref card; orig-ts @278 and proc-ts @304 = `YYYY-MM-DD-HH.MM.SS.hh0000`, read once
   per transaction from the clock; filler 330–349 spaces (R1).
7. Account rewrite: `curr-bal = (curr-bal + total)` mod 10^10, then cyc-credit = 0 and cyc-debit = 0 (encoded
   `00000000000{`).
8. **Abend**: DISPLAY `ABENDING PROGRAM`, print `CEE3ABD: ABEND U0999` to stderr, rc 16. Java writes **no** output
   files on abend, so ACCTFILE is left as input. Legacy leaves partial, environment-dependent output; nothing relies
   on it.

## Edge cases

| Case | Handling |
|---|---|
| Negative TCATBAL balance | Negative interest, truncated toward zero (−0.004 → `{` zero). No guard (D7) |
| Monthly interest truncates to 0 with rate ≠ 0 | Transaction is still written with amount 0 |
| Rate 0 category | No transaction and no counter increment. It still triggers the account break and rewrite |
| Account in ACCTFILE but not in TCATBAL | Byte-identical in output |
| ACCT-GROUP-ID blank (all stock data) | Miss → DEFAULT fallback with 2 SYSOUT lines per record (D4) |
| Empty TCATBAL | No rewrites, empty TRANSACT, rc 0 |

**Preserved defects:** D1 last account not rewritten · D2 truncation instead of rounding · D3 silent overflow and
counter wrap · D4 blank group-id means DEFAULT for every account · D5 empty fees hook · D6 wrong DISCGRP open-error
text · D7 negative interest on credit balances · R1 filler is spaces (not `x'00'`).

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -q verify`, pom has JUnit Jupiter + Surefire only | 1 |
| `GoldenEquivalenceTest.stock`: TRANSACT, ACCTFILE, SYSOUT byte-equal to `legacy/golden/CBACT04C/`, rc = `RC.txt` | 2, 4 |
| `GoldenEquivalenceTest.afterCbtrn02c`: same, using ACCTFILE+TCATBALF from `golden/CBTRN02C/` | 3, 4 |
| `InterestTest.truncatesNotRounds` (golden a/c 1: 1164.87 × 15 / 1200 = 14.560875 → 14.56, `0000000145F`) and a synthetic `…5` case where half-up gives a different result; negative balance case | 5 |
| `CharacterizationTest.lastAccountNotRewritten`: 2 accounts, both get a txn, only the first is updated | 6 |
| `CharacterizationTest.defaultFallback` and abends for missing account, xref or DEFAULT: rc 16, SYSOUT lines as in rule 2/3, no output files | 7 |
| `CharacterizationTest.zeroRateNoTxnButRewrite` | 8 |
| `ZonedTest`: round-trip of every sign char, −0 input, mod wrap | 2, 3 |
| `legacy/harness/golden.sh --check` passes; `git diff --stat baseline -- legacy/golden legacy/harness .github` is empty | 9 |
