# CR-1 Spec: `CBACT04C` in Java 21

Behaviour-preserving rewrite. The legacy behaviour, with file:line references, is in [intent.md](intent.md). This spec pins only what the Java code must reproduce byte for byte.

## Invocation

`java -jar cbact04c.jar <PARM-DATE> --dd TCATBALF=<path> XREFFILE=… ACCTFILE=… DISCGRP=… TRANSACT=… [--sysout <path>]`
- `PARM-DATE` is `X(10)`: right-padded with spaces or cut to 10 characters.
- Clock: `java.time.Clock`, UTC by default. Tests inject `2022-07-18T00:00:00Z`.
- Exit code: 0 on success, 16 on any abend path.
- SYSOUT goes to stdout, or to the file given by `--sysout`. The line `CEE3ABD: ABEND U0999` goes to stderr, as the harness stub does.

## Files (canonical text form, Q1)

Each record is one fixed-width line ending in LF. On read, strip `\r` (the harness also runs `tr -d '\r'`) and right-pad each line with spaces to LRECL.

| DD | Copybook | LRECL | Fields used (offset, len, PIC → Java) |
|---|---|---|---|
| `TCATBALF` in | [`CVTRA01Y`](../../app/cpy/CVTRA01Y.cpy) | 50 | acct 0,11 `9(11)` → String; type 11,2 `X(2)`; cat 13,4 `9(4)`; bal 17,11 `S9(9)V99` → BigDecimal scale 2 |
| `XREFFILE` in | [`CVACT03Y`](../../app/cpy/CVACT03Y.cpy) | 50 | card 0,16 `X(16)`; acct 25,11 `9(11)`. Lookup by acct = **first in file order** (R3) |
| `DISCGRP` in | [`CVTRA02Y`](../../app/cpy/CVTRA02Y.cpy) | 50 | key 0,16 (group `X(10)` + type `X(2)` + cat `9(4)`); rate 16,6 `S9(4)V99` → BigDecimal |
| `ACCTFILE` in/out | [`CVACT01Y`](../../app/cpy/CVACT01Y.cpy) | 300 | id 0,11; curr-bal 12,12 `S9(10)V99`; cyc-credit 188,12; cyc-debit 200,12; group-id 212,10 `X(10)` |
| `TRANSACT` out | [`CVTRA05Y`](../../app/cpy/CVTRA05Y.cpy) | 350 | all fields, see rule 5 |

- `ACCTFILE` is loaded whole, keyed by id (insertion order is key order). It is written back whole in the same order. The record bytes of fields that are not rewritten are kept exactly.
- `TRANSACT` is written in creation order. It is created even when it is empty.
- Zoned decimal: digits, with the sign overpunched on the last byte (`{`/`A-I` mean + 0..9, `}`/`J-R` mean − 0..9). On encode, zero and positive values use `{A-I` and negative values use `}J-R`.

