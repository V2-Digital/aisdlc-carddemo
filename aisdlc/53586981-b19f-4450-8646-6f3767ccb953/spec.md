# CR-1 Spec: `CBACT04C` in Java 21 (behaviour-preserving)

The legacy behaviour, with file:line references, is in [intent.md](intent.md). This spec sets only what the Java code must reproduce **exactly**.

## I/O (canonical text, see `legacy/README.md` "Outputs")

Each file is `<DD>.txt`: one fixed-width record per line, LF endings. Zoned decimals use EBCDIC overpunch (`{`/`A-I` = +0..9, `}`/`J-R` = -0..9). On read, strip `\r` and right-pad with spaces to LRECL (`cardxref.txt` is 36 bytes, `tcatbal.txt` is CRLF). On write, emit exactly LRECL bytes plus `\n`.

| DD | Copybook | LRECL | Dir | Fields used (1-based offset:len, PIC → Java) |
|---|---|---|---|---|
| `TCATBALF` | `app/cpy/CVTRA01Y.cpy` | 50 | in | acct 1:11 `9(11)`→String; type 12:2; cat 14:4; `TRAN-CAT-BAL` 18:11 `S9(9)V99`→BigDecimal |
| `XREFFILE` | `app/cpy/CVACT03Y.cpy` | 50 | in | card 1:16; acct 26:11 (AIX) |
| `ACCTFILE` | `app/cpy/CVACT01Y.cpy` | 300 | in/out | id 1:11; `CURR-BAL` 13:12 `S9(10)V99`; `CYC-CREDIT` 79:12; `CYC-DEBIT` 91:12; `GROUP-ID` 113:10 `X(10)`. Other bytes are copied through untouched |
| `DISCGRP` | `app/cpy/CVTRA02Y.cpy` | 50 | in | key 1:16 = group(10)+type(2)+cat(4); `DIS-INT-RATE` 17:6 `S9(4)V99` |
| `TRANSACT` | `app/cpy/CVTRA05Y.cpy` | 350 | out | built in full (rule 6) |
| SYSOUT | n/a | var | out | stdout lines. The RC is the process exit code |

Entry point: `java -jar cbact04c.jar <PARM>`. Default PARM is `2022071800`; it is taken as `X(10)`, padded or cut to 10 characters. Each DD resolves to env `DD_<DD>` if set, otherwise `$INPUT_DIR/<DD>.txt` for inputs and `$OUTPUT_DIR/<DD>.txt` for outputs. The clock comes from `CARDDEMO_CURRENT_DATE`/`COB_CURRENT_DATE` (`YYYYMMDDHHMMSSss`), otherwise the system UTC clock. In-process, `Cbact04c.run(parm, io, clock, out) → int` does the work, and `main` calls `System.exit(rc)`.

## Processing rules (in order)

