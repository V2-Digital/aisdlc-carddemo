# CR-3 Spec: CBTRN02C in Java 21 (behaviour-preserving)

Legacy behaviour, file:line refs and defects D1–D8 are in [intent.md](intent.md). This spec fixes the Java contract.
Approver defaults are adopted: SYSOUT is line-identical (Q1), D1 is preserved (Q2), I/O is canonical text only (Q3).

## Invocation
`java -jar cbtrn02c.jar` (main class `carddemo.cbtrn02c.Main`). Each DD is a path, set by an env var or `-D<DD>=path`:
`DALYTRAN XREFFILE ACCTFILE TCATBALF` (inputs). `ACCTFILE`/`TCATBALF` also take an output path via `<DD>_OUT`, which defaults to in-place. `TRANFILE DALYREJS` are outputs.
Clock: `CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss` (same 16-digit form as the harness). If it is unset, the system clock (UTC) is used.
SYSOUT goes to stdout. The exit code is the process RC.

## Files (canonical text: one record per line, LF, fixed width)
| DD | Copybook | LRECL | Read as |
|---|---|---|---|
| DALYTRAN | `app/cpy/CVTRA06Y.cpy` | 350 | sequential |
| XREFFILE | `app/cpy/CVACT03Y.cpy` | 50 | map card(1-16) → line; short lines right-padded with spaces |
| ACCTFILE | `app/cpy/CVACT01Y.cpy` | 300 | sorted map acct-id(1-11) → byte[300] |
| TCATBALF | `app/cpy/CVTRA01Y.cpy` | 50 | sorted map key(1-17) → byte[50]; CR stripped |
| TRANFILE | `app/cpy/CVTRA05Y.cpy` | 350 | sorted map tran-id(1-16), written in key order |
| DALYREJS | 350 tran + `9(4)` + `X(76)` | 430 | appended in write order |

Every input line has its trailing `\r` stripped and is then space-padded or truncated to LRECL, matching the harness load. Records are handled as `byte[]` (ISO-8859-1). Only these fields are decoded (1-based offset, length):

| Field | Off | Len | PIC | Java |
|---|---|---|---|---|
| DALYTRAN-ID / TRAN-ID | 1 | 16 | X(16) | key string |
| DALYTRAN-TYPE-CD, CAT-CD | 17, 19 | 2, 4 | X(2), 9(4) | raw bytes (TCATBAL key) |
| DALYTRAN-AMT | 133 | 11 | S9(9)V99 | `long` cents |
| DALYTRAN-CARD-NUM | 263 | 16 | X(16) | XREF key |
| DALYTRAN-ORIG-TS(1:10) | 279 | 10 | X | string |
| XREF-ACCT-ID | 26 | 11 | 9(11) | raw bytes (ACCT key) |
| ACCT-CURR-BAL | 13 | 12 | S9(10)V99 | `long` cents |
| ACCT-CREDIT-LIMIT | 25 | 12 | S9(10)V99 | `long` cents |
| ACCT-EXPIRAION-DATE | 59 | 10 | X(10) | string |
| ACCT-CURR-CYC-CREDIT / DEBIT | 79 / 91 | 12 / 12 | S9(10)V99 | `long` cents |
| TRAN-CAT-BAL | 18 | 11 | S9(9)V99 | `long` cents |
| TRAN-PROC-TS | 305 | 26 | X(26) | set from the clock |

Overpunch: the last byte carries the sign and digit (`{`/`A`-`I` = +0..9, `}`/`J`-`R` = −0..9). Encoding writes `{A-I` for values ≥ 0 and `}J-R` for values < 0, so a zero result is written as `{`.

## Processing rules (in order)
1. Load all inputs. A missing or unreadable file prints the COBOL open-error lines and exits with RC 16.
2. Print `START OF EXECUTION OF PROGRAM CBTRN02C`. For each DALYTRAN record: `count++`, reason=0, desc=spaces.
3. Validate:
   - Card not in XREF → 100 `INVALID CARD NUMBER FOUND`. Stop validating.
   - Else acct not in ACCT → 101 `ACCOUNT RECORD NOT FOUND`.
   - Else `temp = trunc9(cycCredit − cycDebit + amt)`, where trunc9 keeps sign and the low 11 digits (S9(9)V99, no SIZE ERROR). If `limit < temp` → 102 `OVERLIMIT TRANSACTION`.
   - Then, independently, if `expiry < origTs[0..10]` (byte-wise text compare) → 103 `TRANSACTION RECEIVED AFTER ACCT EXPIRATION` (overwrites 102).
