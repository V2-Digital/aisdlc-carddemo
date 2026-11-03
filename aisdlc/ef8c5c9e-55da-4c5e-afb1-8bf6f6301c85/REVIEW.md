# CR-1 Review: `modernized/cbact04c/`

**Verdict: ready to merge.** Both golden scenarios are byte-identical, the COBOL cross-check passes, and the protected paths are untouched. The findings below are should-fix or nit, and none of them affects the outputs the golden data covers.

## Evidence (re-run by the reviewer)

| Check | Result |
|---|---|
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty (untouched) |
| `mvn -q test` in `modernized/cbact04c` | exit 0, all tests pass |
| `bash legacy/tests/cbact04c_edge.sh` | 8/8 PASS (ok: SYSOUT, RC, TRANSACT, ACCTFILE; abend_xref and abend_default: SYSOUT, RC) |
| `legacy/harness/golden.sh --check` | OK, two runs byte-identical |
| pom dependencies | only `junit-jupiter` (test) + surefire/jar plugins |
| ACCT offsets vs `app/cpy/CVACT01Y.cpy` | code is correct: bal 13, cyc-credit 79, cyc-debit 91, group 113. The spec's 246/258/280 are wrong |
| Does the golden data tell truncation from rounding? | yes: 28 type-01 rows in `golden/CBTRN02C/TCATBALF.txt` round differently than they truncate, so `GoldenTest` really pins D5 |

Money handling holds up. Amounts are `long` cents/hundredths, and the worst case `bal×rate` (≈10^17) fits in a `long`. Java `/` truncates toward zero, which matches COBOL with no ROUNDED. Wrap is mod 10^11 / 10^12 with the sign kept. Zero always encodes as `{`, and the harness confirms this for −0.01.

## Findings

1. **should-fix**: open-failure SYSOUT differs from COBOL and is unverified. `Cbact04c.java:65` prints `ERROR OPENING CROSS REF FILE`, but `CBACT04C.cbl:263` also displays `XREFFILE-STATUS` on the same line. All open failures also hard-code status `35` (`Cbact04c.java:157`), and the TRANSACT open failure (`cbl:318`) is not modelled. *Suggestion:* append the status to the XREF line. Then add an `abend_open` case to `cbact04c_edge.sh` (for example an unreadable DISCGRP) to confirm the text and status, or record these paths as out of parity.
2. **should-fix**: spec deviation on the interface. Spec "Interface" requires `DD_<name>` per-dataset paths. `Main.java:245-262` supports only `<inDir> <outDir> <PARM>` or `DATA_DIR`/`OUT_DIR`, as `build.md` admits. *Suggestion:* either add `DD_*` overrides (small) or amend `spec.md`. Deploy wiring depends on this choice.
3. **should-fix**: `spec.md` still lists the wrong ACCTFILE offsets (246/258/280). *Suggestion:* correct the spec to 79/91/113 so later CRs (D1 fix, CR-2 fees) don't inherit the error.
4. **should-fix**: the test is tautological. `InterestTest.java:69-73` checks Java's `/` operator, not the program. Its own comment notes that 1164.87@15 does not tell rounding from truncation. *Suggestion:* run the program on a balance where the two differ, for example 1000.40 @ 15.00 = 12.505, and assert the output is `0000000125{` (12.50, truncated) and not 12.51 (rounded). The golden data already covers this (28 rows), so this is about test hygiene.
5. **nit**: the abend ACCTFILE is not cross-checked against COBOL, because `run.sh` skips the unload on RC 16 (stated in test-report). The Java side writes it per spec R9. This is acceptable, but a human should know it is spec-asserted, not harness-proven.
6. **nit**: `Datasets.read` (`Datasets.java:193-204`) silently pads or cuts lines to LRECL. A truncated input file would then run instead of failing. Consider failing on a wrong length for fixed-width inputs.
7. **nit**: `Zoned.decode` (`Zoned.java:285`) throws `NumberFormatException` on blank or non-digit fields. The exception escapes `run()` as a stack trace with no exit 16. Legacy behaviour on bad data is undefined anyway, but a clear message would help ops.
8. **nit**: `Main.clock` (`Main.java:265`) does not check that `CARDDEMO_CURRENT_DATE` is 16 characters long, so a short value gives `StringIndexOutOfBounds`.
9. **nit**: readability. Instance fields are declared after the constructor (`Cbact04c.java:41-42`). The magic offsets are inline even though the plan had `Records.java`. Named constants would make the copybook mapping reviewable.

## What a human should look at before merging

- **Decide finding 2**: should `DD_*` be implemented now or the spec amended? It affects how Deploy maps datasets.
- **Accept, or ask for, the open-failure parity gap** (finding 1). It only matters for operator runbooks that grep SYSOUT.
- **Raise the follow-up CRs** for the preserved defects: D1 (last account never posted; acct 50 is charged 18.77 but its balance never changes) and D5 (truncation). D2 fees is already CR-2.
- `legacy/tests/cbact04c_edge.sh` and its fixtures are new under `legacy/`. CI runs them in the "Legacy tests" step, which now needs Maven and Java. Confirm that's the intended home.
