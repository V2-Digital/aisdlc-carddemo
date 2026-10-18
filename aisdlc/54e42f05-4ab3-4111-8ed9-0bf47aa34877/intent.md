# CR-3 Intent: POSTTRAN (`CBTRN02C`) to Java 21

## Problem
The daily posting job runs on a mainframe. It is a single COBOL program bound to VSAM files. It can't run in a container and is hard to change or test. Cards Technology wants it on a standard runtime without changing any business result.

## Outcome
`modernized/cbtrn02c/` is a plain Java 21 program that can run in a container. For the same inputs it gives the same results as the COBOL job: the same posted transactions, rejects, account balances and category balances, byte for byte, and the same return code. It reproduces today's behaviour, quirks included. Defects are flagged, not fixed.

## What the legacy code does today
Evidence: I ran `legacy/harness/run.sh CBTRN02C`. All six outputs are byte-identical to `legacy/golden/CBTRN02C/`. SYSOUT shows `TRANSACTIONS PROCESSED :000000300` and `REJECTED :000000038`, and RC is `4`.

### Files (JCL `app/jcl/POSTTRAN.jcl` STEP15; SELECTs at `CBTRN02C.cbl:28-61`)
| DD | Org / access | Open | LRECL | Key | Layout | Stock data |
|---|---|---|---|---|---|---|
| DALYTRAN | sequential | INPUT | 350 | – | `CVTRA06Y` | `dailytran.txt`, 300 recs |
| XREFFILE | KSDS random | INPUT | 50 | card num (1-16) | `CVACT03Y` (acct id at 26-36) | `cardxref.txt`, lines are 36 bytes, padded to 50 |
| ACCTFILE | KSDS random | I-O | 300 | acct id (1-11) | `CVACT01Y` | `acctdata.txt`, 50 recs |
| TCATBALF | KSDS random | I-O | 50 | acct(11)+type(2)+cat(4) | `CVTRA01Y` | `tcatbal.txt`, 50 recs, CRLF |
| TRANFILE | KSDS random | **OUTPUT** (replaced) | 350 | tran id (1-16) | `CVTRA05Y` | starts empty |
| DALYREJS | sequential | OUTPUT | 430 | – | 350 tran + 4-digit reason + 76-char desc | new GDG |

Amounts are zoned decimal with overpunch signs: DALYTRAN-AMT `S9(9)V99` at cols 133-143, ACCT balances `S9(10)V99`, TRAN-CAT-BAL `S9(9)V99` at cols 18-28.

### Processing steps
1. Open all six files. Any open status other than `00` displays an error and abends (`:236-343`, `9999` at `:707`). The harness stub exits with 16.
2. Read DALYTRAN sequentially until status `10` (`:345-369`). Count every record read.
3. **Validate** (`:370-422`). Reason code and description are reset per record (`:208-209`).
   - **100** `INVALID CARD NUMBER FOUND`: card number not in XREF (`:380-392`). The account is then not looked up.
   - **101** `ACCOUNT RECORD NOT FOUND`: the XREF account id is not in ACCTFILE (`:393-399`).
   - **102** `OVERLIMIT TRANSACTION`: `CREDIT-LIMIT < CURR-CYC-CREDIT − CURR-CYC-DEBIT + AMT` (`:403-413`).
   - **103** `TRANSACTION RECEIVED AFTER ACCT EXPIRATION`: `EXPIRATION-DATE < ORIG-TS(1:10)`, compared as **text** (`:414-420`). It is checked even when 102 has already failed, and **overwrites** it.
4. **Reject** (`:446-465`): write the 350-byte input record plus the 80-byte trailer (`9(4)` reason + `X(76)` desc). Count it. No balances change.
5. **Post** (`:424-444`). This runs in the order below:
   - Build TRAN-RECORD field by field from DALYTRAN. `TRAN-PROC-TS` = current date as `YYYY-MM-DD-HH.MM.SS.hh0000` (`:692-705`). The input PROC-TS is ignored. The 20-byte FILLER is never set, so it stays spaces.
   - **TCATBAL** (`:467-542`): random-read the key acct+type+cat. If found, add AMT and REWRITE. If not found (`23`), display `TCATBAL record not found for key : <17-char key>.. Creating.`, INITIALIZE, set the key, add AMT, and WRITE. Any other status abends.
   - **ACCOUNT** (`:545-560`): `CURR-BAL += AMT`. If `AMT >= 0` then `CURR-CYC-CREDIT += AMT`, else `CURR-CYC-DEBIT += AMT`. Then REWRITE. The record is the one read during validation.
   - **TRANFILE** WRITE (`:562-579`). A non-`00` status (for example a duplicate id) abends.
6. Close all files, then display the processed and rejected counts. **RC = 4 if any rejects**, else 0 (`:221-234`).

### Stock-data facts (golden)
- 262 posted and 38 rejected, **all 38 are 0102**. Each card resolves to an account, and every ORIG-TS is `2022-06-10`, earlier than every expiry (2023-01-06 to 2025-12-28).
- All 50 accounts change. Example: account 50 has CURR-BAL 492.00 → 1945.87, CYC-CREDIT 1501.75, CYC-DEBIT −47.88.
- TCATBALF: 50 existing `01/0001` records are updated, and 50 `03/0001` records are created (SYSOUT lines). That makes 100.
- KSDS outputs are unloaded in key order. TRANFILE comes out sorted because the input is sorted.

