# CR-1 Test report — `CBACT04C` → Java 21

**Result: all green.** 21 JUnit tests, golden check OK, COBOL edge test PASS. 1 fix iteration in the Java code (plus 2 corrections to my own test expectations).

## Commands and results

| Command | Result |
|---|---|
| `cd modernized/cbact04c && mvn -q verify` | pass: 21 tests, 0 failures (Cli 2, Defect 4, Golden 3, Interest 2, Overpunch 4, Rate 6) |
| `bash legacy/tests/cbact04c_edges.sh` | `PASS` (real COBOL run, diffed against `expected/`) |
| `legacy/harness/golden.sh --check` | `OK - outputs match legacy/golden` (legacy untouched) |
| `java -jar target/cbact04c.jar 2022071800 --TCATBALF=… --out=/tmp/o2` with `CARDDEMO_CURRENT_DATE=2022071800000000` | RC 0; TRANSACT, ACCTFILE and SYSOUT byte-identical to `golden/CBACT04C` |

## Equivalence (byte-identical)

| Scenario | TRANSACT | ACCTFILE | SYSOUT | RC |
|---|---|---|---|---|
| `CBACT04C` (stock) | 50 records equal | 50 equal | 152 lines equal | 0 |
| `CBACT04C-after-CBTRN02C` | 50 equal | 50 equal | 302 lines equal | 0 |
| `edges` (COBOL-generated, 3 accounts, 4 category rows) | 4 equal | 3 equal | equal | 0 |

## Characterization tests

- **D1:** `DefectTest.d1_…`. Account 50 is byte-equal to its input. 49 of 50 accounts change. TRANSACT #50 is `0000000187G`.
- **D2:** `DefectTest.d2_…`. 100× `DISCLOSURE GROUP RECORD MISSING`.
- **D8:** `DefectTest.d8_…`. 50 zero-amount transactions on stock data.
- **D3:** `InterestTest`. 1164.87 × 15.00 → 14.56. Negative values truncate toward zero.
- **D6:** `DefectTest.d6_…` only checks the modulo arithmetic. It is not an end-to-end test, because 10^6 records is impractical.
- `OverpunchTest`: all 20 sign bytes round-trip, and −0 is written as `{`.
- `RateTest`: rate 0, the DEFAULT fallback, and the abend paths (missing DEFAULT, account, xref). Each gives exit 16 and the exact text. It also covers empty input.
- `CliTest`: separate JVM process with paths on the command line. Abend exits 16 and prints `CEE3ABD: ABEND U0999` to stderr.

## COBOL edge test (T3) — `legacy/tests/cbact04c_edges.sh`

Fixtures are in `legacy/tests/cbact04c_edges/`, with the real COBOL outputs in `expected/`. Run with `REGEN=1` to refresh them.
- **−0.01 at the DEFAULT 1.50 rate:** the COBOL writes `0000000000{`. Negative-zero truncation matches the spec.
- **−1000.00 at the DEFAULT 1.50 rate:** the COBOL writes `0000000012N`, which is −1.25 (truncated toward zero). Java matches.
- **Group `A000000000` with a rate of 6.00:** the COBOL finds the non-DEFAULT group, and its transaction is `0000000005{`. No fallback line is printed for that row.
- **D1 on real COBOL:** the last account is unchanged.

## Iterations

1. **Bug found by the T3 fixture; fixed in `Cbact04c.java`.** `ACCT-GROUP-ID` was read from offset 113–123. The correct bytes are 112–122 (0-based), i.e. cols 113–122 1-based. The golden scenarios never caught it, because the group is blank in every sample account (D2). This is a fix to the Java rewrite, not to legacy behaviour.

## Not covered / for a human

- **R2 (an account with several cards) is unverified against COBOL.** The harness cannot load duplicate alternate keys (`cdfiles` fails with file status 22). Java takes the first card in file order, which is an assumption only. There are no duplicates in the sample data.
- **D6 end-to-end:** the 10^6 suffix wrap and the balance overflow are not exercised on real data.
- **Malformed numeric input:** the Java prints `ERROR READING <DD>` and exits 16. This is the one deliberate divergence, and it has no dedicated test beyond the code path.
- **Decision for the approver:** the D1 follow-up CR. Last-account interest is posted to TRANSACT but not to the balance.
