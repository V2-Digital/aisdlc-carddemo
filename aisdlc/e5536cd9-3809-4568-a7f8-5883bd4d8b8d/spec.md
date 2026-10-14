# CR-1 Spec: `CBACT04C` (interest calc) in Java 21

Behaviour-preserving rewrite of `app/cbl/CBACT04C.cbl`. Legacy facts, line refs and defects are in [intent.md](intent.md). Approver defaults apply: Q1 SYSOUT is byte-identical, Q2 abend exits 16, Q3 D1/D2 are logged as follow-ups and not fixed.

## Invocation

`java -jar cbact04c.jar <PARM> <IN_DIR> <OUT_DIR>`. `PARM` is a 10-char run date, e.g. `2022071800`.
- Inputs: `<IN_DIR>/{TCATBALF,XREFFILE,ACCTFILE,DISCGRP}.txt`.
- Outputs: `<OUT_DIR>/{TRANSACT,ACCTFILE,SYSOUT}.txt`. SYSOUT also goes to stdout.
- Clock: env `CARDDEMO_CURRENT_DATE` (`YYYYMMDDHHMMSSss`, the same value the harness uses), else the system clock in UTC. Code reaches it through an injectable `Clock`.
- Exit code: 0 on success, 16 on abend.

## Record I/O (canonical text, as in `legacy/golden/`)

- Read: one record per line. Strip a trailing `\r`. Right-pad with spaces to the record length (XREF lines are 36 bytes).
- Write: fixed-length records, each ending in `\n`.
- Zoned `S9(n)V99`: n+2 digits with the sign overpunched on the last byte (`{A-I` = +0..9, `}J-R` = -0..9). Use `BigDecimal` or `long` cents only. A zero result is written with a positive sign (`{`).

| DD | Copybook | Len | Fields used (offset, len, PIC → Java) |
|---|---|---|---|
| TCATBALF | `app/cpy/CVTRA01Y.cpy` | 50 | acct 0,11 `9(11)`→String; type 11,2 `X`; cat 13,4 `9(4)`; bal 17,11 `S9(9)V99`→BigDecimal |
| XREFFILE | `app/cpy/CVACT03Y.cpy` | 50 | card 0,16 `X`; acct 25,11 (alternate key) |
| ACCTFILE | `app/cpy/CVACT01Y.cpy` | 300 | id 0,11; curr-bal 12,12 `S9(10)V99`; cyc-credit 76,12; cyc-debit 88,12; group-id 110,10 `X`. Every other byte is copied through unchanged |
| DISCGRP | `app/cpy/CVTRA02Y.cpy` | 50 | key 0,16 = group(10)+type(2)+cat(4); rate 16,6 `S9(4)V99` |
| TRANSACT | `app/cpy/CVTRA05Y.cpy` | 350 | written in full, see R6 |

## Processing rules (in order)

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`. Load XREF (indexed by acct, first match wins), DISCGRP (indexed by 16-byte key) and ACCTFILE (ordered map by id). A missing or unreadable input file is an open error: print the legacy message (DISCGRP's says `DALY REJECTS FILE`, D6), then abend.
2. Read TCATBALF in file order (R1: the input is assumed to be in key order). For each record: count it, then print the raw 50-byte record.
3. **Account break**, when the acct differs from the last one:
   - If this is not the first account, apply R7 to the previous account.
   - Reset `totalInt = 0`.
   - Look up the account. If missing: print `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`, then abend.
   - Look up the xref by acct. If missing: print `ACCOUNT NOT FOUND: <id>`, `ERROR READING XREF FILE`, `FILE STATUS IS: NNNN0023`, then abend.
4. **Rate lookup** with key `ACCT-GROUP-ID + type + cat`.
   - If not found: print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with group `DEFAULT` padded to 10.
   - If DEFAULT is also missing: print `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, then abend.
