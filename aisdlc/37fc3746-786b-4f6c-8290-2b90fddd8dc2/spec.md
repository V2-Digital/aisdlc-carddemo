# CR-3 Spec: CBTRN02C → Java 21 (behaviour-preserving)

Legacy behaviour, file:line refs and defects: [intent.md](intent.md). This spec is the contract for the rewrite; where it is silent, the COBOL `app/cbl/CBTRN02C.cbl` wins.

## Invocation

- `java -jar cbtrn02c.jar` and the class `Cbtrn02c.main`. Each DD is a text file path taken from the env var `DD_<DD>` (same names as the harness). Input text is fixed-width and LF; any `\r` is stripped on load, like `run.sh:99`.
- Clock: `CARDDEMO_CURRENT_DATE`, else `COB_CURRENT_DATE` (`YYYYMMDDHHMMSSss`, 16 digits), else the system clock (UTC). Tests inject a `Clock`.
- SYSOUT goes to stdout. Exit code: `0`, `4` (rejects > 0) or `16` (abend; `CEE3ABD: ABEND U0999` goes to stderr).

## Files

| DD | Copybook | Mode | Java model |
|---|---|---|---|
| `DALYTRAN` | `app/cpy/CVTRA06Y.cpy` (350) | read seq | `List<String>` lines |
| `XREFFILE` | `app/cpy/CVACT03Y.cpy` (50) | read keyed | `Map<card,acctId>`. Short lines (36 bytes) are right-padded to 50 |
| `ACCTFILE` | `app/cpy/CVACT01Y.cpy` (300) | load, update, unload | `TreeMap<String,char[]>` keyed on cols 1-11 |
| `TCATBALF` | `app/cpy/CVTRA01Y.cpy` (50) | load, update/insert, unload | `TreeMap` keyed on cols 1-17 |
| `TRANFILE` | `app/cpy/CVTRA05Y.cpy` (350) | new, insert | `TreeMap` keyed on cols 1-16. Duplicate key → abend |
| `DALYREJS` | 350 tran + `9(4)` reason + `X(76)` desc (430) | write seq | list in write order |

KSDS keys are compared as plain ASCII strings (byte order = BDB order for these digit/char keys). Outputs are written only on normal end (RC 0/4), as the harness unloads only then.

Fields read, written or reinterpreted (1-based cols; zoned overpunch, held as `long` cents):

| Record | Field | Cols | PIC |
|---|---|---|---|
| DALYTRAN | type / cat | 17-18 / 19-22 | `X(2)` / `9(4)` |
| | amt | 133-143 | `S9(9)V99` |
| | card / orig-ts | 263-278 / 279-304 | `X(16)` / `X(26)` |
| XREF | acct-id | 26-36 | `9(11)` |
| ACCOUNT | curr-bal, credit-limit | 13-24, 25-36 | `S9(10)V99` |
| | expiry | 59-68 | `X(10)` |
| | cyc-credit, cyc-debit | 79-90, 91-102 | `S9(10)V99` |
| TCATBAL | bal | 18-28 | `S9(9)V99` |
| TRAN out | proc-ts / filler | 305-330 / 331-350 | `X(26)` / spaces |

## Zoned decimal codec

- Decode: digits plus the last byte as an overpunch (`{A-I` = +0..9, `}J-R` = -0..9; a plain digit = +). Unknown sign byte → abend (status-equivalent).
- Encode: absolute value mod 10^n (n = PIC digits). This is **high-order truncation**. Last digit is overpunched; zero is always encoded as `{` (positive).
- `TRAN-AMT` is copied as raw bytes from DALYTRAN (6 stock records end in `}` and pass through byte-for-byte).

## Processing rules (per DALYTRAN line, in file order)

