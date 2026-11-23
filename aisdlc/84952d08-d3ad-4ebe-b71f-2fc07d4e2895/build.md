# CR-1 Build notes

Implemented `modernized/cbact04c/` (Java 21, JUnit 5 only) and `legacy/tests/cbact04c-oracle.sh`.

**Evidence**
- `mvn test`: 13 tests green (golden x2, oracle cases, zoned, characterization).
- Java output is byte-identical to `legacy/golden/CBACT04C` and `CBACT04C-after-CBTRN02C`
  (TRANSACT, ACCTFILE and SYSOUT, RC 0).
- COBOL-generated oracle cases (`legacy/tests/cbact04c/`): negative, group-found, overflow, abend-acct,
  abend-xref, abend-default, empty. Java matches RC, SYSOUT and outputs.
- `golden.sh --check` OK; `legacy/golden` and `legacy/harness` unchanged vs `baseline`.

**Deviations from the approved spec**
1. Spec ACCTFILE offsets were wrong. Per `CVACT01Y`: cycle credit 79-90, cycle debit 91-102, group-id 113-122
   (spec said 77-88 / 89-100 / 111-120). Golden diff caught it; code uses the copybook.
2. `multicard` oracle (R2) is impossible: the XREF alternate key has no `WITH DUPLICATES`, so the harness load
   fails (status 22). The COBOL file cannot hold several cards per account; Java takes the first by card key.
3. Abend oracles keep only SYSOUT and RC: `run.sh` exports no datasets when RC is 16, so partial outputs
   after an abend are not covered by a COBOL oracle.
4. Package is `com.v2.carddemo.cbact04c` (per build instructions), not `carddemo.cbact04c`.
5. Negative zero (R1): COBOL writes `-0.00` as `{` in TRANSACT; see oracle `negative`.
