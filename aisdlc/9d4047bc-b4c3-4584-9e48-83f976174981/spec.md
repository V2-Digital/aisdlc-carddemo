# CR-1 Spec — `CBACT04C` in Java 21 (behaviour-preserving)

Legacy behaviour, file table, processing steps and defects D1-D7: see [intent.md](intent.md). This spec only adds the
contract the Java code must meet. "Like COBOL" means: byte-identical with `legacy/golden/`.

## Invocation
`java -cp target/classes com.v2.carddemo.cbact04c.Cbact04c --parm <text> --in <dir> --out <dir> [--clock YYYYMMDDHHMMSSss]`
- `--in` dir holds `TCATBALF.txt XREFFILE.txt ACCTFILE.txt DISCGRP.txt` (DD names). The tests copy them from
  `app/data/ASCII` (`tcatbal`, `cardxref`, `acctdata`, `discgrp`), or for scenario 3 they take ACCTFILE and TCATBALF from `legacy/golden/CBTRN02C`.
- `--out` gets `TRANSACT.txt`, `ACCTFILE.txt` (whole master, rewritten in key order) and `SYSOUT.txt`. The exit status is the RC.
- `--clock` defaults to the real clock (`java.time.Clock`). The tests pass `2022071800000000` (UTC).
- PARM: the first 10 chars, right-padded with spaces if shorter (the length is never checked, D5).

## Record I/O (canonical text)
- Read: strip a trailing `\r`, then right-pad with spaces to the LRECL (`cardxref` lines are 36 bytes, so pad them to 50). Write: exactly LRECL bytes + `\n`.
- Zoned decimal `S9(n)V99`: the last byte is an overpunch (`{`/`A-I` = +0..9, `}`/`J-R` = -0..9). Encode +0 as `{` and -0 as `}`.
  Java type: `long` cents (scale 2). Unsigned `9(n)` stays plain digits.
- Unchanged bytes go through untouched. The Java code edits only the fields below, in place on the record's `byte[]`/`String`.

| File (copybook) | Field | Cols (1-based) | PIC | Use |
|---|---|---|---|---|
| TCATBALF (`app/cpy/CVTRA01Y.cpy`) | acct id / type / cat | 1-11 / 12-13 / 14-17 | 9(11) X(2) 9(4) | break key, DISCGRP key |
| | `TRAN-CAT-BAL` | 18-28 | S9(9)V99 | read |
| XREFFILE (`app/cpy/CVACT03Y.cpy`) | `XREF-CARD-NUM` / `XREF-ACCT-ID` | 1-16 / 26-36 | X(16) 9(11) | lookup by acct (first match wins) |
| ACCTFILE (`app/cpy/CVACT01Y.cpy`) | `ACCT-ID` | 1-11 | 9(11) | key |
| | `ACCT-CURR-BAL` | 13-24 | S9(10)V99 | read + write |
| | `ACCT-CURR-CYC-CREDIT` / `-DEBIT` | 79-90 / 91-102 | S9(10)V99 | set to +0 |
| | `ACCT-GROUP-ID` | 113-122 | X(10) | DISCGRP key part |
| DISCGRP (`app/cpy/CVTRA02Y.cpy`) | group / type / cat | 1-10 / 11-12 / 13-16 | X(10) X(2) 9(4) | key |
| | `DIS-INT-RATE` | 17-22 | S9(4)V99 | read |
| TRANSACT (`app/cpy/CVTRA05Y.cpy`) | full 350-byte layout | 1-350 | see copybook | written (step 6 of intent) |

TRANSACT record, built in order: id (16) = PARM(10) + suffix `%06d`. Type `01`. Cat `0005`. Source `System` padded to 10.
Desc = `Int. for a/c ` + acct id (11), padded to 100. Amount S9(9)V99. Merchant id `000000000`. Name, city and zip: spaces (50/50/10).
Card (16). Orig and proc timestamps `yyyy-MM-dd-HH.mm.ss.hh0000` (hh = hundredths). Then 20 spaces.

## Processing rules (must reproduce exactly)
1. Load ACCTFILE, XREFFILE and DISCGRP into maps. A file that is missing or unreadable → open-error path (R9). SYSOUT line 1 is `START OF EXECUTION OF PROGRAM CBACT04C`.
2. For each TCATBALF record in file order: SYSOUT the 50-byte record (trailing spaces kept).
3. Account break (`acct id ≠ last`): if this is not the first record, apply R7 to the previous account. Then total = 0, look up the account and the xref.
   If either is missing → `ACCOUNT NOT FOUND: <acct id>` (+ status lines as COBOL, `:372-413`) → R9.
