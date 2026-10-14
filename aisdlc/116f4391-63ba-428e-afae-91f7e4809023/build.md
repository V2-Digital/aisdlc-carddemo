# CR-1 Build notes

Implemented `modernized/cbact04c/` (pom, `Zoned`, `FileIo`, `AbendException`, `InterestCalc`, `Main`, README) per plan.

**Evidence:** `mvn package` green. `GoldenEquivalenceTest` (stock + after-CBTRN02C) is byte-equal on TRANSACT, ACCTFILE, SYSOUT, RC.

**Deviation from spec:** the spec's offsets for cycle credit/debit (75/87) and group id (109) were wrong. Per `CVACT01Y.cpy` they are 78, 90 and 112 (len 10). Code uses the copybook values; spec.md should be corrected.

**Not done (Test stage):** InterestTest, RateLookupTest, DefectCharacterizationTest, CliTest, ZonedTest, `legacy/tests/cbact04c-java-vs-cobol.sh`. The xref-duplicate (F3) order is still unverified against COBOL.
