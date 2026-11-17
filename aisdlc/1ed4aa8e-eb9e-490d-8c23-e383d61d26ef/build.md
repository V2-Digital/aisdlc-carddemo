# CR-1 Build notes

Built `modernized/cbact04c/` (pom, `Zoned`, `Datasets`, `Abend`, `InterestCalc`, `Main`, README, Dockerfile) per plan.

**Evidence (jar run, PARM 2022071800, frozen clock):**
- `CBACT04C` (stock data): TRANSACT, ACCTFILE and SYSOUT byte-identical to `legacy/golden/CBACT04C`, exit 0.
- `CBACT04C-after-CBTRN02C` (ACCTFILE and TCATBALF from `golden/CBTRN02C`): TRANSACT, ACCTFILE and SYSOUT byte-identical, exit 0.

**Deviations from plan**
- Package is `com.v2.carddemo.cbact04c` (per the build instructions).
- `mvn -o` could not resolve plugins on a cold cache, so the first build ran online.
- Not yet done, left for Test: JUnit tests, `legacy/tests/cbact04c-diff.sh` (sign-of-zero, negative truncation and abend paths are unverified against GnuCOBOL), `golden.sh --check`.
- Abend paths and open-error texts are implemented from the spec but untested.
