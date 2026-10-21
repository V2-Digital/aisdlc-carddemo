# CR-1 Test report — CBACT04C → Java 21

**Result: all green, 0 fix iterations needed** (one of my own test fixtures had wrong offsets; fixed in the test, not the code).

## Commands and results

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | PASS — 19 tests (Golden 2, Characterization 10, Abend 4, Zoned 3), 0 failures |
| `legacy/tests/cbact04c-abends.sh` | PASS — real COBOL, 3 cases, RC 16, SYSOUT matches `SYSOUT.expected` |
| `legacy/harness/golden.sh --check` | PASS — COBOL outputs unchanged vs `legacy/golden/`, two runs byte-identical |

## Equivalence (AC 2–4, 9)
`GoldenTest` runs Java on each scenario's inputs and byte-compares **TRANSACT, ACCTFILE, SYSOUT, RC**:
- `CBACT04C` (stock, CRLF inputs): TRANSACT 50, ACCTFILE 50, SYSOUT 152 lines, RC 0 — identical.
- `CBACT04C-after-CBTRN02C`: TRANSACT 50, ACCTFILE 50, SYSOUT 302 lines, RC 0 — identical.

Total: 2 scenarios × 4 files, 0 diffs (~100 transactions/accounts and 454 SYSOUT lines).

## Characterization / unit tests
- `truncatesNotRounds_D2` — every golden `01` category row where truncate ≠ round is checked in TRANSACT (amount equals truncated, not rounded value).
- `lastAccountNotRewritten_D1` — acct `…050` byte-identical to input; tran `…000050` amt `0000000187G`; exactly 49 of 50 accounts changed.
- `fallsBackToDefault`, `nonDefaultGroupUsedWhenPresent_F1`, `zeroRateWritesNothingButUpdatesAccount`.
- `negativeBalance_truncatesTowardZero` (-100.01 @15% → -1.25), `firstXrefWins_F4`, `suffixWraps_F4` (999999 → 000000), `unsortedTcatbalIsSortedByKey` (reversed input gives golden output), `crlfInputAccepted` (no `\r` in output).
- `AbendTest` — missing account / XREF / DEFAULT rate: RC 16, SYSOUT equal to the real COBOL run, no TRANSACT written, ACCTFILE untouched; plus open failure (`…0035`).

## CI test script
`legacy/tests/cbact04c-abends.sh` (fixtures in `legacy/tests/cbact04c-abends/{noacct,noxref,nodefault}/`) runs the COBOL with `INPUT_DIR` fixtures, asserts RC 16 and exact SYSOUT. Exits non-zero on failure. `UPDATE_EXPECTED=1` regenerates the expectations.

## Findings / for a human
- **Doc error in intent/spec:** cycle credit/debit columns are 79-90 / 91-102 (per `CVACT01Y`), not 83-94 / 95-106. The Java code is right (goldens match); only the documents are off.
- Not tested by running COBOL (no GnuCOBOL reference): suffix wrap and negative balances — covered by Java unit tests following COBOL semantics, as agreed (F4).
- `suffixWraps_F4` uses reflection to preset the counter rather than generating 1M records.
- Open failure of non-TCATBAL files is only tested for the first file (TCATBALF) in Java; the others share the same code path.
- D1/D2/D3/F5 remain preserved; fixing needs separate CRs.

Nothing left failing.
