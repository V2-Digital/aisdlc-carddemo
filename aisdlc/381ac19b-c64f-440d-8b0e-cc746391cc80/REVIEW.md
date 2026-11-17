# CR-1 Review — `CBACT04C` → Java 21

**Verdict: ready to merge, with should-fixes listed below.** Both golden scenarios are byte-identical (TRANSACT, ACCTFILE, SYSOUT, RC), and the COBOL-generated edge fixture is too. All 8 ACs are met. The open findings are about operational safety and test hygiene. None is a correctness gap against golden data.

## What I ran (this session)

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | exit 0 (21 tests) |
| `bash legacy/tests/cbact04c_edges.sh` | `cbact04c_edges: PASS` (real COBOL vs `expected/`) |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` |
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty: judge files untouched |
| `java -jar target/cbact04c.jar 2022071800` with default paths, in a scratch dir | RC 0. **Input `ACCTFILE.txt` overwritten** (md5 `734a2f…` → `1f086b…`), see F1 |
| COBOL with account 3 removed from XREF (abend after 2 accounts) | RC 16, same 4 error lines as Java. The harness unloads no TRANSACT/ACCTFILE, see F2 |

Checked by reading the code against cbl:188-221, 350-370, 415-520:
- Truncation: `divide(…,10,DOWN)` then `setScale(2,DOWN)` equals exact truncation toward zero.
- Modulo: 10^9 for interest/total and 10^10 for the balance. The suffix uses mod 10^6.
- `-0` is written as `{`.
- Only bytes 13–24, 80–91 and 92–103 of ACCTFILE are patched.
- The group is read from cols 113–122.
- D1 has no EOF rewrite.
- A non-contiguous account re-break uses the already-updated record, matching a VSAM re-read.

## Findings

1. **should-fix — default paths overwrite the input ACCTFILE.** `Main.java:335-336`: `--ACCTFILE` defaults to `./ACCTFILE.txt` and `--out` defaults to `.`, so `Cbact04c.java:124` overwrites the input. This contradicts spec §Interface ("The input `ACCTFILE` is never overwritten"). The data written is correct, since it is the in-place update a KSDS would get. But a failed re-run can no longer start from the pre-run master. *Suggestion:* exit 2 if `out/ACCTFILE.txt` resolves to the same file as the input, or change the default `--out` to `./out`. Alternatively, amend the spec to say in-place is intended.
2. **should-fix — the abend leaves a partial `TRANSACT.txt`.** `Cbact04c.java:79-122`: on `AbendException` the output stream closes and the transactions written so far stay on disk. ACCTFILE is not written at all. The JCL has `TRANSACT DISP=(NEW,CATLG,DELETE)` (INTCALC.jcl:37), so the mainframe deletes it on abend, and the harness unloads nothing either. A scheduler that only looks for the file could pick up a partial ledger. *Suggestion:* delete `TRANSACT.txt` (or write it to a temp file and move it on success) when the RC is 16. Add a `RateTest` assertion that the file is absent after an abend.
   - Note for ops: on a real KSDS, accounts rewritten before the abend *stay* rewritten. Java writes none of them, which is safer, but different. Document this in the README.
3. **should-fix — `modernized/cbact04c/README.md` is missing.** Plan step 5 asks for it: how to run, the env var, the preserved-defect list. *Suggestion:* add it, including F1/F2 behaviour and the UTC clock (COBOL `CURRENT-DATE` is local time; spec Q2 chose UTC).
4. **should-fix — `CliTest` does not run the jar.** `CliTest.java:16-18` runs `-cp target/classes …Main`, so the `Main-Class` manifest (AC 8 / spec "runs the shaded jar") is untested. I checked `java -jar` by hand and it works. *Suggestion:* move it to an integration phase (Failsafe, `*IT`) using `-jar target/cbact04c.jar`. Or note that `package` runs after `test`, and keep the manual check in the test report.
5. **should-fix — `DefectTest.d6_suffixWrapsModulo` is a tautology.** `DefectTest.java:102-105` asserts `(999_999+1) % 1_000_000 == 0` and never touches production code. *Suggestion:* delete it and say "D6 not tested end-to-end", or expose the starting suffix (package-private) and assert the id `…000000` after 999,999.
6. **nit — `InterestTest` re-implements the formula.** `InterestTest.java:12-16` copies the expression from `Cbact04c.java:115-116` instead of calling it. A change to production would not fail this test, though `GoldenTest.afterCbtrn02c` would (account 1 → `0000000145F`). *Suggestion:* extract `static BigDecimal interest(bal, rate)` in `Cbact04c` and test that.
7. **nit — dead code in tests.**
   - `OverpunchTest.java:194`: unused `p` and `"0".repeat(0)`.
   - `RateTest.java:237-239`: unused `tc(...)`.
   - `RateTest.java:241`: unused `useAcct2` parameter.
   - `CliTest.java:6`: unused `Files` import.
8. **nit — style.**
   - `Cbact04c.java:160` uses the fully-qualified `java.util.function.Supplier`, although it is imported.
   - `Main.java:354-355` uses inline FQNs.
   - `Zoned.java:221,236`: `java.math.RoundingMode` is not imported.
9. **nit — the edge fixtures moved.** The plan put them in `src/test/resources/edges/`. They are in `legacy/tests/cbact04c_edges/`, shared by the shell script and `GoldenTest.edges`. That is a better single source of truth. I'm only noting the deviation from the plan.

## What a human should look at before merging

- **F1 / F2:** decide whether in-place ACCTFILE and a partial TRANSACT on abend are acceptable for the container cut-over, or must be fixed before merge. These are the only findings with production impact.
- **R2 is unverified:** the harness cannot load duplicate alternate keys (status 22). For multi-card accounts, "first card in file order" is an assumption. Confirm card-per-account cardinality in production data before cut-over.
- **The expected edge outputs** (`legacy/tests/cbact04c_edges/expected/`) were generated by the COBOL and re-verified by me this session. A future `REGEN=1` run must stay COBOL-only, never Java output.
- **D1 follow-up CR** (last account's interest posted to TRANSACT but not to the balance: account 50, 18.77 in scenario 3) and the D2 data issue (blank `ACCT-GROUP-ID`) need business owners.
- **Timezone:** Java uses UTC and the mainframe uses local time. TRAN-ORIG/PROC-TS will shift by the offset after cut-over.
