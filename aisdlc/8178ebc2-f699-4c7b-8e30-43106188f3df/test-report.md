# CR-3 Test report — CBTRN02C → Java 21

**Result: green.** 32 JUnit tests, 0 failures. Fix iterations: **1** (a wrong expectation in my own test; no program change).

## Commands
| Command | Result |
|---|---|
| `cd modernized/cbtrn02c && mvn -q verify` | pass, 32 tests |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` (harness untouched) |
| `INPUT_DIR=…/in legacy/harness/run.sh CBTRN02C …/expected` ×5 | produced the edge fixtures from the real COBOL |

## Tests
| Class | Tests | Covers |
|---|---|---|
| `GoldenEquivalenceTest` | 1 | AC2-4: stock inputs, clock 2022-07-18 00:00:00.00. TRANFILE/DALYREJS/ACCTFILE/TCATBALF **byte-identical** to golden (262 / 38 / 50 / 100 records compared); stdout = `SYSOUT.txt` (54 lines); RC 4 |
| `EdgeScenarioTest` | 5 | Fixtures captured from COBOL: `tcatbal-filler` (D9), `zero-sign` (zero → `{`, −0 amount kept `}` in TRANFILE), `temp-overflow` (D7), `bal-overflow` (D7), `dup-tranid` (D4; SYSOUT + RC 16). Four compare all 4 outputs byte-for-byte too |
| `RulesTest` | 16 | AC5-7: reasons 100/101/102/103 with exact 80-byte trailers, 102+103 → 103 (D2), running cycle totals, negative amount raises temp balance (D1), CURR-BAL ignored (D1), inactive account posts (D8), TCATBAL create/update/"Creating" line, FILLER spaces on first create (D9), PROC-TS from clock, RC 0 with no rejects, empty input |
| `AbendTest` | 7 | AC9: missing DALYTRAN/XREFFILE/ACCTFILE/TCATBALF → `NNNN0035`; unwritable TRANFILE → `NNNN0030`; duplicate tran id → `NNNN0022` with balances still posted (D4); TRANFILE replaced, not appended (D5). All exit 16 |
| `ZonedTest` | 3 | AC8: every sign char round-trips, −0, zero → `{`, left truncation at 11 and 12 digits |

## Notes / gaps
- Fixtures are hand-built synthetic inputs run through the real COBOL (generator in /tmp, not committed); `expected/` is COBOL output. `dup-tranid` has no output files because the harness doesn't unload after an abend, so `AbendTest` checks the D4 file state against the spec instead (no COBOL oracle for it).
- Abend messages for DALYREJS/XREFFILE-open (status 30) and the 35 cases other than `dup-tranid` are checked against the spec, not captured from COBOL.
- Not testable, documented only: D3 (REWRITE failure can't occur), D6 (close can't fail), counter wrap at 10⁹.
- Not run: Docker image (no Docker here); the `Dockerfile`/`run.sh` are untested.
- Finding: the spec's "Creating line FILLER = spaces" matches the program when the create is the first TCATBAL access of the run; after any read it inherits that record's FILLER (D9, confirmed by `tcatbal-filler` fixture and golden zeros).
- Nothing left failing.
