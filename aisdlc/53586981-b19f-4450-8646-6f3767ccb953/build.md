# CR-1 Build

Implemented `modernized/cbact04c/` (Java 21, JUnit 5 only) per plan items 1-6 and 8.

- `Zoned`, `Records`, `DdFiles`, `Cbact04c`, `Main` in `com.v2.carddemo.cbact04c`. Package is `com.v2.carddemo...` (per the build rules), not `carddemo.cbact04c` as written in plan.md.
- Container: `Dockerfile` and `README.md`.
- D1 (last account not rewritten) and the other defects are preserved as specified.

## Evidence
- `mvn -f modernized/cbact04c verify` passes. `GoldenTest` (2 tests) compares TRANSACT, ACCTFILE and SYSOUT byte-for-byte with `legacy/golden/` for `CBACT04C` and `CBACT04C-after-CBTRN02C`, with RC 0.
- The first build needed network access: `-o` failed on missing plugins.

## Left for the Test stage
`ZonedTest`, `InterestTest`, `RateZeroTest`, `DefectTest`, `AbendTest`, and `legacy/tests/cbact04c-edge.sh` (the COBOL cross-check of abend, negative and overflow paths). Overflow wrap is mod 10^n, as specified. It is unverified against GnuCOBOL.
