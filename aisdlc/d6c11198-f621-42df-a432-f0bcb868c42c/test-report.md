# CR-1 Test report — CBACT04C → Java 21

**Result: all green.** 17 JUnit tests pass, golden check passes, COBOL-vs-Java edge script passes.
Fix iterations needed on the Java: **0** (all failures were wrong expectations in my new tests, corrected).

## Commands and results

| Command | Result |
|---|---|
| `mvn -q -f modernized/cbact04c test` | 17 tests, 0 failures, 0 errors |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden/` (legacy untouched) |
| `bash legacy/tests/cbact04c-edge.sh` | 5/5 cases ok, exit 0 |

## Golden equivalence (`GoldenTest`)

| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` | 50 records byte-equal | 50 byte-equal | 152 lines byte-equal | 0 |
| `CBACT04C-after-CBTRN02C` | 50 byte-equal | 50 byte-equal | 302 lines byte-equal | 0 |

Total compared: 200 records + 454 SYSOUT lines, 0 diffs.

## Tests by AC

| AC | Test | Notes |
|---|---|---|
| 1 | whole suite | deps: junit-jupiter + surefire only |
| 2–4 | `GoldenTest.stock`, `.afterCbtrn02c` | as above |
| 5 | `InterestTest` | 2339.97 @ 15% → 29.24 (`0000000292D`); −1.00 → −0.01; −0.50 → 0 written `{`; overflow modulo |
| 6 | `DefectTest.F1_lastAccountIsNeverRewritten…` | acct 50 row == input; TRANSACT #50 `0000000187G` |
| 7 | `AbendTest` E1/E2/E3 | exit 16, SYSOUT tail, stderr `CEE3ABD: ABEND U0999` |
| 8 | `RateLookupTest` | group row 2.00% used (3.89 vs 29.24 at DEFAULT), no warning for that account |
| 9 | `OverpunchTest` | all 10 digits × both signs round-trip (`-0` normalises to `{`) |
| extra | `DefectTest` F3 (50 warnings), F4 (cycle fields cleared except last), F6 (suffix) | |

## COBOL-vs-Java edge script: `legacy/tests/cbact04c-edge.sh`

Runs the real COBOL (harness, `INPUT_DIR`) and the compiled Java on the same synthetic inputs
(`legacy/tests/cbact04c-edge/{e1,e2,e3,grp,neg}/`) and requires equal exit code, SYSOUT, ACCTFILE and TRANSACT
(TRANSACT compared up to col 277; the script uses the Java wall clock, timestamps are covered by `GoldenTest`).
Non-zero exit on any mismatch. Cases: e1 missing account (rc 16), e2 missing XREF (16), e3 no DEFAULT (16),
grp group-specific rate (0), neg negative balances (0). All identical to COBOL.

## Left failing / flags for a human

- Nothing failing.
- **F1** (last account not rewritten) is preserved and pinned; recommend raising CR-3.
- Note: the edge script compiles the Java with `javac` directly, not Maven, so it needs only a JDK in CI.
