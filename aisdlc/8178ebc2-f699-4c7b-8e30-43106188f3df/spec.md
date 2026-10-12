# CR-3 Spec: CBTRN02C (POSTTRAN) in Java 21

This is a behaviour-preserving rewrite. "COBOL" means `app/cbl/CBTRN02C.cbl` as compiled by the harness (GnuCOBOL 3.2, `-fsign=EBCDIC`). Line refs are `CBTRN02C.cbl:NNN`.

Facts marked **[probe]** were observed in this stage by running `legacy/harness/run.sh CBTRN02C` with `INPUT_DIR` edge-case inputs (built in `/tmp/exp`, not committed). Approver questions Q1 and Q2 are taken at their defaults: SYSOUT is part of the contract, and D5 is kept.

## 1. Invocation

| Item | Behaviour |
|---|---|
| Entry | `java -jar cbtrn02c.jar` (no args; POSTTRAN has no PARM) |
| File binding | Env `DD_<DDNAME>` gives the path. If unset, the path is `<DDNAME>` in the cwd (same as GnuCOBOL `-fassign-clause=ibm`). |
| Clock | Env `CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss` freezes the clock, the same as the harness. Otherwise the system clock in the default zone, truncated to hundredths. In code it is an injectable `java.time.Clock`. |
| stdout | The DISPLAY lines, LF-terminated (§5). |
| stderr | `CEE3ABD: ABEND U0999 TIMING=0` on abend (not part of the contract). |
| Exit code | 0 = no rejects, 4 = rejects > 0, 16 = abend. |

## 2. Datasets

All files are in the harness's canonical text form: one record per line, LF, fixed width, ISO-8859-1 (byte = char).

- **On load:** strip `\r`, right-pad with spaces to LRECL, truncate longer lines. `cardxref.txt` (36 bytes) and `tcatbal.txt` (CRLF) depend on this.
- **On write:** exactly LRECL bytes + `\n`.
- **KSDS:** loaded into a sorted map and written back in ascending key order (byte compare).

| DD | Org | Open | LRECL | Key (1-based) | Notes |
|---|---|---|---|---|---|
| `DALYTRAN` | seq | INPUT | 350 | — | read in file order |
| `XREFFILE` | KSDS | INPUT | 50 | 1-16 card num | the AIX is not used by this program |
| `ACCTFILE` | KSDS | I-O | 300 | 1-11 acct id | rewritten in place |
| `TCATBALF` | KSDS | I-O | 50 | 1-17 acct+type+cat | records can be added |
| `TRANFILE` | KSDS | **OUTPUT** | 350 | 1-16 tran id | created or replaced (D5) |
| `DALYREJS` | seq | OUTPUT | 430 | — | in write order |

### Record layouts (1-based offset, length)

**DALYTRAN / TRANFILE** (`CVTRA06Y`, `CVTRA05Y`, same shape)

| Field | Off | Len | PIC | Java |
|---|---|---|---|---|
| ID | 1 | 16 | X(16) | String |
| TYPE-CD | 17 | 2 | X(2) | String |
| CAT-CD | 19 | 4 | 9(4) | String (copied verbatim) |
| SOURCE | 23 | 10 | X | String |
| DESC | 33 | 100 | X | String |
| AMT | 133 | 11 | S9(9)V99 zoned | `long` cents |
| MERCHANT-ID | 144 | 9 | 9(9) | String |
| MERCHANT-NAME / CITY / ZIP | 153 / 203 / 253 | 50 / 50 / 10 | X | String |
| CARD-NUM | 263 | 16 | X | String |
| ORIG-TS | 279 | 26 | X | String |
| PROC-TS | 305 | 26 | X | String |
| FILLER | 331 | 20 | X | String |

**XREFFILE** (`CVACT03Y`): CARD-NUM 1/16, CUST-ID 17/9, ACCT-ID 26/11 (9(11), used verbatim as the key), FILLER 37/14.

**ACCTFILE** (`CVACT01Y`): ID 1/11, ACTIVE-STATUS 12/1, CURR-BAL 13/12, CREDIT-LIMIT 25/12, CASH-CREDIT-LIMIT 37/12, OPEN-DATE 49/10, EXPIRAION-DATE 59/10, REISSUE-DATE 69/10, CURR-CYC-CREDIT 79/12, CURR-CYC-DEBIT 91/12, ADDR-ZIP 103/10, GROUP-ID 113/10, FILLER 123/178. The money fields are S9(10)V99 zoned, held as `long` cents.

**TCATBALF** (`CVTRA01Y`): ACCT-ID 1/11, TYPE-CD 12/2, CAT-CD 14/4, BAL 18/11 (S9(9)V99 zoned), FILLER 29/22.

**DALYREJS**: 1-350 = the input DALYTRAN record unchanged. 351-354 = reason `9(4)` (e.g. `0102`). 355-430 = description, space-padded to 76.

### Zoned decimal (overpunch)

