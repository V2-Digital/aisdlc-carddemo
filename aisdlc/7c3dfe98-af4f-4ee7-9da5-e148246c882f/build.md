# CR-1 Build notes

Built `modernized/cbact04c/` (pom, `Zoned`, `Records`, `InterestCalc`, `AbendException`, `Main`, README) per plan.

**Evidence** (jar run with `--now 2022071800000000`):
- Scenario `CBACT04C`: TRANSACT, ACCTFILE, SYSOUT byte-identical to golden; RC 0.
- Scenario `CBACT04C-after-CBTRN02C`: same, byte-identical; RC 0.

**Spec correction:** spec.md gives cycle credit/debit as cols 83-106. From `CVACT01Y` they are cols 79-90 / 91-102.
The first run differed from the golden at byte 90; the code now uses 79-102 and matches.

**Not done (Test stage):** JUnit tests, abend-path checks (E1-E4), `golden.sh --check`.
Surefire/JUnit were not exercised yet; the build used online Maven (offline cache was empty).
