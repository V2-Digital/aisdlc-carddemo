# Test report: CR-1 (`CBACT04C` → Java 21)

**Result: green.** 39 JUnit tests pass, golden check passes, nothing under `legacy/`, `.github/` or `app/` changed.

## Commands and results
| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -B verify` | BUILD SUCCESS, 39 tests, 0 failures. Jar `target/cbact04c.jar` built. |
| `legacy/harness/golden.sh --check` | OK. Two runs byte-identical, outputs match `legacy/golden`. |
| `git status --porcelain legacy/ .github/ app/` | empty |

## Equivalence (AC 2-4): byte comparison of raw files
| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` (stock) | 50 records, identical | 50, identical | 152 lines, identical | 0 = `RC.txt` |
| `CBACT04C-after-CBTRN02C` | 50, identical | 50, identical | 302 lines, identical | 0 = `RC.txt` |

F1 confirmed on golden: 49 of 50 ACCTFILE records changed. Account 50 (TRAN `2022071800000050`) is unchanged from `golden/CBTRN02C`.

## Tests by class
| Class | Tests | Covers |
|---|---|---|
| `GoldenEquivalenceTest` | 3 | AC 2, 3, 4, F1 on golden |
| `CharacterizationTest` | 19 | F1 (single and two accounts), F2 (1.255→1.25, -1.543625→-1.54, no -0), F3, F4, F5 (monthly and total overflow, suffix wrap at 1,000,000), F6 re-run, F7, zero and -0 rate, layout, hundredths, empty and unsorted TCATBAL |
| `AbendTest` | 5 | AC 6: missing account, xref, DEFAULT row (exact SYSOUT tail, RC 16, stderr, no outputs); in-place ACCTFILE untouched; duplicate-key input error |
| `InputFormatTest` | 5 | AC 7: padding, CRLF, overlong line, LF-only writes, 36-byte xref |
| `ZonedTest` | 6 | All 20 sign characters, -0, truncation, overflow |
| `NoFloatingPointTest` | 1 | AC 8 |

## Iterations: 2 (limit 5). Only the tests were wrong; no production code changed.
1. Two test mistakes (a mis-written zoned literal, and the wrong exception type for an overlong line, which is an `IOException`). Fixed.
2. `f5_totalOverflow`: I expected 333,300,193.64, copied from spec §5. Java returned 333,300,099.64. I ran the same crafted input through the real COBOL (`INPUT_DIR` + `run.sh`) and got `03333000996D`, the same as Java. So the spec's balance example used a different starting balance; the accumulated total, 333,299,999.64, matches. The test now expects the COBOL value.

## Not covered / notes
- Open errors for missing files print status `0035`. The golden harness does not cover this, and I did not probe it in COBOL.
- Abend atomicity (D1) is tested as proposed (no outputs). The COBOL harness discards outputs on abend, so there is no reference for it.
- Not independently tested: PARM shorter than 10 characters and the system-clock default.
- The suffix-wrap test runs 1,000,001 rows in memory (about 10 s, which dominates the suite time).

## For a human
- Spec §5's "account ends at 333,300,193.64" example is only valid for a different starting balance. Behaviour is right; the doc is off.
- F1-F7 stay as separate CR candidates; F1 is the most material.