The last byte carries the sign and digit: `{ A-I` = +0..9, `} J-R` = −0..9. All other bytes are digits.

- **Decode:** `−0` decodes to 0. Comparisons treat −0 as equal to +0.
- **Encode** (only for fields that are arithmetic *targets*):
  - The value is truncated on the left to the PIC's digits: `v mod 10^n`, keeping the sign of the untruncated result.
  - Zero is always written `{` **[probe: 500.00 + −500.00 → `0000000000{`]**.
  - Negative values use `J-R`, positive `A-I`/`{`.
- **Fields that are not arithmetic targets keep their original bytes.** That includes `TRAN-AMT`, which is copied from the input: a `-0` input stays `0000000000}` in TRANFILE **[probe]**.

## 3. Processing rules (in order)

1. **Start.** DISPLAY `START OF EXECUTION OF PROGRAM CBTRN02C`.
2. **Open** in this order: DALYTRAN, TRANFILE, XREFFILE, DALYREJS, ACCTFILE, TCATBALF. If an open fails, abend (§4) with:

   | DD | Message | Status |
   |---|---|---|
   | DALYTRAN | `ERROR OPENING DALYTRAN` | 35 if missing |
   | TRANFILE | `ERROR OPENING TRANSACTION FILE` | 30 if it can't be created |
   | XREFFILE | `ERROR OPENING CROSS REF FILE` | 35 |
   | DALYREJS | `ERROR OPENING DALY REJECTS FILE` | 30 |
   | ACCTFILE | `ERROR OPENING ACCOUNT MASTER FILE` | 35 |
   | TCATBALF | `ERROR OPENING TRANSACTION BALANCE FILE` | 35 |

   Missing input → `NNNN0035`, unwritable output → `NNNN0030` **[probe: all five checked]**.
3. **Loop** over DALYTRAN records until EOF. A read error gives `ERROR READING DALYTRAN FILE`, status 30. For each record:
   1. Add 1 to processed (`9(9)`). Set reason = 0 and description = spaces.
   2. **XREF:** look up CARD-NUM (263-278). Not found → reason 100, `INVALID CARD NUMBER FOUND`, and skip step 3.
   3. **Account:** look up XREF-ACCT-ID. Not found → 101, `ACCOUNT RECORD NOT FOUND`. If found:
      - **Over limit:** `temp = trunc9(CYC-CREDIT − CYC-DEBIT + AMT)`, where `trunc9` truncates to S9(9)V99 (D1, D7). If `CREDIT-LIMIT < temp` → 102 `OVERLIMIT TRANSACTION`.
      - **Expiry:** if `EXPIRAION-DATE < ORIG-TS[1..10]` (byte string compare) → 103 `TRANSACTION RECEIVED AFTER ACCT EXPIRATION`. This overwrites a 102 (D2).
   4. Reason ≠ 0: add 1 to rejected and write the reject record. A write failure gives `ERROR WRITING TO REJECTS FILE`.
   5. Reason = 0: post.
      1. Build the tran record: bytes 1-304 from the input; PROC-TS = `YYYY-MM-DD-HH.MM.SS.hh0000` from the clock, read once per posted transaction; FILLER 331-350 = spaces. The input PROC-TS and FILLER are discarded.
      2. **TCATBAL:** key = XREF-ACCT-ID + TYPE-CD + CAT-CD.
         - **Not found:** DISPLAY `TCATBAL record not found for key : <17-byte key>.. Creating.` Then build a new record: key fields set, BAL = trunc9(0 + AMT), **FILLER = the FILLER of the last TCATBAL record successfully read in this run, or spaces if none** (D9). WRITE it.
         - **Found:** BAL = trunc9(BAL + AMT), then REWRITE. Errors give `ERROR READING / WRITING / REWRITING TRANSACTION BALANCE FILE`.
      3. **Account:** CURR-BAL = trunc10(CURR-BAL + AMT). If AMT ≥ 0 (including −0), CYC-CREDIT = trunc10(CYC-CREDIT + AMT); otherwise CYC-DEBIT = trunc10(CYC-DEBIT + AMT). REWRITE. Any failure is ignored (D3).
      4. **TRANFILE:** WRITE. A duplicate key gives `ERROR WRITING TO TRANSACTION FILE`, `NNNN0022`, abend. The balance updates from steps 2-3 remain (D4) **[probe]**.
4. **Close** in open order. Messages: `ERROR CLOSING DALYTRAN FILE`, `… TRANSACTION FILE`, `… CROSS REF FILE`, `… DAILY REJECTS FILE` (shows the XREFFILE status, D6), `… ACCOUNT FILE`, `… TRANSACTION BALANCE FILE`. KSDS files are written back to disk on close.
5. **End.** DISPLAY `TRANSACTIONS PROCESSED :` + 9 digits, `TRANSACTIONS REJECTED  :` + 9 digits (two spaces), then `END OF EXECUTION OF PROGRAM CBTRN02C`. Exit 4 if rejected > 0, else 0.

