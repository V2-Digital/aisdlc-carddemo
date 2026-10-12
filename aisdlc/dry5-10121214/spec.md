# CR-1 Spec: CBACT04C (INTCALC) in Java 21

Run `dry5-10121214` · Stage: Design · Target `modernized/cbact04c/`

**Contract: behaviour-preserving.** For the same inputs, the Java program writes the same bytes as `CBACT04C.cbl` under the harness. That covers `TRANSACT`, `ACCTFILE`, SYSOUT and the exit code. Legacy defects are kept on purpose and listed in §6.

Evidence comes from `legacy/golden/` and from six probe runs of the COBOL through `legacy/harness/run.sh` with `INPUT_DIR` overrides (P1–P6, listed in §7).

---

## 1. Invocation (Q1–Q3 defaults applied)

```
java -jar cbact04c.jar <PARM> <IN_DIR> <OUT_DIR>
```

| Item | Rule |
|---|---|
| `PARM` | Run id. The first 10 chars are used, right-padded with spaces if shorter (COBOL `PARM-DATE X(10)`). It is not validated (F5). |
| `IN_DIR` | `TCATBALF.txt`, `XREFFILE.txt`, `ACCTFILE.txt`, `DISCGRP.txt` |
| `OUT_DIR` | Gets `TRANSACT.txt` and the updated `ACCTFILE.txt`. Both are written **only on RC 0**, which matches `run.sh` ("outputs not exported" on an abend). The input is never overwritten. |
| Clock | `CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss` when set, else system clock in UTC. The core takes an injectable `Supplier<LocalDateTime>`. |
| SYSOUT | DISPLAY lines go to stdout, LF-terminated. |
| stderr | On an abend: `CEE3ABD: ABEND U0999 TIMING=0` (matches the harness stub). |
| Exit code | 0 on success, 16 on any abend, 2 on bad CLI usage (wrong argument count). Exit 2 is new; the COBOL has no such path. |

## 2. Files and record layouts

Canonical text form:
- one record per line, with LF line endings;
- input lines may end in CRLF (the CR is stripped);
- short input lines are right-padded with spaces to LRECL;
- an input line longer than LRECL is invalid input (abend, see §4);
- an empty final line is ignored.

Columns are 1-based. Zoned fields use overpunch on the last byte:
- `{`, `A`–`I` mean +0..9;
- `}`, `J`–`R` mean −0..9;
- a plain digit is read as positive;
- on output, ≥ 0 is written as `{`/`A-I` and < 0 as `}`/`J-R`. A result of zero is always written as `{`.

### TCATBALF: driver, LRECL 50, key cols 1–17 (`CVTRA01Y`)
| Cols | Field | PIC | Java |
|---|---|---|---|
| 1–11 | TRANCAT-ACCT-ID | 9(11) | String (compared as text) |
| 12–13 | TRANCAT-TYPE-CD | X(2) | String |
| 14–17 | TRANCAT-CD | 9(4) | String |
| 18–28 | TRAN-CAT-BAL | S9(9)V99 | BigDecimal scale 2 |
| 29–50 | FILLER | X(22) | ignored |

The records are processed in **ascending byte order of cols 1–17** (KSDS sequential read). Java sorts them and does not trust the file order. A duplicate key is invalid input.

### XREFFILE: LRECL 50, primary key card 1–16, alternate key acct 26–36 (`CVACT03Y`)
| Cols | Field | PIC |
|---|---|---|
| 1–16 | XREF-CARD-NUM | X(16) |
| 17–25 | XREF-CUST-ID | 9(9) |
| 26–36 | XREF-ACCT-ID | 9(11) |
| 37–50 | FILLER | X(14) |

The stock `cardxref.txt` lines are 36 bytes, so they get padded. A lookup is by account id. If several cards share an account, the first one in file order wins; see risk R3 in plan.md.

### ACCTFILE: LRECL 300, key cols 1–11, read and rewritten (`CVACT01Y`)
| Cols | Field | PIC | Touched? |
|---|---|---|---|
| 1–11 | ACCT-ID | 9(11) | key |
| 12 | ACCT-ACTIVE-STATUS | X | — |
| 13–24 | ACCT-CURR-BAL | S9(10)V99 | **+= total interest** |
| 25–36 | ACCT-CREDIT-LIMIT | S9(10)V99 | — |
| 37–48 | ACCT-CASH-CREDIT-LIMIT | S9(10)V99 | — |
| 49–78 | OPEN / EXPIRAION / REISSUE dates | 3×X(10) | — |
| 79–90 | ACCT-CURR-CYC-CREDIT | S9(10)V99 | **set to 0** → `00000000000{` |
| 91–102 | ACCT-CURR-CYC-DEBIT | S9(10)V99 | **set to 0** → `00000000000{` |
| 103–112 | ACCT-ADDR-ZIP | X(10) | — |
| 113–122 | ACCT-GROUP-ID | X(10) | used as the rate key |
| 123–300 | FILLER | X(178) | — |

