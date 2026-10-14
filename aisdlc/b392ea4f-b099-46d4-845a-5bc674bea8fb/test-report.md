# CR-1 Test report

**Result: all green.** Fix iterations on production code: **0**. (Test-code errors fixed: wrong byte offsets
in my own assertions, wrong `nodefault` fixture; no change to `src/main`.)

## Commands and results
| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q test` | 17 tests, 0 failures |
| `legacy/tests/cbact04c_edge.sh` | exit 0; 4 cases, COBOL vs Java `diff -r` clean |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` (legacy untouched) |

## Unit and equivalence tests (JUnit 5 only)
| Class | Tests | Covers (AC) |
|---|---|---|
| `GoldenEquivalenceTest` | 2 | AC 2-4. Byte compare of TRANSACT, ACCTFILE, SYSOUT, RC against `CBACT04C` (50 tx, 50 accts, 152 SYSOUT lines) and `CBACT04C-after-CBTRN02C` (50 tx, 50 accts, 302 SYSOUT lines) |
| `InterestTest` | 3 | AC 5: 1164.87@15 -> 14.56; -1164.87 -> -14.56; 0.79@15 -> 0.00 (not rounded, D3); 0.01@0.01 -> 0; overflow wrap (D6) |
| `DefectCharacterizationTest` | 8 | D1 last account untouched but txn written; D4 rate 0 -> no txn, rate!=0 & bal 0 -> zero txn; D2 fallback 2 SYSOUT lines; real group hit; missing DEFAULT/account/xref -> RC 16 + exact SYSOUT; D6 account balance wrap |
| `TranRecordTest` | 1 | AC 9: id `2022071800000001/2`, TS `2022-07-18-00.00.00.000000`, length 350, field offsets |
| `ZonedDecimalTest` | 3 | every overpunch char round trip, `}` -> 0, zero -> `{`, high-order truncation |

## COBOL vs Java edge script (`legacy/tests/cbact04c_edge.sh`, fixtures `legacy/tests/cbact04c_edge/`)
Runs real GnuCOBOL via `run.sh` with `INPUT_DIR`, and the Java jar, on the same inputs; compares every exported file and RC.
- `main` (8 TCATBAL rows, 6 accounts): real group hit (GRPX 24%), fallback (7 MISSING pairs), negative balance
  (-14.56 style), truncation case (0.79), balance 0 with non-zero rate, rate-0 category, 999999999.99 balance,
  account-balance overflow (9999999999.99 + interest), last-account defect. 8 transactions, identical. RC 0.
- `nodefault`, `noacct`, `noxref`: RC 16 in both, SYSOUT identical, only `SYSOUT.txt` + `RC.txt` exported.

## Gaps (not proven against COBOL)
- Overflow of the **monthly** interest field (needs bal x rate / 1200 > 999,999,999.99); only unit-tested against
  my reading of the spec. Account-balance wrap is proven.
- File-open failure texts/status (`35` suffix is a guess); duplicate cards per account (first-by-card-number).
- `Main` CLI (file I/O, exit code) is only exercised through the edge script, not by JUnit.

## For a human
- Defects D1-D8 are preserved and pinned; D1 (last account never updated) needs its own CR.