5. If `rate == 0`, skip to the next record: no transaction and no counter increment.
6. Otherwise:
   - `monthly = trunc2(bal × rate / 1200)`, truncated toward zero (`RoundingMode.DOWN`, scale 2), then high-order truncated mod 10^9 (D3).
   - `totalInt = (totalInt + monthly)`, mod 10^9 with the same rule.
   - Then write the transaction:

   | Field | Value |
   |---|---|
   | suffix | `(suffix+1) mod 1_000_000` |
   | `TRAN-ID` | PARM + 6-digit suffix |
   | type / cat | `01` / `0005` |
   | source | `System` padded to 10 |
   | desc | `Int. for a/c ` + 11-digit acct, padded with spaces to 100 |
   | amount | `monthly` (zoned) |
   | merchant id | `000000000` |
   | merchant name / city / zip | spaces |
   | card | xref card |
   | orig-ts / proc-ts | both `YYYY-MM-DD-HH.MM.SS.hh0000`, from the clock at write time |
   | bytes 330-349 | spaces |

   - Fees are a no-op (D5).
7. **Update account**:
   - `currBal = (currBal + totalInt)`, mod 10^10.
   - Cycle credit and cycle debit are set to 0.
   - Store the result back in the map. This applies even when `totalInt` is 0.
8. At EOF, do **not** apply R7 to the last account (D1). Write ACCTFILE: every record, in key order. Print `END OF EXECUTION OF PROGRAM CBACT04C`, then exit 0.
9. **Abend**:
   - Print `ABENDING PROGRAM` to SYSOUT and to stderr, then exit 16.
   - Transactions already written stay. ACCTFILE is flushed with the rewrites done so far, which mirrors in-place VSAM REWRITE. Abend-time outputs are not golden-checked.

## Edge cases

| Case | Handling |
|---|---|
| Empty TCATBALF | No transactions. ACCTFILE is unchanged. Exit 0 |
| Negative balance | Negative interest, truncated toward zero (e.g. -0.009 → -0.00, written `{`) |
| Account with only rate-0 categories | Rewritten with cycle fields zeroed (unless it is the last account, D1) |
| Duplicate xref acct | First match in file order. None in the sample data (checked: 0 duplicates) |
| Account with no TCATBAL rows | Untouched |

## Preserved defects (fix later, deliberately)

- **D1:** the last account is never rewritten: no interest posted to its balance, cycle fields not cleared.
- **D2:** interest is truncated, not rounded.
- **D3:** silent overflow. The TRAN-ID suffix wraps at 10^6, and amounts lose their high-order digits.
- **D4:** blank `ACCT-GROUP-ID` → always `DEFAULT` (data issue).
- **D5:** fees are a no-op.
- **D6:** the DISCGRP open-error text says `DALY REJECTS FILE`.

## Acceptance tests (mapped to intent AC)

| AC | Test |
|---|---|
| 1 | `mvn test` green. The POM has only JUnit 5 + Surefire |
| 2 | `GoldenTest.stock`: `app/data/ASCII` inputs → TRANSACT/ACCTFILE/SYSOUT byte-equal to `legacy/golden/CBACT04C/` |
| 3 | `GoldenTest.afterCbtrn02c`: ACCTFILE + TCATBALF from `legacy/golden/CBTRN02C/`, stock XREF/DISCGRP → equal to `legacy/golden/CBACT04C-after-CBTRN02C/` |
| 4 | `InterestTest`: a balance where truncation ≠ HALF_UP, plus a negative case |
| 5 | `DefectTest.lastAccountNotRewritten`: acct `00000000050` is unchanged, and tran `…000050` has amount `0000000187G` |
| 6 | `RateTest`: DEFAULT fallback (SYSOUT lines); rate 0 → no transaction, suffix unchanged, account still rewritten |
| 7 | `AbendTest`: missing acct, missing xref, missing DEFAULT → exit 16, `ABENDING PROGRAM`, no further records processed |
| 8 | `TranRecordTest`: TRAN-ID format, counter across accounts, wrap at 999999 → 000000, spaces at 330-349 and the desc tail |
| 9 | Covered by the SYSOUT comparisons in AC 2 and 3 |
