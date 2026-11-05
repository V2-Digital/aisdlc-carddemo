# CR-1 Test report — `modernized/cbact04c/`

**Result: green.** 20 JUnit tests, 6 differential cases and the golden check all pass. No Java defects found. Fix iterations: 0 on `src/main`. Only the new tests and fixtures needed edits (see "Iterations").

## Commands and results

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | 20 tests, 0 failures (Java 21, JUnit 5 + Surefire only) |
| `legacy/tests/cbact04c-differential.sh` | 6/6 PASS, exit 0 (real GnuCOBOL vs Java) |
| `legacy/harness/golden.sh --check` | OK, goldens untouched, two runs byte-identical |

## JUnit tests (`src/test/java/carddemo/cbact04c/`)

| Class | Tests | Proves (AC) |
|---|---|---|
| `GoldenTest` | `stock`, `afterCbtrn02c` | TRANSACT (50 records), ACCTFILE (50 records), SYSOUT (152 / 302 lines) and RC byte-identical to `legacy/golden/CBACT04C` and `…/CBACT04C-after-CBTRN02C` (AC 2, 3, 9) |
| `InterestTest` | truncation, negative, balance update | Acct 2: 2339.97 @ 15% → `0000000292D` (29.24, not 29.25). Negative balance → `…M` (-29.24). Balance and cycle fields updated (AC 4) |
| `RateLookupTest` | direct hit, miss→DEFAULT, rate 0, zero balance | Direct hit has no messages. A miss prints 2 messages per record. Rate 0 writes no transaction. D3 zero-amount transaction pinned (AC 5) |
| `DefectTest` | `lastAccountNotRewritten_D1…`, `groupIdBlank…_D6` | Acct 50: transaction `2022071800000050` with `0000000187G` written, but ACCTFILE record unchanged. Only 49 of 50 records differ from the input (AC 6) |
| `TranRecordTest` | layout, hundredths, `suffixWrapsAfter999999_D5` | Id, constants, spaces at 153-262 and 331-350, timestamp format. Suffix wraps 999999 → 000000 with 1,000,001 records (AC 7) |
| `AbendTest` | account / xref / DEFAULT missing, input unreadable | rc 16 and exact SYSOUT tail (AC 8) |
| `ZonedTest` | codec, truncation | Overpunch round-trip, high-order digit drop |

## Differential test (`legacy/tests/cbact04c-differential.sh`, fixtures in `legacy/tests/cbact04c-differential/`)

It builds the jar, then runs each fixture through `legacy/harness/run.sh` (`INPUT_DIR`) and through the Java program. It compares SYSOUT and RC always, and TRANSACT and ACCTFILE when COBOL exits 0. On abend the harness does not export datasets.

| Case | COBOL rc | Compared | Result |
|---|---|---|---|
| `mixed` (direct group hit, DEFAULT fallback, rate 0, zero balance) | 0 | SYSOUT, RC, TRANSACT (4), ACCTFILE (3) | identical |
| `negative` (`P`/`J` signs) | 0 | same | identical |
| `overflow` (balance 9999999999.00; account balance high-order digits dropped) | 0 | same | identical |
| `missing-account` | 16 | SYSOUT, RC | identical |
| `missing-xref` | 16 | SYSOUT, RC | identical |
| `missing-default` | 16 | SYSOUT, RC | identical |

This gives real-COBOL evidence for R5 (negatives, overflow) and R8 (abend text and RC 16). Before this stage those were only code reading.

## Iterations

1. Three test or fixture mistakes, all fixed in the test code. `src/main` was not touched.
   - Fixture column offsets were wrong (the zoned balance is 12 wide).
   - Expected amounts in two tests were wrong.
   - The `missing-xref` fixture was not in key order. The COBOL loader reads KSDS in key order, so the first run showed a spurious diff.
2. A `dup-xref` case was dropped. VSAM AIX duplicate-key behaviour isn't reproducible with the harness's loader, so "first in file order" stays unverified.

## Not covered / for a human

- **Duplicate xref acct ids:** the Java picks the first in file order. This is not verified against COBOL (see iteration 2).
- **Partial output after an abend:** COBOL does not export datasets on abend, so partial TRANSACT/ACCTFILE state is not compared. Java persists the work done before the abend, as the spec says.
- **Spec deviation from the build stage:** account cycle and group-id columns follow `CVACT01Y.cpy` (79-90 / 91-102 / 113-122), not the spec table. The goldens confirm this. The spec table should be corrected.
- **Flag for separate CRs (not fixed here):** D1 (last account not rewritten, billed but balance not updated), D2 (fees empty), D3 (zero-amount transactions), D5 (TRAN-ID wrap), D6 (group id blank, so DEFAULT always).
- `TranRecordTest.suffixWrapsAfter999999_D5` writes about 1M records and takes ~6 s.