## Processing rules (in order)

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`. Open the files: a missing or unreadable input, or an unwritable output, prints the legacy open-error text for that DD (the DISCGRP text is `ERROR OPENING DALY REJECTS FILE`, F5), then `FILE STATUS IS: NNNN<status>`, then abends.
2. For each `TCATBALF` record: count it and print the raw 50 bytes.
3. When the account id differs from the last one: if this is not the first account, rewrite the previous account (rule 6). Set total = 0. Look up the account, then the xref. Not found → print `ACCOUNT NOT FOUND: <11-digit id>`, `ERROR READING ACCOUNT FILE` (or `… XREF FILE`), `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM`, then exit 16.
4. Rate lookup with key = the account's `ACCT-GROUP-ID` + type + cat. Miss → print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with group `DEFAULT` padded to 10. Second miss → print `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM`, then exit 16.
5. If rate ≠ 0:
   - `monthly = (bal × rate) / 1200`. Use exact BigDecimal arithmetic, then `setScale(2, RoundingMode.DOWN)`, which truncates toward zero (F2). Keep the result modulo 10^9 (high-order truncation into `S9(9)V99`, because there is no `ON SIZE ERROR`).
   - `total += monthly`, also kept within `S9(9)V99`.
   - The suffix counter is `(suffix + 1) mod 1,000,000` (F4), global across accounts.
   - Write the 350-byte record. It starts as all spaces. Then set: `TRAN-ID` = PARM(10) + suffix 6 digits; `01`; `0005`; `System` padded to 10; `Int. for a/c ` + ACCT-ID (11) padded to 100; amount zoned 11; merchant id `000000000`; name/city/zip spaces; card number; orig-ts = proc-ts = `yyyy-MM-dd-HH.mm.ss.` + hundredths(2) + `0000`. The 20-byte FILLER stays spaces.
   - Fees: no-op (F3).
6. Rewrite the account: `curr-bal = (curr-bal + total)`, kept within `S9(10)V99`. Set cyc-credit and cyc-debit to `+0` (`00000000000{`). This happens even when total = 0.
7. At EOF: **do not rewrite the last account (F1)**. Flush `TRANSACT`, write `ACCTFILE`, print `END OF EXECUTION OF PROGRAM CBACT04C`, exit 0.

On an abend, the outputs are not written. The harness's unload state after an abend is not a golden artifact.

## Edge cases

| Case | Handling |
|---|---|
| Empty `TCATBALF` | No rewrite, empty `TRANSACT`, `ACCTFILE` unchanged, exit 0 |
| Rate 0 (e.g. `DEFAULT/03/0001`) | No txn, no suffix increment. The account is still rewritten (rule 6) |
| Negative balance or rate | Interest truncates toward zero (−29.249 → −29.24) and is encoded with `}J-R` |
| Several xref rows per account | Take the first in file order (R3, untested by golden) |
| Non-consecutive repeats of an account in TCATBALF | Each new run is a new "account change": the account is re-read from the in-memory master (which already holds the earlier rewrites) |
| CRLF or short lines in inputs | Strip `\r` and pad to LRECL |

### Preserved defects (fix later, deliberately)
- **F1** The last account is never rewritten: its interest is posted as a txn, but not to its balance.
- **F2** Interest is truncated, not rounded.
- **F3** The fee step is empty.
- **F4** The txn-id suffix wraps at 999,999, which gives duplicate ids.
- **F5** The DISCGRP open-error text says `DALY REJECTS FILE`.
- **F6** Interest is posted as type `01` / category `0005`.
- Silent high-order truncation on the monthly, total and balance overflow (no `ON SIZE ERROR`).

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -q verify`, Java 21, JUnit 5 only | 1 |
| `GoldenEquivalenceTest.stock`: stock `app/data/ASCII` inputs vs `legacy/golden/CBACT04C/` (TRANSACT, ACCTFILE, SYSOUT, RC) | 2, 4 |
| `GoldenEquivalenceTest.afterCbtrn02c`: ACCTFILE+TCATBALF from `golden/CBTRN02C/` vs `golden/CBACT04C-after-CBTRN02C/` | 3, 4 |
| `DefectF1Test`: acct `00000000050` record is unchanged (`00000019458G`, cycle fields not zeroed) while txn `2022071800000050` exists | 5 |
| `InterestTruncationTest`: 2339.97 × 1.50 → `0000000292D`; negative case | 6, 9 |
| `RulesTest`: default-group fallback, 3 SYSOUT lines per record, rate 0 → no txn but account rewritten | 7 |
| `AbendTest`: missing acct, missing xref, missing DEFAULT → legacy lines + exit 16 | 8 |
| `ZonedDecimalTest`: round-trip all 20 sign characters | 9 |
| `legacy/harness/golden.sh --check` + `git diff --stat baseline -- legacy/golden legacy/harness .github` is empty | 10 |
