# CR-1 Review — `CBACT04C` → `modernized/cbact04c/`

**Verdict: ready to merge.** Both golden scenarios and a COBOL-captured edge dataset match byte for byte, and nothing protected changed. The findings below are should-fix test-strength and edge-path items, not correctness blockers on the golden behaviour.

## What I ran (reviewer, fresh session)

| Check | Result |
|---|---|
| `mvn -q -f modernized/cbact04c test` (clean `target/`) | exit 0, **30 tests, 0 failures** |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden`, two runs identical |
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | **empty** |
| Fresh `INPUT_DIR=…/edge/in run.sh CBACT04C /tmp/rv` vs `edge/expected/*` | TRANSACT, ACCTFILE, SYSOUT, RC are all `cmp`-identical. The expectations came from COBOL, not from Java. |
| `grep double\|float src/main` | none |

## Correctness against spec (spot-checked)

- Main loop (`InterestCalc.java:96-149`) matches spec §3 step by step: account-change break, UPDATE on the previous account, DEFAULT fallback with two miss lines, `rate != 0` gate, suffix only on write, and no final UPDATE (D1).
- Money: `BigDecimal` throughout. `divide(1200, 2, DOWN)` then `wrap(…,9)`. `totalInt` is wrapped at 9 digits (`WS-TOTAL-INT S9(9)V99`, cbl:169) and the balance at 10. Zero always encodes as `{`. Signs round-trip.
- Account rewrite patches only cols 13-24 and 79-102 (`:171-172`). The edge ACCTFILE diff confirms that only the balance and cycle fields change.
- The edge data exercises a negative balance through **both** COBOL and Java (−10.01 → `0000000001K` = −0.12), plus a group hit (GRP1 12.50%), a zero rate (DEFAULT/01/0002) and D1 on the last account.

## Findings

1. **should-fix — the D4 suffix-wrap test is a tautology.** `DefectCharacterizationTest.java` `D4_suffixWrapsAfter999999` asserts `String.format("%06d",(999_999+1)%1_000_000)` and never calls production code. Suggestion: extract `static int nextSuffix(int)` (or make the starting suffix injectable) in `InterestCalc` and test that.
2. **should-fix — the AC 5 overflow test re-implements the formula.** `InterestTest.java:14-16` `interest()` copies the production expression. The 333,324,999.91 case would still pass if `InterestCalc.java:141` changed. (29.24 and −0.12 are also covered end-to-end by the D2 and edge tests.) Suggestion: extract `static BigDecimal monthlyInterest(BigDecimal bal, BigDecimal rate)` in `InterestCalc`, call it from `process`, and test that.
3. **should-fix — an over-length record gives exit 16, but spec §2 says load error / exit 3.** `FixedText.java:25` throws `IOException`, and `Main.java:47` maps that to 16 with only `I/O error` on stderr. Suggestion: throw `Ksds.LoadError` from `FixedText.read` and add a test.
4. **should-fix — an unreadable input file bypasses the E1 message.** `InterestCalc.open` (`:80-86`) catches only `NoSuchFileException`. Spec E1 says "missing **or unreadable**", but `AccessDeniedException` exits 16 with no `ERROR OPENING…`/`NNNN0035` lines. Suggestion: catch `IOException` (minus `LoadError`) there.
5. **should-fix — unknown CLI options are silently ignored.** `Main.java:22-25`: a typo such as `--acctfile-ot` would quietly fall back to **rewriting the input ACCTFILE in place**. Suggestion: reject keys that are not in the known set (exit 2).
6. **should-fix — the E9 guard is wider than COBOL.** `InterestCalc.java:108` decodes `TRAN-CAT-BAL` before the account lookup, even when the rate is 0. A bad balance on a zero-rate row abends in Java, although COBOL never touches the field. A bad balance plus a missing account reports E9 instead of E3. Suggestion: decode inside the `rate != 0` branch. Either way, note the exact scope of the approved E9 deviation.
7. **nit — the spec/README entry point differs.** Spec §1 says `cbact04c.Main`; the code and README use `com.v2.carddemo.cbact04c.Main`. Align the spec, or tell the Deploy plumbing.
8. **nit — dead or odd code:**
   - `ErrorPathTest` E12: `line = … ? T3 : T3`
   - `Ksds.byAlt` comment says "null when unused", but it is always allocated
   - `discRec` is declared outside the loop
   - the record name `Files_`
   - `TestSupport.assertBytes` computes the record number with a fixed 351, which is wrong for 50- and 300-byte files
9. **nit — plan file 4 (`Records.java`) was not created.** Field offsets are inline `substring` calls with comments. This is readable enough; just note the deviation from the plan.

## What a human should look at before merging

- **Status codes.** `build.md` says a `cobc` probe confirmed `35` (open) and `30` (OPEN OUTPUT). `test-report.md` says `35`/`30`/`34` were *not* verified. Only `23` is harness-verified, so resolve which is true. E6 (write/rewrite failure, `0030`) has no test.
- **E9 deviation** (abend on non-numeric zoned data). Approved in the plan; confirm the scope given finding 6.
- **Open decisions:** Q2, raising D1 (last account never updated: acct 50 is charged 18.77 but its balance is unchanged) and D2 (truncation) as separate CRs. Both affect customer balances.
- **R1:** equivalence is proven on the canonical text form only. EBCDIC/VSAM conversion at the platform boundary is still unowned.
- Decide whether findings 1-5 are fixed in this PR or tracked separately. None of them changes golden output.