## Scope
**In:** a Java 21 rewrite of CBTRN02C in `modernized/cbtrn02c/` (Maven, JUnit 5 only). It reads and writes the canonical fixed-width text form used by the harness. Files are configured by DD name (paths), and the clock can be injected. Container-runnable. It includes characterization tests and an equivalence test against `legacy/golden/CBTRN02C`.

**Out:** fixing any legacy defect; other programs (CBACT04C and others); real VSAM/EBCDIC binary I/O; JCL or scheduler changes; GDG handling; changes to the golden data or harness.

## Acceptance criteria
1. On the stock inputs, with the clock frozen at `2022-07-18 00:00:00.00`, the Java program's `TRANFILE`, `DALYREJS`, `ACCTFILE` and `TCATBALF` are **byte-identical** to `legacy/golden/CBTRN02C/*.txt` (262 / 38 / 50 / 100 records).
2. The exit code is **4** when rejects exist (stock: 38), **0** when there are none, and **16** on any I/O failure that abends the COBOL program (open, read, write or rewrite errors, a duplicate TRAN-ID).
3. SYSOUT lines match `golden/CBTRN02C/SYSOUT.txt` (54 lines), including the `Creating.` messages and the zero-padded 9-digit counts.
4. Characterization tests pin each rule: 100 (no account lookup), 101, 102 boundary (`limit == temp` posts), 103 overriding 102, a negative amount going to CYC-DEBIT, a zero amount going to CYC-CREDIT, a TCATBAL create versus an update, and rejects not touching balances.
5. Overpunch encode and decode round-trips every sign character found in the data (`{ A-I } J-R`). Inputs with CRLF endings or short (36-byte) lines are read the same way the harness loads them.
6. `mvn -q verify` passes in `modernized/cbtrn02c/` with no dependencies other than JUnit 5. `legacy/harness/golden.sh --check` still passes.
7. Every legacy defect below is preserved and has a named test that documents it.

## Risks and findings
Legacy defects: preserve them and flag them.
- **D1 Overlimit formula double-counts debits?** `:403-405` uses `credit − debit + amt`. CYC-DEBIT is stored **negative** (account 50: `00000000478Q` = −47.88), so subtracting it *adds* the payment or refund magnitude to exposure. In a Python replay of the stock data, legacy rules give 38 rejects. Using `credit + debit + amt` would give **23**. It also ignores CURR-BAL. This is probably not the intended rule, and it is kept as-is.
- **D2 Last failing rule wins:** a 103 overwrites a 102 (`:410` vs `:417`), so a transaction that is over limit and expired is reported only as expired.
- **D3 Reason 109 is dead code:** the ACCOUNT REWRITE `INVALID KEY` sets 109 (`:555-558`), but nothing reads it afterwards. The transaction is still written and not counted as a reject.
- **D4 Wrong status shown:** a DALYREJS close error displays `XREFFILE-STATUS` (`:649`).
- **D5 No ON SIZE ERROR:** the ADDs at `:508,527,547-551` silently truncate high-order digits on overflow. Java must truncate the same way: modulo 10^11 for TRAN-CAT-BAL, 10^12 for account fields.
- **D6 Partial posting on abend:** TCATBAL and the account are rewritten *before* the TRANFILE write. A duplicate TRAN-ID therefore abends with balances already updated and no rollback.
- **D7 Expiry check is a text compare:** a blank or invalid date sorts low and rejects with 103.
- **D8 TRANFILE is opened OUTPUT:** every run replaces the transaction master, not appends to it (`:256`).

Rewrite risks:
- **INITIALIZE leaves FILLER alone** (GnuCOBOL 3.2, checked with a scratch program). A newly created TCATBAL record keeps the 22-byte FILLER of the last record READ INTO the work area, because INVALID KEY doesn't move data. In the golden data that is always zeros. If the first TCATBAL access of a run is a create, the FILLER would be the initial working-storage value (spaces). Java must model this "sticky work area" to stay byte-exact.
- **Sign of zero:** the output must write `{` for +0. Check whether any negative-zero result appears.
- The ACCT record read in validation is reused at posting. If two transactions for the same account appear, each one sees the balances already posted, so processing must stay strictly sequential.
- The golden data covers only reason 102. Reasons 100, 101 and 103, the abend paths and overflow are proved only by characterization tests, not by golden data. **Proposed:** generate extra scenarios through `INPUT_DIR` with the harness during Test and compare the Java output with the COBOL output (without changing `legacy/golden/`).

## Questions for the approver
1. **Should SYSOUT be part of the equivalence check?** It is not in the CR's list. *Default: yes, line-identical. It is cheap and it pins the `Creating.` side effect.*
2. **Is D1 (overlimit formula) a real business defect to raise as a follow-up CR?** *Default: preserve it now, raise a separate ticket.*
3. **I/O format:** should the Java program use the harness's canonical text files (LF, fixed width) rather than binary VSAM or EBCDIC? *Default: yes, text only.*