Output is **every** input record in ascending key order. Untouched records and untouched bytes pass through verbatim; only cols 13–24 and 79–102 are re-encoded on rewritten records. A duplicate key is invalid input.

### DISCGRP: LRECL 50, key cols 1–16 (`CVTRA02Y`)
| Cols | Field | PIC |
|---|---|---|
| 1–10 | DIS-ACCT-GROUP-ID | X(10) |
| 11–12 | DIS-TRAN-TYPE-CD | X(2) |
| 13–16 | DIS-TRAN-CAT-CD | 9(4) |
| 17–22 | DIS-INT-RATE | S9(4)V99 |
| 23–50 | FILLER | X(28) |

The fallback key is `DEFAULT` padded to 10 with spaces, followed by the same type and category.

### TRANSACT: output, LRECL 350, in write order (`CVTRA05Y`)
| Cols | Field | PIC | Value |
|---|---|---|---|
| 1–16 | TRAN-ID | X(16) | PARM(10) + suffix 9(6) from `000001` |
| 17–18 | TRAN-TYPE-CD | X(2) | `01` |
| 19–22 | TRAN-CAT-CD | 9(4) | `0005` |
| 23–32 | TRAN-SOURCE | X(10) | `System` + 4 spaces |
| 33–132 | TRAN-DESC | X(100) | `Int. for a/c ` + ACCT-ID (from ACCTFILE) + 76 spaces |
| 133–143 | TRAN-AMT | S9(9)V99 | monthly interest |
| 144–152 | TRAN-MERCHANT-ID | 9(9) | `000000000` |
| 153–262 | MERCHANT NAME / CITY / ZIP | X(50)+X(50)+X(10) | spaces |
| 263–278 | TRAN-CARD-NUM | X(16) | XREF-CARD-NUM |
| 279–304 | TRAN-ORIG-TS | X(26) | `YYYY-MM-DD-HH.MM.SS.hh0000` |
| 305–330 | TRAN-PROC-TS | X(26) | same value as ORIG-TS |
| 331–350 | FILLER | X(20) | spaces |

The clock is read **once per transaction**, and both timestamp fields get that same value.

