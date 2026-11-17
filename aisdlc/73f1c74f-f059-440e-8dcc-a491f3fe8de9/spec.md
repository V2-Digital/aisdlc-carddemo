# CR-1 Spec: `CBACT04C` (INTCALC) in Java 21

This spec covers behaviour only. For legacy facts and line references, see [intent.md](intent.md). The approver's
defaults are taken as decided: **atomic outputs on abend** (Q1) and **SYSOUT as a bonus check** (Q2).

## Invocation
`java -jar cbact04c.jar --parm <10 chars> --clock <YYYYMMDDHHMMSSss> --in <dir> --out <dir>`
- `--in` holds `TCATBALF.txt XREFFILE.txt ACCTFILE.txt DISCGRP.txt` (the harness DD names). `--out` receives
  `TRANSACT.txt` and `ACCTFILE.txt`. DISPLAY lines go to stdout.
- `--clock` is optional and defaults to the system clock in UTC. It matches `COB_CURRENT_DATE`.
- `--parm` is used as-is, padded or cut to 10 characters (legacy does not validate it, D6).

## Record I/O (canonical text, see `legacy/README.md` "Canonical text form")
- Read: strip a trailing `CR` and right-pad with spaces to LRECL. `cardxref.txt` lines are 36 bytes, and `tcatbal.txt` uses CRLF.
- Write: exactly LRECL bytes plus `LF` per record, in ISO-8859-1 (1 char = 1 byte).
- Zoned decimal `S9(n)V99`: n+2 digits, and the last character carries the overpunch sign (`{ A-I` +0..9, `} J-R` -0..9).
  Read as `BigDecimal` with scale 2. Write the same way. Positive and zero use `{`/`A-I`.

| File | Copybook | LRECL | Fields used (offset 0-based, len, PIC → Java) |
|---|---|---|---|
| TCATBALF in | `app/cpy/CVTRA01Y.cpy` | 50 | acct-id 0,11 `9(11)`→String; type 11,2 `X`; cat 13,4 `9(4)`→String; `TRAN-CAT-BAL` 17,11 `S9(9)V99`→BigDecimal |
| XREFFILE in | `app/cpy/CVACT03Y.cpy` | 50 | card-num 0,16 `X`; acct-id 25,11 (alt key; the first record per acct wins) |
| ACCTFILE in/out | `app/cpy/CVACT01Y.cpy` | 300 | acct-id 0,11; `CURR-BAL` 12,12; `CURR-CYC-CREDIT` 66,12; `CURR-CYC-DEBIT` 78,12 (all `S9(10)V99`); `GROUP-ID` 100,10 `X`. Every other byte is copied through |
| DISCGRP in | `app/cpy/CVTRA02Y.cpy` | 50 | key 0,16 (group 10 + type 2 + cat 4); `DIS-INT-RATE` 16,6 `S9(4)V99`→BigDecimal |
| TRANSACT out | `app/cpy/CVTRA05Y.cpy` | 350 | built from scratch, see R6 |

## Processing rules (in order)
- **R1** Load XREFFILE, ACCTFILE and DISCGRP into maps. Keep ACCTFILE as an ordered map of raw records keyed by
  acct-id. Print `START OF EXECUTION OF PROGRAM CBACT04C`.
- **R2** Stream TCATBALF in file order (key order). Print each raw 50-byte record.
- **R3** On an acct-id change: if a previous account exists, apply R7 to it. Then set total = 0 and look up the account,
  then the xref. If either is missing, print `ACCOUNT NOT FOUND: <acct-id>` and abend (R9).
- **R4** Rate key = `GROUP-ID` + type + cat. If it is missing, print `DISCLOSURE GROUP RECORD MISSING` and then
  `TRY WITH DEFAULT GROUP CODE`, and retry with `DEFAULT   ` (space-padded to 10). If that is also missing, print
  `ERROR READING DEFAULT DISCLOSURE GROUP` and abend.