1. `count += 1`, `reason = 0`, `desc = ""`.
2. The card is not in XREF → 100 `INVALID CARD NUMBER FOUND`. Stop validating.
3. XREF acct is not in ACCTFILE → 101 `ACCOUNT RECORD NOT FOUND`. Stop validating.
4. `temp = trunc11(cycCredit − cycDebit + amt)`. This is evaluated exactly and then truncated to `S9(9)V99`. If `creditLimit < temp` → 102 `OVERLIMIT TRANSACTION`.
5. Independently: if `expiry.compareTo(origTs[0..10]) < 0` (string) → 103 `TRANSACTION RECEIVED AFTER ACCT EXPIRATION`. This **overwrites** 102.
6. reason ≠ 0: `rejects += 1`. Append `line(350) + %04d reason + desc padded to 76`.
7. reason = 0, post in this order:
   a. Look up TCATBAL key `acctId(11)+type(2)+cat(4)`. Missing → print `TCATBAL record not found for key : <key>.. Creating.` and create `key + 0 + 22 spaces`. Then `bal = trunc11(bal + amt)`.
   b. Account: `currBal = trunc12(currBal + amt)`. If `amt >= 0`, `cycCredit = trunc12(cycCredit + amt)`, else `cycDebit = trunc12(cycDebit + amt)`.
   c. TRAN out = cols 1-304 of input with amt unchanged, + proc-ts `YYYY-MM-DD-HH.MM.SS.hh0000` + 20 spaces. Insert into TRANFILE.
8. End: print `TRANSACTIONS PROCESSED :%09d`, `TRANSACTIONS REJECTED  :%09d` and `END OF EXECUTION OF PROGRAM CBTRN02C`. The first line printed is `START OF EXECUTION OF PROGRAM CBTRN02C`. RC = rejects > 0 ? 4 : 0.

Only the touched fields are re-encoded. Every other byte of a record is preserved.

## Edge cases

| Case | Handling |
|---|---|
| Missing/unreadable DD file | Print `ERROR OPENING <file>` as COBOL does + `FILE STATUS IS: NNNN0035` + `ABENDING PROGRAM` → exit 16 |
| Duplicate TRAN-ID | `ERROR WRITING TO TRANSACTION FILE`, status `0022`, abend 16 |
| Empty DALYTRAN | counts 0/0, RC 0, outputs = inputs re-sorted |
| Line shorter than LRECL | Right-pad with spaces |
| amt = 0 (`…{`) | Counts as credit (`>= 0`) |
| Overflow past PIC digits | Silent high-order truncation (defect #6) |

## Preserved defects (pinned, not fixed; see intent #1-#6)

- D1 The limit check uses `cycCredit − cycDebit + amt` (debit is negative) and ignores curr-bal.
- D2 103 overwrites 102 on the same transaction.
- D3 Reason 109 (account REWRITE INVALID KEY) cannot occur with an in-memory map. It is unreachable in Java as in practice. No reject is written.
- D4 Non-atomic order TCATBAL → account → TRAN. Kept. The Java writes outputs only at the end, so it matches the harness's observable result.
- D5 A DALYREJS close error displays XREFFILE status. This is mirrored in the close-error path.
- D6 Silent truncation of `WS-TEMP-BAL`/`TRAN-CAT-BAL` to 9 integer digits and of account fields to 10.

## Acceptance tests (→ intent AC)

| Test | AC |
|---|---|
| `mvn -q verify` green, deps JUnit 5 + Surefire only | 1 |
| `GoldenEquivalenceTest`: stock inputs, clock `2022071800000000`; 4 data files byte-equal to `legacy/golden/CBTRN02C/` | 2, 5 |
| Same test: stdout = `SYSOUT.txt`, RC 4. `NoRejectsTest` RC 0. `DuplicateTranIdTest` RC 16 | 3, 4 |
| `Defect1_LimitUsesGrossCycle`, `Defect2_103Overwrites102`, `Defect6_TruncatesHighOrder`, `Defect5_CloseStatusMessage` (unit), `Defect3/4` documented tests | 6 |
| `Reason100/101/103`, `TcatbalCreateVsUpdate`, `NegativeAmountGoesToDebit`, `ZonedCodecTest` (`{`, `}`, `-0`) | 6 |
| Script: Java ACCTFILE/TCATBALF → `INPUT_DIR` for CBACT04C, diff with `golden/CBACT04C-after-CBTRN02C` | 7 |
| `legacy/harness/golden.sh --check` | 8 |
