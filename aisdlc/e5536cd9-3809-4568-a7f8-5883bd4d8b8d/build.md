# Build notes
- Implemented modernized/cbact04c (Zoned, FixedRecords, InterestCalc, Main) + legacy/tests/cbact04c-java-equiv.sh + README.
- Java vs golden and vs live COBOL: TRANSACT, ACCTFILE, SYSOUT byte-identical in both scenarios.
- Spec correction: ACCTFILE offsets are cycle-credit 78, cycle-debit 90, group-id 112 (spec said 76/88/110); from CVACT01Y.
- Not done (Test stage): JUnit tests. Offline Maven lacks resources plugin, so builds use online Maven Central.
