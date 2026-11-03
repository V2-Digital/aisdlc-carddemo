# CR-1 Spec: `CBACT04C` in Java 21

Behaviour-preserving rewrite. Legacy behaviour, file layouts and defects are in [intent.md](intent.md); this spec only pins what the Java code must reproduce.

## Interface
```
java -jar cbact04c.jar --parm 2022071800 [--timestamp 2022-07-18-00.00.00.000000]
     --TCATBALF <f> --XREFFILE <f> --DISCGRP <f> --ACCTFILE <f> --out <dir>
```
- Writes `<dir>/TRANSACT.txt` and `<dir>/ACCTFILE.txt`. DISPLAY lines go to stdout (SYSOUT parity, approver Q1 default). Exit code 0 or 16.
- `--parm`: the first 10 characters, right-padded with spaces (`PARM-DATE X(10)`).
- `--timestamp`: 26 characters, used for every transaction. Default: system clock UTC, `yyyy-MM-dd-HH.mm.ss.hh` + `0000` (`:613-626`).

## Record I/O (unload text form)
- Read: strip `\r`, right-pad each line with spaces to LRECL, ignore anything past LRECL. Records are LF-terminated.
- Write: exactly LRECL bytes + `\n`. No trailing-space trimming.
- Zoned `S9(n)V99`: last byte is an overpunch. `{`/`A-I` mean + with digit 0/1-9; `}`/`J-R` mean − with digit 0/1-9. Zero is always encoded with `{`. Java type: `long` cents (scale 2).

| File | Copybook | LRECL | Fields used (offset, len, PIC) |
|---|---|---|---|
| TCATBALF in | `app/cpy/CVTRA01Y.cpy` | 50 | acct 0,11 `9(11)`; type 11,2 `X`; cat 13,4 `9(4)`; bal 17,11 `S9(9)V99`. Whole record echoed to stdout |
| XREFFILE in | `app/cpy/CVACT03Y.cpy` | 50 | card 0,16 `X`; acct 25,11 (alt key; first match in file order) |
| DISCGRP in | `app/cpy/CVTRA02Y.cpy` | 50 | key 0,16 (group `X(10)`+type+cat); rate 16,6 `S9(4)V99` |
| ACCTFILE in/out | `app/cpy/CVACT01Y.cpy` | 300 | id 0,11; curr-bal 12,12 `S9(10)V99`; cyc-credit 66,12; cyc-debit 78,12; group-id 100,10. Other bytes are copied unchanged |
| TRANSACT out | `app/cpy/CVTRA05Y.cpy` | 350 | see rule 5 |

ACCTFILE output contains every input record, sorted by acct id (KSDS unload order), with rewrites applied in place.

## Processing rules (in order)
1. Print `START OF EXECUTION OF PROGRAM CBACT04C`. Load the four inputs. TCATBALF is processed in key order (acct+type+cat).
2. For each TCATBAL record: print the 50-byte record. If acct ≠ last acct: if this is not the first record, **update the held account** (rule 6). Then set total = 0, look up the account (miss → abend A), then look up the xref by acct (miss → abend X).
3. Rate key = held `ACCT-GROUP-ID` (10 bytes as-is) + type + cat. On a miss: print `DISCLOSURE GROUP RECORD MISSING`, then `TRY WITH DEFAULT GROUP CODE`, and retry with `DEFAULT   `. A miss on the retry → abend D.
4. If rate ≠ 0: `monthly = trunc2(bal × rate / 1200)`. Compute it exactly (BigDecimal), truncate toward zero to 2 decimals, then keep the low 9 integer digits (`S9(9)V99` high-order truncation). Then `total = (total + monthly)` with the same 9-digit wrap.
5. Write the transaction. All bytes are spaces unless set below:
   - `TRAN-ID` 0,16 = parm(10) + suffix `%06d`. The suffix counter is job-wide and wraps modulo 1,000,000 (D6).
   - type 16,2 `01`; cat 18,4 `0005`; source 22,10 `System    `.
   - desc 32,100 = `Int. for a/c ` + held `ACCT-ID` (11 bytes), then space padding.
   - amt 132,11 zoned; merchant-id 143,9 `000000000`; name/city/zip are spaces.
   - card 262,16 = `XREF-CARD-NUM`; orig-ts 278,26 and proc-ts 304,26 = timestamp; FILLER 330,20 = spaces.
   - The fees step is a deliberate no-op method `computeFees()` (D2).
6. Update account: `curr-bal = (curr-bal + total)` mod 10^10 integer digits (`S9(10)V99`). Set cyc-credit = 0 and cyc-debit = 0. Store the result in the output map.
7. At EOF there is **no** final update (D1). Print `END OF EXECUTION OF PROGRAM CBACT04C`. Exit 0.

## Abend path (exit 16)
Print these lines to stdout, then exit 16. Outputs written so far are flushed but are not part of the contract.

| Case | stdout lines |
|---|---|
| A: account missing | `ACCOUNT NOT FOUND: <acct11>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM` |
| X: xref missing | `ACCOUNT NOT FOUND: <acct11>`, `ERROR READING XREF FILE`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM` |
| D: DEFAULT missing | (two MISSING lines), `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM` |
| Input file missing or unreadable | the matching open message (`:245-318`, including D5 `ERROR OPENING DALY REJECTS FILE` for DISCGRP), `FILE STATUS IS: NNNN0035`, `ABENDING PROGRAM` |

## Edge cases
- Empty TCATBALF: banners only, 0 transactions, ACCTFILE unchanged, exit 0.
- Rate = 0 (for example DEFAULT type 03): no transaction, the suffix does not advance, and the total is unchanged. The account is still updated at the next break (cycle fields zeroed).
- Negative balance: monthly is truncated toward zero (−14.5609 → −14.56, written `00000000145O`).
- Lines that are short (cardxref, 36 bytes) or CRLF: handled by the read rule.

## Preserved defects (fix later, deliberately)
- **D1** The last account in TCATBALF is never rewritten.
- **D2** `computeFees()` is empty.
- **D3** A blank `ACCT-GROUP-ID` always falls back to `DEFAULT`.
- **D4** Interest is truncated, not rounded.
- **D5** The DISCGRP open-error text says `DALY REJECTS`.
- **D6** The transaction suffix wraps after 999,999 (duplicate IDs).
- **D7** Every row of an account is rated and described with the account record read at the break.

## Acceptance tests (→ intent AC)
| Test | AC |
|---|---|
| `mvn test` green, only JUnit 5 + Surefire | 1 |
| `GoldenTest.stock`: TRANSACT/ACCTFILE/stdout byte-equal to `legacy/golden/CBACT04C`, exit 0 | 2, 4 |
| `GoldenTest.afterCbtrn02c`: ACCTFILE+TCATBALF from `golden/CBTRN02C`, rest stock, equal to `golden/CBACT04C-after-CBTRN02C`, exit 0 | 3, 4 |
| `InterestTest.truncatesPositive` (14.5609→14.56), `truncatesNegative` (→−14.56) | 5 |
| `DefectTest.lastAccountNotRewritten`, `feesStepIsNoOp`, `fallsBackToDefaultGroup` | 6 |
| `AbendTest.missingAccount/Xref/DefaultGroup` → exit 16 + stdout lines above | 7 |
| `RecordLayoutTest.unsetBytesAreSpaces` (desc tail 56-131, FILLER 330-349) | 8 |
| `ZonedCodecTest`: round-trip of all overpunch chars, −0 read, zero written as `{` | R1 |
