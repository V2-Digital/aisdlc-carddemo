# CR-1 Spec: `CBACT04C` in Java 21

This is a behaviour-preserving rewrite of `app/cbl/CBACT04C.cbl`. Legacy behaviour and findings F1–F9 are in [intent.md](intent.md). This spec covers only what the Java code must reproduce exactly.
Approver defaults are taken: **SYSOUT parity yes** (Q1), and **separate ACCTFILE output path** (Q2).

## Invocation
`java -jar cbact04c.jar <PARM> --TCATBALF=p --XREFFILE=p --ACCTFILE=p --DISCGRP=p --TRANSACT=p --ACCTOUT=p [--SYSOUT=p]`
- `PARM` is the 10-character date (`2022071800`). The `--DD` options are text-file paths. SYSOUT goes to stdout by default.
- Clock: `CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss` (16 digits, as in the harness). If it is unset, the system clock in UTC is used. The core class takes a `Clock`.
- Exit codes: `0` on success, `16` on abend.

## Files (canonical text: one record per line, LF, fixed width)
On input, strip a trailing `\r` and right-pad short lines with spaces to the LRECL (`cardxref.txt` lines are 36 bytes). Output lines are exactly LRECL bytes followed by `\n`.

| DD | Copybook | LRECL | Load as |
|---|---|---|---|
| TCATBALF | `app/cpy/CVTRA01Y.cpy` | 50 | sorted by key cols 1-17 (KSDS order) |
| XREFFILE | `app/cpy/CVACT03Y.cpy` | 50 | map acct-id → first record in file order (F8) |
| ACCTFILE | `app/cpy/CVACT01Y.cpy` | 300 | `TreeMap` by ACCT-ID (cols 1-11) |
| DISCGRP | `app/cpy/CVTRA02Y.cpy` | 50 | map by key cols 1-16 |
| TRANSACT (out) | `app/cpy/CVTRA05Y.cpy` | 350 | in write order |
| ACCTOUT (out) | CVACT01Y | 300 | every account, in key order (AC 9) |

Fields used (1-based columns). Zoned fields are overpunch-signed and map to `BigDecimal`. All other fields are kept as raw `String` bytes.

| Record | Field | Cols | PIC | Use |
|---|---|---|---|---|
| TCATBAL | acct / type / cat | 1-11 / 12-13 / 14-17 | 9(11) X(2) 9(4) | break key, rate key |
| TCATBAL | TRAN-CAT-BAL | 18-28 | S9(9)V99 | read |
| XREF | XREF-CARD-NUM / XREF-ACCT-ID | 1-16 / 26-36 | X(16) 9(11) | read |
| ACCT | ACCT-CURR-BAL | 13-24 | S9(10)V99 | read + write |
| ACCT | CURR-CYC-CREDIT / -DEBIT | 79-90 / 91-102 | S9(10)V99 | write `0` |
| ACCT | ACCT-GROUP-ID | 113-122 | X(10) | rate key |
| DISCGRP | DIS-INT-RATE | 17-22 | S9(4)V99 | read |

A rewrite replaces only cols 13-24 and 79-102. Every other byte is copied through unchanged.

## Zoned codec
- Decode: the last byte carries the sign and digit. `{`/`A`-`I` mean +0..9 and `}`/`J`-`R` mean −0..9. A plain digit counts as positive.
- Encode: take the absolute value **mod 10^intDigits** (COBOL high-order truncation, F5), then scale 2 with `RoundingMode.DOWN`. Zero is always positive (`{`).

