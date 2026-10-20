# CR-3 Build notes

Java 21 port in `modernized/cbtrn02c/` (`com.v2.carddemo.cbtrn02c`: `Zoned`, `Datasets`, `Cbtrn02c`), plus `Dockerfile` and `scripts/compare.sh`.

## Evidence
- `mvn -q package -DskipTests` builds `target/cbtrn02c.jar` (JUnit 5 only; `-o` fails because plugins were not cached, so I built online).
- `scripts/compare.sh`: on stock data with the clock frozen, `TRANFILE`, `DALYREJS`, `ACCTFILE`, `TCATBALF`, `SYSOUT` and `RC` (4) are **byte-identical** to `legacy/golden/CBTRN02C`.
- Chain check (AC 7): CBACT04C run on the Java `ACCTFILE`/`TCATBALF` matches `golden/CBACT04C-after-CBTRN02C` (TRANSACT, ACCTFILE, SYSOUT, RC).
- No DD files set: abend path prints `ERROR OPENING DALYTRAN` / `FILE STATUS IS: NNNN0035`, exit 16.
- `legacy/harness/golden.sh --check`: OK.

## Finding not in the spec
On create, the COBOL `INITIALIZE` leaves the TCATBAL FILLER (cols 29-50) as the last successful READ left it. In the golden data every created record carries the stock filler (22 zeros), not spaces. The Java mirrors this (`tcatFiller`). The Test stage should pin it, and the spec line "create `key + 0 + 22 spaces`" is wrong.

## Not done (Test stage)
JUnit tests, `legacy/tests/cbtrn02c_reasons.sh`, duplicate-TRAN-ID and truncation references.

## Notes for a human
- Spec says the `DALYREJS` close-error message shows the XREFFILE status (D5). It is not mirrored yet; the Java close path only has a generic message. Decide whether Test should pin it as a unit-level stub.
