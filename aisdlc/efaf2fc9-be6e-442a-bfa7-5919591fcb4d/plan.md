# CR-1 Plan — `modernized/cbact04c/`

Implements [spec.md](spec.md). No changes to `app/`, `legacy/golden/`, `legacy/harness/` or `.github/`.

## Files (in order)

1. `modernized/cbact04c/pom.xml`: Java 21, JUnit Jupiter 5.x, Surefire 3.x. No other dependencies.
2. `src/main/java/carddemo/cbact04c/`:
   - `Zoned.java`: overpunch codec and field-width truncation (`mod 10^n`).
   - `Records.java`: fixed-width read/patch helpers for the 5 layouts. The column constants come from the spec table.
   - `Datasets.java`: `DD_<name>` resolution, LRECL padding, LF/ISO-8859-1 I/O.
   - `Cbact04c.java`: `main` and the rules 1–8 loop. `Clock` is injectable through the `CARDDEMO_CURRENT_DATE` parse. `Abend` exits 16.
3. `src/test/java/carddemo/cbact04c/`: `GoldenTest`, `InterestTest`, `RateLookupTest`, `DefectTest`, `TranRecordTest`, `AbendTest`, `ZonedTest`.
   - The tests run `Cbact04c.run(env, out)` in-process on temp dirs, with no `System.exit` inside the tests.
   - Golden inputs: `app/data/ASCII/*.txt`, plus `legacy/golden/CBTRN02C/{ACCTFILE,TCATBALF}.txt` for scenario 2. Tests resolve them relative to the repo root (`../..`).
4. `modernized/cbact04c/README.md`: run command, env vars, and the preserved defects D1–D6.
5. `legacy/tests/cbact04c-differential.sh`: crafted inputs (negative balance, direct group hit, rate 0, missing acct/xref/DEFAULT) are run through `legacy/harness/run.sh CBACT04C` (with `INPUT_DIR`) and through the Java jar, then diffed per DD + SYSOUT + RC. This supplies the R5/R8 evidence that the goldens lack.

## Test strategy

- **Equivalence:** golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`. Byte compare of TRANSACT, ACCTFILE, SYSOUT, RC with clock `2022071800000000`.
- **Characterization:** D1 (acct 50), truncation (acct 2 → 29.24), the DEFAULT fallback, rate 0, the suffix wrap, and abends.
- Commands:
  ```sh
  cd modernized/cbact04c && mvn -q verify
  legacy/tests/cbact04c-differential.sh          # needs cobc; exits non-zero on any diff
  legacy/harness/golden.sh --check               # confirms that the goldens are untouched
  ```

## Risks

| Risk | Mitigation |
|---|---|
| Sign/overpunch or truncation mismatch on negative values (no golden coverage) | `ZonedTest`, plus the differential script against real COBOL |
| Abend-path output (status text, partial file state) differs from COBOL | Differential script for each trigger. If COBOL leaves partial output, match it |
| Input quirks: 36-byte xref lines, CRLF in `tcatbal.txt` | Pad and strip on read. Golden test 1 uses the raw ASCII files |
| Duplicate xref acct ids: which card wins | First in file order. Pinned in the differential script |
| SYSOUT trailing spaces / TCATBAL echo width | Echo the padded 50-byte record and compare byte-for-byte with the golden |

## Effort (agent)

| Step | Minutes |
|---|---|
| Build | ~15 |
| Tests and differential script | ~15 |
| **Total** | **~30** |