## Processing (in order)
1. SYSOUT `START OF EXECUTION OF PROGRAM CBACT04C`.
2. For each TCATBAL record in key order, SYSOUT the 50-byte record, then:
   1. **Account change** (acct ≠ last): if this is not the first record, run *update* on the previous account. Then set `total = 0` and look up the account and xref (see Errors).
   2. **Rate**: look up key `ACCT-GROUP-ID + type + cat`. On a miss, SYSOUT `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with `DEFAULT   ` (padded to 10) + type + cat.
   3. **If rate ≠ 0**:
      - `monthly = trunc(bal × rate / 1200)` to 2 dp, toward zero, mod 10^9 (`S9(9)V99`).
      - `total = (total + monthly)`, mod 10^9.
      - `suffix = (suffix + 1) mod 10^6`.
      - Write a TRAN record:
        - id = PARM + suffix (6 digits, zero-padded); type `01`; cat `0005`; source `System` (padded to 10)
        - desc = `Int. for a/c ` + ACCT-ID, space-padded to 100
        - amt = monthly; merchant id `000000000`; name, city and zip all spaces
        - card = XREF-CARD-NUM
        - ORIG-TS = PROC-TS = `yyyy-MM-dd-HH.mm.ss.hh0000` from the clock (hh = hundredths)
        - cols 331-350 are spaces
      - The fees step is a no-op (F3).
3. *Update(account)*: `CURR-BAL = (CURR-BAL + total)` mod 10^10, then CYC-CREDIT = CYC-DEBIT = 0, and store the result in the map.
4. At EOF, **do not** update the last account (F1). Write ACCTOUT and SYSOUT `END OF EXECUTION OF PROGRAM CBACT04C`. Exit 0.

## Errors (SYSOUT lines, then `ABENDING PROGRAM`; stderr `CEE3ABD: ABEND U0999`; exit 16)
| Case | SYSOUT before `ABENDING PROGRAM` |
|---|---|
| account missing | `ACCOUNT NOT FOUND: <acct11>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023` |
| xref missing | `ACCOUNT NOT FOUND: <acct11>`, `ERROR READING XREF FILE`, `FILE STATUS IS: NNNN0023` |
| DEFAULT rate missing | the two MISSING/TRY lines, `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023` |
| input file unreadable | open message from :245-318, e.g. DISCGRP prints `ERROR OPENING DALY REJECTS FILE` (F7); then `FILE STATUS IS: NNNN0035` |

Outputs written before an abend do not need to match the legacy partial outputs. Exit 16 is the contract.

## Preserved defects (fix only by separate CR)
- F1: the last account in TCATBAL order is never rewritten. Its interest is lost and its cycle fields are not reset.
- F2: interest is truncated, not rounded.
- F3: fees are never computed.
- F4: timestamps come from the clock, not the PARM, with hundredths precision followed by `0000`.
- F5: silent modulo overflow on monthly, total, CURR-BAL, and the 6-digit suffix.
- F6: group-id comes from cols 113-122, which are blank in the sample data, so every lookup falls back to DEFAULT.
- F7: the DISCGRP open error says "DALY REJECTS".
- F8: the first xref card per account is used.

## Edge cases
- Zero rate: no TRAN is written and the suffix does not advance. The account is still updated (cycle fields zeroed).
- Zero total: the account is still rewritten. Its balance stays the same and its cycles go to 0.
- Accounts with no TCATBAL rows are output unchanged.
- Empty TCATBALF: TRANSACT is empty, ACCTOUT equals the input, exit 0.
- Negative balance × rate is truncated toward zero (−0.005 → `0.00`, encoded `{`).

## Acceptance tests → intent AC
| Test | AC |
|---|---|
| `mvn test` on JDK 21, JUnit 5 only | 1 |
| `GoldenEquivalenceTest.stock`: `app/data/ASCII` inputs, PARM `2022071800`, clock `2022071800000000`; TRANSACT, ACCTOUT, SYSOUT == `legacy/golden/CBACT04C/` | 2, 4 |
| `GoldenEquivalenceTest.afterPosting`: ACCTFILE + TCATBALF from `golden/CBTRN02C/`; same diff against `golden/CBACT04C-after-CBTRN02C/` | 3, 4 |
| `truncatesNotRounds` (e.g. 1945.87 × 15 / 1200 = 24.323… → 24.32; and a .xx5+ case) | 5 |
| `lastAccountNotUpdated`: acct `00000000050` row identical to the input, while a TRAN with amount 18.77 exists | 6 |
| `groupHit`, `groupMissFallsBackToDefault`, `zeroRateNoTranNoSuffix`, `zeroTotalStillZeroesCycles`, `untouchedAccounts` | 7 |
| `missingAccount`/`missingXref`/`missingDefault` → exit 16 and the SYSOUT tail above | 8 |
| `ZonedTest`: overpunch round-trip, −0 → `{`, mod overflow on 9/10 digits, suffix wrap at 999,999 | 5, F5 |
| ACCTOUT count and key order == input | 9 |
