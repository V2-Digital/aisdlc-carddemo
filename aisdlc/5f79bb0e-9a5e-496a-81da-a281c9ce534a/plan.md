# CR-3 Plan: CBTRN02C in Java 21

Run `5f79bb0e-9a5e-496a-81da-a281c9ce534a` · Stage: Design · Implements `spec.md`

## 1. Files, in build order

All paths are under `modernized/cbtrn02c/`. Package `carddemo.cbtrn02c`. No frameworks.

| # | File | Content |
|---|---|---|
| 1 | `pom.xml` | Java 21 (`maven.compiler.release`), JUnit Jupiter 5.x (test scope), Surefire 3.x. No other plugins or dependencies |
| 2 | `src/main/java/.../Zoned.java` | Overpunch decode to cents, encode with truncation (`trunc(v, digits)`) |
| 3 | `.../Records.java` | Column constants and fixed-width get/set helpers for the 5 layouts (spec §3) |
| 4 | `.../TextDataset.java` | Read (strip CR, pad to LRECL, reject long lines) and write (LF). `KsdsFile` = `TreeMap<String,char[]>` with `read`/`write`/`rewrite` returning a COBOL status (`00`/`22`/`23`) |
| 5 | `.../Cbtrn02c.java` | Spec §4–5: the processing loop, carried `tcatWork`/`acctWork`, SYSOUT sink, `Clock`, abend → `Abend` exception with exit 16 |
| 6 | `.../Main.java` | CLI `<IN_DIR> <OUT_DIR>`, `CARDDEMO_CURRENT_DATE` → fixed `Clock`, `System.exit(rc)` |
| 7 | `src/test/resources/probes/<case>/{in,expected}/` | Inputs, plus outputs captured once from `legacy/harness/run.sh` (see §3) |
| 8 | `src/test/java/...` | Tests in §2 |
| 9 | `modernized/cbtrn02c/README.md` | Run instructions, defect list F1–F8 (short) |

The golden test reads `app/data/ASCII/*` and `legacy/golden/CBTRN02C/*` **in place**, via a path relative to the module (`../../`). No copies are made, so the golden files stay the single source of truth.

## 2. Test strategy

| Test class | What it proves | AC |
|---|---|---|
| `GoldenTest` | Stock inputs (raw files: 36-byte xref, CRLF tcatbal) and clock `2022071800000000` give `TRANFILE`/`DALYREJS`/`ACCTFILE`/`TCATBALF`/`SYSOUT` byte-equal to golden, plus RC 4 | 2, 3, 4, 8 |
| `ZonedTest` | All 20 sign chars. Unsigned digit = +. `0 + (−0)` → `{`. Truncation mod 10^11 / 10^12 | F7 |
| `TimestampTest` | `…000000` and `…12.34.56.780000` | 5 |
| `HarnessProbeTest` (parameterised over `probes/*`) | Java output = captured harness output, for every file present + SYSOUT + RC | 3, 6, 7 |
| `DefectTest` | F4: a store seam makes the account rewrite return `23` → TRAN posted, not counted as a reject, RC unchanged. F6: the close-error message carries the XREF status. F2: golden reject count is 38 (pinned), not 23 | 7 |

**Harness probes** (captured during Build with `INPUT_DIR=<case>/in legacy/harness/run.sh CBTRN02C <case>/expected`):

| Case | Input (built from stock data) | Pins |
|---|---|---|
| `reasons` | stock records 2, 1 with card `9999999999999999`, 1 with id `…X` / amt `99999999999` / ORIG `2030-…`, 1 with id `…Y` / card `0500024453765740`. ACCTFILE without acct 50 | 100, 101, 102→103 override, F1 (22-space FILLER), RC 4 (reproduces run2) |
| `accept-only` | stock record 1 | RC 0, empty DALYREJS |
| `neg-zero` | record 1 with amt `0000000000}` | `}` kept in TRAN, `{` in balances |
| `unsigned-amt` | record 1 with amt `00000001005` | unsigned = + |
| `f7-truncation` | record 1. Acct 7 CYC-CREDIT `10000000000{`. TCAT `…07010001` bal `99999999999` | F7: accepted; tcat `0000005047F` |
| `dup-id` | stock record 1, 2, 2 | RC 16, 5 SYSOUT lines, `NNNN0022`. Datasets not compared (harness exports none) |

All six probes already ran in Design (`/tmp/run2..6`, `/tmp/run3` = dup-id). The results are quoted in spec §6–7. Build re-captures them into the repo.

**Commands**

```sh
cd modernized/cbtrn02c && mvn -q verify                       # AC1: all tests
legacy/harness/golden.sh --check                              # golden still reproduces (no harness changes)
CARDDEMO_CURRENT_DATE=2022071800000000 java -cp modernized/cbtrn02c/target/classes \
  carddemo.cbtrn02c.Main <in> /tmp/j && diff -r /tmp/j legacy/golden/CBTRN02C   # manual end-to-end (RC.txt excluded)
```

For the manual diff, `<in>` must hold the DD-named inputs. The CLI also accepts the stock file names through a `--stock <app/data/ASCII>` switch, which maps `dailytran/cardxref/acctdata/tcatbal.txt`. CI runs `mvn test` per `modernized/*/pom.xml` (`.github/workflows/ci.yml:44`), which covers the golden test.

## 3. Risks and mitigations

| Risk | Mitigation |
|---|---|
| Overpunch/zero encoding differs from GnuCOBOL (`{` vs `}`) | `ZonedTest`, golden (25 `{`, 6 `}`), `neg-zero` probe |
| Raw-copy vs re-encode mistakes (e.g. normalising the TRAN amount) | Spec rule: MOVE = raw bytes. Golden TRANFILE has 6 `}` amounts copied as-is |
| F1 work-area leak modelled wrongly | `reasons` probe (spaces) + golden (zeros) cover both paths |
| Truncation off by a digit (S9(9) vs S9(10)) | `f7-truncation` probe against real GnuCOBOL output |
| SYSOUT drift (spacing in `REJECTED  :`, 9-digit counters) | golden SYSOUT is byte-compared |
| Abend dataset state cannot be verified against the harness | Documented in spec §5. Only RC and SYSOUT are pinned |
| Golden path resolution in CI | Test resolves paths from `user.dir` (Maven runs in the module dir), with a clear failure message if the files are missing |
| Clock read per record vs once per run | Read per posted record, like COBOL. The test uses a fixed clock, so either is invisible. A real run can show different hundredths, as legacy does |

## 4. Effort (agent)

| Step | Min |
|---|---|
| pom, codec, records, dataset I/O | 10 |
| Main processing + CLI | 10 |
| Capture 6 probes + tests | 12 |
| Green run, golden diff, README | 5 |
| **Build + Test total** | **≈ 37** |
