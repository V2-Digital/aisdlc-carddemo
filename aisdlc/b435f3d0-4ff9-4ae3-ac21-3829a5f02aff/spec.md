# CR-1 Spec: `CBACT04C` → Java 21 (behaviour-preserving)

Legacy behaviour, file:line refs and defects R1–R9: see [intent.md](intent.md). This spec fixes the contract the Java must meet.

## Invocation
`java -jar cbact04c.jar --in <dir> --out <dir> --parm 2022071800` (+ optional `--now YYYYMMDDHHMMSSss`, default system clock UTC).
- Reads `<in>/{TCATBALF,XREFFILE,ACCTFILE,DISCGRP}.txt`. Writes `<out>/{TRANSACT,ACCTFILE,SYSOUT}.txt`, and also prints SYSOUT lines to stdout. Exit code = RC.
- Test-only `Clock` injection. The golden clock is `2022-07-18 00:00:00.00` UTC.
- Text form follows `legacy/README.md`: one record per line, LF, fixed width, overpunch signs. On read, strip CR and right-pad to LRECL (cardxref lines are 36 bytes).

## Files and the fields used (1-based offset, length)
| DD | Copybook | LRECL | Fields read/written → Java type |
|---|---|---|---|
| TCATBALF in | `app/cpy/CVTRA01Y.cpy` | 50 | acct 1/11 `9(11)` String; type 12/2 `X`; cat 14/4 `9(4)`; `TRAN-CAT-BAL` 18/11 `S9(9)V99` BigDecimal |
| XREFFILE in | `app/cpy/CVACT03Y.cpy` | 50 | card 1/16 `X`; acct 26/11 `9(11)` (alt key) |
| DISCGRP in | `app/cpy/CVTRA02Y.cpy` | 50 | key group 1/10 + type 11/2 + cat 13/4; `DIS-INT-RATE` 17/6 `S9(4)V99` |
| ACCTFILE in/out | `app/cpy/CVACT01Y.cpy` | 300 | id 1/11; `CURR-BAL` 13/12 `S9(10)V99`; `CYC-CREDIT` 79/12; `CYC-DEBIT` 91/12; `GROUP-ID` 113/10. All other bytes are copied through unchanged |
| TRANSACT out | `app/cpy/CVTRA05Y.cpy` | 350 | all fields (see intent step 6), built over a 350-space buffer |

ACCTFILE output = every input record in primary-key (acct id) order, with rewritten records replaced in place.

## Processing rules (in order)
1. SYSOUT `START OF EXECUTION OF PROGRAM CBACT04C`. Load the inputs. A missing or unreadable file means an open error: DISPLAY the COBOL open-error text (including R8 `ERROR OPENING DALY REJECTS FILE` for DISCGRP), `FILE STATUS IS: NNNN00xx`, `ABENDING PROGRAM`, RC 16.
2. Iterate TCATBAL in key order (acct+type+cat). For each row, count it and DISPLAY the raw 50-byte record.
3. If the acct id differs from `lastAcct` (initially spaces): if this is not the first account, rewrite the held account (rule 7). Set `totalInt = 0`. Look up the account by id and the XREF by acct id (first in file order, R7).
   - Account miss: `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM`, RC 16.
   - XREF miss: the same, with `ERROR READING XREF FILE`.
4. Rate: key = `ACCT-GROUP-ID` (10 bytes as-is) + type + cat.
   - Miss: DISPLAY `DISCLOSURE GROUP RECORD MISSING`, then `TRY WITH DEFAULT GROUP CODE`, and retry with `DEFAULT   ` (space-padded to 10).
   - DEFAULT miss: `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM`, RC 16.
5. If rate ≠ 0: `monthly = bal × rate / 1200`, truncated toward zero (`RoundingMode.DOWN`) to scale 2. Then keep it mod 10^9 (PIC S9(9)V99 high-order truncation). `totalInt += monthly`, also mod 10^9. Fees: no-op (R2).
6. Write the transaction. `suffix = (suffix+1) mod 10^6` (R5).
   - `TRAN-ID` = PARM(10) + suffix(6, zero-padded).
   - `01` / `0005` / `System` padded to 10.
   - DESC = `Int. for a/c ` + acct id (11 digits), padded to 100.
   - AMT = monthly as zoned `S9(9)V99`.
   - Merchant id `000000000`; name, city and zip are spaces.
   - Card = XREF card number.
   - Both TS = `YYYY-MM-DD-HH.MM.SS.hh0000`. FILLER = spaces.
7. Rewrite the account: `CURR-BAL = (CURR-BAL + totalInt)`, truncated to S9(10)V99 (mod 10^10). `CYC-CREDIT = CYC-DEBIT = +0` (`00000000000{`).
8. At EOF, **do not** rewrite the held account (R1). SYSOUT `END OF EXECUTION OF PROGRAM CBACT04C`. Write the outputs. RC 0.

On an abend (rules 1, 3, 4), SYSOUT ends with `ABENDING PROGRAM` and RC is 16. TRANSACT/ACCTFILE contents after an abend are not part of the contract.

## Zoned decimal codec
- The last byte carries the sign plus the digit: `{`/`A`–`I` = +0..9, `}`/`J`–`R` = −0..9.
- Encode: positive or zero → `{A-I`, negative → `}J-R`. Zero is always `+0` (`{`).
- Decode also accepts a plain digit in the last byte as positive (ASCII sample data).

## Edge cases
| Case | Handling |
|---|---|
| Zero balance or zero rate | Rate 0 → no transaction, totalInt unchanged. Balance 0 with rate ≠ 0 → zero-amount transaction (stock scenario) |
| Negative balance | Interest truncates toward zero, e.g. −0.009 → `0.00`, encoded `+0` (unit test) |
| Several TCATBAL rows per account | Interest sums into one rewrite. Each row gets its own transaction |
| Interest/balance overflow, suffix > 999999 | Truncated silently, as in COBOL (R5) |
| Duplicate XREF per account | First record in file order (R7) |

## Preserved defects (fix deliberately later)
- **R1** The last account in TCATBAL is never rewritten, though its transactions are written.
- **R2** `1400-COMPUTE-FEES` is empty: no fees.
- **R3** Blank `ACCT-GROUP-ID` → every lookup falls back to DEFAULT (data issue; logic kept).
- **R5** The tran-id suffix and amounts wrap or truncate with no error.
- **R8** The DISCGRP open-error text says "DALY REJECTS".
- **R9** The DESC tail and FILLER are spaces (default dialect), not `x'00'`.

## Acceptance tests (→ intent AC)
| Test | AC |
|---|---|
| `mvn -f modernized/cbact04c test` green; POM has JUnit 5 + Surefire only | 1 |
| `GoldenStockTest`: stock inputs → TRANSACT, ACCTFILE, SYSOUT and RC byte-equal `legacy/golden/CBACT04C/` | 2, 4 |
| `GoldenAfterTrn02Test`: ACCTFILE and TCATBALF from `golden/CBTRN02C`, rest stock → byte-equal `golden/CBACT04C-after-CBTRN02C/` | 3, 4 |
| `InterestTest`: 1164.87 × 15.00 → 14.56 (not 14.57); negative case truncates toward 0 | 5 |
| `DefectR1Test`: acct `00000000050` has a transaction (`0000000187G`) but an unchanged ACCTFILE row | 6 |
| `AbendTest`: missing account / XREF / DEFAULT → RC 16 and the SYSOUT tail as specified | 7 |
| `git diff --stat baseline -- legacy/golden legacy/harness` empty; `golden.sh --check` passes | 8 |