4. Rate: key = `ACCT-GROUP-ID`+type+cat. On a miss, SYSOUT `DISCLOSURE GROUP RECORD MISSING`, `TRY WITH DEFAULT GROUP CODE`,
   and retry with `DEFAULT   `. If that also misses → R9.
5. If rate ≠ 0: `interest = trunc(bal_cents × rate_hundredths / 10000 / 12)`, with a single truncation **toward zero** of the exact product/1200
   (`BigDecimal` or `long` exact arithmetic, `RoundingMode.DOWN`). Then **mod 10^11 cents**, keeping the sign (S9(9)V99, D4).
   `total = (total + interest) mod 10^11`. Then write a TRANSACT (suffix = `(suffix+1) mod 10^6`). Fees: no-op (D7).
6. Rate = 0 → no interest, no transaction. The account still counts as "touched" (R7 still runs for it, D6).
7. Account update: `bal = (bal + total) mod 10^12 cents`, keeping the sign (S9(10)V99, D4). Cycle credit = cycle debit = +0. Store the record back.
8. EOF: **do not** apply R7 to the last account (D1). Write ACCTFILE (all records, key order, untouched ones verbatim) and TRANSACT.
   SYSOUT `END OF EXECUTION OF PROGRAM CBACT04C`, exit 0.
9. Abend: SYSOUT the error DISPLAYs exactly as COBOL, then `ABENDING PROGRAM`. Print `CEE3ABD: ABEND U0999` to stderr and exit **16**.
   Outputs are still written, like VSAM after an abend: TRANSACT so far, and ACCTFILE with the rewrites already done.
   The COBOL abend text is confirmed by `legacy/tests/cbact04c_abend.sh`.

## Preserved defects (fix later, deliberately)
- D1: last account's interest is not added to its balance and its cycle fields are not zeroed.
- D2: interest truncates toward zero instead of rounding.
- D3: the group key comes from `ACCT-GROUP-ID` (blank in samples), so every lookup falls back to `DEFAULT`.
- D4: interest and balance overflow wrap silently (high-order digits lost).
- D5: the 6-digit tran-id suffix wraps after 999,999. PARM length is not checked.
- D6: cycle credit/debit are zeroed even when the account's interest is 0.
- D7: `1400-COMPUTE-FEES` is a no-op.
- Quirk: the DISCGRP open error text is `ERROR OPENING DALY REJECTS FILE`.

## Edge cases
| Case | Behaviour |
|---|---|
| Negative `TRAN-CAT-BAL` | Interest is negative, truncated toward zero (−0.005 → `}`-signed 0). |
| Empty TCATBALF | No TRANSACT records. ACCTFILE is unchanged. SYSOUT is the 2 banners. RC 0. |
| Single account | Its transactions are written. Its account is not updated (D1). |
| Duplicate xref acct | The first record in file order wins. |
| Account with no TCATBAL rows | Untouched. |

## Acceptance tests (→ intent AC)
| Test | AC |
|---|---|
| `mvn -f modernized/cbact04c verify` is green, deps JUnit 5 + Surefire only | 1 |
| `GoldenEquivalenceTest.stock`: TRANSACT, ACCTFILE, SYSOUT and RC are byte-equal to `legacy/golden/CBACT04C/` | 2, 4 |
| `GoldenEquivalenceTest.afterCbtrn02c`: same, against `legacy/golden/CBACT04C-after-CBTRN02C/` | 3, 4 |
| `InterestTest.truncatesNotRounds`: 2339.97 × 15.00 → `0000000292D`. Also a negative-balance case and an overflow-wrap case | 5, D4 |
| `CharacterizationTest.lastAccountNotRewritten` (acct 50 in scenario 3) | 6 |
| `CharacterizationTest.directGroupHit` (`A000000000` set in `ACCT-GROUP-ID` → no MISSING lines, group rate used), `defaultFallback` | 7, D3 |
| `CharacterizationTest.abend{MissingDefault,MissingAccount,MissingXref}` → SYSOUT tail `ABENDING PROGRAM`, exit 16 | 7 |
| `CharacterizationTest.zeroRateNoTx`, `untouchedAccountVerbatim`, `cycleZeroedWhenInterestZero` | 8, D6 |
| `ZonedDecimalTest` round-trips all 20 overpunch chars | format |
