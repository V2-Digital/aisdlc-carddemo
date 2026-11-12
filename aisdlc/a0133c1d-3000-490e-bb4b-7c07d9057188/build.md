# CR-1 Build notes

Implemented `modernized/cbact04c/` per plan (pom, `Zoned`, `FixedFile`, `Abend`, `Cbact04c`, README).
Package is `com.v2.carddemo.cbact04c` (the plan's `carddemo.cbact04c` was shortened; build rule says `com.v2.carddemo`).

## Evidence
- `mvn -q verify`: 15 tests green (golden equivalence ×2, F1/F2/F3/DEFAULT fallback, abends, clock, zoned).
- Golden: TRANSACT, ACCTFILE, SYSOUT and RC byte-equal for `CBACT04C` and `CBACT04C-after-CBTRN02C`.
- Built jar run with `DD_*` env vars: TRANSACT identical to golden.
- `legacy/harness/golden.sh --check`: OK. No changes under protected paths.

## Deviations / for the approver
- **Spec offset error:** ACCTFILE offsets in spec.md are wrong. Per `CVACT01Y`: curr-bal 12,12; cyc-credit **78**; cyc-debit **90**; group id **112** (spec said 66 / 78 / 100). Code uses the copybook values; golden bytes confirm.
- Not done: `legacy/tests/cbact04c_negzero.sh` (plan item 6). Zero is encoded `{` (matches golden); a truncated *negative* zero is unverified against COBOL. Left for Test stage.
- Characterization tests were written for the F-items since they were cheap; Test stage may extend them.
