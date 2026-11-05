# CR-1 Build notes

Implemented `modernized/cbact04c/` (pom, `Zoned`, `Datasets`, `Cbact04c`, README). `mvn -q package` OK.

**Evidence (ad-hoc run, clock `2022071800000000`):** both scenarios, `CBACT04C` and `CBACT04C-after-CBTRN02C`,
give byte-identical TRANSACT, ACCTFILE, SYSOUT and RC against `legacy/golden/`.

**Spec deviation:** spec.md lists ACCT cycle credit/debit at cols 179-190/191-202 and group id at 213-222.
`CVACT01Y.cpy` puts them at 79-90 / 91-102 / 113-122 (the other fields shift down by the same offset).
The code uses the copybook offsets, and this is what makes ACCTFILE match the golden.

**Not done yet (Test stage):** JUnit tests, differential script, abend-path checks against COBOL.
Abend paths are implemented from code reading and are unverified.
