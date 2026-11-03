# CR-1 Build notes

**Built:** `modernized/cbact04c/` (pom, `Zoned`, `FixedRecords`, `Cbact04c`, README). No dependencies beyond JUnit 5 (test scope).

**Verified (CLI, diffed against golden):**
- Stock scenario: TRANSACT, ACCTFILE and SYSOUT byte-identical to `legacy/golden/CBACT04C`, exit 0.
- After-CBTRN02C scenario: byte-identical to `legacy/golden/CBACT04C-after-CBTRN02C`, exit 0.

**Spec correction:** the spec's ACCTFILE offsets were wrong. Per `CVACT01Y.cpy` they are cyc-credit 78, cyc-debit 90, group-id 112 (not 66/78/100). The code uses the copybook offsets. Test stage should use these.

**Not done (Test stage):** JUnit tests, `legacy/tests/cbact04c-abend.sh`. The abend text is from the spec and is not yet checked against the real COBOL.

**Notes:** `mvn -o` fails (plugins not cached); use online `mvn`. Only `Cbact04c.run(...)` returns the exit code; `main` calls `System.exit`.
