# CR-1 Spec: `CBACT04C` in Java 21

Behaviour-preserving rewrite. What the legacy code does (with line refs) is in [intent.md](intent.md). This spec defines only what the Java must reproduce. Defaults Q1–Q4 from the intent apply. Q3 is the only deviation: the PARM must be exactly 10 characters, otherwise exit 16.

## Invocation

`java -jar cbact04c.jar <PARM>`. Paths are bound by env vars named after the DD: `DD_TCATBALF`, `DD_XREFFILE`, `DD_ACCTFILE`, `DD_DISCGRP`, `DD_TRANSACT`. The clock comes from `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`, the same as the harness); if unset, the system clock in UTC. SYSOUT goes to stdout. The exit code is the RC.

## Files (canonical text: fixed width, LF, trailing spaces kept)

| DD | In/Out | LRECL | Copybook | Java access |
|---|---|---|---|---|
| `TCATBALF` | in | 50 | `app/cpy/CVTRA01Y.cpy` | stream lines in file order (already in key order) |
| `XREFFILE` | in | 50 | `app/cpy/CVACT03Y.cpy` | map acct-id → **first** record in file (primary-key) order |
| `ACCTFILE` | in → out | 300 | `app/cpy/CVACT01Y.cpy` | `LinkedHashMap` acct-id → raw line; written back in input order |
| `DISCGRP` | in | 50 | `app/cpy/CVTRA02Y.cpy` | map 16-byte key → rate |
| `TRANSACT` | out | 350 | `app/cpy/CVTRA05Y.cpy` | appended in write order |

Fields used (offsets 0-based). Zoned `S9` fields use an overpunch last byte: `{A-I` = +0..9, `}J-R` = −0..9.

| Field | Off | Len | PIC | Java |
|---|---|---|---|---|
| `TRANCAT-ACCT-ID` / `-TYPE-CD` / `-CD` | 0 / 11 / 13 | 11 / 2 / 4 | 9(11) / X(2) / 9(4) | String |
| `TRAN-CAT-BAL` | 17 | 11 | S9(9)V99 | BigDecimal scale 2 |
| `XREF-CARD-NUM` / `XREF-ACCT-ID` | 0 / 25 | 16 / 11 | X / 9 | String |
| `ACCT-ID` | 0 | 11 | 9(11) | String |
| `ACCT-CURR-BAL` | 12 | 12 | S9(10)V99 | BigDecimal scale 2 |
| `ACCT-CURR-CYC-CREDIT` / `-DEBIT` | 78 / 90 | 12 / 12 | S9(10)V99 | written as `00000000000{` |
| `ACCT-GROUP-ID` | 112 | 10 | X(10) | String (DISCGRP key part) |
| `DIS-GROUP-KEY` / `DIS-INT-RATE` | 0 / 16 | 16 / 6 | X(16) / S9(4)V99 | String / BigDecimal scale 2 |

All other ACCTFILE bytes are passed through untouched; only bytes 12-23 and 78-101 are spliced.

TRANSACT record, built as 350 spaces and then filled: `TRAN-ID`(0,16) = PARM + suffix `%06d`; type(16,2) `01`; cat(18,4) `0005`; source(22,10) `System    `; desc(32,100) `Int. for a/c ` + ACCT-ID, space padded; amt(132,11) S9(9)V99; merchant-id(143,9) `000000000`; name/city/zip(152,110) spaces; card(262,16); orig-ts(278,26) and proc-ts(304,26) = `YYYY-MM-DD-HH.MM.SS.hh0000`; filler(330,20) spaces.