## 3. Processing rules, in order

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`.
2. Open (load) the inputs in this order: TCATBALF, XREFFILE, DISCGRP, ACCTFILE. A missing or unreadable file is an open error (§4).
3. Set `lastAcct` to 11 spaces, `first` to true, and `suffix` to 0.
4. For each TCATBAL record in key order:
   1. Print the 50-byte record as-is.
   2. **On an account break** (`acctId ≠ lastAcct`):
      - if this is not the first account, *update* the previous account (rule 6);
      - otherwise clear `first`;
      - set `totalInt` to 0 and `lastAcct` to `acctId`;
      - read ACCTFILE by `acctId`;
      - read XREF by `acctId`.
   3. Build the rate key: account `ACCT-GROUP-ID` + type + category.
      - If the key is found, use its rate.
      - If it is not found, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then look up `DEFAULT`. If `DEFAULT` is not found, that is an abend (§4).
   4. If the rate ≠ 0:
      - `monthly = trunc2(bal × rate / 1200)`, truncating toward zero (`RoundingMode.DOWN`) on the exact product;
      - then wrap `monthly` to the S9(9)V99 range: keep `|x| mod 10^9` (F5);
      - `totalInt = wrap11(totalInt + monthly)` (S9(9)V99);
      - `suffix = (suffix + 1) mod 1 000 000`;
      - write the transaction (§2). Fees are a no-op (F6).
5. At EOF, **do not update the last account** (F1).
6. *Update account*:
   - `CURR-BAL = wrap(CURR-BAL + totalInt)` in S9(10)V99, keeping `|x| mod 10^10`;
   - `CYC-CREDIT = 0`, `CYC-DEBIT = 0`;
   - replace the record in the in-memory master.
   This runs even when `totalInt` is 0, i.e. when every rate was 0.
7. Write the outputs, print `END OF EXECUTION OF PROGRAM CBACT04C`, and exit 0.

The overflow wrap matches probes P2 and P6:
- 999999999.99 × 9999.99 / 1200 = 8333324999.91…, stored as `333324999.91`;
- 9999999999.99 + 1.25 = 10000000001.24, stored as `1.24`.

## 4. Error paths (abend means exit 16, nothing further is processed, no output files)

Each path prints these SYSOUT lines, in order, then `ABENDING PROGRAM`:

| Condition | SYSOUT lines | Evidence |
|---|---|---|
| ACCTFILE key not found | `ACCOUNT NOT FOUND: <acct11>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023` | P1 |
| XREF account not found | `ACCOUNT NOT FOUND: <acct11>`, `ERROR READING XREF FILE`, `FILE STATUS IS: NNNN0023` | P4 |
| DEFAULT rate missing | (MISSING + TRY lines), `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023` | P5 |
| Open error, input file missing | TCATBALF: `ERROR OPENING TRANSACTION CATEGORY BALANCE`; XREFFILE: `ERROR OPENING CROSS REF FILE35`; DISCGRP: `ERROR OPENING DALY REJECTS FILE` (F7); ACCTFILE: `ERROR OPENING ACCOUNT MASTER FILE`. Each is followed by `FILE STATUS IS: NNNN0035` | code `.cbl:234-323`; status 35 is GnuCOBOL's code for a missing file |
| Invalid input (line > LRECL, duplicate key, non-numeric zoned field) | `ERROR READING <file>` + `FILE STATUS IS: NNNN0090` | **new**; the COBOL harness rejects these at load time or reads garbage. Flagged D2. |
| Output write failure | `ERROR WRITING TRANSACTION RECORD` or `ERROR RE-WRITING ACCOUNT FILE` + `FILE STATUS IS: NNNN0030` | code |

## 5. Edge cases

| Case | Behaviour |
|---|---|
| Account in ACCTFILE with no TCATBAL | Passed through unchanged |
| All rates 0 for an account | No transaction; cycle fields still zeroed; balance unchanged (rule 6) |
| Negative category balance | Truncation goes toward zero: −12.39 × 9999.99 / 1200 = −103.2498… → `−103.24` (`0000001032M`, P2) |
| Product rounds toward zero to 0 | `0000000000{`; a transaction is **still written** because rate ≠ 0 (P3: −0.01 × 15 / 1200) |
| Stock data (all balances 0) | 50 zero-amount transactions; ACCTFILE identical to the input |
| Empty TCATBALF | No transactions, ACCTFILE unchanged, 2 SYSOUT lines, RC 0 |
| Single account | That account is never updated (F1) |
| Suffix > 999999 | Wraps to `000000` (F5) |

## 6. Preserved defects (fix later, deliberately)

| # | Defect | Pinned by |
|---|---|---|
| F1 | The last account is never rewritten (`.cbl:219-220` is unreachable) | `lastAccountNotRewritten` |
| F2 | Interest is truncated, not rounded | `interestTruncatesTowardZero` |
| F3 | The group id sits in the ZIP field of the data, so every lookup falls back to DEFAULT (data defect; the code is correct) | golden SYSOUT (100 `MISSING` lines) |
| F4 | TS comes from the clock, not the PARM | `timestampFromClockNotParm` |
| F5 | Silent high-order truncation; PARM not validated; suffix wraps | `overflowTruncatesHighOrder` |
| F6 | Type `01` / cat `0005` hard-coded; fees empty | golden bytes |
| F7 | *New:* the DISCGRP open error says `DALY REJECTS FILE`, and the XREF open message has the status glued on | `openErrorMessages` |

## 7. Acceptance tests ↔ intent criteria

| AC | Test (JUnit 5) | Expectation |
|---|---|---|
| 1 | `mvn test` | green; dependencies are JUnit 5 + Surefire only |
| 2 | `GoldenTest.stock` | stock inputs, PARM `2022071800`, clock `2022071800000000` → `TRANSACT`, `ACCTFILE` byte-equal to `golden/CBACT04C` (50/50) |
| 3 | `GoldenTest.afterCbtrn02c` | ACCTFILE + TCATBALF from `golden/CBTRN02C` → byte-equal to `golden/CBACT04C-after-CBTRN02C` (50/50) |
| 4 | `interestTruncatesTowardZero` | at least one golden case where HALF_UP would differ, plus a negative case (P2: `0000001032M`) |
| 5 | `lastAccountNotRewritten` | scenario 3: acct `00000000050` output = input, while tran `2022071800000050` amount = `0000000187G` |
| 6 | `defaultFallbackAndZeroRate` | SYSOUT MISSING/TRY lines; type `03` rate 0 → no tran; cycle fields `00000000000{` |
| 7 | `AbendTest` ×4 | P1, P4, P5 inputs and a missing-file case → exit 16, SYSOUT tail as in §4, no output files |
| 8 | `ZonedTest` | encode/decode tables, `}` → 0, a zero result → `{`, LF only, widths |
| 9 | `GoldenTest` | SYSOUT byte-equal (152 / 302 lines) and RC 0 |

Probe inputs (built with `sed` on the stock data and run with `INPUT_DIR=… legacy/harness/run.sh CBACT04C`):
- **P1**: TCATBAL row for acct 99, no account. RC 16.
- **P2**: DEFAULT 01/0001 rate 9999.99, and balances 999999999.99 / −0.01 / −12.39. Gives `3333249999A`, `0000000000Q` (−0.08), `0000001032M`.
- **P3**: acct 1 balance −0.01 at 15%. Gives `0000000000{`.
- **P4**: XREF without acct 2. RC 16.
- **P5**: DISCGRP without `DEFAULT 01/0001`. RC 16.
- **P6**: acct 1 CURR-BAL 9999999999.99 plus interest 1.25. Gives `00000000012D`.
