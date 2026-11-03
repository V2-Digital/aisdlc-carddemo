# Build notes — CR-1

Built `modernized/cbact04c/` (Java 21, JUnit 5 + Surefire only): `Zoned`, `Datasets`, `Cbact04c`, `Main`, `GoldenTest`.

- `mvn -q test`: green. `GoldenTest` byte-compares TRANSACT, ACCTFILE, SYSOUT and RC for `CBACT04C` and
  `CBACT04C-after-CBTRN02C`.
- **Spec correction:** the spec gave account offsets 246/258/280 for cycle credit/debit and group id. These are
  wrong for the 300-byte `CVACT01Y` layout. Correct 1-based: credit 79, debit 91, group id 113. Used the copybook.
- Differences from plan: the packages are `com.v2.carddemo.cbact04c`. `Records.java` is not separate; offsets are inline in
  `Cbact04c`. `Main` takes `<inDir> <outDir> <PARM>` (or `DATA_DIR`/`OUT_DIR` + PARM); `DD_*` per-file paths are not supported.
- Not done (Test stage): `InterestTest`, `ZeroRateTest`, `RateFallbackTest`, `LastAccountDefectTest`,
  `TransactLayoutTest`, `ZonedTest`, `legacy/tests/cbact04c_edge.sh`.
- Abend path text: for a missing account/xref, the file-status line is printed as `FILE STATUS IS: NNNN0023`, which is
  unverified against the harness. The Test stage's cross-check must confirm it.