4. If reason ≠ 0: append `rec(350) + %04d reason + desc padded to 76` to DALYREJS. `rejects++`. Nothing else changes.
5. Else post:
   - TRAN = the DALYTRAN bytes 1–304, then PROC-TS = `YYYY-MM-DD-HH.MM.SS.hh0000`, then bytes 331–350 = 20 spaces.
   - TCATBAL key = acct(11)+type(2)+cat(4). If found: copy it into the **work area**, `bal = trunc11(bal + amt)`, rewrite. If not found: print `TCATBAL record not found for key : <key>.. Creating.`. In the work area, set the key, set bal = amt, and keep bytes 29–50 from the previous work-area contents (initially spaces). Then insert.
   - ACCT: `currBal`, then `cycCredit` (if amt ≥ 0) or `cycDebit` (if amt < 0), each `= trunc12(x + amt)` (S9(10)V99). Rewrite.
   - TRANFILE insert. A duplicate id prints the write-error lines and exits with RC 16. Balances already changed stay changed (D6). The partial files are still written.
6. Write outputs. Print `TRANSACTIONS PROCESSED :%09d`, `TRANSACTIONS REJECTED  :%09d`, `END OF EXECUTION OF PROGRAM CBTRN02C`. RC = 4 if rejects > 0, else 0.

Abend message text is copied from the COBOL DISPLAYs (`CBTRN02C.cbl:707-735`): `ERROR ...`, `FILE STATUS IS: NNNN`, then `ABENDING PROGRAM`. These messages are not golden-checked.

## Edge cases
| Case | Handling |
|---|---|
| Same account twice in a run | strictly sequential; the second txn validates against the posted balances |
| amt = 0 | goes to CYC-CREDIT (`>= 0`) |
| `limit == temp` | posts (no 102) |
| blank or garbage expiry / ORIG-TS | text compare; blank expiry sorts low → 103 (D7) |
| sum overflow | high-order digits silently dropped (D5 / D9) |
| first TCATBAL access is a create | FILLER is spaces; later creates inherit the last record read |
| CRLF / 36-byte XREF lines | normalised on load |
| empty DALYTRAN | counts 0, RC 0, TRANFILE empty |

## Preserved defects (one test each, `LegacyDefectTest`)
- D1 overlimit uses `credit − debit + amt`; a negative debit inflates exposure. CURR-BAL is ignored.
- D2 103 overwrites 102.
- D3 the 109 path is unreachable (rewrite of a just-read account); not modelled beyond a comment.
- D4 DALYREJS close error shows XREF status; reproduced in the message text.
- D5 ADDs truncate modulo 10^11 / 10^12 with no error.
- D6 a duplicate TRAN-ID abends after the TCATBAL/ACCT updates.
- D7 the expiry check is a string compare.
- D8 TRANFILE starts empty every run (opened OUTPUT); any prior content is ignored.
- **D9 (new)** `WS-TEMP-BAL` is `S9(9)V99` (`:187`), so exposure above 999,999,999.99 is truncated before the limit compare.

## Acceptance tests → intent AC
| AC | Test |
|---|---|
| 1, 3 | `GoldenEquivalenceTest`: stock inputs, clock `2022071800000000`; 4 files + SYSOUT byte-equal to `legacy/golden/CBTRN02C/`; RC 4 |
| 2 | `ReturnCodeTest`: no rejects → 0; missing file → 16; duplicate TRAN-ID → 16 |
| 4 | `ValidationRulesTest`, `PostingTest`: 100 (no acct lookup), 101, 102 boundary, 103 over 102, negative → debit, zero → credit, create vs update, reject leaves balances untouched |
| 5 | `OverpunchTest` (all 20 sign chars round-trip), `RecordIoTest` (CRLF, short lines) |
| 6 | `mvn -q verify`; `legacy/harness/golden.sh --check` |
| 7 | `LegacyDefectTest` D1–D9 |
