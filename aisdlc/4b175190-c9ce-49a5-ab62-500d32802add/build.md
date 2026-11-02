# CR-1 Build notes

Implemented `modernized/cbact04c/` per plan.md (pom, `Zoned`, `FixedFile`, `Cbact04c`, README).

- Both golden scenarios (`CBACT04C`, `CBACT04C-after-CBTRN02C`) pass: TRANSACT, ACCTFILE and SYSOUT are byte-equal, RC matches. They are covered by `GoldenTest`.
- `java -jar` smoke run with `CARDDEMO_CURRENT_DATE=2022071800000000` is byte-identical on stock data, RC 0.
- Not done (Test stage): unit and characterization tests (InterestMath, RateLookup, TranLayout, D1, Abend).
- Deviation: the package is `carddemo.cbact04c`, as in the plan.
- Abend: no output files are written (spec R9). A failed open on a missing file reports status 35; that code is my choice, since the spec gives none.
