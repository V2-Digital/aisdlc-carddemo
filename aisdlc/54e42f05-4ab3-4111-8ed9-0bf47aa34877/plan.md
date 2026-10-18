# CR-3 Plan: CBTRN02C → Java 21

Behaviour is defined in [spec.md](spec.md). Background is in [intent.md](intent.md).

## Files (in order) under `modernized/cbtrn02c/`
1. `pom.xml`: Java 21, JUnit Jupiter 5 (test scope), Surefire, and a jar manifest with `Main-Class`. No other dependencies.
2. `src/main/java/carddemo/cbtrn02c/Overpunch.java`: zoned decimal ↔ `long` cents, plus `truncate(value, digits)` (D5/D9).
3. `.../RecordIo.java`: reads LF/CRLF lines and pads or truncates them to LRECL (`byte[]`, ISO-8859-1). Writes LF-terminated records.
4. `.../KsdsFile.java`: `TreeMap<String, byte[]>` keyed by a byte range. Provides read, write (fails on duplicate) and rewrite. Unloads in key order.
5. `.../Cbtrn02c.java`: the program, split into paragraphs that mirror COBOL (`validate`, `postTransaction`, `updateTcatbal`, `updateAccount`, `writeTransaction`, `writeReject`). Includes the sticky TCATBAL work area and an injected `Clock`/timestamp supplier. SYSOUT goes to a `PrintStream`.
6. `.../Main.java`: resolves DD paths from env/`-D`, sets the clock from `CARDDEMO_CURRENT_DATE`, and returns the exit code. An abend is an `AbendException` mapped to RC 16.
7. `src/test/java/...`: `OverpunchTest`, `RecordIoTest`, `ValidationRulesTest`, `PostingTest`, `ReturnCodeTest`, `LegacyDefectTest`, `GoldenEquivalenceTest`. The golden test reads `../../app/data/ASCII` and `../../legacy/golden/CBTRN02C` relative to the module.
8. `modernized/cbtrn02c/Dockerfile` (eclipse-temurin:21-jre, mounts `/data`) and `README.md` (run instructions plus defect list).
9. `legacy/tests/cbtrn02c_equiv.sh`: builds edge-case inputs into a temp `INPUT_DIR`, runs **both** COBOL (`legacy/harness/run.sh`) and Java, then diffs every output and the RC. The golden data and harness are untouched.

## Test strategy
- **Golden equivalence:** scenario `legacy/golden/CBTRN02C` (262/38/50/100 records, SYSOUT 54 lines, RC 4), with the clock fixed at `2022071800000000`.
- **COBOL-vs-Java differential** (`legacy/tests/cbtrn02c_equiv.sh`), covering what the golden data lacks:
  - (a) unknown card → 100;
  - (b) XREF to a missing account → 101;
  - (c) expired account plus overlimit → 103;
  - (d) amt 0 and negative amt;
  - (e) `limit == temp`;
  - (f) first TCATBAL access is a create (FILLER = spaces);
  - (g) zero rejects → RC 0;
  - (h) duplicate TRAN-ID → RC 16 with partial outputs;
  - (i) balance overflow near 10^10.

  Its results confirm or correct the spec before the unit tests are frozen.
- **Unit and characterization tests:** one per spec rule and per defect D1–D9 (see the spec AC table).

Commands:
```
cd modernized/cbtrn02c && mvn -q verify
legacy/tests/cbtrn02c_equiv.sh
legacy/harness/golden.sh --check
```

## Top risks
| Risk | Mitigation |
|---|---|
| Sticky TCATBAL FILLER differs from COBOL in some order | differential case (f), plus a create-after-update case |
| Truncation/sign semantics on overflow or −0 differ from GnuCOBOL | differential case (i); never emit `}` for zero unless COBOL does |
| What COBOL leaves in TRANFILE/ACCT after an abend (partial unload) | differential case (h); match whatever the harness unloads |
| PROC-TS hundredths / timezone | 16-digit clock form and UTC only, as the harness does |
| Golden test paths break when run outside the repo | paths come from a system property, defaulting to the repo-relative path |

## Estimate
Build ≈ 20 min, tests (unit + differential) ≈ 20 min. **≈ 40 min total.**
