# CR-1 Test report

**Result: all green. 0 fix iterations needed (limit 5). Nothing left failing.**

## Commands and results
| Command | Result |
|---|---|
| `mvn -q -f modernized/cbact04c verify` | 16 tests, 0 failures |
| `legacy/tests/cbact04c-edge.sh` | 5 cases PASS (COBOL vs Java) |
| `legacy/harness/golden.sh --check` | OK, golden untouched, two runs identical |

## JUnit (16 tests)
| Class | Tests | Proves |
|---|---|---|
| `GoldenTest` | 2 | `CBACT04C` and `CBACT04C-after-CBTRN02C`: TRANSACT (50), ACCTFILE (50) and SYSOUT (152 / 302 lines) byte-identical to `legacy/golden/`, RC 0 (AC 2-4, 9) |
| `InterestTest` | 3 | 116.48 → 1.45 (truncated, not 1.46); -10.01 → -0.12 (toward zero); overflow wraps with no exception (AC 5, D5) |
| `RateZeroTest` | 2 | rate-0 category: no transaction, nothing added, account still rewritten (AC 6) |
| `DefectTest` | 3 | D1: account `00000000050` byte-equal to its input (balance `00000019458G`), while its transaction `0000000187G` is written (AC 7). D6: suffix restarts at 000001. D3: DEFAULT fallback with 2 warning lines per category |
| `AbendTest` | 3 | missing account / xref / DEFAULT rate: SYSOUT tail as specified, `CEE3ABD` on stderr, RC 16; work done before the abend is kept (AC 8) |
| `ZonedTest` | 3 | all of `{A-I}` / `}J-R` round-trip; `}` (-0) decodes to 0 and re-encodes as `{`; `fit` keeps the sign |

## COBOL cross-check: `legacy/tests/cbact04c-edge.sh`
Runs the real COBOL (harness) and the Java jar on the same edited inputs, then diffs SYSOUT.txt and RC.txt, plus TRANSACT.txt and ACCTFILE.txt when COBOL completes.

| Case | RC | Compared | Result |
|---|---|---|---|
| `negative` (balance -12.34) | 0 | SYSOUT, RC, TRANSACT, ACCTFILE | identical |
| `overflow` (account balance 9999999999.99) | 0 | SYSOUT, RC, TRANSACT, ACCTFILE | identical, so the high-order wrap matches GnuCOBOL here |
| `no_account` | 16 | SYSOUT, RC | identical |
| `no_xref` | 16 | SYSOUT, RC | identical |
| `no_default` | 16 | SYSOUT, RC | identical |

The harness skips its unload on an abend, so for abends only SYSOUT and RC are compared. The Java partial-output behaviour on abend (rewritten accounts and transactions written before the abend are kept) is pinned by `AbendTest` only. It is not checked against COBOL.

## Gaps
- **Duplicate xrefs per account: unverified.** The harness AIX has no `WITH DUPLICATES`, so COBOL cannot load such data. Java takes the first card in card-key order, as specified. It is not in the edge script.
- **Overflow:** one overflow case matches COBOL (the account balance). The 9-digit transaction amount and total wrap are covered by Java unit logic only.
- Edge-script case builds use post-CBTRN02C inputs. They need GnuCOBOL and Maven, which the CI image has.

## Iterations
Test-code mistakes only, no program defects:
- the `RateZeroTest` expectation was wrong (account 2 correctly gets a 0.00 transaction), and
- my edge-script fixtures had a mis-sized field.

Both were fixed in the tests.

## For a human
- D1 and D3 stay preserved. Raise them as follow-up CRs (see intent.md).