1. Print `START OF EXECUTION OF PROGRAM CBACT04C`. Load the inputs into maps: ACCTFILE by id (kept as a `TreeMap`, in key order), XREF by acct id (the **first** record in card-key order wins), DISCGRP by its 16-byte key. TCATBALF is sorted by its 17-byte key, which is KSDS order.
2. For each TCATBALF record, print the raw 50-byte record.
3. **Account break**: if acct ≠ last acct, then: if this is not the first record, rewrite the previous account (rule 7). Set total = 0. Look up the account. If it is missing, print `ACCOUNT NOT FOUND: <acct11>`, `ERROR READING ACCOUNT FILE`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM` and abend. Then look up the xref. If it is missing, print the same lines but with `ERROR READING XREF FILE`, and abend.
4. **Rate**: the key is `GROUP-ID`(10) + type + cat. If it is missing, print `DISCLOSURE GROUP RECORD MISSING` and `TRY WITH DEFAULT GROUP CODE`, then retry with `DEFAULT   ` (padded to 10). If that is missing too, print `ERROR READING DEFAULT DISCLOSURE GROUP`, `FILE STATUS IS: NNNN0023`, `ABENDING PROGRAM` and abend.
5. **Interest**, only if rate ≠ 0: `monthly = (bal × rate) / 1200`, computed exactly and then **truncated toward zero** to 2 dp (`RoundingMode.DOWN`). Then keep the low 9 integer digits (`mod 10^9`, sign kept). `total = (total + monthly)`, which is also reduced to S9(9)V99.
6. **Write TRANSACT**: `suffix = (suffix+1) mod 10^6`.
   - ID = PARM(10) + suffix as 6 zero-padded digits; type `01`; cat `0005`; source `System` padded to 10.
   - desc `Int. for a/c ` + ACCT-ID(11), padded to 100; amount = monthly as S9(9)V99 overpunch; merchant id `000000000`.
   - name/city/zip are spaces; card number from the xref.
   - ORIG-TS = PROC-TS = `YYYY-MM-DD-HH.MM.SS.hh0000` from the clock, which is read once per transaction.
   - FILLER 331-350 is spaces.
7. **Rewrite account**: `CURR-BAL = (CURR-BAL + total)`, reduced to S9(10)V99; set CYC-CREDIT and CYC-DEBIT to `+0`. Store the result in the account map.
8. At EOF: **do not** rewrite the last account (D1). Write ACCTFILE (all records, in key order) and TRANSACT (in write order). Print `END OF EXECUTION OF PROGRAM CBACT04C`. RC 0.
9. **Abend**: write `CEE3ABD: ABEND U0999` to stderr. Output files keep what was done before the abend, i.e. rewritten accounts and transactions written so far. RC **16**.

The zoned encoder writes `{`…`I` for ≥ 0 and `}`…`R` for < 0. A zero result is written as `+0` (`{`), as in the golden ACCTFILE cycle fields. Negative zero (`}`) must decode correctly.

## Edge cases

| Case | Handling |
|---|---|
| Rate = 0 | No transaction, total unchanged, suffix not incremented. The account is still rewritten (cycle fields zeroed) |
| Negative balance | Truncates toward zero, e.g. -10.01 × 15 / 1200 = -0.1251… → -0.12 |
| Third decimal ≠ 0 | Truncated, never rounded |
| Overflow of amount/total/balance | Silent high-order truncation, no exception (D5) |
| Missing account / xref / DEFAULT rate | Messages as in rules 3-4, RC 16 |
| Several xrefs for one account | First in card-key order (not checked against COBOL with duplicates) |
| Empty TCATBALF | Start/end lines only, ACCTFILE copied unchanged, empty TRANSACT, RC 0 |

### Preserved defects (one line each, to be fixed by a separate decision)

- **D1**: the last account's interest and cycle reset are never rewritten.
- **D2**: fees are not computed (empty `1400-COMPUTE-FEES`).
- **D3**: the rate key uses the blank `ACCT-GROUP-ID`, so every lookup falls back to DEFAULT with 2 warning lines.
- **D4**: interest is truncated instead of rounded.
- **D5**: no size-error guard; high-order digits are dropped silently.
- **D6**: not idempotent. TCATBAL is not reset, and the TRAN-ID suffix restarts at 1 and wraps at 10^6.
- **D7**: wrong DISCGRP open-error text (`DALY REJECTS`), kept verbatim. The record count is never printed.
- **D8**: timestamps carry hundredths only, and the clock is re-read for each transaction.

## Acceptance tests → intent AC

| Test | AC |
|---|---|
| `mvn -f modernized/cbact04c verify`, JUnit 5 only | 1 |
| `GoldenTest.stock`: TRANSACT/ACCTFILE/SYSOUT byte-equal to `legacy/golden/CBACT04C/`, RC 0 (50/50/152) | 2, 4, 9 |
| `GoldenTest.afterCbtrn02c`: inputs ACCTFILE+TCATBALF from `golden/CBTRN02C` → `golden/CBACT04C-after-CBTRN02C/` (50/50/302), RC 0 | 3, 4, 9 |
| `InterestTest`: 116.48 × 15 / 1200 = 1.456 → 1.45; -10.01 → -0.12; overflow wraps | 5, D5 |
| `RateZeroTest`: a 03/0001 category gives no transaction and total += 0 | 6 |
| `DefectTest.lastAccountNotRewritten`: account `00000000050` is byte-equal to its input, and the transaction for it has amount `0000000187G` | 7, D1 |
| `AbendTest` ×3 (missing account / xref / DEFAULT): SYSOUT tail as in rules 3-4, RC 16 | 8 |
| `ZonedTest`: round-trip of `{`, `}`, `A-I`, `J-R` | risk: signs |
| `legacy/tests/cbact04c-edge.sh`: COBOL vs Java byte diff on the abend, negative and overflow inputs | 5, 8, D5 |
