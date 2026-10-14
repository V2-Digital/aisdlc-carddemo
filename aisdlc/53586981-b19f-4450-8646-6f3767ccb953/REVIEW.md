# CR-1 Review: `CBACT04C` in Java 21

**Verdict: ready to merge.** All 9 ACs are met and I reproduced the evidence myself. Both golden scenarios are byte-identical, and the COBOL cross-checks pass. I found no blockers. The should-fix items below only make the tests stronger and can go in a follow-up.

## What I ran (reviewer, fresh session)

| Command | Result |
|---|---|
| `mvn -q -B verify` (in `modernized/cbact04c`) | exit 0. 16 tests, 0 failures (Golden 2, Interest 3, RateZero 2, Defect 3, Abend 3, Zoned 3) |
| `legacy/tests/cbact04c-edge.sh` | 5/5 PASS: negative, overflow, no_account, no_xref, no_default |
| `legacy/harness/golden.sh --check` | OK, two runs byte-identical |
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty, so all three are untouched |
| My own COBOL-vs-Java check, **amount overflow** (bal 999999999.99, rate 9999.99) | TRANSACT, ACCTFILE and SYSOUT identical. Amount is `3333249999A` (333324999.91) in both, so GnuCOBOL does wrap with mod 10^9 |
| My own COBOL-vs-Java check, **-0.01 balance → -0.00** | identical. Both write `0000000000{`, never `}` |

## Spec conformance (spot-checked)

- Arithmetic (`Cbact04c.java:104-106`): `divide(1200, 2, DOWN)` followed by `fit(…, 9)`. This matches `S9(09)V99` for both `WS-MONTHLY-INT` and `WS-TOTAL-INT` (`CBACT04C.cbl:168-169`). The balance uses `fit(…, 10)` (`:135`). Truncation goes toward zero, and the sign is kept.
- D1 (`:110`): the loop ends without the final `updateAccount`. `DefectTest` pins account 50 as byte-equal to its input, with transaction `0000000187G` still written.
- Transaction layout (`:119-129`): the offsets match CVTRA05Y, and the FILLER at 331-350 is left as spaces. The golden diff proves this on 100 records.
- The golden tests compare raw bytes against `legacy/golden/` files read at test time. The expectations are not regenerated, so the tests are not tautological.

## Findings

1. **should-fix**: `InterestTest.java:~25` (`overflowWrapsSilently`). The expected value is computed with `Zoned.fit`/`Zoned.encode`, which are the same functions under test, so the test is partly tautological. Hardcode the expected value `"3333249999A"` instead. I verified that value against GnuCOBOL (see table above).
2. **should-fix**: `legacy/tests/cbact04c-edge.sh:~26-30`. The edge script only covers overflow of the *balance*. The test report lists overflow of the 9-digit amount and total as "Java unit logic only". Add the `amtovf` case I ran: TCATBAL record 1 balance `9999999999I`, `DEFAULT   010001` rate `99999I`. Also add a `-0.00` case. Both pass today, and keeping them in the script stops a regression.
3. **nit**: `cbact04c-edge.sh:39`. `mkdir -p "$d/jout"` is never used (dead code), and so is `grep -v original` (`:13`), because there is no shade plugin.
4. **nit**: `test-report.md`. The "negative" case is described as balance -12.34, but the fixture `0000001234N` is -123.45. Only the doc is wrong.
5. **nit**: `Main.java:194`. A frozen clock shorter than 16 characters fails with `StringIndexOutOfBoundsException` (`Cbact04c.java:116`) rather than a clear message. Validate the length in `Main`.
6. **nit**: `Records.java:223`. Blank lines are skipped silently, and a non-digit in a zoned field throws `NumberFormatException` (`Zoned.java:~21`). That is fine for the golden-format inputs, but the behaviour on dirty data differs from COBOL. Document it in the README.
7. **nit**: `DefectTest.java:69-70` and `InterestTest.java:~31`. Fully-qualified `java.io.*` / `java.math.*` names are written inline. Use imports to match the other tests.
8. **nit**: The package is `com.v2.carddemo.cbact04c`, while plan.md says `carddemo.cbact04c`. `build.md` already explains why. No action needed.

## What a human should look at before merging

- **Partial outputs on abend are not COBOL-verified.** On RC 16, Java still writes ACCTFILE (with the accounts rewritten so far) and TRANSACT. The harness does not unload outputs after an abend, so only SYSOUT and RC are compared. Decide whether downstream jobs can ever consume outputs from a failed run. On the mainframe a failed step would normally stop the stream or trigger a restore.
- **Duplicate xrefs per account cannot be verified** with the harness (its AIX has no `WITH DUPLICATES`). Java takes the first card in card-key order.
- **Clock zone:** without a frozen clock, Java uses UTC. COBOL `CURRENT-DATE` on the mainframe gives local time. Confirm UTC is acceptable for `TRAN-ORIG-TS`/`PROC-TS` in production, or set the container `TZ` and switch to local time.
- **Dockerfile** copies a pre-built `target/cbact04c.jar`, so it needs `mvn package` first and has no multi-stage build. That is acceptable for this CR.
- **Preserved defects D1-D8** need follow-up decisions. D1 (the last account's interest is never posted every night) and D3 (blank group id, so DEFAULT rates are always used) have business impact.
