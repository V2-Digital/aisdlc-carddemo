# CR-1 Test report

**Result: all green.** 2 fix iterations (of 5 allowed), both in my own test expectations. No production code changed.

## Commands and results

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | pass, 24 JUnit tests, only JUnit 5 + Surefire |
| `bash legacy/tests/cbact04c-edge.sh` | pass, 4 cases, Java vs real GnuCOBOL |
| `legacy/harness/golden.sh --check` | OK, outputs match `legacy/golden`, two runs identical |
| `git diff --stat baseline -- legacy/golden legacy/harness .github` | empty |

## Equivalence with golden (`GoldenEquivalenceTest`)

| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` | 50 records, byte-identical | 50, byte-identical | 152 lines, identical | 0 = 0 |
| `CBACT04C-after-CBTRN02C` | 50, byte-identical | 50, byte-identical | 302 lines, identical | 0 = 0 |

That is 200 records and 454 SYSOUT lines compared, with 0 diffs. The comparison is on raw bytes.

## Other tests

- `ZonedDecimalTest`: round-trip for -9.99..9.99, known encodings (`F`, `O`, `G`, `{`), no negative zero, overflow modulo with sign (R2, R3).
- `InterestTest`: positive, negative and zero balances. Results truncate toward zero (14.5608 -> 14.56, -14.5608 -> -14.56, -0.01 -> 0.00).
- `DefectTest` (characterization):
  - D1 last account not rewritten, on a crafted case and on golden account `…050` (18.77, `0000000187G`, record unchanged).
  - DEFAULT fallback on a miss, and no fallback on a real group hit.
  - Rate 0 gives no transaction, but the account is still updated.
  - The transaction-id counter runs across accounts.
  - Space-filled `TRAN-DESC` tail and FILLER, and the fixed fields of the transaction.
  - An account with no TCATBAL rows is copied through unchanged.
  - Empty TCATBAL leaves outputs unchanged.
- `AbendTest`: missing account, missing XREF and missing DEFAULT. Each checks the exact SYSOUT tail, exit 16 and the partial outputs. It also checks that no `.tmp` file is left behind.

## COBOL cross-check (`legacy/tests/cbact04c-edge.sh`, fixtures in `legacy/tests/cbact04c-edge/`)

For each case it runs the real COBOL through `run.sh` with `INPUT_DIR`, then the jar on the same inputs. It diffs RC and SYSOUT, plus TRANSACT and ACCTFILE when RC is 0.

| Case | Covers | COBOL = Java |
|---|---|---|
| `mixed` | negative balance (-14.56, `…145O`), a result truncating to -0.00 (encoded `{`), a real group hit (24%), rate 0, no-tran-row account, D1 | yes, RC 0 |
| `abend-account` | missing account | yes, RC 16, SYSOUT identical |
| `abend-xref` | missing XREF | yes, RC 16, SYSOUT identical |
| `abend-default` | missing DEFAULT group | yes, RC 16, SYSOUT identical |

- The script exits non-zero on any difference. As a negative control, I ran a copy of it with a different clock and it exited 1.
- It builds the jar if `target/cbact04c.jar` is missing, so CI can run it as is.

## Gaps and notes

- **Abend-path outputs are not compared against COBOL.** `run.sh` exits before unloading datasets when RC is 16. SYSOUT and RC are compared against COBOL. The partial TRANSACT/ACCTFILE contents are covered by `AbendTest` only, based on the spec.
- **Overflow (R2)** is unit-tested only. It is not reachable with real inputs, so there is no COBOL cross-check.
- **Disclosure-group file-open abends** (status 35 messages) are not tested. Their message text was not checked against COBOL.
- **XREF duplicate alt keys** are untested, as planned.
- Nothing is left failing.

## For a human

Same decisions as in intent.md. D1 (last account not rewritten) and D2 (blank `ACCT-GROUP-ID`, group code in the zip field) are preserved and pinned. They need separate decisions.
