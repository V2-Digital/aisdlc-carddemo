# CR-1 Review: `CBACT04C` → Java 21

**Verdict: ready to merge.** Both golden scenarios are byte-identical, and the abend text was checked against the real COBOL. The open findings are all in error paths or test hygiene, and none affects the AC1–AC8 outputs.

## What I ran (reviewer, fresh session)
| Command | Result |
|---|---|
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty: the judging files are untouched |
| `cd modernized/cbact04c && mvn -q test` | rc 0 |
| `legacy/tests/cbact04c-abend.sh` | ok account / xref / default (real COBOL, RC 16, SYSOUT tail matches) |
| `legacy/harness/golden.sh --check` | OK, two runs byte-identical |
| CLI jar, stock inputs → `diff -r -x RC.txt` vs `golden/CBACT04C` | identical, rc 0 |
| CLI jar, CBTRN02C ACCTFILE+TCATBALF → diff vs `golden/CBACT04C-after-CBTRN02C` | identical, rc 0 |

Equivalence is real. `GoldenTest` reads `legacy/golden/*` at test time and compares TRANSACT, ACCTFILE and SYSOUT with `assertArrayEquals` / `assertEquals`. No expected output was regenerated. The abend fixtures in `legacy/tests/cbact04c-abend/*.expected` are verified against the COBOL by the script.

## Money / sign / truncation
- `monthlyInterest` = `bal¢ × rate / 120000` in `long` (`Cbact04c.java:166-168`). Java integer division truncates toward zero, which matches COBOL without `ROUNDED`. Worst case is 10^11 × 10^6 ≈ 10^17, which fits in a long. Using a long instead of the spec's BigDecimal is exact, so this is fine.
- Totals and balances wrap at 11 / 12 digits and keep the sign (`Zoned.wrap`). Zero is encoded `{`, and −0 reads as 0.
- Offsets follow the copybook: group-id at 112, cycle credit/debit at 78/90 (`Cbact04c.java:106,196-202`). Note that the **spec table is wrong** here (it says 66/78/100). The code is right and the golden run proves it, because the zip at 102 holds group codes.

## Findings
1. **should-fix**: The XREF open-error message is missing the status suffix. `CBACT04C.cbl:263` is `DISPLAY 'ERROR OPENING CROSS REF FILE' XREFFILE-STATUS`, so COBOL prints `ERROR OPENING CROSS REF FILE35`. `Cbact04c.java:73` prints the text without it. Pass the status into `open()` for this file only, and add an `AbendTest` case. Ideally capture it from COBOL in the abend script too.
2. **should-fix**: A TRANSACT/ACCTFILE output failure exits with code 1, not 16. `Files.createDirectories` / `FixedRecords.write` (`Cbact04c.java:60-62`) throw an uncaught `IOException`, so the JVM exits 1 with a stack trace. COBOL does `ERROR OPENING TRANSACTION FILE` + abend (`:309-322`). Wrap it: display the message, `FILE STATUS IS: NNNN0035`, `ABENDING PROGRAM`, return 16. The scheduler relies on RC 16.
3. **should-fix (spec vs code)**: Behaviour on abend differs from the spec. The spec says "outputs written so far are flushed". The code writes **nothing** (`Cbact04c.java:57-59`), and `AbendTest.abendWritesNoOutputFiles` pins that. I think writing nothing is the safer behaviour for a non-VSAM target, because no half-updated master gets written. Either update the spec or change the code (human decision, see below).
4. **should-fix**: Some test assertions are tautologies.
   - `ZonedCodecTest.java:34` (`multiDigitValues`, line 2) appends `"O"` to both sides, so it never checks the sign byte. Line 1 decodes a plain digit `6`, not an overpunch. Replace them with `assertEquals("0000000145O", Zoned.encode(-1455, 11))` and `assertEquals(1456, Zoned.decode("0000000145F"))`.
   - `DefectTest.java:142` checks only the first 10 bytes and then appends `G` to both sides. Use `assertEquals("0000000187G", last.substring(132, 143))`.
   The golden tests already cover these bytes, so this is about the tests' honesty, not a coverage gap.
5. **nit**: The input decode is lenient. `Zoned.decode` (`Zoned.java:246-262`) silently turns spaces or letters into garbage digits. Duplicate account ids collapse in the `TreeMap` (`:77-79`), and duplicate TCATBAL keys are both processed. A KSDS would reject both of these at load time. Consider failing fast with exit 16 (out of scope for parity, but worth a ticket).
6. **nit**: The CLI is loose (`Cbact04c.java:218-228`). Unknown flags are ignored, and `--timestamp` length is not checked (the spec requires 26 chars). A short value would shift the FILLER bytes. Validate it, and exit 2 with usage on bad input.
7. **nit**: There is dead or redundant test code. `Support.lines()` (`Support.java:35`) and `AbendTest.nonKey()` (`AbendTest.java:20`) are unused. `RecordLayoutTest.java:58` duplicates line 59. The comment at `InterestTest.java:16` mentions a 1.99 case that isn't tested.
8. **nit**: `updateAccount` reassigns `heldAccount` (`Cbact04c.java:203`), and the next `getAccount` immediately overwrites it. This is harmless but can be removed.

## What a human should look at before merging
- **Abend output contract (finding 3).** Decide whether a Java abend writes partial TRANSACT/ACCTFILE (as COBOL does to VSAM/GDG) or nothing (current code). This affects restart/rerun procedures.
- **RC on an output-write failure (finding 2).** It is currently 1, not 16. Fix it before this goes behind the scheduler.
- **Timestamp zone.** The default clock is UTC (as the spec says). COBOL `CURRENT-DATE` is local time. Production runs should pass `--timestamp`, or the container TZ has to be agreed.
- **Duplicate xref alternate keys** use the first match in file order. This is not validated against VSAM AIX ordering, and the sample data has none.
- **Preserved defects D1, D3, D4, D6** still need follow-up CRs (approver Q2). D1 means the last account's interest is charged as a transaction but never posted to the balance.
- The **spec's ACCTFILE offsets table** (66/78/100) should be corrected to 78/90/112 so that later CRs don't inherit the error.
