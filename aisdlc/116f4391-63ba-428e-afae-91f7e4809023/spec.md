# CR-1 Spec: CBACT04C in Java 21 (behaviour-preserving)

Background, file inventory, defect evidence: [intent.md](intent.md). Approver defaults Q1–Q3 are assumed: SYSOUT and RC parity required, clock injectable, D1–D4 preserved.

## Inputs and outputs (canonical text, see `legacy/README.md`)
One record per line, LF, ISO-8859-1, fixed width (strip `\r`, right-pad short lines with spaces).

| DD | Copybook | LRECL | Java handling |
|---|---|---|---|
| `TCATBALF` in | `app/cpy/CVTRA01Y.cpy` | 50 | sort by key `0-16`, read sequentially |
| `XREFFILE` in | `app/cpy/CVACT03Y.cpy` | 50 | map acct-id `25-35` → first card `0-15` in card order (F3) |
| `DISCGRP` in | `app/cpy/CVTRA02Y.cpy` | 50 | map key `0-15` → rate |
| `ACCTFILE` I-O | `app/cpy/CVACT01Y.cpy` | 300 | `TreeMap` by `0-10`; rewritten in place at close, key order |
| `TRANSACT` out | `app/cpy/CVTRA05Y.cpy` | 350 | written in processing order |
| SYSOUT | — | var | stdout, one DISPLAY per line, no padding |

Fields read or written (0-based offset, length, PIC → Java):
- TCATBAL: `TRANCAT-ACCT-ID` 0,11 `9(11)` String · `TRANCAT-TYPE-CD` 11,2 · `TRANCAT-CD` 13,4 · `TRAN-CAT-BAL` 17,11 `S9(9)V99` BigDecimal scale 2.
- DISCGRP: key 0,16 String · `DIS-INT-RATE` 16,6 `S9(4)V99` BigDecimal scale 2.
- ACCOUNT: `ACCT-ID` 0,11 · `ACCT-CURR-BAL` 12,12 `S9(10)V99` · `ACCT-CURR-CYC-CREDIT` 75,12 · `ACCT-CURR-CYC-DEBIT` 87,12 (both `S9(10)V99`) · `ACCT-GROUP-ID` 109,10 `X(10)` (taken as-is, blanks included). Every other byte is copied unchanged.
- TRAN (written): `TRAN-ID` 0,16 · type 16,2 `01` · cat 18,4 `0005` · source 22,10 `System    ` · desc 32,100 `Int. for a/c <ACCT-ID>` space-padded · `TRAN-AMT` 132,11 `S9(9)V99` · merchant id 143,9 `000000000` · name/city/zip 152,110 spaces · card 262,16 · orig-ts 278,26 · proc-ts 304,26 · filler 330,20 spaces (F5).

**Zoned codec.** The last char carries the sign: `{A-I` = +0..9, `}J-R` = −0..9, a plain digit = +. On write, ≥0 → `{A-I`, <0 → `}J-R`; zero is always `{`. Unsigned `9(n)` fields are plain digits.

