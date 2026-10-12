# CR-3 Intent: POSTTRAN / CBTRN02C to Java 21

## Problem / outcome

**Problem.** The daily posting job (`POSTTRAN`) is a mainframe COBOL program (`CBTRN02C`). It checks each card transaction from the day, posts the good ones, updates account and category balances, and writes rejects. Running and changing it requires mainframe skills and infrastructure.

**Outcome.** A plain Java 21 program in `modernized/cbtrn02c/` that runs in a container. Given the same inputs, it writes byte-identical `TRANFILE`, `DALYREJS`, `ACCTFILE` and `TCATBALF` and returns the same code. Legacy behaviour, defects included, is kept and pinned by tests. The golden scenario `legacy/golden/CBTRN02C` is the proof.

## What the legacy code does today

Evidence: `legacy/harness/run.sh CBTRN02C` on stock data gave byte-identical results to `legacy/golden/CBTRN02C` for all 6 files. Counts: **300 read, 262 posted, 38 rejected, RC 4.** SYSOUT has 54 lines.

### Files (`app/jcl/POSTTRAN.jcl`, `CBTRN02C.cbl:29-61`)

| DD | Org / open mode | LRECL | Key | Layout | Stock data |
|---|---|---|---|---|---|
| `DALYTRAN` | Sequential, INPUT | 350 | — | `CVTRA06Y` | `dailytran.txt`, 300 recs |
| `XREFFILE` | KSDS random, INPUT | 50 | card num 1-16 | `CVACT03Y` (acct id at 26-36) | `cardxref.txt`, 50 |
| `ACCTFILE` | KSDS random, **I-O** | 300 | acct id 1-11 | `CVACT01Y` | `acctdata.txt`, 50 |
| `TCATBALF` | KSDS random, **I-O** | 50 | acct(11)+type(2)+cat(4), 1-17 | `CVTRA01Y` | `tcatbal.txt`, 50 (all type `01`/cat `0001`) |
| `TRANFILE` | KSDS random, **OUTPUT** (`:256`) | 350 | tran id 1-16 | `CVTRA05Y` | starts empty |
| `DALYREJS` | Sequential, OUTPUT | 430 | — | 350 tran + 80 trailer | new |

Amounts are zoned decimal with an overpunched sign. `DALYTRAN-AMT`/`TRAN-AMT`/`TRAN-CAT-BAL` are S9(9)V99; account balances are S9(10)V99.

### Processing steps

1. Six opens (`:236-343`). If a status isn't `00`, the program prints `ERROR OPENING …` and `FILE STATUS IS: NNNN…`, then abends: `CEE3ABD` U999, which the harness turns into exit 16 (`:707-711`).
2. Reads `DALYTRAN` sequentially until status `10` (`:345-369`) and counts each record (`:206`).
3. **Validation** (`:370-422`). The reason starts at 0 with a blank description (`:208-209`).
   - XREF lookup by card number. Not found → **100** `INVALID CARD NUMBER FOUND` (`:380-392`).
   - Only if the XREF lookup passed: read the account by `XREF-ACCT-ID`. Not found → **101** `ACCOUNT RECORD NOT FOUND` (`:393-399`).
   - If the account is found, both checks below always run:
     - `WS-TEMP-BAL = CYC-CREDIT − CYC-DEBIT + AMT`. If `CREDIT-LIMIT < WS-TEMP-BAL` → **102** `OVERLIMIT TRANSACTION` (`:403-413`).
     - If `ACCT-EXPIRAION-DATE < ORIG-TS(1:10)` (text comparison) → **103** `TRANSACTION RECEIVED AFTER ACCT EXPIRATION` (`:414-420`).
4. **Reject** (`:446-465`). Counts the reject and writes the input record unchanged plus an 80-byte trailer: reason `9(4)` + description `X(76)`.
5. **Post** (`:424-444`). Order matters:
   1. Build `TRAN-RECORD` from the input. `TRAN-PROC-TS` = current time in DB2 format `YYYY-MM-DD-HH.MM.SS.hh0000` (`:692-705`). The input `PROC-TS` is ignored.
   2. **TCATBAL** (`:467-542`): read key (acct, type, cat).
      - Status `23` → print `TCATBAL record not found for key : <17-byte key>.. Creating.`, then `INITIALIZE` and WRITE a new record with bal = amt.
      - Otherwise bal += amt, then REWRITE.
   3. **Account** (`:545-560`): `CURR-BAL += amt`. If amt ≥ 0, `CYC-CREDIT += amt`; else `CYC-DEBIT += amt`. Then REWRITE.
   4. WRITE `TRANFILE` (`:562-579`). A non-`00` status (e.g. `22`, duplicate id) → abend.
6. Closes, then prints `TRANSACTIONS PROCESSED :<9 digits>` and `TRANSACTIONS REJECTED  :<9 digits>`. **RC = 4 if rejects > 0, else 0** (`:221-232`).

### Observed on stock data
- Every card has an XREF and every XREF has an account, so there are no 100/101 rejects.
- Earliest expiry is 2023-01-06; all ORIG-TS dates are 2022-06-10. So there are no 103 rejects.
- All 38 rejects are 102. A Python replay of rule 3 with running cycle totals reproduces exactly the same 38 IDs in the same order.
- `TRANFILE`: 262 records in key order. Bytes 1-304 equal the input record; PROC-TS is `2022-07-18-00.00.00.000000` (frozen clock); FILLER is spaces.
- `ACCTFILE`: all 50 accounts change, and only `CURR-BAL`, `CYC-CREDIT` and `CYC-DEBIT` differ.
- `TCATBALF`: 100 records = 50 updated `01/0001` + 50 created `03/0001`, which matches 50 "Creating" lines in SYSOUT.