- **R5** If rate = 0, write nothing for this row. Otherwise:
  `monthly = (bal × rate) / 1200`. Multiply exactly, then divide to scale 2 with `RoundingMode.DOWN` (truncate toward
  zero), and keep the **low 9 integer digits** (`S9(9)V99`, no SIZE ERROR → high-order truncation).
  Then `total = trunc9(total + monthly)`, then R6.
- **R6** TRANSACT record: `TRAN-ID` = parm(10) + suffix `%06d` (a run-wide counter, mod 1,000,000). Type `01`, cat `0005`,
  source `System    `, and desc `Int. for a/c ` + ACCT-ID(11) padded to 100. Amount = monthly (11-byte zoned). Merchant-id
  `000000000`. Name, city and zip are spaces. Card = `XREF-CARD-NUM`. Both timestamps are
  `yyyy-MM-dd-HH.mm.ss.hh0000` from the clock. FILLER(20) is spaces.
- **R7** Account update: `CURR-BAL = trunc10(CURR-BAL + total)`, and cycle credit = cycle debit = `0` (`00000000000{`).
- **R8** At EOF, **do not** apply R7 to the last account (D1). Write ACCTFILE (all input records, in key order) and
  TRANSACT. Print `END OF EXECUTION OF PROGRAM CBACT04C`, then exit 0.
- **R9** Abend: print `ABENDING PROGRAM`, write **no** output files (both are staged to temp files and renamed only
  on success), and exit 16.

## Edge cases
| Case | Handling |
|---|---|
| Negative balance, rate > 0 | Truncate toward zero (−14.5608 → −14.56). If the result is −0.00, use the sign that legacy writes (pinned by `legacy/tests/cbact04c-negative.sh`) |
| Rate 0 (e.g. DEFAULT 03/0001) | No transaction and no suffix increment. The account is still rewritten by R7 (unless it is the last) |
| Account with only rate-0 rows | Rewritten with balance unchanged and cycle fields zeroed |
| Empty TCATBALF | No transactions, ACCTFILE copied unchanged, exit 0 |
| Overflow of `S9(9)V99` / `S9(10)V99` | Silent high-order truncation, as COBOL does |
| Accounts not in TCATBALF | Copied through byte-for-byte |

## Preserved defects (fix only by a separate decision)
- **D1** The last account's interest is posted to TRANSACT but never applied to ACCTFILE, and its cycle fields are not reset.
- **D2** Interest is truncated, not rounded, for both signs.
- **D3** TCATBALF is not reset, so the next run accrues interest again on the same balance.
- **D4** Blank `GROUP-ID` → always DEFAULT on sample data.
- **D5** No fees are charged (`1400` is empty).
- **D6** The suffix wraps at 999,999, timestamps come from the clock and not PARM, and PARM length is not validated.

## Acceptance tests (AC = intent acceptance criterion)
| AC | Test |
|---|---|
| 1 | `mvn test`, Java 21, pom has only JUnit 5 + Surefire |
| 2 | `GoldenTest.stock`: `app/data/ASCII` (mapped to DD names), parm `2022071800`, clock `2022071800000000` → byte-compare with `legacy/golden/CBACT04C/{TRANSACT,ACCTFILE}.txt` (50 + 50) |
| 3 | `GoldenTest.afterCbtrn02c`: ACCTFILE + TCATBALF from `legacy/golden/CBTRN02C/` → `legacy/golden/CBACT04C-after-CBTRN02C/` |
| 4 | `DefectTest.lastAccountNotRewritten`: tran `2022071800000050` amount `0000000187G`, acct 50 record == input |
| 5 | `InterestTest` (truncation ±, overflow), `GroupFallbackTest`, `ZeroRateTest`, `SuffixTest` (runs across accounts), `ClockTest` (TS from clock ≠ PARM) |
| 6 | `AbendTest`: missing account, xref, or DEFAULT rate → exit 16, `--out` stays empty |
| 7 | `legacy/harness/golden.sh --check` passes unchanged |
| bonus | stdout == `SYSOUT.txt` for both scenarios (152 / 302 lines) |