## 4. Abend

DISPLAY the message, then `FILE STATUS IS: NNNN` + the status, then `ABENDING PROGRAM`. Print the CEE3ABD line to stderr and exit **16**.

- **Status format** (`9910`): for a numeric status whose first byte isn't `9`, print `00` + the 2-char status. Otherwise print the first byte + the binary value of the second byte as 3 digits.
- **Files on abend.** VSAM and BDB writes are durable, so on abend Java flushes the current state of ACCTFILE, TCATBALF and TRANFILE, plus the DALYREJS lines written so far. This keeps D4 observable.

## 5. SYSOUT on stock data

There are 54 lines: start, 50 `Creating` lines in posting order (`…20030001`, `…13030001`, …), the two counts (`000000300` / `000000038`), and end. The output must match `legacy/golden/CBTRN02C/SYSOUT.txt` byte for byte.

## 6. Edge cases

| Case | Behaviour | Evidence |
|---|---|---|
| Empty DALYTRAN | 0/0 counts, RC 0, TRANFILE and DALYREJS empty | code |
| −0 amount | counted as credit (`≥ 0`); TRANFILE keeps `}`; balances written `{` | [probe] |
| temp bal overflow | truncated: cyc-credit 1,000,000,000.00 + 0.01 → temp 0.01 → **posted** | [probe] |
| Balance overflow | ADD truncates: 9,999,999,999.99 + 5.00 → 4.99 (`00000000499I`); TCATBAL 999,999,999.99 + 5.00 → 4.99 | [probe] |
| Create TCATBAL first in run | FILLER = spaces | [probe] |
| Create after a read | FILLER copied from the last record read (e.g. `XXXX…`) | [probe] |
| Stock creates | FILLER = 22 × `0` (inherited from `tcatbal.txt`) | golden |
| Duplicate tran id | abend 16 after TCATBAL and ACCT were updated | [probe] |
| Missing input / unwritable output | abend 16, status 35 / 30 | [probe] |
| Non-numeric zoned data | undefined in COBOL; Java throws, treated as a read error (not in scope) | — |
| Counter > 999,999,999 | wraps (9(9)); not tested | code |

## 7. Preserved defects

D1-D8 are as listed in the intent. The defects below are new or refined in this stage.

| # | Defect | Pinned by |
|---|---|---|
| D1 | Overlimit uses `credit − debit + amt`, so a payment increases the temp balance; CURR-BAL is ignored | `OverlimitTest` |
| D2 | 103 overwrites 102 | `ReasonCodeTest` |
| D3 | Account REWRITE failure is ignored (reason 109 is set but never used) | doc only (can't happen: the record was just read) |
| D4 | No atomicity on TRANFILE write abend | `AbendTest.duplicateTranIdLeavesBalancesPosted` |
| D5 | TRANFILE opened OUTPUT (replaced each run) | `TranfileReplacedTest` |
| D6 | DALYREJS close error shows the XREFFILE status | doc only (close can't fail in Java) |
| D7 | Silent truncation, with **an observed effect: an over-limit transaction passes when the temp balance overflows** | `TruncationTest` |
| D8 | ACTIVE-STATUS not checked; expiry compared as text; input PROC-TS discarded | `ReasonCodeTest`, `PostingTest` |
| **D9** (new) | `INITIALIZE` skips FILLER, so a created TCATBAL record inherits the FILLER of the previous READ. This corrects the intent, which said "spaces". | `TcatbalTest` |

## 8. Acceptance tests (AC → test)

| AC | Test |
|---|---|
| 1 | `mvn -q verify` runs all tests; the pom has only JUnit 5 + Surefire |
| 2 | `GoldenEquivalenceTest`: stock inputs, clock 2022-07-18 00:00:00.00 → the 4 files byte-equal to golden; 262/38/50/100 |
| 3 | `GoldenEquivalenceTest` RC = 4; `PostingTest.noRejectsRc0` |
| 4 | `GoldenEquivalenceTest` stdout equals `SYSOUT.txt` (54 lines) |
| 5 | `ReasonCodeTest`: 100, 101, 102, 103, and 102+103 → `0103…` (exact 80-byte trailers) |
| 6 | `OverlimitTest`: running totals across transactions; a negative amount raises the temp balance (D1) |
| 7 | `TcatbalTest`: create plus the "Creating" line; add to an existing record; FILLER inheritance (D9) |
| 8 | `ZonedTest`: round-trip of every sign char, −0, zero → `{`, left truncation at 11 and 12 digits; `TruncationTest` end to end |
| 9 | `AbendTest`: missing DALYTRAN (`NNNN0035`), duplicate tran id (`NNNN0022`): exact stdout lines, exit 16 |
| 10 | README lists D1-D9; tests named above |
| extra | `EdgeScenarioTest`: COBOL-captured expected outputs for the probe scenarios (see plan) |
