# CR-1 Spec: `CBACT04C` in Java 21 (behaviour-preserving)

Legacy behaviour, line refs and defects: [intent.md](intent.md). This spec fixes the Java contract.
Answers to intent Q1–Q3 are taken as the **defaults** (SYSOUT and RC match; text files by DD name; D1/D3 go to separate CRs).

## Interface
- `java -cp target/classes carddemo.cbact04c.Cbact04c <PARM>`, where PARM is 10 chars (e.g. `2022071800`).
  The value is space-padded or truncated to 10 chars, like `PARM-DATE X(10)`.
- Datasets are found through env vars `DD_TCATBALF`, `DD_XREFFILE`, `DD_ACCTFILE`, `DD_DISCGRP`, `DD_TRANSACT`, the same names the harness uses.
- Clock: env `CARDDEMO_CURRENT_DATE=YYYYMMDDHHMMSSss` freezes it, the same format as the harness. If unset, the
  system clock in the default zone is used. Internally the program takes `java.time.Clock`.
- SYSOUT goes to stdout. RC is the process exit code. Tests call `Cbact04c.run(parm, dds, clock, out) → int`.

## Files (canonical text: one record per line, LF, fixed width)
On read, a trailing `CR` is stripped and the line is space-padded to LRECL. XREF lines are 36 bytes in the sample data.

| DD | Copybook | LRECL | Access in Java |
|---|---|---|---|
| TCATBALF | `app/cpy/CVTRA01Y.cpy` | 50 | read lines in file order (= key order) |
| XREFFILE | `app/cpy/CVACT03Y.cpy` | 50 | map acct-id (off 25 len 11) → first record in file order |
| ACCTFILE | `app/cpy/CVACT01Y.cpy` | 300 | `TreeMap` by acct-id (off 0 len 11). The whole file is rewritten in key order at close **or abend** |
| DISCGRP | `app/cpy/CVTRA02Y.cpy` | 50 | map by raw 16-byte key (off 0) |
| TRANSACT | `app/cpy/CVTRA05Y.cpy` | 350 | created or truncated at open, one line appended per WRITE |

Fields used (offset 0-based, length; zoned fields have a trailing overpunch sign, so Java uses `BigDecimal` at the given scale):

| Record | Field | Off | Len | PIC → Java |
|---|---|---|---|---|
| TCATBAL | TRANCAT-ACCT-ID / TYPE-CD / CD | 0 / 11 / 13 | 11 / 2 / 4 | `String` (raw bytes) |
| TCATBAL | TRAN-CAT-BAL | 17 | 11 | S9(9)V99 → BigDecimal scale 2 |
| XREF | XREF-CARD-NUM | 0 | 16 | X(16) |
| ACCT | ACCT-CURR-BAL | 12 | 12 | S9(10)V99, read and **written** |
| ACCT | ACCT-CURR-CYC-CREDIT / DEBIT | 75 / 87 | 12 / 12 | S9(10)V99, **written** as 0 |
| ACCT | ACCT-GROUP-ID | 109 | 10 | X(10) |
| DISCGRP | DIS-INT-RATE | 16 | 6 | S9(4)V99 |
| TRAN | all 350 bytes | | | built per the rule in §Processing 5 |

Every other byte of an ACCT record is copied through unchanged.

## Zoned codec
- Decode: the last byte carries the sign. `{`/`A`–`I` mean + with digit 0–9, `}`/`J`–`R` mean − with digit 0–9. A plain digit means +.
- Encode: |value| goes to the low N digits (high-order digits are dropped, as on COBOL overflow) and the last digit is overpunched.
  **Zero is always `{`**, including −0 after truncation. Negative → `}`/`J`–`R`.

## Processing (in this order)
1. Print `START OF EXECUTION OF PROGRAM CBACT04C`. Open the files: TCATBALF, XREFFILE, DISCGRP, ACCTFILE, TRANSACT.
   An open failure prints the program's `ERROR OPENING …` line and then abends (step 8).
2. For each TCATBAL record: print the 50-byte record exactly as it was read.
3. If acct-id ≠ last acct-id (a string compare; last starts as spaces):
   rewrite the previous account (step 6) unless this is the first record. Then set total-int = 0, read ACCT by id,
   and read XREF by acct-id.
   - ACCT missing → `ACCOUNT NOT FOUND: <id>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`, abend.
   - XREF missing → the same pattern, with `ERROR READING XREF FILE`.
