# CR-3 Review: CBTRN02C → Java 21

**Verdict: changes needed.** The output equivalence holds up: golden matches byte for byte, and the 17 COBOL-vs-Java cases agree. But AC 2 (RC 16 on I/O failure) is broken when an output can't be written. The program crashes with an uncaught exception and **RC 1**.

## What I ran
| Command | Result |
|---|---|
| `cd modernized/cbtrn02c && mvn -q verify` | pass (42 tests) |
| `legacy/tests/cbtrn02c_equiv.sh` | 17/17 cases ok, COBOL and Java both run |
| `legacy/harness/golden.sh --check` | OK, two runs byte-identical |
| `git diff baseline --stat -- legacy/golden legacy/harness .github` | empty: **untouched** ✅ |
| Jar run with `TRANFILE=/nonexistent/T.txt` | stack trace, **RC=1** ❌ |
| Jar run with `TRANFILE`/`DALYREJS` unset | NPE stack trace, **RC=1** ❌ |

The tests are real. `GoldenEquivalenceTest` reads `legacy/golden/CBTRN02C/*` directly and compares all 4 files, SYSOUT and RC. The differential script runs the actual GnuCOBOL program through `run.sh`, so its expectations aren't regenerated from Java. The test report is open about an earlier bad fixture generator (everything rejected) and says it was fixed. The current RC mix (0/4/16) confirms the cases now take the intended paths.

## Findings
1. **blocker:** `Cbtrn02c.java:73` / `:110`. On the normal path, `flush()` runs outside the `try`, so its `AbendException` escapes `run()` and the JVM exits 1. On the abend path (`:70`), a failing flush inside the `catch` escapes the same way. A missing `TRANFILE`/`DALYREJS` path gives an NPE at `RecordIo.write`. *Fix:* wrap `flush()` in both places, or catch `AbendException` in `Main.run`, so the result is RC 16 with the COBOL `ERROR … / ABENDING PROGRAM` lines. Validate that all 6 DDs are present in `open()`. Add a `ReturnCodeTest` case for an unwritable output.
2. **should-fix:** `Cbtrn02c.java:96-97`. The open-error text is `ERROR OPENING <DDNAME>` with a hard-coded `NNNN0035`. The spec says the messages are copied from the COBOL DISPLAYs (`CBTRN02C.cbl:247-338`: `ERROR OPENING DALYTRAN`, `… CROSS REF FILE`, `… ACCOUNT MASTER FILE`, `… TRANSACTION BALANCE FILE`). Only DALYTRAN matches today. *Fix:* map DD → COBOL text. Operators grep these lines.
3. **should-fix:** D6 (partial state after a duplicate TRAN-ID) isn't verified against COBOL, because `run.sh` exports nothing on RC 16. Java writes partial outputs, which is a spec choice. Whether downstream jobs ever see those files is an ops decision (see below). The test report already flags this gap honestly.
4. **nit:** `Main.java:286`. A malformed `CARDDEMO_CURRENT_DATE` (shorter than 16 characters) silently falls back to the system clock. Non-digits aren't checked at all. Prefer failing fast. A silent wall-clock PROC-TS in a replay is easy to miss.
5. **nit:** `Overpunch.java:308`. Non-digit bytes decode silently to nonsense values. That roughly matches COBOL's lack of a check, but add a comment so it doesn't look like an oversight.
6. **nit:** `KsdsFile.size()` looks unused in main code, and the `rewrite()` boolean is ignored at `:178`/`:196` (D3 explains why). Drop the return value or the method, or assert on it.
7. **nit:** `Cbtrn02c.java:189` `xrefAcct` re-reads XREF on each post. It's harmless, but the account id was already known in `validate`. Keeping the key avoids a second lookup.
8. **nit:** The `Dockerfile` needs `mvn package` to be run first. That isn't stated in the file, and README coverage is minimal. A multi-stage build or one comment line would fix it.

Money, sign and truncation handling look right. All amounts are `long` cents (no floating point). `truncate` keeps the sign and the low N digits, so it matches GnuCOBOL in the overflow cases (D5/D9). Zero is encoded as `{`. Exposure goes through S9(9)V99 before the compare, and `limit == temp` posts. The sticky TCATBAL FILLER is correct both ways (differential cases `c_first_create_filler`, `c_create_after_read_filler`).

## What a human should look at before merging
- **Finding 1 fix plus a test.** It's the only AC violation.
- **D6 operational decision:** on an RC 16 abend, Java leaves partially-updated ACCTFILE/TCATBALF (in place by default, because `_OUT` defaults to the input path). The mainframe has the same no-rollback behaviour, but confirm the container job never runs in place on master data without a backup or restore step.
- **D1 follow-up ticket** (the overlimit formula: 38 rejects vs 23 under `credit + debit`). It's preserved here by design, and the business owner needs to triage it.
- `legacy/tests/` is new and runs both COBOL and Java. Decide whether CI should run it. `.github/` wasn't touched, so it isn't wired in yet.
