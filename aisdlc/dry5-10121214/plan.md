# CR-1 Plan: CBACT04C to Java 21

Run `dry5-10121214` · Stage: Design · Implements `spec.md`

## Files, in build order

All paths are under `modernized/cbact04c/`. Package `com.v2.carddemo.cbact04c`.

| # | Path | Purpose |
|---|---|---|
| 1 | `pom.xml` | Java 21, JUnit Jupiter 5.11.x (test scope), Surefire 3.x. Builds a runnable jar (`Main-Class` set in the manifest via `maven-jar-plugin`). No other dependencies. |
| 2 | `src/main/java/.../Zoned.java` | Overpunch decode/encode for `S9(n)V99` fields, with high-order wrap (F5) |
| 3 | `.../FixedFile.java` | Reads LRECL text: strips CR, pads short lines, rejects lines longer than LRECL. Writes LF records. |
| 4 | `.../Records.java` | Field offsets and record accessors for TCATBAL, XREF, ACCOUNT, DISCGRP, TRAN |
| 5 | `.../InterestCalc.java` | Core logic (spec §3–4). Pure: takes inputs, PARM and a clock; returns the outputs, SYSOUT lines and RC. Abends as an internal `Abend` exception, so tests never call `System.exit`. |
| 6 | `.../Main.java` | CLI: `<PARM> <IN_DIR> <OUT_DIR>`, reads `CARDDEMO_CURRENT_DATE`, writes files only on RC 0, `System.exit(rc)` |
| 7 | `src/test/java/.../ZonedTest.java` | AC8 |
| 8 | `.../GoldenTest.java` | AC2, AC3, AC9: reads `../../legacy/golden` and `../../app/data/ASCII` in place, with no copies |
| 9 | `.../CharacterizationTest.java` | AC4, AC5, AC6, plus F4, F5, F7 pins |
| 10 | `.../AbendTest.java` | AC7: P1, P4, P5 and the missing-file case |
| 11 | `modernized/cbact04c/README.md` | Short: how to run, I/O contract, preserved-defect list linking to spec §6 |

Nothing outside `modernized/cbact04c/` changes.

## Test strategy

- **Equivalence (the proof):** both golden scenarios run in-process, with a byte comparison of `TRANSACT`, `ACCTFILE` and SYSOUT, and an RC check.
  - `CBACT04C`: 50 + 50 records, 152 SYSOUT lines.
  - `CBACT04C-after-CBTRN02C`: 50 + 50 records, 302 SYSOUT lines.
- **Characterization:** small synthetic inputs built in the test, with expected values taken from the COBOL probe runs P1–P6 in spec §7 (already executed; the results are quoted there). No golden files are created or changed.
- **Truncation pin:** pick a golden record where HALF_UP ≠ DOWN (28 of 50 differ) and assert the DOWN value, plus the P2 negative case `0000001032M`.
- **CLI smoke test:** run the jar end-to-end on both scenarios and `diff` against golden.

Commands:
```sh
cd modernized/cbact04c && mvn -q test                      # AC1-AC9
mvn -q package -DskipTests
for s in CBACT04C CBACT04C-after-CBTRN02C; do
  in=$(mktemp -d); out=$(mktemp -d)
  cp ../../app/data/ASCII/tcatbal.txt $in/TCATBALF.txt; cp ../../app/data/ASCII/cardxref.txt $in/XREFFILE.txt
  cp ../../app/data/ASCII/acctdata.txt $in/ACCTFILE.txt; cp ../../app/data/ASCII/discgrp.txt $in/DISCGRP.txt
  [ $s != CBACT04C ] && cp ../../legacy/golden/CBTRN02C/{ACCTFILE,TCATBALF}.txt $in/
  CARDDEMO_CURRENT_DATE=2022071800000000 java -jar target/cbact04c.jar 2022071800 $in $out > $out/SYSOUT.txt; echo $? > $out/RC.txt
  diff -r ../../legacy/golden/$s $out && echo "$s OK"
done
../../legacy/harness/golden.sh --check                     # legacy side still green
```

## Risks and mitigations

| # | Risk | Mitigation |
|---|---|---|
| R1 | Decimal drift (rounding or scale) | `BigDecimal` only. Exact product, then `setScale(2, DOWN)`, then wrap. The golden 50/50 comparison plus the P2 pins catch any drift. |
| R2 | Sign encoding (negative zero, `}`) | `Zoned` normalises a zero result to `{`; table-driven `ZonedTest`; P3 pin |
| R3 | XREF with several cards per account (untested in golden) | First match in file order. That equals the BDB alternate-key order for duplicates (insertion order). Documented as an assumption, not pinned. |
| R4 | TCATBAL not in key order in a real extract | Sort by the 17-byte key before processing (spec §2) |
| R5 | ACCTFILE pass-through corrupts untouched bytes | Edit the raw 300-byte record in place: only cols 13–24 and 79–102 change. The golden byte comparison covers this. |
| R6 | Timestamp (F4) breaks reproducibility | Injectable clock; tests freeze it at `2022-07-18 00:00:00.00` |
| R7 | Error paths not in golden | Messages and RC were taken from probe runs P1, P4, P5 of the real COBOL, not from guesses |
| R8 | Golden path resolution from Maven | Tests resolve `../../legacy/golden` from `user.dir`, with a clear failure message if it is missing |

## Decisions for the approver

- **D1 (Q1–Q3):** the intent defaults are applied: SYSOUT and RC match golden; dir-in / dir-out contract; `CARDDEMO_CURRENT_DATE` or UTC clock.
- **D2:** malformed input (over-long line, duplicate key, non-numeric zoned field) abends with RC 16 and status `0090`. The COBOL has no equivalent, because the harness loader rejects such input first. **Default: abend, don't guess.**
- **D3:** CLI exit code 2 on wrong usage (new; outside COBOL behaviour).

## Effort estimate (agent)

| Step | Minutes |
|---|---|
| pom + Zoned + FixedFile + Records | 8 |
| InterestCalc + Main | 10 |
| Tests (golden, characterization, abend, zoned) | 12 |
| Run, fix, CLI smoke diff, README | 8 |
| **Total build + test** | **≈ 38** |
