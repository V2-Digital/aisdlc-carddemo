# CR-1 Spec: CBACT04C in Java 21 (behaviour-preserving)

Legacy behaviour, file table and defects are in [intent.md](intent.md). This spec covers only what the Java code must do and how we prove it.

## Interface

`java -jar cbact04c.jar <PARM> --TCATBALF=<f> --XREFFILE=<f> --ACCTFILE=<f> --DISCGRP=<f> --out=<dir> [--clock=YYYYMMDDHHMMSSss]`

- `PARM`: the run date, right-padded with spaces or cut to 10 chars (`PARM-DATE X(10)`).
- Clock: `--clock`, else env `CARDDEMO_CURRENT_DATE` (same 16-digit form as the harness), else system clock in UTC.
- Writes `<out>/TRANSACT.txt` and `<out>/ACCTFILE.txt`. SYSOUT goes to stdout. Exit code 0, or 16 on abend.

## Files (canonical text, see `legacy/README.md` "Outputs")

| DD | Copybook | Read / written |
|---|---|---|
| TCATBALF | `app/cpy/CVTRA01Y.cpy` | acct-id 0/11 `9(11)`, type 11/2 `X`, cat 13/4 `9(4)`, `TRAN-CAT-BAL` 17/11 `S9(9)V99` -> `BigDecimal` |
| XREFFILE | `app/cpy/CVACT03Y.cpy` | card-num 0/16 `X`, acct-id 25/11 (alt key) |
| ACCTFILE | `app/cpy/CVACT01Y.cpy` | acct-id 0/11; `CURR-BAL` 12/12, `CYC-CREDIT` 78/12, `CYC-DEBIT` 90/12, all `S9(10)V99`; `GROUP-ID` 112/10 `X`. Other bytes are copied through unchanged. |
| DISCGRP | `app/cpy/CVTRA02Y.cpy` | key 0/16 (group 10 + type 2 + cat 4); `DIS-INT-RATE` 16/6 `S9(4)V99` |
| TRANSACT (out) | `app/cpy/CVTRA05Y.cpy` | the full 350-byte record (offsets below) |

Input reading: strip `\r`, accept a missing final newline, right-pad each line with spaces to LRECL (the same as the harness loader). KSDS files are loaded into `TreeMap`s keyed by the primary key. XREF also gets an acct-id index; with duplicate alt keys, the first record in primary-key order wins.

TRANSACT layout: id 0/16, type 16/2 `01`, cat 18/4 `0005`, source 22/10 `System␣␣␣␣`, desc 32/100, amt 132/11 `S9(9)V99`, merchant-id 143/9 `000000000`, name/city/zip 152/110 spaces, card 262/16, orig-ts 278/26, proc-ts 304/26, filler 330/20 spaces.

Zoned decimal codec: digits plus a trailing overpunch on the last digit (`{A-I` = +0..9, `}J-R` = -0..9). Encode zero as `{`. Encode values modulo 10^intDigits, keeping the sign (this is the COBOL high-order truncation).

## Processing rules (must be reproduced exactly)

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`. Load all inputs. If an input is missing or unreadable, abend (R-ABEND) with the open-error message from the matching `0x00-*-OPEN` paragraph.
2. For each TCATBAL record, in file order: print the raw 50-byte record.
3. When the acct-id differs from the previous one: if this is not the first record, run **update(prev)**. Then total = 0. Look up the account (if missing, run M-ACCT). Then look up the XREF by acct-id (if missing, run M-XREF).
4. Rate: key = `GROUP-ID` + type + cat. On a miss, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with `DEFAULT␣␣␣`. If that also misses, run M-DISC.
5. If rate != 0: `int = trunc2(bal × rate / 1200)`. Use exact `BigDecimal`, `RoundingMode.DOWN`, then keep it modulo 10^9 (S9(9)V99). `total = mod10^9(total + int)`. Suffix = (suffix + 1) mod 10^6. Write TRANSACT: id = PARM + 6-digit suffix; desc = `Int. for a/c ` + acct-id, padded with spaces; amt = int; card from XREF; both timestamps = clock as `YYYY-MM-DD-HH.MM.SS.hh0000`. Fees: no-op.
6. **update(acct)**: `CURR-BAL = mod10^10(CURR-BAL + total)`, `CYC-CREDIT = 0`, `CYC-DEBIT = 0`, written back into the in-memory master.
7. At EOF: **do not update the last account** (D1). Write the outputs, print `END OF EXECUTION OF PROGRAM CBACT04C`, exit 0.

**Abend messages** (each sequence ends with `ABENDING PROGRAM` and exit 16):
- M-ACCT: `ACCOUNT NOT FOUND: <acct>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`
- M-XREF: `ACCOUNT NOT FOUND: <acct>`, `ERROR READING XREF FILE`, `FILE STATUS IS: NNNN0023`
- M-DISC: `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`

**Output on abend**: the same as what COBOL leaves behind. TRANSACT holds the records written so far. ACCTFILE holds the updates already applied and the original bytes for every other record. Each output is written to a temp file and then renamed, also on the abend path, so no half-written file is visible (R6).

## Edge cases

| Case | Handling |
|---|---|
| Negative balance | Truncate toward zero (-14.5608 -> -14.56, encoded `0000000145O`) |
| Result -0.00x | Truncates to 0, encoded `{` (no negative zero) |
| Rate 0 (e.g. `DEFAULT 03 0001`) | No transaction, suffix unchanged, but the account is still updated |
| Account with no TCATBAL rows | ACCTFILE record copied through unchanged |
| Overflow (int, bal, suffix) | Silent high-order truncation (R2) |
| Empty TCATBALF | No update, empty TRANSACT, ACCTFILE unchanged, RC 0 |

**Preserved defects** (to be fixed later, each under its own decision):
- D1: the last account in TCATBAL order is never rewritten (interest charged, balance and cycle fields unchanged).
- D2: blank `ACCT-GROUP-ID` sends every lookup to `DEFAULT` (a data issue; the code is unchanged).
- D3: `1400-COMPUTE-FEES` is a no-op.
- R2: overflow truncates silently, with no size-error handling.

## Acceptance tests

| AC | Test |
|---|---|
| 1 | `mvn -q verify` (JUnit 5 + Surefire only) |
| 2, 4 | `GoldenEquivalenceTest.stock`: TRANSACT, ACCTFILE and SYSOUT byte-equal to `legacy/golden/CBACT04C/`, exit = `RC.txt` |
| 3, 4 | `GoldenEquivalenceTest.afterCbtrn02c`: the same against `legacy/golden/CBACT04C-after-CBTRN02C/` |
| 5 | `InterestTest` (+/-/0, truncation, overflow); `ZonedDecimalTest` round-trip; `legacy/tests/cbact04c-edge.sh` diffs Java against COBOL on crafted inputs |
| 6 | `DefectTest.lastAccountNotRewritten` (account `00000000050`, 18.77) |
| 7 | `DefectTest`: DEFAULT fallback when a group exists vs. when it misses, rate 0 means no transaction, run-wide suffix, space-filled desc tail and filler |
| 8 | `AbendTest` (3 paths: SYSOUT, exit 16, partial outputs) + `legacy/tests/cbact04c-edge.sh` abend cases against COBOL |
| 9 | `legacy/harness/golden.sh --check`; `git diff --stat baseline -- legacy/golden legacy/harness .github` is empty |