## Scope

**In:**
- Java 21 Maven project `modernized/cbtrn02c/` (JUnit 5 only). It reads and writes the harness's canonical text datasets: fixed-width, LF, overpunch signs, KSDS in key order.
- Same DD names. A file location is configurable per DD, as `DD_<name>` variables in the harness do.
- Same validation, posting order, arithmetic, sign encoding, return codes (0 / 4 / 16 on I/O failure) and SYSOUT text.
- Injectable clock, so `TRAN-PROC-TS` is deterministic (default: system clock, hundredths precision).
- An equivalence test against `legacy/golden/CBTRN02C`, and characterization tests for each rule and defect below.
- A container entry point (Dockerfile or run script).

**Out:**
- Fixing any legacy defect.
- Real VSAM/EBCDIC binary I/O.
- JCL/scheduler changes, CICS screens, other programs (CBACT04C is CR-2).
- Changes to the golden data or harness.

## Acceptance criteria

1. `mvn -q verify` in `modernized/cbtrn02c/` passes on Java 21, with no dependencies beyond JUnit 5 and Surefire.
2. With the stock inputs (`dailytran`, `cardxref`, `acctdata`, `tcatbal`) and the clock fixed at 2022-07-18 00:00:00.00, the program's `TRANFILE`, `DALYREJS`, `ACCTFILE` and `TCATBALF` are **byte-identical** to `legacy/golden/CBTRN02C/*.txt`. Expected counts: 262 / 38 / 50 / 100 records.
3. The exit code is **4** on that scenario, matching `RC.txt`. It is 0 when there are no rejects.
4. Stdout matches `legacy/golden/CBTRN02C/SYSOUT.txt` (54 lines). See Q1.
5. Each reason code has a characterization test that checks the trailer bytes exactly:
   - 100: unknown card.
   - 101: XREF found, account missing.
   - 102: over limit.
   - 103: expired.
   - 102 and 103 together: the trailer shows 103.
6. Overlimit uses the running cycle totals, so earlier postings in the same run count against later transactions. A test pins that a negative amount *increases* `WS-TEMP-BAL` (defect D1).
7. A missing TCATBAL key creates a record and prints the "Creating" line; an existing key gets the amount added.
8. Sign encoding round-trips. Positive values and zero are written with `{`/`A-I`, negative with `}`/`J-R`. Results that overflow S9(9)V99 or S9(10)V99 are truncated on the left, as COBOL does without `ON SIZE ERROR`.
9. An I/O failure (e.g. a missing input file, or a duplicate tran id in `DALYTRAN`) prints the same `ERROR …` / `FILE STATUS IS:` / `ABENDING PROGRAM` lines and exits **16**.
10. Each defect D1-D8 below is listed in the Java code or README and pinned by a test where it can be observed.

## Risks and findings

### Legacy defects (preserve, do not fix)

| # | Finding | Evidence |
|---|---|---|
| D1 | **Overlimit formula counts payments as spend.** Debits are stored as negative amounts (`:551`), so `CREDIT − DEBIT` adds their size. A payment (negative amt) makes the check *worse*. The check also ignores `ACCT-CURR-BAL`. | `:403-405`, `:548-552` |
| D2 | **Only the last failure is reported.** The 102 and 103 checks both run, and 103 overwrites 102. | `:407-420` |
| D3 | **Account REWRITE failure is ignored.** INVALID KEY sets reason 109, but nothing reads it afterwards: the transaction is still written and not counted as a reject. Other non-`00` statuses aren't checked. | `:554-559` |
| D4 | **No atomicity.** TCATBAL and the account are updated *before* the TRANFILE write. An abend on that write (e.g. duplicate id) leaves balances posted with no transaction. Files already processed are not rolled back. | `:440-442` |
| D5 | **TRANFILE is opened OUTPUT.** Each run replaces the transaction master instead of appending to it. On z/OS this fails for a non-empty KSDS without REUSE. | `:256` |
| D6 | **Wrong status shown on close error.** A DALYREJS close error shows `XREFFILE-STATUS`, not `DALYREJS-STATUS`. | `:649` |
| D7 | **Silent truncation.** `WS-TEMP-BAL` and `TRAN-CAT-BAL` are S9(9)V99 while account fields are S9(10)V99, and there's no `ON SIZE ERROR`. | `:187`, `CVTRA01Y` |
| D8 | **Missing checks.** `ACCT-ACTIVE-STATUS` is never checked; expiry is compared as text (correct only for well-formed ISO dates); the input `PROC-TS` is discarded. | `:393-420` |

### Rewrite risks
- **Clock.** `TRAN-PROC-TS` is taken once per posted transaction, with hundredths precision. Java must use an injectable clock. The harness freezes the clock with `COB_CURRENT_DATE=2022071800000000`.
- **Input quirks.** `cardxref.txt` lines are 36 bytes and `tcatbal.txt` uses CRLF. The harness pads and strips these on load. The Java loader must do the same, or it should consume the harness's normalised form.
- **Write formats.**
  - `INITIALIZE` sets the TCATBAL FILLER to spaces.
  - The sign of a zero result must be written exactly as GnuCOBOL writes it. Not yet observed in golden data; confirm with a harness test in Build.
  - KSDS output must be written in key order.
- **Rule order.** XREF lookup failure skips the account checks. Only reason ≠ 0 means reject.

## Questions for the approver

1. **Is SYSOUT part of the contract?** The CR lists only the four datasets and RC. *Default: yes, match `SYSOUT.txt` byte for byte too.* It's cheap and is golden.
2. **D5 (TRANFILE opened OUTPUT): keep it?** *Default: keep the like-for-like behaviour (the output file is created or replaced) and flag it for a follow-up CR.*