4. Rate key = ACCT-GROUP-ID(10) + TYPE-CD(2) + CD(4). If it is missing, print `DISCLOSURE GROUP RECORD MISSING` and
   `TRY WITH DEFAULT GROUP CODE`, then retry with group `DEFAULT   ` (space-padded to 10). If that is missing too:
   `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, abend.
5. If rate ≠ 0:
   - monthly = trunc₂(bal × rate / 1200). The division is exact and then truncated toward zero (`RoundingMode.DOWN`) to
     scale 2, then reduced mod 10⁹ (S9(9)V99).
   - total-int = (total-int + monthly) mod 10⁹, with the sign kept.
   - The suffix becomes (suffix + 1) mod 10⁶. Then write a TRAN record:
     `TRAN-ID` = PARM(10) + suffix as 6 digits · type `01` · cat `0005` · source `System    ` ·
     desc `Int. for a/c ` + ACCT-ID(11), space-padded to 100 · amt = monthly (11, zoned) · merchant id `000000000` ·
     name/city/zip spaces (50/50/10) · card = XREF-CARD-NUM · ORIG-TS = PROC-TS = `yyyy-MM-dd-HH.mm.ss.hh0000`
     (hh = hundredths, read from the clock at each write) · 20 spaces.
   - Fees: no-op (D2).
6. Rewrite an account: CURR-BAL = (CURR-BAL + total-int) mod 10¹⁰. CYC-CREDIT = CYC-DEBIT = 0. Store the record back in the map.
7. At EOF, **do not** rewrite the last account (D1). Close the files, print `END OF EXECUTION OF PROGRAM CBACT04C`, RC 0.
8. Abend: print `ABENDING PROGRAM`, print `CEE3ABD: ABEND U0999` to stderr, and return RC 16. ACCTFILE rewrites and TRANSACT
   records made before the abend are kept, as with VSAM. The exact abend text is confirmed against COBOL by `legacy/tests/`.

## Preserved defects (one line each, fix later by decision)
- D1: the last account in TCATBAL order is never rewritten, but its interest tran is still written.
- D2: `1400-COMPUTE-FEES` is empty.
- D3: interest is truncated (not rounded) per category record.
- D4: the tran-ID suffix wraps at 999,999 (mod 10⁶), so IDs repeat.
- D5: timestamps come from the clock, not from PARM.
- D6: rates come from ACCT-GROUP-ID, so sample data always falls back to DEFAULT. The code is unchanged.
- D7: non-adjacent duplicate acct-ids would be re-read and rewritten twice. Java processes records in file order, as COBOL does.
- Silent high-order truncation on overflow (R2).

## Edge cases
| Case | Handling |
|---|---|
| Empty TCATBALF | no trans, no rewrite, SYSOUT has 2 lines, RC 0 |
| Negative balance | negative interest, truncated toward zero (e.g. −10.019 → −10.01) |
| Result −0.00 | encoded `{` |
| Rate 0 (e.g. DEFAULT `03/0001`) | no tran, total-int unchanged, suffix unchanged |
| Duplicate acct-id in XREF | first in file order is used (none in the sample data) |

## Acceptance tests (→ intent AC)
| Test | AC |
|---|---|
| `mvn -q verify` on Java 21, JUnit 5 only | 1 |
| `GoldenEquivalenceTest.stock`: stock inputs, clock `2022071800000000` → TRANSACT, ACCTFILE, SYSOUT, RC byte-equal to `legacy/golden/CBACT04C/` | 2, 4 |
| `GoldenEquivalenceTest.afterCbtrn02c`: ACCTFILE and TCATBALF from `legacy/golden/CBTRN02C/` → equal to `golden/CBACT04C-after-CBTRN02C/` | 3, 4 |
| `CharacterizationTest.lastAccountNotRewritten`: acct 50 stays 1945.87 / 1501.75 / −47.88, tran 18.77 present | 5 |
| `…truncatesNotRounds`, `…negativeBalance` | 6 |
| `…zeroRateNoTran` | 7 |
| `…defaultFallback`, `…groupRateHit` (the `00` path, not in golden), `…missingAccount/Xref/Default → RC 16` | 8 |
| `ZonedTest` (codec round-trip, −0, overflow) | R1, R2 |
| `legacy/harness/golden.sh --check` passes; `git diff --stat legacy/golden legacy/harness .github` is empty | 9 |
