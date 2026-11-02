# CR-1 Build notes

Implemented `modernized/cbact04c/` per plan items 1–6 and 9 (pom, `Zoned`, `Records`, `Datasets`, `Cbact04c`, `Main`, README).
Tests and `legacy/tests/cbact04c-diff.sh` are left to the Test stage.

## Checked
- `mvn -q package -DskipTests` builds `target/cbact04c.jar` (JUnit 5 only; Surefire/jar plugins).
- Ad-hoc run with `--current-date 2022071800000000`, compared with `cmp`:
  - `CBACT04C`: TRANSACT, ACCTFILE, SYSOUT byte-identical, RC 0.
  - `CBACT04C-after-CBTRN02C`: TRANSACT, ACCTFILE, SYSOUT byte-identical, RC 0.
- Not yet run: error paths, negative balances, real group hits (Test stage / diff script).

## Notes
- `mvn -o` failed (plugins not cached); online build works.
- Open-failure SYSOUT text (status 35) is my reading of the CBL; the diff script must confirm it.
- RC 16 path prints `CEE3ABD: ABEND U0999` to stderr; TRANSACT/ACCTFILE still written on abend per spec.
