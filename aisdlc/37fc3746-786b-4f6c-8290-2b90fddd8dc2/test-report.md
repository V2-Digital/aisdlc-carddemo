# CR-3 Test report

**Result: all green. 33 JUnit tests pass, plus the COBOL reference script, the golden chain and `golden.sh --check`. 1 fix iteration (of 5), and it was a wrong expected value in my own test. No production code changed.**

## Commands and results
| Command | Result |
|---|---|
| `(cd modernized/cbtrn02c && mvn -q verify)` | 33 tests, 0 failures (Golden 2, CobolReference 5, Rules 17, Zoned 7, ErrorPath 2) |
| `legacy/tests/cbtrn02c_reasons.sh` | COBOL vs checked-in references: 5 cases, all `ok`. Java jar vs COBOL on the same fixtures: no FAIL, exit 0 |
| `modernized/cbtrn02c/scripts/compare.sh` | TRANFILE, DALYREJS, ACCTFILE, TCATBALF, SYSOUT, RC all byte-identical to `golden/CBTRN02C`. CBACT04C chain on Java output (TRANSACT, ACCTFILE, SYSOUT, RC) is identical to `golden/CBACT04C-after-CBTRN02C` |
| `legacy/harness/golden.sh --check` | OK, legacy side untouched |

## Golden scenario (AC 2-5, 7, 8)
- 300 transactions processed and 38 rejected. TRANFILE has 262 records, DALYREJS 38, ACCTFILE 50, TCATBALF 100.
- 0 byte differences in all 4 data files and in SYSOUT (54 lines). RC is 4.
- `GoldenEquivalenceTest` asserts this in-process. `compare.sh` asserts it through the real jar.

## Extra COBOL references (paths the golden data misses)
Fixtures are in `legacy/tests/cbtrn02c_reasons/<case>/`, built by me with a throwaway script. The unmodified COBOL was run on them through `run.sh` with `INPUT_DIR`. The outputs are in `modernized/cbtrn02c/src/test/resources/reasons/`. They are not in `golden/`.

| Case | COBOL result | Java |
|---|---|---|
| `reasons` (10 trans) | RC 4. Rejects 0100, 0101, 0102, 0103, 0103, 0103 (the last two are 103-over-102 and tran dated after expiry). 4 posted, 2 TCATBAL created | identical |
| `norejects` | RC 0, DALYREJS empty | identical |
| `overflow` | TCATBAL 999,999,999.99 + 5.00 gives 4.99 (silent truncation); no reject | identical |
| `overflow_acct` | account balance at 12 digits wraps; no reject | identical |
| `dupid` | duplicate TRAN-ID gives RC 16, no outputs exported | RC 16, status `0022`, no outputs |

`legacy/tests/cbtrn02c_reasons.sh` is the CI script. It exits non-zero on any mismatch. It compares the COBOL run with the references and, if `target/cbtrn02c.jar` exists, the Java run too.

## Defect characterization (AC 6)
| Defect | Test | Strength |
|---|---|---|
| D1 limit uses gross cycle, ignores curr-bal | `Defect1_LimitUsesGrossCycleNotBalance` | Pinned on Java behaviour |
| D2 103 overwrites 102 | `Defect2_103Overwrites102`, plus the COBOL `reasons` case | Pinned against COBOL |
| D3 reason 109 dead | `Defect3_Reason109NeverReported` | **Weak.** Only checks that no 0109 reject is written. The path cannot be triggered with in-memory maps |
| D4 non-atomic posting | `Defect4_NonAtomicPosting_abendExportsNothing` | Pins the observable result only (abend leaves no outputs). The partial-update order is not observable with text I/O |
| D5 wrong status on DALYREJS close error | `Defect5_CloseStatusMessage_notObservable` | **Not mirrored.** See below |
| D6 silent truncation | `Defect6_*` (3 tests) plus the COBOL `overflow` cases | Pinned against COBOL |

## Findings for a human
1. **D5 is not implemented.** The spec says to mirror the XREFFILE-status message on a DALYREJS close error. The Java uses a generic `ERROR CLOSING ... / NNNN0035` message, and the test pins only that. With text-file I/O a close failure cannot occur naturally, so there is no COBOL reference. Decide whether this is acceptable or the message should be copied literally.
2. **Spec error, already handled in Build.** A created TCATBAL record's filler is not spaces. It takes the filler from the last successful read (22 zeros in the stock data). The Java mirrors this and the COBOL references agree. The spec text should be corrected.
3. D1 (20 of 300 decisions would flip) is still a candidate follow-up CR.

## Not covered
- Real VSAM/EBCDIC I/O and the Dockerfile. I did not build or run the image.
- An unknown sign byte throws `IllegalArgumentException` in the Java. `run()` does not catch it, so it is an uncaught crash, not a clean RC 16 as the spec says. `ZonedTest.badSignRejected` pins this at unit level only. The COBOL behaviour on an invalid sign was not compared.
- The `reasons` fixtures are synthetic, with one case per rule. They are not exhaustive.
