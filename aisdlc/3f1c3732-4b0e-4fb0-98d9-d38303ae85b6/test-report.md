# CR-1 Test report — `CBACT04C` Java 21

**Result: green. 0 fix iterations on production code. Nothing failing.**

## Commands and results

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | pass, 23 tests, 0 failures |
| `bash legacy/tests/cbact04c-edge.sh` | exit 0, 6 of 6 cases PASS (COBOL vs Java) |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` (harness and goldens untouched) |

Dependencies: JUnit 5.10.2 (test scope) + Surefire/jar plugins only.

## Unit tests (23)

| Class | Tests | What it proves |
|---|---|---|
| `GoldenEquivalenceTest` | 2 | `stock` vs `golden/CBACT04C`, `afterCbtrn02c` vs `golden/CBACT04C-after-CBTRN02C`. Each compares TRANSACT (50 records), ACCTFILE (50) and SYSOUT (152 and 302 lines) byte for byte, plus RC 0. That is 100 records per scenario, 0 diffs. AC 2, 3, 7, 9 |
| `InterestTest` | 5 | 1164.87×15 → `0000000145F`; −100.00 → `0000000012N` (toward zero); −0.01 → `0000000000{`; 0.79×1% truncates to 0; overflow wraps mod 10^9 (D3, D4). AC 4 |
| `RulesTest` | 5 | Rate 0 gives no transaction and no counter increment. Rate ≠ 0 with bal 0 gives a transaction. The counter spans accounts. The id, ts format and trailing spaces are exact. A group-specific hit prints no MISSING lines (D6). Empty TCATBALF. AC 5, 7 |
| `AbendTest` | 4 | DEFAULT fallback SYSOUT. Missing DEFAULT, account or xref → RC 16 with exact SYSOUT lines and no output files. AC 6 |
| `CharacterizationTest` | 4 | `D1_*` (last account interest posted as a transaction but never rewritten, plus the single-account case). `D2_*` (cycle fields zeroed at zero interest). Accounts without TCATBAL pass through. AC 8 |
| `ZonedTest` | 3 | Every overpunch sign char round-trips. `}` reads as 0 and is written as `{`. Golden bytes. |

## Differential test: `legacy/tests/cbact04c-edge.sh`

Crafted inputs from `legacy/tests/cbact04c-edge/gen.py` run through the real COBOL (`harness/run.sh`, `INPUT_DIR`)
and through the jar. SYSOUT, RC, and where RC=0 TRANSACT and ACCTFILE, must be byte-identical. The script exits
non-zero on any diff. COBOL is the oracle, so no expected values were hand-derived.

| Case | COBOL RC | Result |
|---|---|---|
| `mixed` (9 accts: negative bal, −0.01, fractional, 999999999.99 overflow, bal 0, rate-0 type 03, group-specific hit `GRP`, type 02 rate 1.00) | 0 (9 TRANSACT, 9 ACCTFILE, 32 SYSOUT) | identical |
| `missing-default` | 16 | identical |
| `missing-account` | 16 | identical |
| `missing-xref` | 16 | identical |
| `single-account` (D1) | 0 | identical |
| `empty` TCATBALF | 0 | identical |

This settled plan risk 1 (sign of a truncated −0 result) and risk 2 (intermediate precision) against GnuCOBOL.
The Java code matched the COBOL on all of them with no change needed.

## Gaps

- **R3 (duplicate xref alt key, take the first):** not differentially tested. The harness loader rejects duplicate
  alternate keys (`CDFILES: LOAD XREFFILE file status 22`), so COBOL cannot be run on such input. The Java rule
  (first in card-number order) is untested.
- Open-failure paths (missing input file, status 35) are not tested. The harness always provides the files.
- No `--now` default-clock test. The CLI `Main` is exercised only through the edge script.

## Notes for the human

- The test sources use package `com.v2.carddemo.cbact04c` (matches the Build stage), not `carddemo.cbact04c` as written in the plan.
- The edge script needs `python3`, `java`, `mvn` and GnuCOBOL on the CI runner. It builds the jar itself.
- D1 is still a money defect: the ledger and the transactions disagree. It is preserved and pinned, and needs a separate CR.
