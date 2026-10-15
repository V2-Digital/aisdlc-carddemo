# CR-1 Test report

Run `74748ec5-2d41-48cc-8e26-0e5584ce7b11` · Stage: Test · **Result: all green, 1 fix iteration (tests only)**

## Commands and results

| Command | Result |
|---|---|
| `mvn -f modernized/cbact04c verify` | **20 tests, 0 failures**, BUILD SUCCESS (Java 21, JUnit 5 only) |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden` (two runs byte-identical) |
| `git diff --stat baseline -- legacy .github` | empty |
| CLI smoke: `java -jar target/cbact04c.jar …` on the after-CBTRN02C inputs, `cmp`/`diff` against golden | TRANSACT, ACCTFILE and SYSOUT identical, RC 0 |

## Tests vs acceptance criteria

| Class (tests) | AC | Evidence |
|---|---|---|
| `GoldenEquivalenceTest` (2) | 2, 3, 4 | Byte-identical TRANSACT (50 records) and ACCTFILE (50 records) on `CBACT04C` and `CBACT04C-after-CBTRN02C`. SYSOUT line by line (152 / 302 lines). RC 0. |
| `DefectCharacterizationTest` (3) | 5 | D1: acct `00000000050` output == input, tran `…000050` = 18.77 (`0000000187G`), exactly 49 of 50 accounts changed. D3: 100 `MISSING` messages after CBTRN02C. D4: only `010005` interest records. |
| `InterestTest` (8) | 6, 7 | D2: 1164.87 @ 15.00 → 14.56. 100.00 @ 11.99 → 0.99 (rounding would give 1.00). Negative balance truncates toward zero. A negative result that truncates to zero is encoded `{`. Rate 0: no transaction, but the account is still rewritten with cycle fields zeroed. DEFAULT fallback. A specific group rate is used without fallback. |
| `OverpunchTest` (3) | 7 | All 20 sign characters, decode and encode. Zero is always `{`. D6 high-order overflow drops digits. |
| `AbendTest` (4) | 8 | Missing account / xref / DEFAULT rate: exit 16 and the exact legacy message lines. Unreadable DISCGRP: `ERROR OPENING DALY REJECTS FILE` (D5). |

## Iterations

1. First run: 4 failures, all **errors in my new tests, not in the Java program**:
   - wrong expected decimal scale in the overpunch test (×2);
   - wrong arithmetic in an expected value (rate `00300{` is 30.00%, so 29.12 and not 2.91);
   - D3 count: asserted 100 `MISSING` messages on the stock scenario. See the finding below.

   Fixed in the tests. No production code changed. Second run: green.

## Findings

- **Correction to intent.md D3:** the stock scenario `CBACT04C` has **50** `DISCLOSURE GROUP RECORD MISSING` messages (golden SYSOUT, 50 balances). The 100 is for after-CBTRN02C. This is only a documentation error. The Java output matches both goldens.
- Preserved defects D1–D7 are unchanged and unfixed. D1 (last account not rewritten) and D2 (truncation) still need separate decisions.
- Not tested: `--clock` omitted (system UTC clock); D6 overflow of the transaction counter; D7 duplicate cards per account (no sample data). No COBOL changes, so no `legacy/tests/*.sh` is added.

## Left failing

Nothing.
