# CR-1 Review — `CBACT04C` → Java 21

**Verdict: ready to merge.** Both goldens and 6 COBOL-vs-Java differential cases are byte-identical, and the protected dirs are untouched. Nothing below changes golden or edge-case output. The should-fixes can go in this PR or a follow-up.

## Evidence (re-run by the reviewer)

| Command | Result |
|---|---|
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty: **untouched** |
| `cd modernized/cbact04c && mvn -q verify` | exit 0. 23 tests, 0 failures (Abend 4, Characterization 4, Golden 2, Interest 5, Rules 5, Zoned 3) |
| `bash legacy/tests/cbact04c-edge.sh` | exit 0. PASS for mixed, missing-default, missing-account, missing-xref, single-account, empty |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` |

- The dependencies are JUnit Jupiter 5.10.2 (test scope), Surefire 3.2.5 and the jar plugin. AC1 is met.
- `GoldenEquivalenceTest` reads `legacy/golden/*` directly and compares TRANSACT, ACCTFILE and SYSOUT with `assertArrayEquals` or string equality. It does not regenerate expectations or compare output with itself. AC2, AC3 and AC9 are met.
- The edge script uses the real COBOL as the oracle for negative balances, −0.01, fractional remainders, the 999999999.99 overflow, rate 0, the group hit and all three abends. AC4–AC6 have real differential proof, not hand-derived values.
- Money handling is correct:
  - Interest is computed with exact BigDecimal: `divide(1200, 10, DOWN)` then `setScale(2, DOWN)`. The product has 4 decimals, so this equals a single exact truncation.
  - Sums wrap with `remainder` mod 10^9 / 10^10, which keeps the sign.
  - `}` decodes to 0. A zero result is written as `{`, and COBOL confirmed this on the −0.01 case.
- D1 (`IntCalc.java:106`) and D2 (`updateAccount`) are preserved and pinned in `CharacterizationTest`.

## Findings

1. **should-fix**: `IntCalc.java:39`. The XREFFILE open error message differs from COBOL. COBOL line 263 is `DISPLAY 'ERROR OPENING CROSS REF FILE' XREFFILE-STATUS`, so it prints `ERROR OPENING CROSS REF FILE35`. Java prints the message without the status. The spec (rule 1) missed this too, and no test covers open failures (see test-report gaps). Fix: append `"35"` for XREFFILE only, and add one `AbendTest` case per missing file.
2. **should-fix**: `IntCalc.java:72-77`. The xref lookup scans every xref record for each account, which is O(accounts × cards). That is fine for 50 records but quadratic at production volume, and this job is moving to a container to run real batches. Fix: before the loop, build `Map<acctId, card>` once with `putIfAbsent` while iterating `xrefByCard.values()`. This keeps R3 (first match in card order).
3. **should-fix**: `IntCalc.java:58`. TCATBALF is processed in file order. The COBOL reads a KSDS, which always comes in key order. If an upstream extract is unsorted, the account-break logic would rewrite accounts more than once and produce different results with no warning. Fix: fail fast if the keys (cols 1–17) are not ascending, or sort them. Note that sorting is not like-for-like if duplicate keys are possible.
4. **nit**: `Main.java:34,37`. `PrintStream.println` uses `line.separator`, which would write CRLF to SYSOUT on Windows. Use `print(line + "\n")` to match the LF contract in the spec.
5. **nit**: `Main.java:15`. The argument loop silently ignores a trailing option that has no value (for example `--now` given last). Fail with usage instead.
6. **nit**: `legacy/tests/cbact04c-edge/gen.py:39-41`. Dead assignments: in `missing-default`, `ds` is assigned twice and then overwritten on line 42. Delete lines 39 and 41.
7. **nit**: `Fx.java:32-35`. `acct()` appends `group + pad("",10)` and then rebuilds the group with `substring(0,112)`. Build offset 113 once. In `Fx.java:20-23`, `records()` splits twice. Neither affects correctness.
8. **nit**: `InterestTest.java:28`. `overflowWraps` derives its expected value with the same formula as the code, so it is close to a tautology. The COBOL-verified overflow case is in the edge script (999999999.99 × 15.00). Either say so in a comment or use rate 15.00 with the literal that COBOL produced.
9. **nit**: The package is `com.v2.carddemo.cbact04c` and the plan says `carddemo.cbact04c`. `Records.java` was folded into `IntCalc`. These are harmless deviations from the plan and are already noted in test-report.md.

## What a human should look at before merging

- **D1 is a money defect, shipped on purpose.** The last account's interest goes into TRANSACT but never into ACCTFILE. In golden after-CBTRN02C, acct `00000000050` is charged 18.77 and its balance is unchanged. Raise the follow-up CR (Q3) now.
- **CI coverage.** `.github/` was not changed, so neither `mvn verify` nor `cbact04c-edge.sh` runs in CI unless the existing workflow already picks them up. Confirm this, or wire them in through the deploy plumbing. The edge script needs GnuCOBOL, python3, Java 21 and Maven.
- **Default clock is UTC** (`Main.java`). The mainframe `CURRENT-DATE` is local time. Decide which one the container should use, or always pass `--now` from the scheduler.
- **Stale outputs on abend.** On RC 16 the program writes no files, but it does not delete old `TRANSACT.txt` or `ACCTFILE.txt` in `--out`. Ops must clear the out directory per run or gate on RC.
- Finding 1 is the only known behaviour difference from COBOL, and it only affects a console line on an open failure.
