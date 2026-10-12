# CR-3 Test report — CBTRN02C in Java 21

Run `5f79bb0e-9a5e-496a-81da-a281c9ce534a` · Stage: Test · **Result: all green, 0 fix iterations.**

## Commands and results

| Command | Result |
|---|---|
| `cd modernized/cbtrn02c && mvn -q verify` | pass: 21 tests, 0 failures (Golden 3, HarnessProbe 6, Defect 10, ReturnCode 2) |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` (two runs byte-identical) |
| `CARDDEMO_CURRENT_DATE=2022071800000000 java -cp target/classes carddemo.cbtrn02c.Main --stock app/data/ASCII /tmp/j; diff -r /tmp/j legacy/golden/CBTRN02C` | rc 4, no diff (only `RC.txt` differs by absence; the CLI returns RC as the exit code) |

Dependencies: JUnit 5 + Surefire only. The test stage changed no `legacy/` or `.github/` files.

## Golden scenario (AC 2, 3, 4, 8)

Raw stock inputs (36-byte xref, CRLF `tcatbal`), clock `2022-07-18 00:00:00`:
`TRANFILE` 262, `DALYREJS` 38, `ACCTFILE` 50, `TCATBALF` 100 and `SYSOUT` 54 lines. 504 lines compared, all byte-identical. RC 4.

## Harness probes (AC 3, 6, 7)

Inputs built from stock data and run through `legacy/harness/run.sh CBTRN02C`. The outputs are checked in under `src/test/resources/probes/<case>/expected`. Java must match SYSOUT, RC and each dataset.

| Case | RC | Pins |
|---|---|---|
| `reasons` | 4 | 0100, 0101, 0103 (103 overrides 102), F1 with 22-space FILLER, 3 rejects out of 4 |
| `accept-only` | 0 | RC 0, empty DALYREJS |
| `neg-zero` | 0 | `}` kept in TRANFILE, `{` in balances |
| `unsigned-amt` | 0 | unsigned last digit read as positive |
| `f7-truncation` | 0 | cycle credit 10^9 accepted; TCATBAL → `0000005047F` |
| `dup-id` | 16 | 5 SYSOUT lines, `NNNN0022`; the harness exports no datasets on abend, so only RC and SYSOUT are compared |

## Characterization tests (`DefectTest`)

F1 (harness + golden), F2 (golden 38 rejects, not 23), F3, F4 (failing account store: tran posted, not rejected, RC 0), F5 (TRANFILE replaced; dup id → RC 16), F6, F7 (harness), F8 (type/category `ZZ9999` is posted).

## Findings and caveats

- **Probe reproduction:** the first `f7-truncation` input I generated had a bad TCATBAL key (no existing record at that key, which created a duplicate). The harness rejected the load (status 22). I corrected the input; this was a test-data fix, not a code fix.
- **F6 is only weakly pinned.** `Cbtrn02c.rejsCloseErrorMessage` is a static helper that the processing path never calls (close errors cannot occur with in-memory files). The test checks the text only. It does not prove behaviour, and there is no harness evidence.
- **F4 is a unit test only.** It uses the account-store seam, which is not reachable through the harness.
- **Abend dataset state** is not verified against GnuCOBOL (the harness exports none), as stated in spec §5.
- **Not tested:** the system clock path (`Main.clock()` without the env var) and the 36-byte/CRLF handling beyond the golden test.

## Iterations

0 fix iterations (limit 5). Nothing left failing.

## For a human

- Decide whether the F6 helper should stay as dead-but-pinned code or be wired in at Build/Deploy.
- F2 and F5 remain the follow-up CR candidates from the intent.
