# CR-1 Spec: `CBACT04C` in Java 21 (behaviour-preserving)

Legacy behaviour, file:line evidence and defects F1–F7: see [intent.md](intent.md). This spec covers the **contract** the Java code must meet.

## Invocation (Q1/Q2 defaults applied)

`java -jar cbact04c.jar <PARM-DATE>`, for example `2022071800`. PARM is a 10-character string. A shorter PARM is right-padded with spaces, a longer one is cut to 10.
- Each file path comes from env `DD_TCATBALF`, `DD_XREFFILE`, `DD_DISCGRP`, `DD_ACCTFILE` (input), `DD_ACCTFILE_OUT`, `DD_TRANSACT` (output).
- Clock: env `CARDDEMO_CURRENT_DATE` = `YYYYMMDDHHMMSSss` (same as the harness). If it is unset, the system clock in UTC is used. In code this goes through a `Clock` passed to the constructor, so tests never read the wall clock.
- SYSOUT goes to stdout. Exit code 0 means OK. 16 means abend, printed as `ABENDING PROGRAM` on stdout.

## Files: canonical text, one record per line, LF

Reading: strip a trailing CR, then right-pad with spaces to LRECL (this handles `cardxref.txt` with its 36-byte lines and the CRLF `tcatbal.txt`). Writing: exactly LRECL bytes followed by LF. Files are ISO-8859-1, read and written byte for byte.

| DD | Copybook | LRECL | Fields used (0-based offset, length, PIC → Java) |
|---|---|---|---|
| TCATBALF | [`CVTRA01Y`](../../app/cpy/CVTRA01Y.cpy) | 50 | acct 0,11 `9(11)`→String; type 11,2; cat 13,4; bal 17,11 `S9(9)V99`→BigDecimal |
| XREFFILE | [`CVACT03Y`](../../app/cpy/CVACT03Y.cpy) | 50 | card 0,16; acct 25,11 (alternate key, first match in primary-key order) |
| DISCGRP | [`CVTRA02Y`](../../app/cpy/CVTRA02Y.cpy) | 50 | key 0,16 (group 10 + type 2 + cat 4); rate 16,6 `S9(4)V99` |
| ACCTFILE | [`CVACT01Y`](../../app/cpy/CVACT01Y.cpy) | 300 | id 0,11; curr-bal 12,12 `S9(10)V99`; cyc-credit 66,12; cyc-debit 78,12; group 100,10 |
| TRANSACT | [`CVTRA05Y`](../../app/cpy/CVTRA05Y.cpy) | 350 | all fields written (rule 6) |

Each record is kept as its raw byte string. Only the fields above are spliced in place, so every other byte, including the FILLERs, round-trips unchanged.

**Zoned decimals:** the last byte carries the sign and digit: `{`/`A`–`I` = +0..9, `}`/`J`–`R` = -0..9. A plain digit is also accepted as positive. Encoding: a value ≥ 0 gets `{`/`A`–`I`, a value < 0 gets `}`/`J`–`R`. A result of zero is always written `{`, as COBOL normalises the sign of arithmetic results (this is checked by a legacy test, see plan). A non-numeric zoned field abends (data exception).

## Processing rules (in order)

1. Display `START OF EXECUTION OF PROGRAM CBACT04C`. Load every input. A missing or unreadable file prints the COBOL open-error text (for DISCGRP: `ERROR OPENING DALY REJECTS FILE`, F5), then `FILE STATUS IS: NNNN0035` and the abend.
2. ACCTFILE is held as a `TreeMap` by id. TCATBALF is sorted by its 17-byte key, because a KSDS is read in key order.
3. For each TCATBAL record, display the raw 50-byte record.
4. When the account changes (compare with the last account; the first record also counts as a change): if this is not the first record, apply rule 8 to the previous account. Reset `totalInt = 0`. Look up the account and then the xref. A miss prints `ACCOUNT NOT FOUND: <11-digit id>`, `ERROR READING ACCOUNT FILE` (or `… XREF FILE`), `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM`, then exit 16.
5. Look up the rate with key `ACCT-GROUP-ID + type + cat`. On a miss, display `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with `DEFAULT   ` (padded to 10). If that also misses: `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, abend.
6. If rate ≠ 0:
   - `monthly = (bal × rate) / 1200`, computed exactly, then cut to scale 2 with `RoundingMode.DOWN` (truncation toward zero, **F2**) and taken mod 10^9 (F6).
   - `totalInt = (totalInt + monthly) mod 10^9`.
   - `suffix = (suffix + 1) mod 10^6`.
   - Write the TRANSACT record: id = PARM(10) + suffix `%06d`; type `01`; cat `0005`; source `System` padded to 10; desc `Int. for a/c ` + acct id (11), padded to 100; amt zoned 11; merchant id `000000000`; name, city and zip as spaces (50/50/10); card = xref card; orig-ts = proc-ts = `YYYY-MM-DD-HH.MM.SS.hh0000`; FILLER 20 spaces.
7. Fees: no-op (CR-2).
8. Account update: `curr-bal = (curr-bal + totalInt) mod 10^10`, with cyc-credit and cyc-debit set to `0`. This applies to every account seen, even when its interest is 0.
9. At EOF, **do not** update the last account (**F1**). Write every ACCTFILE record, in key order, including accounts not in TCATBAL. Display `END OF EXECUTION OF PROGRAM CBACT04C`. Exit 0.

## Edge cases

| Case | Handling |
|---|---|
| Empty TCATBALF | No transactions. ACCTFILE is unchanged. Only START/END lines. RC 0. |
| Negative balance | Negative interest, truncated toward zero (−0.004 → 0 → `{`). |
| Rate 0 | No transaction and no counter increment. The account is still rewritten (rule 8). |
| Duplicate XREF acct | The first card in primary-key order wins (BDB alternate-key order). |
| Overflow (F6) | High-order digits are dropped. No error. |

## Preserved defects (fix later, deliberately)

- **F1** The last account in TCATBALF never gets its interest or cycle reset written back.
- **F2** Interest is truncated, not rounded.
- **F3** A 0.00 transaction is written when the rate is non-zero and the balance is 0.
- **F4** The lookup uses blank `ACCT-GROUP-ID`, so the DEFAULT fallback always applies on sample data.
- **F5** A DISCGRP open error says `DALY REJECTS FILE`.
- **F6** Silent overflow truncation on the interest, the balance and the tran-id suffix.
- **F7** Timestamp is hundredths + `0000`.

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -q verify` passes, JUnit 5 + Surefire only | 1 |
| `GoldenEquivalenceTest.stock`: TRANSACT and ACCTFILE byte-equal to `legacy/golden/CBACT04C/` | 2 |
| `GoldenEquivalenceTest.afterCbtrn02c`: inputs from `golden/CBTRN02C`, outputs byte-equal | 3 |
| Both tests above: SYSOUT byte-equal (152/302 lines) and RC = `RC.txt` (0) | 4 |
| `ClockInjectionTest`: a different PARM/clock changes the id/ts; the default never reads the wall clock in tests | 5 |
| `DefectCharacterizationTest`: F1 (acct 50 unchanged), F2 (acct 48 → `18.90`), F3 (zero amount), DEFAULT fallback + abend when DEFAULT is missing | 6a–d |
| `AbendTest`: missing acct, missing xref, missing DEFAULT, non-numeric balance, missing file → `ABENDING PROGRAM`, exit 16 | 7 |
| `legacy/harness/golden.sh --check` passes; `git diff` is clean under the protected paths | 8 |
