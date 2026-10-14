# CR-1 Intent: INTCALC / CBACT04C to Java 21

## Problem
Interest is calculated every night by a COBOL program on the mainframe (`app/jcl/INTCALC.jcl` → `CBACT04C`). That job is one of the things keeping Cards batch on the mainframe.

## Outcome
A plain Java 21 program in `modernized/cbact04c/` that runs in a container. Given the same inputs, it produces exactly the same interest transactions and updated account master as the COBOL program, down to the byte. Known legacy defects are kept on purpose and documented, so they can be fixed later as separate, visible changes.

## What the legacy code does today

**Job:** `INTCALC.jcl:22` runs a single step, `EXEC PGM=CBACT04C,PARM='2022071800'`. The first 10 bytes of PARM become `PARM-DATE` (`CBACT04C.cbl:176-178`).

### Files

| DD | Organisation / access | LRECL | Key | Use |
|---|---|---|---|---|
| `TCATBALF` | KSDS, sequential, INPUT (`cbl:28-32`) | 50 | 17 @0 (acct 11 + type 2 + cat 4), `TCATBALF.jcl:36-45` | Driver: category balances in key order |
| `XREFFILE` | KSDS, random, INPUT, read by **alt key** acct-id (`cbl:34-39`, `394-395`) | 50 | 16 @0 card; AIX 11 @25 NONUNIQUE (`XREFFILE.jcl:72-82`) | Card number for the transaction |
| `ACCTFILE` | KSDS, random, **I-O** (`cbl:41-45`, `291`) | 300 | 11 @0 | Read, then REWRITE |
| `DISCGRP` | KSDS, random, INPUT (`cbl:47-51`) | 50 | 16 @0 (group 10 + type 2 + cat 4) | Interest rate `DIS-INT-RATE` S9(4)V99 |
| `TRANSACT` | Sequential, OUTPUT, new GDG `SYSTRAN(+1)` (`INTCALC.jcl:37-41`) | 350 | none | Interest transactions |

The `XREFFIL1` AIX path DD is never referenced by the program. Record layouts are in `CVTRA01Y`, `CVACT03Y`, `CVTRA02Y`, `CVACT01Y` and `CVTRA05Y` (`app/cpy/`). All numeric fields are zoned decimal with a trailing overpunch sign.

### Processing (`CBACT04C.cbl:181-232`)
1. The program displays `START OF EXECUTION…` and opens all five files. Any open status other than `00` abends (`:234-323`).
2. It reads TCATBALF sequentially and DISPLAYs each 50-byte record (`:190-193`).
3. **When the account changes** (`:194-205`): if this is not the first account, it rewrites the previous account (step 6). It then resets the running total, reads ACCTFILE and reads XREFFILE by acct-id. A missing account or xref displays `ACCOUNT NOT FOUND: <id>`, gets status 23 and abends (`:372-413`).
4. **Rate lookup** (`:210-213`, `415-460`): the key is `ACCT-GROUP-ID` + type + cat. On status 23 it displays `DISCLOSURE GROUP RECORD MISSING` / `TRY WITH DEFAULT GROUP CODE` and retries with group `DEFAULT`. A missing DEFAULT row abends.
5. **If the rate ≠ 0** (`:214-217`):
   - Monthly interest = `TRAN-CAT-BAL × DIS-INT-RATE / 1200`, **truncated** to 2 dp (no `ROUNDED`) and added to the running total (`:464-467`).
   - It writes one TRANSACT record (`:473-500`) with these fields:
     - `TRAN-ID` = PARM-DATE + 6-digit sequence
     - type `01`, cat `0005`, source `System`
     - desc `Int. for a/c <ACCT-ID>`
     - amount = monthly interest; merchant id 0; name, city and zip spaces
     - card = `XREF-CARD-NUM`
     - both timestamps = `YYYY-MM-DD-HH.MM.SS.hh0000` from CURRENT-DATE (`:613-624`)
   - It then performs `1400-COMPUTE-FEES`, which is empty (`:518-520`).
   - When the rate is 0, no transaction is written and the sequence number does not advance.
6. **Account update** (`:350-370`): `ACCT-CURR-BAL += total`, and `CURR-CYC-CREDIT` and `CURR-CYC-DEBIT` are both set to 0, then the record is REWRITTEN.
7. It closes the files and displays `END OF EXECUTION…`. RC is 0. Every abend path calls `CEE3ABD` U0999, which the harness reports as RC 16 (`:628-632`).

### Observed behaviour (harness, frozen clock 2022-07-18)

| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` (stock) | 50 records, all amounts 0 | 50 records, identical to `acctdata.txt` | 152 lines | 0 |
| `CBACT04C-after-CBTRN02C` | 50 records, non-zero amounts | 49 of 50 accounts changed | 302 lines | 0 |

- My run of `legacy/harness/run.sh CBACT04C` matched golden byte for byte.
- The post-CBTRN02C TCATBAL has 100 records: 50 accounts × types `01/0001` and `03/0001`. Under DEFAULT, type 01 has rate 1.50% and type 03 has rate 0, so 50 transactions are written.
- I recomputed all 50 golden amounts as `bal×15/1200`, truncated, and every one matches. **28 of 50 would differ if rounded**, so truncation is load-bearing.

## Scope

**In:**
- A Java 21 Maven project in `modernized/cbact04c/` (JUnit 5 only) that reads and writes the harness's canonical text format: fixed width, LF, overpunch signs.
- The same DD-to-file mapping and the same PARM date.
- An injectable clock.
- Equivalence tests against both golden scenarios.
- Characterization tests that pin each defect listed below.

**Out:**
- Fixing any legacy defect, including the empty fees paragraph (that is CR-2).
- Changes to the COBOL, JCL, golden files or harness.
- Native VSAM/EBCDIC binary I/O.
- Scheduling, container image and deploy plumbing (Deploy stage).
- CBTRN02C.

## Acceptance criteria
1. `mvn test` in `modernized/cbact04c/` passes on Java 21 with JUnit 5 as the only dependency.
2. Given the inputs of scenario `CBACT04C` (stock `app/data/ASCII`), PARM `2022071800` and clock `2022-07-18 00:00:00.00`, the Java `TRANSACT.txt` and `ACCTFILE.txt` are **byte-identical** to `legacy/golden/CBACT04C/`.
3. The same holds for scenario `CBACT04C-after-CBTRN02C`: ACCTFILE and TCATBALF come from `legacy/golden/CBTRN02C/`, everything else from stock.
4. SYSOUT lines match the golden `SYSOUT.txt` and the exit code matches `RC.txt` in both scenarios (see Q1).
5. Interest uses exact decimal arithmetic and truncates to 2 dp (no floating point). A test pins a case where rounding would give a different result.
6. The account ACCTFILE is written in primary-key order (11-byte acct id); all 50 records are present.
7. The disclosure-group fallback to `DEFAULT` works and is tested. A missing DEFAULT row, a missing account or a missing xref ends with a non-zero exit (16) after the same messages.
8. Characterization tests pin each defect D1–D4 below. Each test is named after its defect so the later fix CR flips a known test.
9. The program runs from the command line with file paths for the 5 DDs plus the PARM, so it can be wrapped in a container.

## Risks and findings

| # | Finding | Evidence | Treatment |
|---|---|---|---|
| D1 | **The last account is never rewritten.** After EOF, the `PERFORM UNTIL` test exits before the `ELSE PERFORM 1050-UPDATE-ACCOUNT` branch can run. The final account's interest is lost, and its cycle credit/debit are not zeroed. | `cbl:188-222`. Golden after-CBTRN02C: TRANSACT `2022071800000050` charges acct `00000000050` `0000000187G` (+18.77), yet that account's ACCTFILE line is byte-identical to `golden/CBTRN02C/ACCTFILE.txt` (bal `00000019458G`). | Preserve and pin |
| D2 | **`1400-COMPUTE-FEES` is empty.** No fees are ever charged. | `cbl:518-520` | Preserve (CR-2) |
| D3 | **Interest is truncated, not rounded.** Up to 0.01 per category is lost in the bank's favour. | `cbl:464-465`; 28/50 golden amounts | Preserve and pin |
| D4 | **Wrong error text.** A DISCGRP open failure says `ERROR OPENING DALY REJECTS FILE`. | `cbl:281` | Preserve and pin |
| F1 | **Every account falls back to DEFAULT.** `ACCT-GROUP-ID` (cols 113-122) is blank in all 50 accounts. The group code `A000000000` sits in `ACCT-ADDR-ZIP`. This is a data issue, so the group-specific rates are never exercised by golden. | `cut -c113-122 acctdata.txt` → 50 blanks; 100 `MISSING` lines in SYSOUT | Unit-test the non-default path explicitly |
| F2 | **Silent overflow and wrap.** There is no `ON SIZE ERROR` on the interest/balance arithmetic, and the 6-digit tran sequence wraps after 999,999. | `cbl:173`, `352`, `464` | Mirror COBOL truncation; pin with a unit test |
| F3 | **Ambiguous card number.** The XREF lookup reads a non-unique AIX, so an account with several cards takes whichever comes first. There are no duplicates in the sample data. | `XREFFILE.jcl:72-82`; `cardxref.txt` has no dup acct ids | Take the first by primary-key (card) order; note it as an assumption |
| F4 | **Short PARM.** PARM-DATE is read as 10 bytes no matter what `PARM-LENGTH` says. | `cbl:176-178` | Java pads or truncates the arg to 10 |
| F5 | **Uninitialised `TRAN-RECORD` tail.** The tail of `TRAN-DESC` and FILLER 331-350 are spaces only under the default dialect; they would be `x'00'` under `-std=ibm`. | `legacy/README.md`; golden cols 331-350 are all spaces | Emit spaces (matches golden) |
| R1 | **Negative balances never tested.** No sample balance is negative, so how a negative balance produces negative interest, and how the overpunch sign is written, is only covered by unit tests. | `chk`: 0 negatives | Unit tests for `}`/`J`-`R` signs |
| R2 | **Hard-coded date.** The PARM date is fixed at `2022071800` in the JCL, so every night would reuse the same transaction IDs. | `INTCALC.jcl:22` | Out of scope; flag to ops |

## Questions for the approver
1. **Does SYSOUT and RC parity count as "byte-identical"?** The CR names only TRANSACT and ACCTFILE. *Default: yes. Match `SYSOUT.txt` and `RC.txt` too.* It is cheap to do and makes operational monitoring a like-for-like swap.
2. **Should the clock stay injectable for production?** The timestamp keeps the COBOL format, with hundredths followed by `0000`. *Default: use the system clock in production, and override it through a CLI arg or environment variable for tests, like the harness's `CARDDEMO_CURRENT_DATE`.*
3. **Who decides D1–D4?** *Default: raise D1 (last-account write-back, a real financial impact) and D3 as separate CRs right after this migration. D2 is already CR-2.*
