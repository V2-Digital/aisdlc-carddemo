# CR-3 Build

Implemented `modernized/cbtrn02c/` per plan (package `com.v2.carddemo.cbtrn02c`, per the task's `com.v2.carddemo` rule; plan said `com.carddemo`).

**Files:** `pom.xml`, `Zoned`, `Datasets`, `Records`, `FileStatusException`, `Cbtrn02c` (program, `run()` returns RC), `Main`, `Dockerfile`, `run.sh`, `README.md` (D1-D9).

**Evidence:** `mvn package` OK (first build needs network for plugins). Ran jar on stock data with clock 2022-07-18 00:00:00.00:
- TRANFILE, DALYREJS, ACCTFILE, TCATBALF: `cmp` identical to `legacy/golden/CBTRN02C`.
- stdout identical to `SYSOUT.txt` (54 lines); RC 4.

**Not done (Test stage):** all JUnit tests, edge fixtures (`tcatbal-filler`, `zero-sign`, `temp-overflow`, `bal-overflow`, `dup-tranid`), `mvn verify` with tests. JUnit dependency is declared and Surefire gets `repo.root`.
**Notes:** abend paths (status 35/30/22, flush on abend) are implemented but not yet exercised. Docker image not built (no Docker here).