## Processing rules (mirror `app/cbl/CBACT04C.cbl:181-232`)
1. Print `START OF EXECUTION OF PROGRAM CBACT04C`. Open TCATBALF, XREFFILE, DISCGRP, ACCTFILE, TRANSACT in that order. A missing or unreadable file prints that file's open message (`:245-318`, D4 text verbatim), then the abend sequence with status `35`.
2. For each TCATBAL record in key order, print the raw 50-byte record.
3. If the acct id differs from the last one: rewrite the previous account (rule 7) unless this is the first, set total = 0, then look up the account and the xref (rule 8 on a miss).
4. Look up the rate with key `ACCT-GROUP-ID + type + cat`. On a miss, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with group `DEFAULT` padded to 10. A second miss → rule 8 with `ERROR READING DEFAULT DISCLOSURE GROUP`.
5. If rate ≠ 0: `monthly = (bal × rate).divide(1200, 2, RoundingMode.DOWN)` (truncation toward zero, D3). Store it mod 10^9 with the sign kept, i.e. S9(9)V99 high-order truncation (F2). Then `total = total + monthly`, same S9(9)V99 truncation.
6. Write the TRAN: `seq = (seq + 1) mod 10^6`; `TRAN-ID = parm10 + seq` zero-padded to 6. The timestamp is `yyyy-MM-dd-HH.mm.ss.hh0000` from the clock, read once per record. Fees: no-op (D2).
7. Rewrite: `ACCT-CURR-BAL = (bal + total)` mod 10^10 at 2 dp, sign kept. Cycle credit and debit = 0.
8. Abend: print `ACCOUNT NOT FOUND: <11-digit id>` (account and xref misses only), then `ERROR READING ACCOUNT FILE` / `ERROR READING XREF FILE` / the rule-4 message, then `FILE STATUS IS: NNNN00<ss>` (`23` = key not found), then `ABENDING PROGRAM`. stderr gets `CEE3ABD: ABEND U0999`; exit 16. Output files are undefined after an abend (the harness doesn't export them either).
9. At EOF, **do not rewrite the last account (D1)**. Write ACCTFILE, print `END OF EXECUTION OF PROGRAM CBACT04C`, exit 0.

**PARM (F4):** the argument is right-padded with spaces or truncated to 10 chars.
**Clock (Q2):** `--clock YYYYMMDDHHMMSSss` > env `CARDDEMO_CURRENT_DATE` > system clock (UTC).

**CLI:** `java -jar cbact04c.jar --parm 2022071800 --TCATBALF <f> --XREFFILE <f> --DISCGRP <f> --ACCTFILE <f> --TRANSACT <f> [--clock …]`

## Edge cases
| Case | Handling |
|---|---|
| Empty TCATBALF | Prints START/END only. TRANSACT is empty, ACCTFILE is unchanged, RC 0. |
| Rate 0 (e.g. DEFAULT type 03) | No TRAN, seq unchanged, total unchanged |
| Negative balance (R1) | Interest is negative and truncated toward 0 (−1.234 → −1.23). Sign written as `}J-R`. |
| Non-blank group with its own DISCGRP row (F1) | Uses that rate, no MISSING lines |
| Account with several cards (F3) | Lowest card number wins (assumption) |
| Overflow of monthly/total/balance or seq > 999,999 (F2) | Silent high-order truncation / wrap, no error |

## Preserved defects (fix later, deliberately)
- **D1**: the last account in TCATBALF is never rewritten. Its interest is lost and its cycle fields are not zeroed.
- **D2**: `1400-COMPUTE-FEES` is empty, so no fees are charged.
- **D3**: interest is truncated to 2 dp instead of rounded.
- **D4**: a DISCGRP open failure prints `ERROR OPENING DALY REJECTS FILE`.
- **F2**: no size-error handling. Amounts and the tran seq wrap silently.

## Acceptance tests (→ intent AC #)
| Test | AC |
|---|---|
| `mvn test` green, JUnit 5 is the only dependency | 1 |
| `GoldenEquivalenceTest.stock`: TRANSACT, ACCTFILE, SYSOUT, RC byte-equal to `legacy/golden/CBACT04C/` | 2, 4, 6 |
| `GoldenEquivalenceTest.afterCbtrn02c`: inputs ACCTFILE+TCATBALF from `legacy/golden/CBTRN02C/` → `legacy/golden/CBACT04C-after-CBTRN02C/` | 3, 4, 6 |
| `InterestTest`: truncation case that rounding would change (e.g. bal 1164.67 × 15.00 / 1200 = 14.558 → 14.55, rounded 14.56), negative sign, no `double` | 5 |
| `RateLookupTest`: group-specific hit, DEFAULT fallback; missing DEFAULT / account / xref → messages + exit 16 | 7 |
| `DefectCharacterizationTest.d1_lastAccountNotRewritten`, `d2_noFeesCharged`, `d3_interestTruncatedNotRounded`, `d4_discgrpOpenErrorSaysDalyRejects` | 8 |
| `CliTest`: main with 5 paths + parm + clock writes the files and returns the exit code | 9 |
| `legacy/tests/cbact04c-java-vs-cobol.sh`: crafted inputs (negative balances, group-specific rate, rounding-sensitive amounts) run through COBOL and Java and diffed | 5, 7, R1, F1 |
