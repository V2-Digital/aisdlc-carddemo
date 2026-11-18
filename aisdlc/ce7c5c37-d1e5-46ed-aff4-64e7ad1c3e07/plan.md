# CR-1 Plan — build `modernized/cbact04c/`

Spec: [spec.md](spec.md). Intent: [intent.md](intent.md).

## Files (in order)

1. `modernized/cbact04c/pom.xml` — Java 21, JUnit Jupiter, Surefire; jar with `Main-Class`. No other deps.
2. `src/main/java/carddemo/cbact04c/Zoned.java` — overpunch decode/encode, high-order truncation.
3. `.../Records.java` — fixed-width field offsets for the 5 layouts (spec table), line read/pad/write.
4. `.../Cbact04c.java` — rules 1-8; injectable `parm`, `clock`, in/out dirs; returns RC; `Abend` exception carries SYSOUT lines.
5. `.../Main.java` — CLI args → `Cbact04c`, writes outputs, `System.exit(rc)`.
6. `src/test/java/...`: `ZonedTest`, `InterestTest`, `ZeroRateTest`, `CharacterizationTest`, `GoldenEquivalenceTest` (reads `../../legacy/golden` and `../../app/data/ASCII` via relative paths).
7. `modernized/cbact04c/README.md` — how to run, link to preserved defects.

No change to `app/`, `legacy/golden/`, `legacy/harness/`, `.github/`.

## Test strategy

- **Equivalence:** golden scenarios `CBACT04C` and `CBACT04C-after-CBTRN02C`; TRANSACT, ACCTFILE, SYSOUT, RC compared byte-for-byte (`assertArrayEquals` on file bytes, first differing line in the message).
- **Characterization / unit:** tests listed in spec "Acceptance tests" (D1, D2 fallback, abends, D3 truncation incl. negative, D6 zero rate, D7 wrap, codec).
- **Legacy cross-check:** `legacy/tests/cbact04c-java-vs-cobol.sh` runs `legacy/harness/run.sh CBACT04C` and the jar on the same `INPUT_DIR` (both scenarios) and diffs all four outputs — proves the jar also matches the live COBOL, not just the stored golden.

Commands:
```sh
cd modernized/cbact04c && mvn -q verify
legacy/tests/cbact04c-java-vs-cobol.sh
legacy/harness/golden.sh --check
git diff --stat baseline -- legacy/golden legacy/harness .github   # must be empty
```

## Risks

| Risk | Mitigation |
|---|---|
| Overpunch / sign-of-zero mismatch breaks byte identity | One codec, exhaustive `ZonedTest`; golden diff catches the rest |
| Wrong rounding (HALF_UP creeping in) | `RoundingMode.DOWN` only, explicit test 29.249625 → 29.24 |
| Input normalisation (CRLF tcatbal, 36-byte cardxref) | Strip `\r`, pad to LRECL in one reader; stock-data scenario exercises both |
| SYSOUT ordering/wording drift | Golden SYSOUT compared byte-for-byte (152 / 302 lines) |
| Abend-path outputs unverified by golden | Tests assert SYSOUT + RC only; noted in spec |

## Effort

Build ≈ 15 min, test + cross-check ≈ 10 min. Total ≈ 25 min.