## Processing rules (in order)

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`. Validate the PARM (Q3). Load and open the inputs. If a file is missing or unreadable, print the matching legacy open-error text (DISCGRP: `ERROR OPENING DALY REJECTS FILE`, D8), `FILE STATUS IS: NNNN` + status, then the abend sequence (R9).
2. For each TCATBALF line: print it verbatim (50 bytes).
3. If acct-id ≠ the previous acct-id: if this is not the first account, rewrite the previous account (R7). Then set total = 0, look up the account and look up the XREF by acct-id.
   - Account not found: print `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`, then abend.
   - XREF not found: the same with `ERROR READING XREF FILE`.
4. Rate: key = `ACCT-GROUP-ID` + type + cat. If the key is missing, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then use key `DEFAULT   ` + type + cat. If DEFAULT is also missing, print `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, then abend.
5. If rate ≠ 0: `monthly = trunc2(bal × rate / 1200)`, computed exactly in BigDecimal, scaled to 2 with `RoundingMode.DOWN` (toward zero). Then keep the low 9 integer digits (D6). Then `total = wrap9(total + monthly)`. Increment the suffix mod 1,000,000 (D6) and write the transaction with the timestamp taken from the injected clock.
6. Rate = 0: no transaction, and the suffix is unchanged.
7. Rewrite the account: `CURR-BAL = wrap10(CURR-BAL + total)`, and set cycle credit/debit to 0. Encode with overpunch, so −0 is written as `}`. The result is −0 only if a sign-preserving wrap gives it; normal zero is `{`.
8. At EOF, **do not** rewrite the last account (D1). Write ACCTFILE: every input record, in input order, with the updates applied. Write TRANSACT. Print `END OF EXECUTION OF PROGRAM CBACT04C`. Exit 0.
9. Abend: print `ABENDING PROGRAM` to stdout and `CEE3ABD: ABEND U0999` to stderr. Exit 16. Output files are left as they stand on disk at that point; the run is not RC 0, so callers must discard them.

## Preserved defects (one line each, fix later deliberately)

- D1 Last account in TCATBALF order is never rewritten.
- D2 Interest is truncated toward zero, never rounded.
- D3 A negative balance at a non-zero rate gives negative interest.
- D4 0.00 transactions are written when rate ≠ 0.
- D5 A blank group falls back to `DEFAULT` with two warning lines per balance record.
- D6 Silent high-order truncation: 9 digits for interest/total, 10 for balance. The suffix wraps at 999,999.
- D7 Only the first XREF card per account is used.
- D8 The DISCGRP open-error text says "DALY REJECTS".

## Edge cases

| Case | Handling |
|---|---|
| Empty TCATBALF | No transactions. ACCTFILE is unchanged. Start/end lines only. RC 0 |
| Account with only rate-0 categories | Rewritten (cycle fields zeroed), with no transaction |
| Single account in the file | Never rewritten (D1) |
| PARM ≠ 10 chars | Print `INVALID PARM: EXPECTED 10 CHARACTERS`, then the abend sequence, exit 16 (Q3 deviation) |
| Malformed numeric field | Treat as I/O error: abend, exit 16 |

## Acceptance tests → intent ACs

| Test | AC |
|---|---|
| `GoldenEquivalenceTest.stock`: TRANSACT, ACCTFILE, SYSOUT and RC byte-equal to `legacy/golden/CBACT04C/` | 1, 10 |
| `GoldenEquivalenceTest.afterCbtrn02c`: inputs ACCTFILE/TCATBALF from `golden/CBTRN02C`, same 4 outputs | 2, 10 |
| `DefectTest.lastAccountNotRewritten`: acct 50 equals `golden/CBTRN02C/ACCTFILE.txt`, and the TRANSACT amount is `0000000187G` | 3 |
| `DefectTest.truncatesNotRounds` (0.40 @ 15% → `0000000000{`), `negativeInterest` (−100.00 @ 15% → −1.25 = `0000000012N`) | 4 |
| `RateLookupTest.fallbackToDefault`, `defaultMissingExit16` | 5 |
| `RateLookupTest.zeroRateRewritesNoTx` (2 accounts, first has only rate 0) | 6 |
| `TranIdTest`: suffix starts at `000001`, is contiguous across accounts, and wraps after 999999; filler and desc tail are spaces | 7 |
| `AbendTest`: missing account → 16, missing XREF → 16, missing file → 16, bad PARM → 16 | 8 |
| `mvn test` green. `golden.sh --check` green | 9 |
